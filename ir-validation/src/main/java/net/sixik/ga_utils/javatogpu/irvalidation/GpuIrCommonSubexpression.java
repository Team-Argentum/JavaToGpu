package net.sixik.ga_utils.javatogpu.irvalidation;

import java.util.List;

/**
 * Read-only repeated-expression candidate reported by the scanner.
 */
public record GpuIrCommonSubexpression(
        String fingerprint,
        int occurrenceCount,
        List<String> locations
) {
    public GpuIrCommonSubexpression {
        // Freeze locations so downstream optimizer passes cannot mutate scanner output in place.
        locations = List.copyOf(locations);
    }

    public int estimatedReuseSavings() {
        return Math.max(0, occurrenceCount - 1);
    }
}
