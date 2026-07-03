package net.sixik.ga_utils.javatogpu.irvalidation;

import java.util.List;
import java.util.Objects;

/**
 * Compact diagnostic view for candidates that analysis saw but the planner refused to rewrite.
 */
public record GpuIrCommonSubexpressionSkippedDiagnostic(
        String fingerprint,
        int occurrenceCount,
        List<String> locations,
        GpuIrCommonSubexpressionKind kind,
        GpuIrCommonSubexpressionScope scope,
        GpuIrCommonSubexpressionSkipReason reason
) {
    public GpuIrCommonSubexpressionSkippedDiagnostic {
        if (fingerprint == null || fingerprint.isBlank()) {
            throw new IllegalArgumentException("fingerprint must not be blank");
        }
        if (occurrenceCount < 0) {
            throw new IllegalArgumentException("occurrenceCount must be non-negative");
        }
        locations = List.copyOf(Objects.requireNonNull(locations, "locations"));
        kind = Objects.requireNonNull(kind, "kind");
        scope = Objects.requireNonNull(scope, "scope");
        reason = Objects.requireNonNull(reason, "reason");
    }

    public String summary() {
        return reason + " " + kind + " " + scope + " " + fingerprint + " @ " + locations;
    }
}
