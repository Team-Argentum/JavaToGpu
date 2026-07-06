package net.sixik.ga_utils.javatogpu.irvalidation;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Unified read-only auto-vectorization preview for diagnostics and future optimizer planning.
 */
public record GpuIrAutoVectorizationPreview(
        String methodName,
        List<GpuIrAutoVectorizationRewriteCandidatePreview> rewriteCandidates,
        List<GpuIrAutoVectorizationWarningDiagnostic> warningDiagnostics,
        List<GpuIrAutoVectorizationRejectionDiagnostic> rejections,
        List<GpuIrAutoVectorizationProofSummary> additionalProofSummaries
) {
    public GpuIrAutoVectorizationPreview(
            String methodName,
            List<GpuIrAutoVectorizationRewriteCandidatePreview> rewriteCandidates,
            List<GpuIrAutoVectorizationWarningDiagnostic> warningDiagnostics,
            List<GpuIrAutoVectorizationRejectionDiagnostic> rejections
    ) {
        this(methodName, rewriteCandidates, warningDiagnostics, rejections, List.of());
    }

    public GpuIrAutoVectorizationPreview {
        if (methodName == null || methodName.isBlank()) {
            throw new IllegalArgumentException("methodName must not be blank");
        }
        rewriteCandidates = List.copyOf(Objects.requireNonNull(rewriteCandidates, "rewriteCandidates"));
        warningDiagnostics = List.copyOf(Objects.requireNonNull(warningDiagnostics, "warningDiagnostics"));
        rejections = List.copyOf(Objects.requireNonNull(rejections, "rejections"));
        additionalProofSummaries = List.copyOf(Objects.requireNonNull(additionalProofSummaries, "additionalProofSummaries"));
        if (additionalProofSummaries.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("additionalProofSummaries must not contain null entries");
        }
    }

    public boolean hasRewriteCandidates() {
        return !rewriteCandidates.isEmpty();
    }

    public boolean hasWarnings() {
        return !warningDiagnostics.isEmpty();
    }

    public boolean hasRejections() {
        return !rejections.isEmpty();
    }

    public int rewriteCandidateCount() {
        return rewriteCandidates.size();
    }

    public int warningCount() {
        return warningDiagnostics.size();
    }

    public int rejectionCount() {
        return rejections.size();
    }

    public int totalDiagnosticCount() {
        return rewriteCandidateCount() + warningCount() + rejectionCount();
    }

    public GpuIrAutoVectorizationRewritePlan rewritePlan() {
        return GpuIrAutoVectorizationRewritePlan.from(this);
    }

    public GpuIrAutoVectorizationRewritePolicy rewritePolicy() {
        return rewritePlan().rewritePolicy();
    }

    public GpuIrAutoVectorizationProofDecision proofDecision() {
        return proofBundle().decision();
    }


    /**
     * Shared proof surface for rewrite-plan warnings and guard diagnostics.
     */
    public GpuIrAutoVectorizationProofSummary rewritePlanProofSummary() {
        GpuIrAutoVectorizationRewritePlan plan = rewritePlan();
        return new GpuIrAutoVectorizationProofSummary(
                "rewritePlan",
                methodName,
                warningCount() == 0 && plan.guardDiagnostics().isEmpty(),
                warningCount(),
                plan.guardDiagnostics().size(),
                plan.guardFamilyTypeCounts()
        );
    }

    /**
     * Separates alias and neighboring mutation guards from the broader rewrite-plan proof.
     */
    public GpuIrAutoVectorizationProofSummary mutationProofSummary() {
        return GpuIrAutoVectorizationMutationProofReport.fromGuards(
                methodName,
                rewritePlan().typedGuardDiagnostics()
        ).proofSummary();
    }

    /**
     * Separates backend/device capability guards from the broader rewrite-plan proof.
     */
    public GpuIrAutoVectorizationProofSummary backendProofSummary() {
        return GpuIrAutoVectorizationBackendProofReport.fromGuards(
                methodName,
                rewritePlan().typedGuardDiagnostics()
        ).proofSummary();
    }

    /**
     * Separates unknown vector-type guards from the broader rewrite-plan proof.
     */
    public GpuIrAutoVectorizationProofSummary unknownVectorProofSummary() {
        return GpuIrAutoVectorizationUnknownVectorProofReport.fromGuards(
                methodName,
                rewritePlan().typedGuardDiagnostics()
        ).proofSummary();
    }

    private GpuIrAutoVectorizationProofSummary unknownVectorBundleProofSummary() {
        return GpuIrAutoVectorizationUnknownVectorProofReport.fromGuards(
                methodName,
                rewritePlan().typedGuardDiagnostics()
        ).bundleProofSummary();
    }

    private GpuIrAutoVectorizationProofSummary backendBundleProofSummary() {
        return GpuIrAutoVectorizationBackendProofReport.fromGuards(
                methodName,
                rewritePlan().typedGuardDiagnostics()
        ).bundleProofSummary();
    }

    private GpuIrAutoVectorizationProofSummary mutationBundleProofSummary() {
        return GpuIrAutoVectorizationMutationProofReport.fromGuards(
                methodName,
                rewritePlan().typedGuardDiagnostics()
        ).bundleProofSummary();
    }

    /**
     * Single aggregate proof object for future rewrite gates.
     */
    public GpuIrAutoVectorizationProofBundle proofBundle() {
        List<GpuIrAutoVectorizationProofSummary> summaries = new java.util.ArrayList<>();
        summaries.add(rewritePlanProofSummary());
        GpuIrAutoVectorizationProofSummary unknownVectorProofSummary = unknownVectorBundleProofSummary();
        if (unknownVectorProofSummary.hasDiagnostics()) {
            summaries.add(unknownVectorProofSummary);
        }
        GpuIrAutoVectorizationProofSummary backendProofSummary = backendBundleProofSummary();
        if (backendProofSummary.hasDiagnostics()) {
            summaries.add(backendProofSummary);
        }
        GpuIrAutoVectorizationProofSummary mutationProofSummary = mutationBundleProofSummary();
        if (mutationProofSummary.hasDiagnostics()) {
            summaries.add(mutationProofSummary);
        }
        summaries.addAll(additionalProofSummaries);
        return new GpuIrAutoVectorizationProofBundle(summaries);
    }

    public int rewritePlanGuardCount() {
        return rewritePlan().guardDiagnostics().size();
    }

    public boolean hasRewritePlanGuardDiagnostics() {
        return rewritePlanGuardCount() > 0;
    }

    public Optional<GpuIrAutoVectorizationRewriteGuardDiagnostic> firstRewritePlanGuard() {
        if (!hasRewritePlanGuardDiagnostics()) {
            return Optional.empty();
        }
        return Optional.of(rewritePlan().typedGuardDiagnostics().get(0));
    }

    public int rewriteBlockedCandidateCount() {
        return rewritePlan().blockedCandidateCount();
    }

    public boolean hasRewriteBlockedCandidates() {
        return rewriteBlockedCandidateCount() > 0;
    }

    public boolean hasPolicyBlockedRewrite() {
        return rewritePolicy().hasBlockingGuards();
    }

    public boolean canApplyRewrite() {
        return rewriteReadiness() == GpuIrAutoVectorizationRewriteReadiness.READY
                && rewritePolicy().canRewrite()
                && proofDecision().allowRewrite();
    }

    public GpuIrAutoVectorizationRewriteReadiness rewriteReadiness() {
        if (hasRejections()) {
            return GpuIrAutoVectorizationRewriteReadiness.REJECTED;
        }
        if (hasWarnings()) {
            return GpuIrAutoVectorizationRewriteReadiness.BLOCKED_BY_WARNING;
        }
        if (hasRewriteBlockedCandidates()) {
            return GpuIrAutoVectorizationRewriteReadiness.BLOCKED_BY_GUARD;
        }
        if (hasRewriteCandidates()) {
            return GpuIrAutoVectorizationRewriteReadiness.READY;
        }
        return GpuIrAutoVectorizationRewriteReadiness.NONE;
    }

    public Map<String, Long> vectorTypeCounts() {
        return rewriteCandidates.stream()
                .collect(Collectors.groupingBy(
                        GpuIrAutoVectorizationRewriteCandidatePreview::vectorType,
                        java.util.LinkedHashMap::new,
                        Collectors.counting()
                ));
    }

    public boolean hasBlockingDiagnostics() {
        return hasWarnings()
                || hasRejections()
                || hasRewritePlanGuardDiagnostics()
                || proofDecision().blocksRewrite();
    }

    public Optional<String> firstBlockingDiagnosticSummary() {
        if (hasWarnings()) {
            return Optional.of(warningDiagnostics.get(0).summary());
        }
        if (hasRejections()) {
            return Optional.of(rejections.get(0).summary());
        }
        Optional<String> firstRewritePlanGuard = firstRewritePlanGuard().map(GpuIrAutoVectorizationRewriteGuardDiagnostic::summary);
        if (firstRewritePlanGuard.isPresent()) {
            return firstRewritePlanGuard;
        }
        return proofDecision().firstBlockingProof()
                .map(summary -> "proof decision " + proofDecision().status().artifactValue()
                        + " blocks auto-vectorization rewrite at " + summary.proofKind()
                        + "@" + summary.location()
                        + " diagnostics=" + summary.diagnosticCount());
    }

    public Optional<String> firstBlockingDiagnosticFamily() {
        if (hasWarnings()) {
            return Optional.of(firstWarningFamily(warningDiagnostics.get(0)));
        }
        if (hasRejections()) {
            return Optional.of("rejection." + rejections.get(0).reason().name());
        }
        Optional<String> firstRewritePlanGuard = firstRewritePlanGuard().map(guard -> "guard." + guard.family().artifactValue());
        if (firstRewritePlanGuard.isPresent()) {
            return firstRewritePlanGuard;
        }
        if (proofDecision().blocksRewrite()) {
            return Optional.of("proofDecision." + proofDecision().status().artifactValue());
        }
        return Optional.empty();
    }

    private String firstWarningFamily(GpuIrAutoVectorizationWarningDiagnostic warning) {
        if (!warning.aliasWarnings().isEmpty()) {
            return "warning.alias";
        }
        if (!warning.repeatedTargetWarnings().isEmpty()) {
            return "warning.repeatedTarget";
        }
        if (!warning.crossLaneReadWarnings().isEmpty()) {
            return "warning.crossLaneRead";
        }
        if (!warning.nonLaneReadWarnings().isEmpty()) {
            return "warning.nonLaneRead";
        }
        return "warning.other";
    }

    public Map<String, Long> warningFamilyCounts() {
        return java.util.stream.Stream.of(
                        Map.entry("alias", warningDiagnostics.stream().filter(warning -> !warning.aliasWarnings().isEmpty()).count()),
                        Map.entry("repeatedTarget", warningDiagnostics.stream().filter(warning -> !warning.repeatedTargetWarnings().isEmpty()).count()),
                        Map.entry("crossLaneRead", warningDiagnostics.stream().filter(warning -> !warning.crossLaneReadWarnings().isEmpty()).count()),
                        Map.entry("nonLaneRead", warningDiagnostics.stream().filter(warning -> !warning.nonLaneReadWarnings().isEmpty()).count())
                )
                .filter(entry -> entry.getValue() > 0)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (left, right) -> left,
                        java.util.LinkedHashMap::new
                ));
    }

    public Map<GpuIrAutoVectorizationRejectionReason, Long> rejectionReasonCounts() {
        return rejections.stream()
                .collect(Collectors.groupingBy(
                        GpuIrAutoVectorizationRejectionDiagnostic::reason,
                        java.util.LinkedHashMap::new,
                        Collectors.counting()
                ));
    }

    public String summary() {
        return "auto-vectorization preview method=" + methodName
                + " rewriteCandidates=" + rewriteCandidateCount()
                + " rewriteReadiness=" + rewriteReadiness().artifactValue()
                + " rewritePolicyCanRewrite=" + rewritePolicy().canRewrite()
                + " rewritePolicyBlockingGuards=" + rewritePolicy().blockingGuards().size()
                + " canApplyRewrite=" + canApplyRewrite()
                + " proofDecision=" + proofDecision().status().artifactValue()
                + " proofDecisionAllowRewrite=" + proofDecision().allowRewrite()
                + (proofDecision().blockingProofKinds().isEmpty() ? "" : " proofDecisionBlockingKinds=" + proofDecision().blockingProofKinds())
                + " proofBundleRewriteSafe=" + proofBundle().rewriteSafe()
                + " proofBundleDiagnostics=" + proofBundle().diagnosticCount()
                + " proofBundleUnsafeProofs=" + proofBundle().unsafeProofSummaries().size()
                + proofBundle().firstUnsafeProofSummary()
                .map(summary -> " proofBundleFirstUnsafeProof=" + summary.proofKind() + "@" + summary.location())
                .orElse("")
                + " rewriteBlockedCandidates=" + rewriteBlockedCandidateCount()
                + " rewritePlanOperations=" + rewritePlan().operationCount()
                + " rewritePlanGuards=" + rewritePlanGuardCount()
                + (hasRewritePlanGuardDiagnostics() ? " rewritePlanGuardFamilies=" + rewritePlan().guardFamilyCounts() : "")
                + firstRewritePlanGuard().map(guard -> " firstRewritePlanGuard=" + guard.summary()).orElse("")
                + (hasRewriteCandidates() ? " vectorTypes=" + vectorTypeCounts() : "")
                + " warnings=" + warningCount()
                + " totalDiagnostics=" + totalDiagnosticCount()
                + (hasWarnings() ? " warningFamilies=" + warningFamilyCounts() : "")
                + " rejections=" + rejectionCount()
                + (hasRejections() ? " rejectionReasons=" + rejectionReasonCounts() : "");
    }
}
