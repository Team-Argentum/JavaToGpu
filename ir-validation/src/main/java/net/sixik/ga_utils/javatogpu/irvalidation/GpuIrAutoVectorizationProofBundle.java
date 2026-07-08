package net.sixik.ga_utils.javatogpu.irvalidation;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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

    public List<GpuIrAutoVectorizationProofSummary> unsafeProofSummaries() {
        return summaries.stream()
                .filter(summary -> !summary.rewriteSafe())
                .toList();
    }

    public Optional<GpuIrAutoVectorizationProofSummary> firstUnsafeProofSummary() {
        return unsafeProofSummaries().stream().findFirst();
    }

    public GpuIrAutoVectorizationProofDecision decision() {
        return GpuIrAutoVectorizationProofDecision.from(this);
    }

    public Map<String, Long> unsafeProofKindCounts() {
        Map<String, Long> counts = new java.util.LinkedHashMap<>();
        unsafeProofSummaries().stream()
                .map(GpuIrAutoVectorizationProofSummary::proofKind)
                .forEach(kind -> counts.merge(kind, 1L, Long::sum));
        return Collections.unmodifiableMap(counts);
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

    public List<String> compactProofKinds() {
        return proofKinds().stream()
                .distinct()
                .toList();
    }

    public Map<String, Long> proofKindCounts() {
        Map<String, Long> counts = new java.util.LinkedHashMap<>();
        proofKinds().forEach(kind -> counts.merge(kind, 1L, Long::sum));
        return Collections.unmodifiableMap(counts);
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
        values.put(prefix + "Kinds", String.join(",", compactProofKinds()));
        values.put(prefix + "KindCounts", proofKindCounts().entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining(",", "{", "}")));
        values.put(prefix + "RewriteSafe", Boolean.toString(rewriteSafe()));
        values.put(prefix + "Warnings", Integer.toString(warningCount()));
        values.put(prefix + "GuardDiagnostics", Integer.toString(guardDiagnosticCount()));
        values.put(prefix + "Diagnostics", Integer.toString(diagnosticCount()));
        values.put(prefix + "UnsafeProofs", Integer.toString(unsafeProofSummaries().size()));
        values.put(prefix + "UnsafeProofKindCounts", unsafeProofKindCounts().entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining(",", "{", "}")));
        values.putAll(decision().artifactFields(prefix + "Decision"));
        firstUnsafeProofSummary().ifPresent(summary -> {
            values.put(prefix + "FirstUnsafeProofKind", summary.proofKind());
            values.put(prefix + "FirstUnsafeProofLocation", summary.location());
            values.put(prefix + "FirstUnsafeProofDiagnostics", Integer.toString(summary.diagnosticCount()));
            values.put(prefix + "FirstUnsafeProofSummary", summary.summaryLine());
        });
        values.put(prefix + "Summary", summaryLine());
        guardFamilyTypeCounts().entrySet().stream()
                .sorted(java.util.Comparator.comparing(entry -> entry.getKey().artifactValue()))
                .forEach(entry -> values.put(
                        prefix + "GuardFamily." + entry.getKey().artifactValue(),
                        Long.toString(entry.getValue())
                ));
        proofKindCounts().forEach((kind, count) -> values.put(prefix + "Kind." + kind, Long.toString(count)));
        unsafeProofKindCounts().forEach((kind, count) -> values.put(prefix + "UnsafeProofKind." + kind, Long.toString(count)));
        return Collections.unmodifiableMap(values);
    }

    public Map<String, String> artifactFields() {
        return artifactFields("autoVectorizationProofBundle");
    }

    public String summaryLine() {
        return "auto-vectorization proof bundle"
                + " proofs=" + summaries.size()
                + " kinds=" + compactProofKinds().stream().collect(Collectors.joining(",", "[", "]"))
                + " kindCounts=" + proofKindCounts()
                + " rewriteSafe=" + rewriteSafe()
                + " warnings=" + warningCount()
                + " guardDiagnostics=" + guardDiagnosticCount()
                + " diagnostics=" + diagnosticCount()
                + " unsafeProofs=" + unsafeProofSummaries().size()
                + " unsafeProofKindCounts=" + unsafeProofKindCounts()
                + " decision=" + decision().status().artifactValue()
                + " decisionAllowRewrite=" + decision().allowRewrite()
                + firstUnsafeProofSummary().map(summary -> " firstUnsafeProof=" + summary.proofKind()
                + "@" + summary.location()).orElse("")
                + (guardFamilyTypeCounts().isEmpty() ? "" : " guardFamilies=" + guardFamilyCounts());
    }
}
