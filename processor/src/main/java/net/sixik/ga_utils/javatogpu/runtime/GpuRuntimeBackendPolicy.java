package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.runtime.opencl.OpenClGpuRuntimeBackend;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

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
    private final List<GpuRuntimeBackendFactory> candidateFactories;
    private final List<GpuRuntimeBackendOwnership> candidateOwnerships;

    private GpuRuntimeBackendPolicy(
            List<GpuRuntimeRequirement> requirements,
            List<GpuRuntimeBackendFactory> candidateFactories,
            List<GpuRuntimeBackendOwnership> candidateOwnerships
    ) {
        this.requirements = List.copyOf(requirements);
        this.candidateFactories = List.copyOf(candidateFactories);
        this.candidateOwnerships = List.copyOf(candidateOwnerships);
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
        List<String> failures = new ArrayList<>();
        for (int index = 0; index < candidateFactories.size(); index++) {
            GpuRuntimeBackendFactory factory = candidateFactories.get(index);
            GpuRuntimeBackendOwnership ownership = candidateOwnerships.get(index);
            GpuRuntimeBackend candidate;
            try {
                candidate = factory.create();
            } catch (RuntimeException exception) {
                failures.add("Failed to create backend candidate: " + exception.getMessage());
                continue;
            }

            GpuRuntimeBackendReport report = GpuRuntime.describeBackend(candidate);
            List<String> reasons = GpuRuntimeRequirements.failureReasons(report, requirements);
            if (reasons.isEmpty()) {
                return new GpuRuntimeSelectionResult(new GpuRuntimeBackendSelection(candidate, report, ownership), failures);
            }

            failures.add(report.backendName() + ": " + String.join("; ", reasons));
            if (ownership == GpuRuntimeBackendOwnership.OWNED) {
                closeCandidateQuietly(candidate);
            }
        }

        return new GpuRuntimeSelectionResult(null, failures);
    }

    /**
     * Creates backend candidates, installs the first matching backend as an owned runtime scope, and closes rejected
     * candidates automatically when possible.
     */
    public GpuRuntimeScope use() {
        return select().install();
    }

    private static void closeCandidateQuietly(GpuRuntimeBackend candidate) {
        if (candidate instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception ignored) {
                // Best effort cleanup for rejected candidates.
            }
        }
    }

    /**
     * Fluent builder for {@link GpuRuntimeBackendPolicy}.
     */
    public static final class Builder {

        private final List<GpuRuntimeRequirement> requirements = new ArrayList<>();
        private final List<GpuRuntimeBackendFactory> candidateFactories = new ArrayList<>();
        private final List<GpuRuntimeBackendOwnership> candidateOwnerships = new ArrayList<>();

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
            return this;
        }

        /**
         * Appends one explicitly owned backend instance to the fallback chain.
         */
        public Builder preferOwnedBackend(GpuRuntimeBackend backend) {
            Objects.requireNonNull(backend, "backend");
            candidateFactories.add(() -> backend);
            candidateOwnerships.add(GpuRuntimeBackendOwnership.OWNED);
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
            return preferFactory(OpenClGpuRuntimeBackend::sharedCache);
        }

        /**
         * Builds an immutable backend selection policy.
         */
        public GpuRuntimeBackendPolicy build() {
            if (candidateFactories.isEmpty()) {
                throw new IllegalStateException("GPU runtime backend policy requires at least one candidate backend");
            }
            return new GpuRuntimeBackendPolicy(requirements, candidateFactories, candidateOwnerships);
        }
    }
}
