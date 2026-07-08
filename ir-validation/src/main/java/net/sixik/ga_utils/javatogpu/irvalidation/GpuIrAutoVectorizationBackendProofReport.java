package net.sixik.ga_utils.javatogpu.irvalidation;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Read-only proof surface for backend and device capability blockers.
 */
public record GpuIrAutoVectorizationBackendProofReport(
        String location,
        List<GpuIrAutoVectorizationRewriteGuardDiagnostic> guardDiagnostics
) {
    private static final Set<GpuIrAutoVectorizationRewriteGuardFamily> BACKEND_GUARD_FAMILIES = Set.of(
            GpuIrAutoVectorizationRewriteGuardFamily.BACKEND_VECTOR_WIDTH,
            GpuIrAutoVectorizationRewriteGuardFamily.BACKEND_DOUBLE_VECTOR
    );

    public GpuIrAutoVectorizationBackendProofReport {
        if (location == null || location.isBlank()) {
            throw new IllegalArgumentException("location must not be blank");
        }
        guardDiagnostics = List.copyOf(Objects.requireNonNull(guardDiagnostics, "guardDiagnostics"));
        if (guardDiagnostics.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("guardDiagnostics must not contain null entries");
        }
    }

    public static GpuIrAutoVectorizationBackendProofReport fromGuards(
            String location,
            List<GpuIrAutoVectorizationRewriteGuardDiagnostic> guardDiagnostics
    ) {
        Objects.requireNonNull(guardDiagnostics, "guardDiagnostics");
        return new GpuIrAutoVectorizationBackendProofReport(
                location,
                guardDiagnostics.stream()
                        .filter(guard -> BACKEND_GUARD_FAMILIES.contains(guard.family()))
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
                "backend",
                location,
                includeGuardFamilies ? 0 : guardDiagnostics.size(),
                includeGuardFamilies ? guardDiagnostics : List.of()
        );
    }

    public Map<String, String> artifactFields(String prefix) {
        return proofSummary().artifactFields(prefix);
    }

    public Map<String, String> artifactFields() {
        return artifactFields("autoVectorizationProofBackend");
    }

    public String summary() {
        return proofSummary().summaryLine();
    }
}
