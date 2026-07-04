package net.sixik.ga_utils.javatogpu.irvalidation;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * Read-only snapshot of the first optimizer gate plus grouped blocking counters.
 */
public record GpuIrOptimizerGateSnapshot(
        GpuIrOptimizerGateExplanation explanation,
        Map<String, Long> sourceCounts,
        Map<String, Long> familyCounts
) {
    public GpuIrOptimizerGateSnapshot {
        explanation = Objects.requireNonNull(explanation, "explanation");
        sourceCounts = immutableCopy(sourceCounts, "sourceCounts");
        familyCounts = immutableCopy(familyCounts, "familyCounts");
    }

    public static GpuIrOptimizerGateSnapshot from(GpuIrOptimizationValidationReport report) {
        Objects.requireNonNull(report, "report");
        return new GpuIrOptimizerGateSnapshot(
                GpuIrOptimizerGateExplanation.from(report),
                sourceCounts(report),
                familyCounts(report)
        );
    }

    public String sourceCountsSummary() {
        return countsSummary(sourceCounts);
    }

    public String familyCountsSummary() {
        return countsSummary(familyCounts);
    }

    public Map<String, String> artifactFields(String prefix) {
        Objects.requireNonNull(prefix, "prefix");
        Map<String, String> values = new java.util.LinkedHashMap<>(explanation.artifactFields(prefix));
        values.put(prefix + "CompactSummary", compactSummary());
        values.put(prefix + "SourceCounts", sourceCountsSummary());
        sourceCounts.forEach((source, count) -> values.put(
                prefix + "SourceCount." + source,
                Long.toString(count)
        ));
        values.put(prefix + "FamilyCounts", familyCountsSummary());
        familyCounts.forEach((family, count) -> values.put(
                prefix + "FamilyCount." + family,
                Long.toString(count)
        ));
        return Collections.unmodifiableMap(values);
    }

    public Map<String, String> artifactFields() {
        return artifactFields("optimizerGate");
    }

    public String compactSummary() {
        return explanation.compactSummary()
                + " sourceCounts=" + sourceCountsSummary()
                + " familyCounts=" + familyCountsSummary();
    }

    private static Map<String, Long> sourceCounts(GpuIrOptimizationValidationReport report) {
        Map<String, Long> counts = new java.util.LinkedHashMap<>();
        putPositive(counts, "safety", report.hasSafetyError() ? 1 : 0);
        putPositive(counts, "autoVectorization", autoVectorizationGateCount(report));
        putPositive(counts, "cse", report.commonSubexpressionSkippedCount());
        return counts;
    }

    private static Map<String, Long> familyCounts(GpuIrOptimizationValidationReport report) {
        Map<String, Long> counts = new java.util.LinkedHashMap<>();
        if (report.hasSafetyError()) {
            counts.put("safety.error", 1L);
        }
        report.autoVectorizationPreview().warningFamilyCounts()
                .forEach((family, count) -> putPositive(counts, "warning." + family, count));
        report.autoVectorizationPreview().rejectionReasonCounts()
                .forEach((reason, count) -> putPositive(counts, "rejection." + reason.name(), count));
        report.autoVectorizationPreview().rewritePlan().guardFamilyCounts()
                .forEach((family, count) -> putPositive(counts, "guard." + family, count));
        if (autoVectorizationProofOnlyGateCount(report) > 0) {
            putPositive(counts, "proofDecision." + report.autoVectorizationPreview().proofDecision().status().artifactValue(), 1);
        }
        report.commonSubexpressionPreview().skippedReasonCounts()
                .forEach((reason, count) -> putPositive(counts, "cse." + reason.name(), count));
        return counts;
    }

    private static int autoVectorizationGateCount(GpuIrOptimizationValidationReport report) {
        return report.autoVectorizationWarningCount()
                + report.autoVectorizationRejectionCount()
                + report.autoVectorizationPreview().rewritePlanGuardCount()
                + autoVectorizationProofOnlyGateCount(report);
    }

    private static int autoVectorizationProofOnlyGateCount(GpuIrOptimizationValidationReport report) {
        if (report.autoVectorizationWarningCount() > 0
                || report.autoVectorizationRejectionCount() > 0
                || report.autoVectorizationPreview().rewritePlanGuardCount() > 0) {
            return 0;
        }
        return report.autoVectorizationPreview().proofDecision().blocksRewrite() ? 1 : 0;
    }

    private static Map<String, Long> immutableCopy(Map<String, Long> counts, String name) {
        Objects.requireNonNull(counts, name);
        if (counts.entrySet().stream().anyMatch(entry -> entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null || entry.getValue() < 0)) {
            throw new IllegalArgumentException(name + " must contain non-blank keys and non-negative values");
        }
        return Collections.unmodifiableMap(new java.util.LinkedHashMap<>(counts));
    }

    private static String countsSummary(Map<String, Long> counts) {
        return counts.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(java.util.stream.Collectors.joining(",", "{", "}"));
    }

    private static void putPositive(Map<String, Long> counts, String key, long value) {
        if (value > 0) {
            counts.put(key, value);
        }
    }
}
