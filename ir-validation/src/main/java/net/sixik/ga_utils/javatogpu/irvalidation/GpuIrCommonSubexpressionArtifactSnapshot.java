package net.sixik.ga_utils.javatogpu.irvalidation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Read-only CSE artifact snapshot for validation reports and CI exports.
 */
public record GpuIrCommonSubexpressionArtifactSnapshot(
        GpuIrCommonSubexpressionRewritePreview preview,
        GpuIrCommonSubexpressionRewritePolicy rewritePolicy
) {
    public GpuIrCommonSubexpressionArtifactSnapshot(GpuIrCommonSubexpressionRewritePreview preview) {
        this("unknown", preview);
    }

    public GpuIrCommonSubexpressionArtifactSnapshot(
            String methodName,
            GpuIrCommonSubexpressionRewritePreview preview
    ) {
        this(preview, new GpuIrCommonSubexpressionRewritePolicy(
                methodName,
                preview.insertionCount(),
                preview.insertionCount(),
                preview.replacementEditCount(),
                skippedCandidates(preview)
        ));
    }

    public GpuIrCommonSubexpressionArtifactSnapshot(
            String methodName,
            GpuIrCommonSubexpressionRewritePlanReport planReport
    ) {
        this(planReport.preview(), GpuIrCommonSubexpressionRewritePolicy.from(methodName, planReport));
    }

    public GpuIrCommonSubexpressionArtifactSnapshot {
        preview = Objects.requireNonNull(preview, "preview");
        rewritePolicy = Objects.requireNonNull(rewritePolicy, "rewritePolicy");
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

    public GpuIrCommonSubexpressionSimpleArithmeticProofReport simpleArithmeticProofReport() {
        return GpuIrCommonSubexpressionSimpleArithmeticProofReport.from(preview);
    }

    public GpuIrCommonSubexpressionLayerReadinessSummaryReport layerReadinessSummaryReport() {
        return GpuIrCommonSubexpressionLayerReadinessSummaryReport.from(preview, rewritePolicy);
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
        values.putAll(simpleArithmeticProofReport().artifactFields(prefix + "SimpleArithmeticProof"));
        values.putAll(rewritePolicy.artifactFields(prefix + "RewritePolicy"));
        values.putAll(layerReadinessSummaryReport().artifactFields(prefix + "LayerReadiness"));
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
                + " simpleArithmeticProof={" + simpleArithmeticProofReport().summary() + "}"
                + " rewritePolicy={" + rewritePolicy.summary() + "}"
                + " layerReadiness={" + layerReadinessSummaryReport().summary() + "}"
                + firstSkippedDominanceStatus()
                .map(status -> " firstSkippedDominanceStatus=" + status.artifactValue())
                .orElse("");
    }

    private static List<GpuIrCommonSubexpressionSkippedCandidate> skippedCandidates(
            GpuIrCommonSubexpressionRewritePreview preview
    ) {
        return preview.skippedDiagnostics().stream()
                .map(diagnostic -> new GpuIrCommonSubexpressionSkippedCandidate(
                        new GpuIrCommonSubexpression(
                                diagnostic.fingerprint(),
                                diagnostic.occurrenceCount(),
                                diagnostic.locations()
                        ),
                        diagnostic.kind(),
                        diagnostic.scope(),
                        diagnostic.reason(),
                        diagnostic.dominanceStatus()
                ))
                .toList();
    }

    private static String dominanceStatusCountsSummary(Map<GpuIrCommonSubexpressionDominanceStatus, Long> counts) {
        return counts.entrySet().stream()
                .sorted(java.util.Comparator.comparing(entry -> entry.getKey().artifactValue()))
                .map(entry -> entry.getKey().artifactValue() + "=" + entry.getValue())
                .collect(java.util.stream.Collectors.joining(",", "{", "}"));
    }
}
