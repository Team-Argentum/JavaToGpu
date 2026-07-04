package net.sixik.ga_utils.javatogpu.irvalidation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Read-only CSE artifact snapshot for validation reports and CI exports.
 */
public record GpuIrCommonSubexpressionArtifactSnapshot(
        GpuIrCommonSubexpressionRewritePreview preview
) {
    public GpuIrCommonSubexpressionArtifactSnapshot {
        preview = Objects.requireNonNull(preview, "preview");
    }

    public int insertionCount() {
        return preview.insertionCount();
    }

    public int replacementCount() {
        return preview.replacementEditCount();
    }

    public int skippedCount() {
        return preview.skippedCandidateCount();
    }

    public Optional<GpuIrCommonSubexpressionSkippedDiagnostic> firstSkippedDiagnostic() {
        return preview.firstSkippedDiagnostic();
    }

    public Optional<GpuIrCommonSubexpressionDominanceStatus> firstSkippedDominanceStatus() {
        return preview.firstSkippedDominanceStatus();
    }

    public Optional<String> firstSkippedDominanceSummary() {
        return preview.firstSkippedDominanceSummary();
    }

    public Map<GpuIrCommonSubexpressionDominanceStatus, Long> skippedDominanceStatusCounts() {
        return preview.skippedDominanceStatusCounts();
    }

    public GpuIrCommonSubexpressionLocalExpressionDominanceReport localExpressionDominanceReport() {
        return new GpuIrCommonSubexpressionLocalExpressionDominanceReport(preview);
    }

    public String skippedDominanceStatusCountsSummary() {
        return dominanceStatusCountsSummary(skippedDominanceStatusCounts());
    }

    public Map<String, String> artifactFields(String prefix) {
        Objects.requireNonNull(prefix, "prefix");
        Map<String, String> values = new LinkedHashMap<>();
        values.put(prefix + "Insertions", Integer.toString(insertionCount()));
        values.put(prefix + "Replacements", Integer.toString(replacementCount()));
        values.put(prefix + "Skipped", Integer.toString(skippedCount()));
        firstSkippedDiagnostic().ifPresent(diagnostic -> {
            values.put(prefix + "FirstSkippedReason", diagnostic.reason().name());
            values.put(prefix + "FirstSkippedDominanceStatus", diagnostic.dominanceStatus().artifactValue());
            values.put(prefix + "FirstSkippedDominanceSummary", firstSkippedDominanceSummary().orElseThrow());
        });
        values.put(prefix + "SkippedDominanceStatusCounts", skippedDominanceStatusCountsSummary());
        skippedDominanceStatusCounts().entrySet().stream()
                .sorted(java.util.Comparator.comparing(entry -> entry.getKey().artifactValue()))
                .forEach(entry -> values.put(
                        prefix + "SkippedDominanceStatus." + entry.getKey().artifactValue(),
                        Long.toString(entry.getValue())
                ));
        values.putAll(localExpressionDominanceReport().artifactFields(prefix + "LocalExpression"));
        return Collections.unmodifiableMap(values);
    }

    public Map<String, String> artifactFields() {
        return artifactFields("cse");
    }

    public String summary() {
        return "cse artifact snapshot insertions=" + insertionCount()
                + " replacements=" + replacementCount()
                + " skipped=" + skippedCount()
                + " skippedDominanceStatusCounts=" + skippedDominanceStatusCountsSummary()
                + " localExpression={" + localExpressionDominanceReport().summary() + "}"
                + firstSkippedDominanceStatus()
                .map(status -> " firstSkippedDominanceStatus=" + status.artifactValue())
                .orElse("");
    }

    private static String dominanceStatusCountsSummary(Map<GpuIrCommonSubexpressionDominanceStatus, Long> counts) {
        return counts.entrySet().stream()
                .sorted(java.util.Comparator.comparing(entry -> entry.getKey().artifactValue()))
                .map(entry -> entry.getKey().artifactValue() + "=" + entry.getValue())
                .collect(java.util.stream.Collectors.joining(",", "{", "}"));
    }
}
