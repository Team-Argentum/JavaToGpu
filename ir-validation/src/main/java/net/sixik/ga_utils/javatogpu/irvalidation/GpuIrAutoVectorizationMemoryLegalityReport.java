package net.sixik.ga_utils.javatogpu.irvalidation;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Read-only memory legality proof for future auto-vectorization rewrites.
 */
public record GpuIrAutoVectorizationMemoryLegalityReport(
        String location,
        List<String> targetArrays,
        List<String> sourceArrays,
        List<String> aliasWarnings,
        List<GpuIrAutoVectorizationRewriteGuardDiagnostic> guardDiagnostics
) {
    public GpuIrAutoVectorizationMemoryLegalityReport {
        if (location == null || location.isBlank()) {
            throw new IllegalArgumentException("location must not be blank");
        }
        targetArrays = List.copyOf(Objects.requireNonNull(targetArrays, "targetArrays"));
        sourceArrays = List.copyOf(Objects.requireNonNull(sourceArrays, "sourceArrays"));
        aliasWarnings = List.copyOf(Objects.requireNonNull(aliasWarnings, "aliasWarnings"));
        guardDiagnostics = List.copyOf(Objects.requireNonNull(guardDiagnostics, "guardDiagnostics"));
        if (targetArrays.stream().anyMatch(name -> name == null || name.isBlank())) {
            throw new IllegalArgumentException("targetArrays must not contain blank entries");
        }
        if (sourceArrays.stream().anyMatch(name -> name == null || name.isBlank())) {
            throw new IllegalArgumentException("sourceArrays must not contain blank entries");
        }
        if (aliasWarnings.stream().anyMatch(warning -> warning == null || warning.isBlank())) {
            throw new IllegalArgumentException("aliasWarnings must not contain blank entries");
        }
        if (guardDiagnostics.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("guardDiagnostics must not contain null entries");
        }
    }

    public boolean hasAliasWarnings() {
        return !aliasWarnings.isEmpty();
    }

    public boolean hasGuardDiagnostics() {
        return !guardDiagnostics.isEmpty();
    }

    public boolean rewriteSafe() {
        return aliasWarnings.isEmpty() && guardDiagnostics.isEmpty();
    }

    public int diagnosticCount() {
        return proofSummary().diagnosticCount();
    }

    public Map<GpuIrAutoVectorizationRewriteGuardFamily, Long> guardFamilyTypeCounts() {
        return proofSummary().guardFamilyTypeCounts();
    }

    public Map<String, Long> guardFamilyCounts() {
        return proofSummary().guardFamilyCounts();
    }

    public GpuIrAutoVectorizationProofSummary proofSummary() {
        return GpuIrAutoVectorizationProofSummary.fromGuards(
                "memoryLegality",
                location,
                aliasWarnings.size(),
                guardDiagnostics
        );
    }

    /**
     * Exposes the shared proof summary fields through this analyzer report.
     */
    public Map<String, String> artifactFields(String prefix) {
        return proofSummary().artifactFields(prefix);
    }

    public Map<String, String> artifactFields() {
        return artifactFields("autoVectorizationProofMemoryLegality");
    }

    public String summary() {
        return proofSummary().summaryLine()
                + " targetArrays=" + targetArrays
                + " sourceArrays=" + sourceArrays;
    }
}
