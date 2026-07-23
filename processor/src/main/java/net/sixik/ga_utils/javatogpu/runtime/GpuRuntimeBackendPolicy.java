package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.runtime.methodtest.*;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifact;
import net.sixik.ga_utils.javatogpu.runtime.opencl.OpenClGpuRuntimeBackend;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable backend selection policy that combines capability requirements with an ordered fallback chain.
 *
 * <p>This is a convenience layer on top of {@link GpuRuntimeRequirement} and {@link GpuRuntimeBackendFactory}. It is
 * meant for application code that wants to express preference order once and then reuse it repeatedly.
 *
 * <pre>{@code
 * GpuRuntimeBackendPolicy policy = GpuRuntimeBackendPolicy.builder()
 *         .minimumApiVersion(GpuBackendTarget.OPENCL, 3, 0)
 *         .requireFeature(GpuBackendTarget.OPENCL, GpuRuntimeFeature.IMAGES)
 *         .preferOpenClSharedCache()
 *         .build();
 *
 * try (GpuRuntimeScope ignored = GpuRuntime.use(policy)) {
 *     DemoKernel.invoke(input, output);
 * }
 * }</pre>
 */
public final class GpuRuntimeBackendPolicy {

    private final List<GpuRuntimeRequirement> requirements;
    private final List<GpuRuntimeBackendRequirement> backendRequirements;
    private final List<GpuRuntimeBackendFactory> candidateFactories;
    private final List<GpuRuntimeBackendOwnership> candidateOwnerships;
    private final List<GpuRuntimeBackendCandidateMetadata> candidateMetadata;
    private final GpuRuntimeBackendCandidateOrdering candidateOrdering;
    private final List<GpuRuntimeBackendScoreContributor> scoreContributors;
    private final GpuRuntimeCompileOptions scoreCompileOptions;
    private final Optional<GpuKernelDescriptor> scoreDescriptor;
    private final Optional<IrGpuArtifact> scoreIrGpuArtifact;
    private final Optional<GpuRuntimeDeviceProfile> scoreDeviceProfile;
    private final Optional<GpuBackendCompilerFeedbackReport> scoreCompilerFeedbackReport;
    private final Optional<GpuRuntimeWorkloadHints> scoreWorkloadHints;

    private GpuRuntimeBackendPolicy(
            List<GpuRuntimeRequirement> requirements,
            List<GpuRuntimeBackendRequirement> backendRequirements,
            List<GpuRuntimeBackendFactory> candidateFactories,
            List<GpuRuntimeBackendOwnership> candidateOwnerships,
            List<GpuRuntimeBackendCandidateMetadata> candidateMetadata,
            GpuRuntimeBackendCandidateOrdering candidateOrdering,
            List<GpuRuntimeBackendScoreContributor> scoreContributors,
            GpuRuntimeCompileOptions scoreCompileOptions,
            Optional<GpuKernelDescriptor> scoreDescriptor,
            Optional<IrGpuArtifact> scoreIrGpuArtifact,
            Optional<GpuRuntimeDeviceProfile> scoreDeviceProfile,
            Optional<GpuBackendCompilerFeedbackReport> scoreCompilerFeedbackReport,
            Optional<GpuRuntimeWorkloadHints> scoreWorkloadHints
    ) {
        this.requirements = List.copyOf(requirements);
        this.backendRequirements = List.copyOf(backendRequirements);
        this.candidateFactories = List.copyOf(candidateFactories);
        this.candidateOwnerships = List.copyOf(candidateOwnerships);
        this.candidateMetadata = List.copyOf(candidateMetadata);
        this.candidateOrdering = candidateOrdering == null
                ? GpuRuntimeBackendCandidateOrdering.FALLBACK_ORDER
                : candidateOrdering;
        this.scoreContributors = scoreContributors == null ? List.of() : List.copyOf(scoreContributors);
        this.scoreCompileOptions = scoreCompileOptions == null
                ? GpuRuntimeCompileOptions.defaults(GpuBackendTarget.UNKNOWN)
                : scoreCompileOptions;
        this.scoreDescriptor = scoreDescriptor == null ? Optional.empty() : scoreDescriptor;
        this.scoreIrGpuArtifact = scoreIrGpuArtifact == null ? Optional.empty() : scoreIrGpuArtifact;
        this.scoreDeviceProfile = scoreDeviceProfile == null ? Optional.empty() : scoreDeviceProfile;
        this.scoreCompilerFeedbackReport = scoreCompilerFeedbackReport == null
                ? Optional.empty()
                : scoreCompilerFeedbackReport;
        this.scoreWorkloadHints = scoreWorkloadHints == null ? Optional.empty() : scoreWorkloadHints;
    }

    /**
     * Creates a new mutable builder.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns the ordered capability requirements applied to every candidate report.
     */
    public List<GpuRuntimeRequirement> requirements() {
        return requirements;
    }

    /**
     * Returns candidate requirements that can inspect provider/catalog metadata in addition to runtime reports.
     */
    public List<GpuRuntimeBackendRequirement> backendRequirements() {
        return backendRequirements;
    }

    /**
     * Returns the ordered fallback chain used for backend creation and selection.
     */
    public List<GpuRuntimeBackendFactory> candidateFactories() {
        return candidateFactories;
    }

    /**
     * Returns the ownership policy applied to the ordered fallback chain.
     */
    public List<GpuRuntimeBackendOwnership> candidateOwnerships() {
        return candidateOwnerships;
    }

    /**
     * Returns optional catalog/provider metadata for each candidate in the fallback chain.
     */
    public List<GpuRuntimeBackendCandidateMetadata> candidateMetadata() {
        return candidateMetadata;
    }

    /**
     * Returns how candidates are ordered after hard requirements are applied.
     */
    public GpuRuntimeBackendCandidateOrdering candidateOrdering() {
        return candidateOrdering;
    }

    /**
     * Returns explicit read-only score contributors attached to this policy.
     */
    public List<GpuRuntimeBackendScoreContributor> scoreContributors() {
        return scoreContributors;
    }

    /**
     * Returns compile options exposed to score contributors as workload context.
     */
    public GpuRuntimeCompileOptions scoreCompileOptions() {
        return scoreCompileOptions;
    }

    /**
     * Returns optional kernel descriptor exposed to workload-aware score contributors.
     */
    public Optional<GpuKernelDescriptor> scoreDescriptor() {
        return scoreDescriptor;
    }

    /**
     * Returns optional IrGpu artifact exposed to workload-aware score contributors.
     */
    public Optional<IrGpuArtifact> scoreIrGpuArtifact() {
        return scoreIrGpuArtifact;
    }

    /**
     * Returns optional device profile exposed to workload-aware score contributors.
     */
    public Optional<GpuRuntimeDeviceProfile> scoreDeviceProfile() {
        return scoreDeviceProfile;
    }

    /**
     * Returns optional precomputed compiler feedback exposed to score contributors.
     */
    public Optional<GpuBackendCompilerFeedbackReport> scoreCompilerFeedbackReport() {
        return scoreCompilerFeedbackReport;
    }

    /**
     * Returns optional workload intent exposed to advisory score contributors.
     */
    public Optional<GpuRuntimeWorkloadHints> scoreWorkloadHints() {
        return scoreWorkloadHints;
    }

    /**
     * Creates backend candidates, returns the first matching backend, and leaves ownership of that backend to the
     * caller.
     *
     * <p>Rejected candidates are closed automatically when possible. If the returned backend implements
     * {@link AutoCloseable}, the caller is responsible for closing it.
     */
    public GpuRuntimeBackendSelection select() {
        return trySelect().requireSelection();
    }

    /**
     * Creates backend candidates and returns a non-throwing selection result.
     *
     * <p>This is the preferred API for "capability precheck + skip" flows where the application wants to inspect the
     * miss reason and decide what to do next without relying on exception-based control flow.
     */
    public GpuRuntimeSelectionResult trySelect() {
        return GpuRuntimeBackendSelectionOrchestrator.select(this);
    }

    /**
     * Creates backend candidates, installs the first matching backend as an owned runtime scope, and closes rejected
     * candidates automatically when possible.
     */
    public GpuRuntimeScope use() {
        return select().install();
    }

    /**
     * Fluent builder for {@link GpuRuntimeBackendPolicy}.
     */
    public static final class Builder {

        private final List<GpuRuntimeRequirement> requirements = new ArrayList<>();
        private final List<GpuRuntimeBackendRequirement> backendRequirements = new ArrayList<>();
        private final List<GpuRuntimeBackendFactory> candidateFactories = new ArrayList<>();
        private final List<GpuRuntimeBackendOwnership> candidateOwnerships = new ArrayList<>();
        private final List<GpuRuntimeBackendCandidateMetadata> candidateMetadata = new ArrayList<>();
        private final List<GpuRuntimeBackendScoreContributor> scoreContributors = new ArrayList<>();
        private GpuRuntimeBackendCandidateOrdering candidateOrdering = GpuRuntimeBackendCandidateOrdering.FALLBACK_ORDER;
        private GpuRuntimeCompileOptions scoreCompileOptions = GpuRuntimeCompileOptions.defaults(GpuBackendTarget.UNKNOWN);
        private Optional<GpuKernelDescriptor> scoreDescriptor = Optional.empty();
        private Optional<IrGpuArtifact> scoreIrGpuArtifact = Optional.empty();
        private Optional<GpuRuntimeDeviceProfile> scoreDeviceProfile = Optional.empty();
        private Optional<GpuBackendCompilerFeedbackReport> scoreCompilerFeedbackReport = Optional.empty();
        private Optional<GpuRuntimeWorkloadHints> scoreWorkloadHints = Optional.empty();

        private Builder() {
        }

        /**
         * Adds one capability requirement.
         */
        public Builder require(GpuRuntimeRequirement requirement) {
            requirements.add(Objects.requireNonNull(requirement, "requirement"));
            return this;
        }

        /**
         * Adds one candidate requirement that can inspect catalog/provider metadata.
         */
        public Builder requireBackend(GpuRuntimeBackendRequirement requirement) {
            backendRequirements.add(Objects.requireNonNull(requirement, "requirement"));
            return this;
        }

        /**
         * Sets the candidate ordering mode used after hard requirements are evaluated.
         */
        public Builder candidateOrdering(GpuRuntimeBackendCandidateOrdering ordering) {
            candidateOrdering = Objects.requireNonNull(ordering, "ordering");
            return this;
        }

        /**
         * Opts into selecting the highest-scoring candidate instead of the first matching fallback candidate.
         */
        public Builder rankCandidatesByScore() {
            return candidateOrdering(GpuRuntimeBackendCandidateOrdering.SCORE_DESCENDING);
        }

        /**
         * Adds one read-only candidate score contributor. It affects score evidence immediately, but candidate order
         * changes only when {@link #rankCandidatesByScore()} is enabled.
         */
        public Builder scoreCandidatesWith(GpuRuntimeBackendScoreContributor contributor) {
            scoreContributors.add(Objects.requireNonNull(contributor, "contributor"));
            return this;
        }

        /**
         * Adds read-only candidate score contributors in deterministic caller-provided order.
         */
        public Builder scoreCandidatesWith(Collection<? extends GpuRuntimeBackendScoreContributor> contributors) {
            Objects.requireNonNull(contributors, "contributors");
            for (GpuRuntimeBackendScoreContributor contributor : contributors) {
                scoreCandidatesWith(contributor);
            }
            return this;
        }

        /**
         * Attaches compile options as workload context for score contributors.
         */
        public Builder scoreCandidatesForCompileOptions(GpuRuntimeCompileOptions compileOptions) {
            scoreCompileOptions = Objects.requireNonNull(compileOptions, "compileOptions");
            return this;
        }

        /**
         * Attaches a kernel descriptor as workload context for score contributors.
         */
        public Builder scoreCandidatesForKernel(GpuKernelDescriptor descriptor) {
            scoreDescriptor = Optional.of(Objects.requireNonNull(descriptor, "descriptor"));
            scoreIrGpuArtifact = Optional.empty();
            return this;
        }

        /**
         * Attaches a kernel descriptor and preloaded IrGpu artifact as workload context for score contributors.
         */
        public Builder scoreCandidatesForKernel(GpuKernelDescriptor descriptor, IrGpuArtifact irGpuArtifact) {
            scoreDescriptor = Optional.of(Objects.requireNonNull(descriptor, "descriptor"));
            scoreIrGpuArtifact = Optional.ofNullable(irGpuArtifact);
            return this;
        }

        /**
         * Attaches a full compile request as workload context for score contributors.
         */
        public Builder scoreCandidatesForCompileRequest(GpuRuntimeCompileRequest request) {
            GpuRuntimeCompileRequest value = Objects.requireNonNull(request, "request");
            scoreDescriptor = Optional.of(value.descriptor());
            scoreCompileOptions = value.options();
            scoreDeviceProfile = Optional.of(value.deviceProfile());
            scoreIrGpuArtifact = value.irGpuArtifact();
            return this;
        }

        /**
         * Attaches precomputed compiler feedback as advisory score evidence.
         */
        public Builder scoreCandidatesForCompilerFeedback(GpuBackendCompilerFeedbackReport report) {
            scoreCompilerFeedbackReport = Optional.of(Objects.requireNonNull(report, "report"));
            return this;
        }

        /**
         * Adds the standard compiler-feedback score bridge for a precomputed feedback report.
         */
        public Builder scoreCandidatesWithCompilerFeedback(GpuBackendCompilerFeedbackReport report) {
            scoreCandidatesForCompilerFeedback(report);
            return scoreCandidatesWith(
                    net.sixik.ga_utils.javatogpu.runtime.selection.GpuBackendCompilerFeedbackScoreContributor.fromContext()
            );
        }

        /**
         * Attaches workload intent as advisory score context.
         */
        public Builder scoreCandidatesForWorkloadHints(GpuRuntimeWorkloadHints hints) {
            scoreWorkloadHints = Optional.of(Objects.requireNonNull(hints, "hints"));
            return this;
        }

        /**
         * Adds the standard workload-hints score bridge for caller-provided workload intent.
         */
        public Builder scoreCandidatesWithWorkloadHints(GpuRuntimeWorkloadHints hints) {
            scoreCandidatesForWorkloadHints(hints);
            return scoreCandidatesWith(
                    net.sixik.ga_utils.javatogpu.runtime.selection.GpuRuntimeWorkloadHintBackendScoreContributor.fromContext()
            );
        }

        /**
         * Adds the standard inferred workload-hints score bridge for already-attached descriptor/IrGpu context.
         */
        public Builder scoreCandidatesWithInferredWorkloadHints() {
            return scoreCandidatesWith(
                    net.sixik.ga_utils.javatogpu.runtime.selection.GpuRuntimeInferredWorkloadHintBackendScoreContributor.fromContext()
            );
        }

        /**
         * Attaches a kernel descriptor and adds the standard inferred workload-hints score bridge.
         */
        public Builder scoreCandidatesWithInferredWorkloadHints(GpuKernelDescriptor descriptor) {
            scoreCandidatesForKernel(descriptor);
            return scoreCandidatesWithInferredWorkloadHints();
        }

        /**
         * Attaches a kernel descriptor plus preloaded IrGpu artifact and adds inferred workload scoring.
         */
        public Builder scoreCandidatesWithInferredWorkloadHints(
                GpuKernelDescriptor descriptor,
                IrGpuArtifact irGpuArtifact
        ) {
            scoreCandidatesForKernel(descriptor, irGpuArtifact);
            return scoreCandidatesWithInferredWorkloadHints();
        }

        /**
         * Adds the standard cache-only {@code @GPUTest} backend score bridge.
         */
        public Builder scoreCandidatesWithCachedMethodTestProbeEvidence() {
            return scoreCandidatesWith(GpuRuntimeMethodTestBackendScoreContributor.cacheOnly());
        }

        /**
         * Requires the selected backend to belong to the given backend target family.
         */
        public Builder requireBackendTarget(GpuBackendTarget backendTarget) {
            return require(GpuRuntimeRequirements.requireBackendTarget(backendTarget));
        }

        /**
         * Alias for {@link #requireBackendTarget(GpuBackendTarget)} for callers that want explicit force semantics.
         */
        public Builder forceBackendTarget(GpuBackendTarget backendTarget) {
            return requireBackendTarget(backendTarget);
        }

        /**
         * Rejects backend candidates from the given backend target family.
         */
        public Builder excludeBackendTarget(GpuBackendTarget backendTarget) {
            return require(GpuRuntimeRequirements.excludeBackendTarget(backendTarget));
        }

        /**
         * Appends one catalog entry to the fallback chain.
         */
        public Builder preferCatalogEntry(GpuRuntimeBackendCatalogEntry entry) {
            Objects.requireNonNull(entry, "entry");
            candidateFactories.add(entry.factory());
            candidateOwnerships.add(entry.ownership());
            candidateMetadata.add(GpuRuntimeBackendCandidateMetadata.from(entry));
            return this;
        }

        /**
         * Appends catalog entries to the fallback chain in the order provided.
         */
        public Builder preferCatalog(List<GpuRuntimeBackendCatalogEntry> entries) {
            Objects.requireNonNull(entries, "entries");
            for (GpuRuntimeBackendCatalogEntry entry : entries) {
                preferCatalogEntry(entry);
            }
            return this;
        }

        /**
         * Appends the standard production-ready runtime backend catalog.
         */
        public Builder preferStandardBackends() {
            return preferCatalog(GpuRuntimeBackendCatalog.standard());
        }

        /**
         * Appends standard production-ready backends plus explicit unsupported placeholders for planned backends.
         */
        public Builder preferStandardBackendsWithPlannedDiagnostics() {
            return preferCatalog(GpuRuntimeBackendCatalog.standardWithPlannedBackends());
        }

        /**
         * Adds a requirement for the given feature on any backend.
         */
        public Builder requireFeature(GpuRuntimeFeature feature) {
            return require(GpuRuntimeRequirements.requireFeature(feature));
        }

        /**
         * Adds a requirement for the given feature on the specified backend family.
         */
        public Builder requireFeature(GpuBackendTarget backendTarget, GpuRuntimeFeature feature) {
            return require(GpuRuntimeRequirements.requireFeature(backendTarget, feature));
        }

        /**
         * Requires candidates to declare that they can emit or consume the given module format.
         */
        public Builder requireDeclaredModuleFormat(GpuBackendModuleFormat moduleFormat) {
            return requireBackend(GpuRuntimeRequirements.requireDeclaredModuleFormat(moduleFormat));
        }

        /**
         * Requires candidates from one backend family to declare the given module format.
         */
        public Builder requireDeclaredModuleFormat(
                GpuBackendTarget backendTarget,
                GpuBackendModuleFormat moduleFormat
        ) {
            return requireBackend(GpuRuntimeRequirements.requireDeclaredModuleFormat(backendTarget, moduleFormat));
        }

        /**
         * Requires candidates to declare that they expose the given backend-neutral capability fact.
         */
        public Builder requireDeclaredCapability(GpuRuntimeCapability capability) {
            return requireBackend(GpuRuntimeRequirements.requireDeclaredCapability(capability));
        }

        /**
         * Requires candidates from one backend family to declare the given backend-neutral capability fact.
         */
        public Builder requireDeclaredCapability(
                GpuBackendTarget backendTarget,
                GpuRuntimeCapability capability
        ) {
            return requireBackend(GpuRuntimeRequirements.requireDeclaredCapability(backendTarget, capability));
        }

        /**
         * Requires candidates to declare a complete compile/prepare/invoke execution pipeline.
         */
        public Builder requireExecutionPipelineAvailable() {
            return requireBackend(GpuRuntimeRequirements.requireExecutionPipelineAvailable());
        }

        /**
         * Requires candidates from one backend family to declare a complete compile/prepare/invoke pipeline.
         */
        public Builder requireExecutionPipelineAvailable(GpuBackendTarget backendTarget) {
            return requireBackend(GpuRuntimeRequirements.requireExecutionPipelineAvailable(backendTarget));
        }

        /**
         * Requires at least the given API version for the specified backend family.
         */
        public Builder minimumApiVersion(GpuBackendTarget backendTarget, int major, int minor) {
            return require(GpuRuntimeRequirements.minimumApiVersion(backendTarget, major, minor));
        }

        /**
         * Requires at least the given amount of local memory on any backend.
         */
        public Builder minimumLocalMemoryBytes(long bytes) {
            return require(GpuRuntimeRequirements.minimumLocalMemoryBytes(bytes));
        }

        /**
         * Requires at least the given amount of local memory for the specified backend family.
         */
        public Builder minimumLocalMemoryBytes(GpuBackendTarget backendTarget, long bytes) {
            return require(GpuRuntimeRequirements.minimumLocalMemoryBytes(backendTarget, bytes));
        }

        /**
         * Requires at least the given maximum work-group size on any backend.
         */
        public Builder minimumMaxWorkGroupSize(long size) {
            return require(GpuRuntimeRequirements.minimumMaxWorkGroupSize(size));
        }

        /**
         * Requires at least the given maximum work-group size for the specified backend family.
         */
        public Builder minimumMaxWorkGroupSize(GpuBackendTarget backendTarget, long size) {
            return require(GpuRuntimeRequirements.minimumMaxWorkGroupSize(backendTarget, size));
        }

        /**
         * Appends one managed backend factory to the fallback chain.
         *
         * <p>Each selection attempt creates a fresh backend instance. Rejected candidates may be auto-closed, and a
         * selected candidate may be auto-closed when installed through owned-scope helpers.
         */
        public Builder preferFactory(GpuRuntimeBackendFactory factory) {
            candidateFactories.add(Objects.requireNonNull(factory, "factory"));
            candidateOwnerships.add(GpuRuntimeBackendOwnership.OWNED);
            candidateMetadata.add(GpuRuntimeBackendCandidateMetadata.unknown());
            return this;
        }

        /**
         * Appends one explicitly owned backend instance to the fallback chain.
         */
        public Builder preferOwnedBackend(GpuRuntimeBackend backend) {
            Objects.requireNonNull(backend, "backend");
            candidateFactories.add(() -> backend);
            candidateOwnerships.add(GpuRuntimeBackendOwnership.OWNED);
            candidateMetadata.add(GpuRuntimeBackendCandidateMetadata.unknown());
            return this;
        }

        /**
         * Appends one caller-owned backend instance to the fallback chain.
         *
         * <p>Rejected candidates are not auto-closed, and a selected candidate installed through
         * {@link GpuRuntime#use(GpuRuntimeBackendPolicy)} remains caller-managed.
         */
        public Builder preferBorrowedBackend(GpuRuntimeBackend backend) {
            Objects.requireNonNull(backend, "backend");
            candidateFactories.add(() -> backend);
            candidateOwnerships.add(GpuRuntimeBackendOwnership.BORROWED);
            candidateMetadata.add(GpuRuntimeBackendCandidateMetadata.unknown());
            return this;
        }

        /**
         * Appends an instance-local OpenCL backend to the fallback chain.
         */
        public Builder preferOpenCl() {
            return preferFactory(OpenClGpuRuntimeBackend::new);
        }

        /**
         * Appends a shared-cache OpenCL backend to the fallback chain.
         */
        public Builder preferOpenClSharedCache() {
            return preferCatalogEntry(GpuRuntimeBackendCatalog.openClSharedCache());
        }

        /**
         * Builds an immutable backend selection policy.
         */
        public GpuRuntimeBackendPolicy build() {
            if (candidateFactories.isEmpty()) {
                throw new IllegalStateException("GPU runtime backend policy requires at least one candidate backend");
            }
            return new GpuRuntimeBackendPolicy(
                    requirements,
                    backendRequirements,
                    candidateFactories,
                    candidateOwnerships,
                    candidateMetadata,
                    candidateOrdering,
                    scoreContributors,
                    scoreCompileOptions,
                    scoreDescriptor,
                    scoreIrGpuArtifact,
                    scoreDeviceProfile,
                    scoreCompilerFeedbackReport,
                    scoreWorkloadHints
            );
        }
    }
}
