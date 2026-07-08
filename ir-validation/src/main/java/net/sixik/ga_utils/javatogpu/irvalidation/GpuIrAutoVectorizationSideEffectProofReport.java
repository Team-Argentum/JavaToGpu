package net.sixik.ga_utils.javatogpu.irvalidation;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Read-only proof surface for side-effect blockers before auto-vectorization rewrites.
 */
public record GpuIrAutoVectorizationSideEffectProofReport(
        String location,
        List<GpuIrAutoVectorizationRewriteGuardDiagnostic> guardDiagnostics
) {
    public GpuIrAutoVectorizationSideEffectProofReport {
        if (location == null || location.isBlank()) {
            throw new IllegalArgumentException("location must not be blank");
        }
        guardDiagnostics = List.copyOf(Objects.requireNonNull(guardDiagnostics, "guardDiagnostics"));
        if (guardDiagnostics.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("guardDiagnostics must not contain null entries");
        }
    }

    public static GpuIrAutoVectorizationSideEffectProofReport blocking(String location, String detail) {
        if (detail == null || detail.isBlank()) {
            throw new IllegalArgumentException("detail must not be blank");
        }
        return new GpuIrAutoVectorizationSideEffectProofReport(
                location,
                List.of(new GpuIrAutoVectorizationRewriteGuardDiagnostic(
                        GpuIrAutoVectorizationRewriteGuardFamily.SIDE_EFFECT,
                        location,
                        detail
                ))
        );
    }

    public boolean blocksRewrite() {
        return !guardDiagnostics.isEmpty();
    }

    public GpuIrAutoVectorizationRewriteGuardDiagnostic guardDiagnostic() {
        if (guardDiagnostics.isEmpty()) {
            throw new IllegalStateException("side-effect proof has no guard diagnostics");
        }
        return guardDiagnostics.get(0);
    }

    public GpuIrAutoVectorizationProofSummary proofSummary() {
        return GpuIrAutoVectorizationProofSummary.fromGuards(
                "sideEffect",
                location,
                0,
                guardDiagnostics
        );
    }

    public Map<String, String> artifactFields(String prefix) {
        return proofSummary().artifactFields(prefix);
    }

    public Map<String, String> artifactFields() {
        return artifactFields("autoVectorizationProofSideEffect");
    }

    public String summary() {
        return proofSummary().summaryLine();
    }
}
