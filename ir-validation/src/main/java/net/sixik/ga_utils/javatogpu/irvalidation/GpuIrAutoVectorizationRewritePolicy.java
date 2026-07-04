package net.sixik.ga_utils.javatogpu.irvalidation;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Read-only decision object for future auto-vectorization rewrite entrypoints.
 */
public record GpuIrAutoVectorizationRewritePolicy(
        String methodName,
        int candidateCount,
        int plannedOperationCount,
        List<GpuIrAutoVectorizationRewriteGuardDiagnostic> blockingGuards
) {
    public GpuIrAutoVectorizationRewritePolicy {
        if (methodName == null || methodName.isBlank()) {
            throw new IllegalArgumentException("methodName must not be blank");
        }
        if (candidateCount < 0) {
            throw new IllegalArgumentException("candidateCount must be non-negative");
        }
        if (plannedOperationCount < 0) {
            throw new IllegalArgumentException("plannedOperationCount must be non-negative");
        }
        blockingGuards = List.copyOf(Objects.requireNonNull(blockingGuards, "blockingGuards"));
        if (candidateCount == 0 && plannedOperationCount != 0) {
            throw new IllegalArgumentException("plannedOperationCount must be zero when no candidates exist");
        }
    }

    public static GpuIrAutoVectorizationRewritePolicy from(GpuIrAutoVectorizationRewritePlan plan) {
        Objects.requireNonNull(plan, "plan");
        return new GpuIrAutoVectorizationRewritePolicy(
                plan.methodName(),
                plan.candidateCount(),
                plan.rawInsertionOperationCount() + plan.rawReplacementOperationCount(),
                plan.typedGuardDiagnostics()
        );
    }

    public boolean hasCandidates() {
        return candidateCount > 0;
    }

    public boolean hasBlockingGuards() {
        return !blockingGuards.isEmpty();
    }

    public boolean canRewrite() {
        return hasCandidates() && !hasBlockingGuards();
    }

    public Optional<GpuIrAutoVectorizationRewriteGuardDiagnostic> firstBlockingGuard() {
        if (blockingGuards.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(blockingGuards.get(0));
    }

    public Map<GpuIrAutoVectorizationRewriteGuardFamily, Long> blockingGuardFamilyTypeCounts() {
        return blockingGuards.stream()
                .collect(Collectors.groupingBy(
                        GpuIrAutoVectorizationRewriteGuardDiagnostic::family,
                        LinkedHashMap::new,
                        Collectors.counting()
                ));
    }

    public Map<String, Long> blockingGuardFamilyCounts() {
        return blockingGuardFamilyTypeCounts().entrySet().stream()
                .collect(Collectors.toMap(
                        entry -> entry.getKey().artifactValue(),
                        Map.Entry::getValue,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
    }

    public GpuIrAutoVectorizationRewriteReadiness readiness() {
        if (!hasCandidates()) {
            return GpuIrAutoVectorizationRewriteReadiness.NONE;
        }
        if (hasBlockingGuards()) {
            return GpuIrAutoVectorizationRewriteReadiness.BLOCKED_BY_GUARD;
        }
        return GpuIrAutoVectorizationRewriteReadiness.READY;
    }

    public String summary() {
        return "auto-vectorization rewrite policy method=" + methodName
                + " candidates=" + candidateCount
                + " plannedOperations=" + plannedOperationCount
                + " readiness=" + readiness().artifactValue()
                + " canRewrite=" + canRewrite()
                + (hasBlockingGuards() ? " blockingGuardFamilies=" + blockingGuardFamilyCounts() : "")
                + (hasBlockingGuards() ? " blockingGuards=" + blockingGuards.stream().map(GpuIrAutoVectorizationRewriteGuardDiagnostic::summary).toList() : "");
    }
}
