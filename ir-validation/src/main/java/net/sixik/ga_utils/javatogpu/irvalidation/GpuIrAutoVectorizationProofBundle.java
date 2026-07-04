package net.sixik.ga_utils.javatogpu.irvalidation;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Read-only aggregate for auto-vectorization proof surfaces used by future rewrite gates.
 */
public record GpuIrAutoVectorizationProofBundle(
        List<GpuIrAutoVectorizationProofSummary> summaries
) {
    public GpuIrAutoVectorizationProofBundle {
        Objects.requireNonNull(summaries, "summaries");
        if (summaries.isEmpty()) {
            throw new IllegalArgumentException("summaries must not be empty");
        }
        if (summaries.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("summaries must not contain null entries");
        }
        summaries = List.copyOf(summaries);
    }

    public static GpuIrAutoVectorizationProofBundle of(GpuIrAutoVectorizationProofSummary... summaries) {
        Objects.requireNonNull(summaries, "summaries");
        return new GpuIrAutoVectorizationProofBundle(List.of(summaries));
    }

    public boolean rewriteSafe() {
        return summaries.stream().allMatch(GpuIrAutoVectorizationProofSummary::rewriteSafe);
    }

    public boolean hasDiagnostics() {
        return diagnosticCount() > 0;
    }

    public int warningCount() {
        return summaries.stream()
                .mapToInt(GpuIrAutoVectorizationProofSummary::warningCount)
                .sum();
    }

    public int guardDiagnosticCount() {
        return summaries.stream()
                .mapToInt(GpuIrAutoVectorizationProofSummary::guardDiagnosticCount)
                .sum();
    }

    public int diagnosticCount() {
        return summaries.stream()
                .mapToInt(GpuIrAutoVectorizationProofSummary::diagnosticCount)
                .sum();
    }

    public List<String> proofKinds() {
        return summaries.stream()
                .map(GpuIrAutoVectorizationProofSummary::proofKind)
                .toList();
    }

    public Map<GpuIrAutoVectorizationRewriteGuardFamily, Long> guardFamilyTypeCounts() {
        Map<GpuIrAutoVectorizationRewriteGuardFamily, Long> counts = new java.util.LinkedHashMap<>();
        summaries.forEach(summary -> summary.guardFamilyTypeCounts()
                .forEach((family, count) -> counts.merge(family, count, Long::sum)));
        return Collections.unmodifiableMap(counts);
    }

    public Map<String, Long> guardFamilyCounts() {
        Map<String, Long> counts = new java.util.LinkedHashMap<>();
        guardFamilyTypeCounts().forEach((family, count) -> counts.put(family.artifactValue(), count));
        return Collections.unmodifiableMap(counts);
    }

    /**
     * Exposes stable string fields for future rewrite-gate and CI artifact writers.
     */
    public Map<String, String> artifactFields(String prefix) {
        Objects.requireNonNull(prefix, "prefix");
        Map<String, String> values = new java.util.LinkedHashMap<>();
        values.put(prefix + "Proofs", Integer.toString(summaries.size()));
        values.put(prefix + "Kinds", String.join(",", proofKinds()));
        values.put(prefix + "RewriteSafe", Boolean.toString(rewriteSafe()));
        values.put(prefix + "Warnings", Integer.toString(warningCount()));
        values.put(prefix + "GuardDiagnostics", Integer.toString(guardDiagnosticCount()));
        values.put(prefix + "Diagnostics", Integer.toString(diagnosticCount()));
        values.put(prefix + "Summary", summaryLine());
        guardFamilyTypeCounts().entrySet().stream()
                .sorted(java.util.Comparator.comparing(entry -> entry.getKey().artifactValue()))
                .forEach(entry -> values.put(
                        prefix + "GuardFamily." + entry.getKey().artifactValue(),
                        Long.toString(entry.getValue())
                ));
        return Collections.unmodifiableMap(values);
    }

    public Map<String, String> artifactFields() {
        return artifactFields("autoVectorizationProofBundle");
    }

    public String summaryLine() {
        return "auto-vectorization proof bundle"
                + " proofs=" + summaries.size()
                + " kinds=" + proofKinds().stream().collect(Collectors.joining(",", "[", "]"))
                + " rewriteSafe=" + rewriteSafe()
                + " warnings=" + warningCount()
                + " guardDiagnostics=" + guardDiagnosticCount()
                + " diagnostics=" + diagnosticCount()
                + (guardFamilyTypeCounts().isEmpty() ? "" : " guardFamilies=" + guardFamilyCounts());
    }
}
