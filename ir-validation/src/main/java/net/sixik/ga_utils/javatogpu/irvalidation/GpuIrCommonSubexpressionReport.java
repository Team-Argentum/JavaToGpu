package net.sixik.ga_utils.javatogpu.irvalidation;

import net.sixik.ga_utils.javatogpu.frontend.ir.model.GpuIrMethod;

import java.util.List;

/**
 * Immutable scanner output for one IR method.
 */
public record GpuIrCommonSubexpressionReport(
        String methodName,
        List<GpuIrCommonSubexpression> candidates
) {
    private static final GpuIrCommonSubexpressionClassifier DEFAULT_CLASSIFIER = new GpuIrCommonSubexpressionClassifier();
    private static final GpuIrCommonSubexpressionScopeClassifier DEFAULT_SCOPE_CLASSIFIER = new GpuIrCommonSubexpressionScopeClassifier();
    private static final GpuIrCommonSubexpressionMutationGuard DEFAULT_MUTATION_GUARD = new GpuIrCommonSubexpressionMutationGuard();

    public GpuIrCommonSubexpressionReport {
        // Keep the report immutable so optimizer stages can safely pass it around.
        candidates = List.copyOf(candidates);
    }

    public boolean hasCandidates() {
        return !candidates.isEmpty();
    }

    public List<GpuIrCommonSubexpression> nonLeafCandidates() {
        return candidates.stream()
                .filter(candidate -> !isLeafFingerprint(candidate.fingerprint()))
                .toList();
    }

    public List<GpuIrCommonSubexpression> topCandidates() {
        return nonLeafCandidates().stream()
                .sorted((left, right) -> Integer.compare(right.estimatedReuseSavings(), left.estimatedReuseSavings()))
                .toList();
    }

    public List<GpuIrCommonSubexpression> rewriteReadyCandidates() {
        return topCandidates().stream()
                .filter(DEFAULT_CLASSIFIER::isRewriteReady)
                .filter(DEFAULT_SCOPE_CLASSIFIER::isStraightLine)
                .toList();
    }

    public List<GpuIrCommonSubexpression> rewriteReadyCandidates(GpuIrMethod method) {
        return rewriteReadyCandidates().stream()
                .filter(candidate -> DEFAULT_MUTATION_GUARD.isStableBetweenOccurrences(method, candidate))
                .toList();
    }

    private boolean isLeafFingerprint(String fingerprint) {
        return fingerprint.startsWith("var(") || fingerprint.startsWith("literal(");
    }
}
