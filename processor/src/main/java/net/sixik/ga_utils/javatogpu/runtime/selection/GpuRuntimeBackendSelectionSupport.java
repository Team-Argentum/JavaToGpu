package net.sixik.ga_utils.javatogpu.runtime.selection;

import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifact;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendCompilerFeedbackReport;
import net.sixik.ga_utils.javatogpu.runtime.GpuKernelDescriptor;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntime;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeBackend;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeBackendCandidateDecision;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeBackendCandidateMetadata;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeBackendCandidateOrdering;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeBackendCandidateScore;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeBackendFactory;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeBackendOwnership;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeBackendPolicy;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeBackendReport;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeBackendRequirement;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeBackendScoreContext;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeBackendScoreContribution;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeBackendScoreContributor;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeBackendSelection;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileOptions;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDeviceProfile;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeRequirement;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeRequirements;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeSelectionResult;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeWorkloadHints;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Domain implementation support for backend candidate selection.
 *
 * <p>This class owns the low-level candidate creation, requirement evaluation, scoring, rejection evidence, and
 * selected-backend ownership handling. The root {@code GpuRuntimeBackendSelectionOrchestrator} remains as a compatibility
 * facade for existing callers.</p>
 */
public final class GpuRuntimeBackendSelectionSupport {

    private GpuRuntimeBackendSelectionSupport() {
    }

    /**
     * Selects a backend using an immutable policy and returns candidate-level audit evidence.
     */
    public static GpuRuntimeSelectionResult select(GpuRuntimeBackendPolicy policy) {
        Objects.requireNonNull(policy, "policy");
        return select(
                policy.requirements(),
                policy.backendRequirements(),
                policy.candidateFactories(),
                policy.candidateOwnerships(),
                policy.candidateMetadata(),
                policy.candidateOrdering(),
                policy.scoreContributors(),
                policy.scoreCompileOptions(),
                policy.scoreDescriptor(),
                policy.scoreIrGpuArtifact(),
                policy.scoreDeviceProfile(),
                policy.scoreCompilerFeedbackReport(),
                policy.scoreWorkloadHints()
        );
    }

    /**
     * Selects a borrowed backend from already-created backend candidates.
     */
    public static GpuRuntimeSelectionResult selectBorrowed(
            List<GpuRuntimeRequirement> requirements,
            GpuRuntimeBackend... candidates
    ) {
        Objects.requireNonNull(candidates, "candidates");
        ArrayList<GpuRuntimeBackendFactory> factories = new ArrayList<>(candidates.length);
        ArrayList<GpuRuntimeBackendOwnership> ownerships = new ArrayList<>(candidates.length);
        ArrayList<GpuRuntimeBackendCandidateMetadata> metadata = new ArrayList<>(candidates.length);
        for (int index = 0; index < candidates.length; index++) {
            GpuRuntimeBackend candidate = Objects.requireNonNull(candidates[index], "candidates[" + index + "]");
            factories.add(() -> candidate);
            ownerships.add(GpuRuntimeBackendOwnership.BORROWED);
            metadata.add(GpuRuntimeBackendCandidateMetadata.unknown());
        }
        return select(requirements, factories, ownerships, metadata);
    }

    /**
     * Selects a backend from factories and records every creation, rejection, closure, and selected candidate.
     */
    public static GpuRuntimeSelectionResult select(
            List<GpuRuntimeRequirement> requirements,
            List<GpuRuntimeBackendFactory> candidateFactories,
            List<GpuRuntimeBackendOwnership> candidateOwnerships
    ) {
        return select(
                requirements,
                List.of(),
                candidateFactories,
                candidateOwnerships,
                List.of(),
                GpuRuntimeBackendCandidateOrdering.FALLBACK_ORDER,
                List.of(),
                null,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty()
        );
    }

    /**
     * Selects a backend from factories and records catalog/provider metadata for every candidate when available.
     */
    public static GpuRuntimeSelectionResult select(
            List<GpuRuntimeRequirement> requirements,
            List<GpuRuntimeBackendFactory> candidateFactories,
            List<GpuRuntimeBackendOwnership> candidateOwnerships,
            List<GpuRuntimeBackendCandidateMetadata> candidateMetadata
    ) {
        return select(
                requirements,
                List.of(),
                candidateFactories,
                candidateOwnerships,
                candidateMetadata,
                GpuRuntimeBackendCandidateOrdering.FALLBACK_ORDER,
                List.of(),
                null,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty()
        );
    }

    /**
     * Selects a backend from factories and records catalog/provider metadata for every candidate when available.
     */
    public static GpuRuntimeSelectionResult select(
            List<GpuRuntimeRequirement> requirements,
            List<GpuRuntimeBackendRequirement> backendRequirements,
            List<GpuRuntimeBackendFactory> candidateFactories,
            List<GpuRuntimeBackendOwnership> candidateOwnerships,
            List<GpuRuntimeBackendCandidateMetadata> candidateMetadata
    ) {
        return select(
                requirements,
                backendRequirements,
                candidateFactories,
                candidateOwnerships,
                candidateMetadata,
                GpuRuntimeBackendCandidateOrdering.FALLBACK_ORDER,
                List.of(),
                null,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty()
        );
    }

    /**
     * Selects a backend from factories and records catalog/provider metadata for every candidate when available.
     */
    public static GpuRuntimeSelectionResult select(
            List<GpuRuntimeRequirement> requirements,
            List<GpuRuntimeBackendRequirement> backendRequirements,
            List<GpuRuntimeBackendFactory> candidateFactories,
            List<GpuRuntimeBackendOwnership> candidateOwnerships,
            List<GpuRuntimeBackendCandidateMetadata> candidateMetadata,
            GpuRuntimeBackendCandidateOrdering candidateOrdering
    ) {
        return select(
                requirements,
                backendRequirements,
                candidateFactories,
                candidateOwnerships,
                candidateMetadata,
                candidateOrdering,
                List.of(),
                null,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty()
        );
    }

    /**
     * Selects a backend from factories and records catalog/provider metadata for every candidate when available.
     */
    public static GpuRuntimeSelectionResult select(
            List<GpuRuntimeRequirement> requirements,
            List<GpuRuntimeBackendRequirement> backendRequirements,
            List<GpuRuntimeBackendFactory> candidateFactories,
            List<GpuRuntimeBackendOwnership> candidateOwnerships,
            List<GpuRuntimeBackendCandidateMetadata> candidateMetadata,
            GpuRuntimeBackendCandidateOrdering candidateOrdering,
            List<GpuRuntimeBackendScoreContributor> scoreContributors,
            GpuRuntimeCompileOptions scoreCompileOptions
    ) {
        return select(
                requirements,
                backendRequirements,
                candidateFactories,
                candidateOwnerships,
                candidateMetadata,
                candidateOrdering,
                scoreContributors,
                scoreCompileOptions,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty()
        );
    }

    /**
     * Selects a backend from factories and records catalog/provider metadata for every candidate when available.
     */
    public static GpuRuntimeSelectionResult select(
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
        Objects.requireNonNull(requirements, "requirements");
        Objects.requireNonNull(backendRequirements, "backendRequirements");
        Objects.requireNonNull(candidateFactories, "candidateFactories");
        Objects.requireNonNull(candidateOwnerships, "candidateOwnerships");
        Objects.requireNonNull(candidateMetadata, "candidateMetadata");
        Objects.requireNonNull(scoreContributors, "scoreContributors");
        Optional<GpuKernelDescriptor> descriptor = scoreDescriptor == null ? Optional.empty() : scoreDescriptor;
        Optional<IrGpuArtifact> irGpuArtifact = scoreIrGpuArtifact == null ? Optional.empty() : scoreIrGpuArtifact;
        Optional<GpuRuntimeDeviceProfile> deviceProfile = scoreDeviceProfile == null ? Optional.empty() : scoreDeviceProfile;
        Optional<GpuBackendCompilerFeedbackReport> compilerFeedbackReport = scoreCompilerFeedbackReport == null
                ? Optional.empty()
                : scoreCompilerFeedbackReport;
        Optional<GpuRuntimeWorkloadHints> workloadHints = scoreWorkloadHints == null
                ? Optional.empty()
                : scoreWorkloadHints;
        if (candidateFactories.size() != candidateOwnerships.size()) {
            throw new IllegalArgumentException("candidate factory and ownership counts must match");
        }
        if (!candidateMetadata.isEmpty() && candidateFactories.size() != candidateMetadata.size()) {
            throw new IllegalArgumentException("candidate factory and metadata counts must match");
        }
        if (candidateFactories.isEmpty()) {
            return new GpuRuntimeSelectionResult(
                    null,
                    List.of("no backend candidates were provided"),
                    List.of()
            );
        }

        ArrayList<String> failures = new ArrayList<>();
        ArrayList<GpuRuntimeBackendCandidateDecision> decisions = new ArrayList<>();
        ArrayList<ViableBackendCandidate> viableCandidates = new ArrayList<>();
        GpuRuntimeBackendCandidateOrdering ordering = candidateOrdering == null
                ? GpuRuntimeBackendCandidateOrdering.FALLBACK_ORDER
                : candidateOrdering;
        for (int index = 0; index < candidateFactories.size(); index++) {
            GpuRuntimeBackendFactory factory = Objects.requireNonNull(
                    candidateFactories.get(index),
                    "candidateFactories[" + index + "]"
            );
            GpuRuntimeBackendOwnership ownership = candidateOwnerships.get(index) == null
                    ? GpuRuntimeBackendOwnership.BORROWED
                    : candidateOwnerships.get(index);
            GpuRuntimeBackendCandidateMetadata metadata = candidateMetadata.isEmpty()
                    ? GpuRuntimeBackendCandidateMetadata.unknown()
                    : candidateMetadata.get(index);
            GpuRuntimeBackend candidate;
            try {
                candidate = factory.create();
            } catch (RuntimeException exception) {
                GpuRuntimeBackendCandidateDecision decision = GpuRuntimeBackendCandidateDecision.creationFailed(
                        index,
                        ownership,
                        metadata,
                        exception
                );
                decisions.add(decision);
                failures.add(String.join("; ", decision.diagnostics()));
                continue;
            }

            GpuRuntimeBackendReport report = GpuRuntime.describeBackend(candidate);
            List<String> reasons = new ArrayList<>(GpuRuntimeRequirements.failureReasons(report, requirements));
            reasons.addAll(GpuRuntimeRequirements.failureReasons(report, metadata, backendRequirements));
            GpuRuntimeBackendCandidateScore score = scoreForCandidate(
                    index,
                    report,
                    metadata,
                    !reasons.isEmpty(),
                    scoreContributors,
                    scoreCompileOptions,
                    descriptor,
                    irGpuArtifact,
                    deviceProfile,
                    compilerFeedbackReport,
                    workloadHints
            );
            if (reasons.isEmpty()) {
                if (ordering == GpuRuntimeBackendCandidateOrdering.SCORE_DESCENDING) {
                    viableCandidates.add(new ViableBackendCandidate(index, candidate, report, ownership, metadata, score));
                    continue;
                }
                decisions.add(GpuRuntimeBackendCandidateDecision.selected(index, report, ownership, metadata, score));
                return new GpuRuntimeSelectionResult(
                        new GpuRuntimeBackendSelection(candidate, report, ownership),
                        failures,
                        decisions
                );
            }

            boolean closed = false;
            if (ownership == GpuRuntimeBackendOwnership.OWNED) {
                closed = closeCandidateQuietly(candidate);
            }
            GpuRuntimeBackendCandidateDecision decision = GpuRuntimeBackendCandidateDecision.rejected(
                    index,
                    report,
                    ownership,
                    reasons,
                    closed,
                    metadata,
                    score
            );
            decisions.add(decision);
            failures.add(report.backendName() + ": " + String.join("; ", reasons));
        }

        if (ordering == GpuRuntimeBackendCandidateOrdering.SCORE_DESCENDING && !viableCandidates.isEmpty()) {
            ViableBackendCandidate selected = highestScoringCandidate(viableCandidates);
            for (ViableBackendCandidate candidate : viableCandidates) {
                if (candidate == selected) {
                    decisions.add(GpuRuntimeBackendCandidateDecision.selected(
                            candidate.index,
                            candidate.report,
                            candidate.ownership,
                            candidate.metadata,
                            candidate.score
                    ));
                    continue;
                }
                boolean closed = candidate.ownership == GpuRuntimeBackendOwnership.OWNED
                        && closeCandidateQuietly(candidate.backend);
                decisions.add(GpuRuntimeBackendCandidateDecision.notSelected(
                        candidate.index,
                        candidate.report,
                        candidate.ownership,
                        closed,
                        candidate.metadata,
                        "not selected: score below selected candidate " + selected.report.backendName(),
                        candidate.score
                ));
            }
            decisions.sort(java.util.Comparator.comparingInt(GpuRuntimeBackendCandidateDecision::candidateIndex));
            return new GpuRuntimeSelectionResult(
                    new GpuRuntimeBackendSelection(selected.backend, selected.report, selected.ownership),
                    failures,
                    decisions
            );
        }

        return new GpuRuntimeSelectionResult(null, failures, decisions);
    }

    private static ViableBackendCandidate highestScoringCandidate(List<ViableBackendCandidate> candidates) {
        return candidates.stream()
                .max(java.util.Comparator
                        .comparingInt((ViableBackendCandidate candidate) -> candidate.score.totalScore())
                        .thenComparing(candidate -> -candidate.index))
                .orElseThrow();
    }

    private static GpuRuntimeBackendCandidateScore scoreForCandidate(
            int candidateIndex,
            GpuRuntimeBackendReport report,
            GpuRuntimeBackendCandidateMetadata metadata,
            boolean rejected,
            List<GpuRuntimeBackendScoreContributor> scoreContributors,
            GpuRuntimeCompileOptions scoreCompileOptions,
            Optional<GpuKernelDescriptor> scoreDescriptor,
            Optional<IrGpuArtifact> scoreIrGpuArtifact,
            Optional<GpuRuntimeDeviceProfile> scoreDeviceProfile,
            Optional<GpuBackendCompilerFeedbackReport> scoreCompilerFeedbackReport,
            Optional<GpuRuntimeWorkloadHints> scoreWorkloadHints
    ) {
        List<GpuRuntimeBackendScoreContribution> contributions = scoreContributionsForCandidate(
                candidateIndex,
                report,
                metadata,
                scoreContributors,
                scoreCompileOptions,
                scoreDescriptor,
                scoreIrGpuArtifact,
                scoreDeviceProfile,
                scoreCompilerFeedbackReport,
                scoreWorkloadHints
        );
        return GpuRuntimeBackendCandidateScore.estimate(
                candidateIndex,
                report,
                metadata,
                true,
                rejected,
                contributions
        );
    }

    private static List<GpuRuntimeBackendScoreContribution> scoreContributionsForCandidate(
            int candidateIndex,
            GpuRuntimeBackendReport report,
            GpuRuntimeBackendCandidateMetadata metadata,
            List<GpuRuntimeBackendScoreContributor> scoreContributors,
            GpuRuntimeCompileOptions scoreCompileOptions,
            Optional<GpuKernelDescriptor> scoreDescriptor,
            Optional<IrGpuArtifact> scoreIrGpuArtifact,
            Optional<GpuRuntimeDeviceProfile> scoreDeviceProfile,
            Optional<GpuBackendCompilerFeedbackReport> scoreCompilerFeedbackReport,
            Optional<GpuRuntimeWorkloadHints> scoreWorkloadHints
    ) {
        if (scoreContributors == null || scoreContributors.isEmpty()) {
            return List.of();
        }
        ArrayList<GpuRuntimeBackendScoreContribution> contributions = new ArrayList<>();
        GpuRuntimeBackendScoreContext context = new GpuRuntimeBackendScoreContext(
                candidateIndex,
                report,
                metadata,
                scoreCompileOptions,
                scoreDescriptor,
                scoreIrGpuArtifact,
                scoreDeviceProfile,
                scoreCompilerFeedbackReport,
                scoreWorkloadHints
        );
        for (GpuRuntimeBackendScoreContributor contributor : scoreContributors) {
            if (contributor == null || !contributor.appliesTo(report.backendTarget())) {
                continue;
            }
            try {
                GpuRuntimeBackendScoreContribution contribution = contributor.scoreCandidate(context);
                if (contribution != null && (contribution.adjustment() != 0 || !contribution.diagnostics().isEmpty())) {
                    contributions.add(annotateContribution(contributor, contribution));
                }
            } catch (RuntimeException exception) {
                contributions.add(GpuRuntimeBackendScoreContribution.of(
                        0,
                        "policy score contributor " + contributor.extensionId() + " failed: " + exceptionMessage(exception)
                ));
            }
        }
        return List.copyOf(contributions);
    }

    private static GpuRuntimeBackendScoreContribution annotateContribution(
            GpuRuntimeBackendScoreContributor contributor,
            GpuRuntimeBackendScoreContribution contribution
    ) {
        String prefix = "policy score contributor " + contributor.extensionId();
        if (contribution.diagnostics().isEmpty()) {
            return GpuRuntimeBackendScoreContribution.of(
                    contribution.adjustment(),
                    prefix + " " + signed(contribution.adjustment())
            );
        }
        return GpuRuntimeBackendScoreContribution.of(
                contribution.adjustment(),
                contribution.diagnostics().stream()
                        .map(diagnostic -> prefix + ": " + diagnostic)
                        .toList()
        );
    }

    private static String signed(int value) {
        return value >= 0 ? "+" + value : Integer.toString(value);
    }

    private static String exceptionMessage(RuntimeException exception) {
        if (exception == null || exception.getMessage() == null || exception.getMessage().isBlank()) {
            return "score-contributor-failed";
        }
        return exception.getMessage();
    }

    private record ViableBackendCandidate(
            int index,
            GpuRuntimeBackend backend,
            GpuRuntimeBackendReport report,
            GpuRuntimeBackendOwnership ownership,
            GpuRuntimeBackendCandidateMetadata metadata,
            GpuRuntimeBackendCandidateScore score
    ) {
    }

    private static boolean closeCandidateQuietly(GpuRuntimeBackend candidate) {
        if (candidate instanceof AutoCloseable closeable) {
            try {
                closeable.close();
                return true;
            } catch (Exception ignored) {
                return false;
            }
        }
        return false;
    }
}
