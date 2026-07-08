package net.sixik.ga_utils.javatogpu.irvalidation;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Read-only proof surface for candidates whose scalar element type is still unknown.
 */
public record GpuIrAutoVectorizationUnknownVectorProofReport(
        String location,
        List<GpuIrAutoVectorizationRewriteGuardDiagnostic> guardDiagnostics
) {
    public GpuIrAutoVectorizationUnknownVectorProofReport {
        if (location == null || location.isBlank()) {
            throw new IllegalArgumentException("location must not be blank");
        }
        guardDiagnostics = List.copyOf(Objects.requireNonNull(guardDiagnostics, "guardDiagnostics"));
        if (guardDiagnostics.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("guardDiagnostics must not contain null entries");
        }
    }

    public static GpuIrAutoVectorizationUnknownVectorProofReport fromGuards(
            String location,
            List<GpuIrAutoVectorizationRewriteGuardDiagnostic> guardDiagnostics
    ) {
        Objects.requireNonNull(guardDiagnostics, "guardDiagnostics");
        return new GpuIrAutoVectorizationUnknownVectorProofReport(
                location,
                guardDiagnostics.stream()
                        .filter(guard -> guard.family() == GpuIrAutoVectorizationRewriteGuardFamily.UNKNOWN_VECTOR_TYPE)
                        .toList()
        );
    }

    public boolean blocksRewrite() {
        return !guardDiagnostics.isEmpty();
    }

    public GpuIrAutoVectorizationProofSummary proofSummary() {
        return proofSummary(true);
    }

    public GpuIrAutoVectorizationProofSummary bundleProofSummary() {
        return proofSummary(false);
    }

    private GpuIrAutoVectorizationProofSummary proofSummary(boolean includeGuardFamilies) {
        return GpuIrAutoVectorizationProofSummary.fromGuards(
                "unknownVector",
                location,
                includeGuardFamilies ? 0 : guardDiagnostics.size(),
                includeGuardFamilies ? guardDiagnostics : List.of()
        );
    }

    public Map<String, String> artifactFields(String prefix) {
        return proofSummary().artifactFields(prefix);
    }

    public Map<String, String> artifactFields() {
        return artifactFields("autoVectorizationProofUnknownVector");
    }

    public String summary() {
        return proofSummary().summaryLine();
    }
}
