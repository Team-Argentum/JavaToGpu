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
        List<GpuIrAutoVectorizationRejectionDiagnostic> rejections
) {
    public GpuIrAutoVectorizationPreview {
        if (methodName == null || methodName.isBlank()) {
            throw new IllegalArgumentException("methodName must not be blank");
        }
        rewriteCandidates = List.copyOf(Objects.requireNonNull(rewriteCandidates, "rewriteCandidates"));
        warningDiagnostics = List.copyOf(Objects.requireNonNull(warningDiagnostics, "warningDiagnostics"));
        rejections = List.copyOf(Objects.requireNonNull(rejections, "rejections"));
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
     * Single aggregate proof object for future rewrite gates.
     */
    public GpuIrAutoVectorizationProofBundle proofBundle() {
        return GpuIrAutoVectorizationProofBundle.of(rewritePlanProofSummary());
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
        return rewriteReadiness() == GpuIrAutoVectorizationRewriteReadiness.READY && rewritePolicy().canRewrite();
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
        return hasWarnings() || hasRejections() || hasRewritePlanGuardDiagnostics();
    }

    public Optional<String> firstBlockingDiagnosticSummary() {
        if (hasWarnings()) {
            return Optional.of(warningDiagnostics.get(0).summary());
        }
        if (hasRejections()) {
            return Optional.of(rejections.get(0).summary());
        }
        return firstRewritePlanGuard().map(GpuIrAutoVectorizationRewriteGuardDiagnostic::summary);
    }

    public Optional<String> firstBlockingDiagnosticFamily() {
        if (hasWarnings()) {
            return Optional.of(firstWarningFamily(warningDiagnostics.get(0)));
        }
        if (hasRejections()) {
            return Optional.of("rejection." + rejections.get(0).reason().name());
        }
        return firstRewritePlanGuard().map(guard -> "guard." + guard.family().artifactValue());
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
