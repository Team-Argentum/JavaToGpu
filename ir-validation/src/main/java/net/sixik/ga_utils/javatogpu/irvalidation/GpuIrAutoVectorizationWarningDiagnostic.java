package net.sixik.ga_utils.javatogpu.irvalidation;

import java.util.List;
import java.util.Objects;

/**
 * Compact read-only warning preview for a candidate that is visible but not rewrite-ready yet.
 */
public record GpuIrAutoVectorizationWarningDiagnostic(
        String loopLocation,
        int priorityScore,
        List<String> aliasWarnings,
        List<String> crossLaneReadWarnings
) {
    public GpuIrAutoVectorizationWarningDiagnostic {
        if (loopLocation == null || loopLocation.isBlank()) {
            throw new IllegalArgumentException("loopLocation must not be blank");
        }
        aliasWarnings = List.copyOf(Objects.requireNonNull(aliasWarnings, "aliasWarnings"));
        crossLaneReadWarnings = List.copyOf(Objects.requireNonNull(crossLaneReadWarnings, "crossLaneReadWarnings"));
        if (aliasWarnings.isEmpty() && crossLaneReadWarnings.isEmpty()) {
            throw new IllegalArgumentException("at least one warning must be present");
        }
    }

    public static GpuIrAutoVectorizationWarningDiagnostic from(GpuIrAutoVectorizationCandidate candidate) {
        Objects.requireNonNull(candidate, "candidate");
        return new GpuIrAutoVectorizationWarningDiagnostic(
                candidate.loopLocation(),
                candidate.priorityScore(),
                candidate.aliasWarnings(),
                candidate.crossLaneReadWarnings()
        );
    }

    public String summary() {
        return "auto-vectorization warning at " + loopLocation
                + " priorityScore=" + priorityScore
                + (aliasWarnings.isEmpty() ? "" : " aliasWarnings=" + aliasWarnings)
                + (crossLaneReadWarnings.isEmpty() ? "" : " crossLaneReadWarnings=" + crossLaneReadWarnings);
    }
}
