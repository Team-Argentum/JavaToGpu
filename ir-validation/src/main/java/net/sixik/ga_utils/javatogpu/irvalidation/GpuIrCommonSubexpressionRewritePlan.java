package net.sixik.ga_utils.javatogpu.irvalidation;

import java.util.List;

/**
 * Read-only description of a possible temporary extraction for one repeated expression.
 */
public record GpuIrCommonSubexpressionRewritePlan(
        String temporaryName,
        String fingerprint,
        int estimatedReuseSavings,
        int insertionStatementIndex,
        List<String> replacementLocations
) {
    public GpuIrCommonSubexpressionRewritePlan {
        if (insertionStatementIndex < 0) {
            throw new IllegalArgumentException("insertionStatementIndex must be non-negative");
        }
        replacementLocations = List.copyOf(replacementLocations);
    }
}
