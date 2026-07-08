package net.sixik.ga_utils.javatogpu.irvalidation;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Shared read-only summary for auto-vectorization proof surfaces.
 */
public record GpuIrAutoVectorizationProofSummary(
        String proofKind,
        String location,
        boolean rewriteSafe,
        int warningCount,
        int guardDiagnosticCount,
        Map<GpuIrAutoVectorizationRewriteGuardFamily, Long> guardFamilyTypeCounts
) {
    public GpuIrAutoVectorizationProofSummary {
        if (proofKind == null || proofKind.isBlank()) {
            throw new IllegalArgumentException("proofKind must not be blank");
        }
        if (location == null || location.isBlank()) {
            throw new IllegalArgumentException("location must not be blank");
        }
        if (warningCount < 0) {
            throw new IllegalArgumentException("warningCount must be non-negative");
        }
        if (guardDiagnosticCount < 0) {
            throw new IllegalArgumentException("guardDiagnosticCount must be non-negative");
        }
        guardFamilyTypeCounts = Collections.unmodifiableMap(new java.util.LinkedHashMap<>(Objects.requireNonNull(
                guardFamilyTypeCounts,
                "guardFamilyTypeCounts"
        )));
    }

    public static GpuIrAutoVectorizationProofSummary fromGuards(
            String proofKind,
            String location,
            int warningCount,
            List<GpuIrAutoVectorizationRewriteGuardDiagnostic> guardDiagnostics
    ) {
        Objects.requireNonNull(guardDiagnostics, "guardDiagnostics");
        Map<GpuIrAutoVectorizationRewriteGuardFamily, Long> guardFamilyTypeCounts = guardDiagnostics.stream()
                .collect(Collectors.groupingBy(
                        GpuIrAutoVectorizationRewriteGuardDiagnostic::family,
                        java.util.LinkedHashMap::new,
                        Collectors.counting()
                ));
        return new GpuIrAutoVectorizationProofSummary(
                proofKind,
                location,
                warningCount == 0 && guardDiagnostics.isEmpty(),
                warningCount,
                guardDiagnostics.size(),
                guardFamilyTypeCounts
        );
    }

    public int diagnosticCount() {
        return warningCount + guardDiagnosticCount;
    }

    public boolean hasDiagnostics() {
        return diagnosticCount() > 0;
    }

    public Map<String, Long> guardFamilyCounts() {
        Map<String, Long> counts = new java.util.LinkedHashMap<>();
        guardFamilyTypeCounts.forEach((family, count) -> counts.put(family.artifactValue(), count));
        return Collections.unmodifiableMap(counts);
    }

    /**
     * Exposes stable string fields for CI/prototype artifact writers.
     */
    public Map<String, String> artifactFields(String prefix) {
        Objects.requireNonNull(prefix, "prefix");
        Map<String, String> values = new java.util.LinkedHashMap<>();
        values.put(prefix + "Kind", proofKind);
        values.put(prefix + "Location", location);
        values.put(prefix + "RewriteSafe", Boolean.toString(rewriteSafe));
        values.put(prefix + "Warnings", Integer.toString(warningCount));
        values.put(prefix + "GuardDiagnostics", Integer.toString(guardDiagnosticCount));
        values.put(prefix + "Diagnostics", Integer.toString(diagnosticCount()));
        values.put(prefix + "Summary", summaryLine());
        guardFamilyTypeCounts.entrySet().stream()
                .sorted(java.util.Comparator.comparing(entry -> entry.getKey().artifactValue()))
                .forEach(entry -> values.put(
                        prefix + "GuardFamily." + entry.getKey().artifactValue(),
                        Long.toString(entry.getValue())
                ));
        return Collections.unmodifiableMap(values);
    }

    public Map<String, String> artifactFields() {
        return artifactFields("autoVectorizationProof");
    }

    public String summaryLine() {
        return "auto-vectorization proof kind=" + proofKind
                + " location=" + location
                + " rewriteSafe=" + rewriteSafe
                + " warnings=" + warningCount
                + " guardDiagnostics=" + guardDiagnosticCount
                + " diagnostics=" + diagnosticCount()
                + (guardFamilyTypeCounts.isEmpty() ? "" : " guardFamilies=" + guardFamilyCounts());
    }
}
