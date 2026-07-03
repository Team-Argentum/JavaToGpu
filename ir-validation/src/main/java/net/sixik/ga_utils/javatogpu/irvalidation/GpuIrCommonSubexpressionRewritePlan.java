package net.sixik.ga_utils.javatogpu.irvalidation;

import java.util.List;

/**
 * Read-only description of a possible temporary extraction for one repeated expression.
 */
public record GpuIrCommonSubexpressionRewritePlan(
        String temporaryName,
        String fingerprint,
        int estimatedReuseSavings,
        List<String> replacementLocations
) {
    public GpuIrCommonSubexpressionRewritePlan {
        replacementLocations = List.copyOf(replacementLocations);
    }
}
