package net.sixik.ga_utils.javatogpu.irvalidation;

import java.util.Objects;

/**
 * Read-only explanation for a loop that was scanned but not reported as vectorizable yet.
 */
public record GpuIrAutoVectorizationRejectionDiagnostic(
        String loopLocation,
        GpuIrAutoVectorizationRejectionReason reason,
        String detail
) {
    public GpuIrAutoVectorizationRejectionDiagnostic {
        if (loopLocation == null || loopLocation.isBlank()) {
            throw new IllegalArgumentException("loopLocation must not be blank");
        }
        reason = Objects.requireNonNull(reason, "reason");
        detail = detail == null ? "" : detail;
    }

    public String summary() {
        return reason + " at " + loopLocation + (detail.isBlank() ? "" : ": " + detail);
    }
}
