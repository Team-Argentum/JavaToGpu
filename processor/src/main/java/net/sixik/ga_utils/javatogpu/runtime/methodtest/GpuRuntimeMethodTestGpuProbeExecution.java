package net.sixik.ga_utils.javatogpu.runtime.methodtest;

import net.sixik.ga_utils.javatogpu.runtime.*;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One bounded GPU probe execution result for a materialized {@code @GPUTest} vector.
 */
public record GpuRuntimeMethodTestGpuProbeExecution(
        String testId,
        GpuRuntimeMethodTestGpuProbeEvidenceKey evidenceKey,
        boolean cacheHit,
        boolean executionReady,
        boolean executionPassed,
        GpuExecutionConfig executionConfig,
        List<GpuRuntimeMethodTestReferenceComparison> comparisons,
        List<String> blockers,
        List<String> diagnostics
) {

    public GpuRuntimeMethodTestGpuProbeExecution {
        testId = normalize(testId, "unknown");
        comparisons = comparisons == null ? List.of() : List.copyOf(comparisons);
        blockers = normalizeList(blockers);
        diagnostics = normalizeList(diagnostics);
        executionReady = executionReady && executionConfig != null && blockers.isEmpty()
                && comparisons.stream().allMatch(GpuRuntimeMethodTestReferenceComparison::comparisonReady);
        executionPassed = executionReady && executionPassed && comparisons.stream()
                .allMatch(GpuRuntimeMethodTestReferenceComparison::passed);
    }

    public int comparisonReadyCount() {
        return (int) comparisons.stream()
                .filter(GpuRuntimeMethodTestReferenceComparison::comparisonReady)
                .count();
    }

    public int comparisonPassedCount() {
        return (int) comparisons.stream()
                .filter(GpuRuntimeMethodTestReferenceComparison::passed)
                .count();
    }

    public int comparisonFailedCount() {
        return (int) comparisons.stream()
                .filter(comparison -> comparison.comparisonReady() && !comparison.passed())
                .count();
    }

    public String firstBlocker() {
        return blockers.isEmpty() ? "none" : blockers.get(0);
    }

    public String firstFailure() {
        return comparisons.stream()
                .filter(comparison -> comparison.comparisonReady() && !comparison.passed())
                .map(GpuRuntimeMethodTestReferenceComparison::failure)
                .filter(failure -> !"none".equals(failure))
                .findFirst()
                .orElse("none");
    }

    public GpuRuntimeMethodTestGpuProbeExecution withCacheHit(boolean value) {
        return new GpuRuntimeMethodTestGpuProbeExecution(
                testId,
                evidenceKey,
                value,
                executionReady,
                executionPassed,
                executionConfig,
                comparisons,
                blockers,
                diagnostics
        );
    }

    public Map<String, String> artifactFields(String prefix) {
        String normalizedPrefix = prefix == null || prefix.isBlank() ? "methodTestGpuProbeExecution" : prefix.trim();
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        fields.put(normalizedPrefix + ".testId", testId);
        fields.put(normalizedPrefix + ".cacheHit", Boolean.toString(cacheHit));
        fields.put(normalizedPrefix + ".evidenceKey.present", Boolean.toString(evidenceKey != null));
        if (evidenceKey != null) {
            fields.putAll(evidenceKey.artifactFields(normalizedPrefix + ".evidenceKey"));
        }
        fields.put(normalizedPrefix + ".executionReady", Boolean.toString(executionReady));
        fields.put(normalizedPrefix + ".executionPassed", Boolean.toString(executionPassed));
        writeExecutionConfig(fields, normalizedPrefix + ".executionConfig", executionConfig);
        fields.put(normalizedPrefix + ".comparison.count", Integer.toString(comparisons.size()));
        fields.put(normalizedPrefix + ".comparison.ready.count", Integer.toString(comparisonReadyCount()));
        fields.put(normalizedPrefix + ".comparison.passed.count", Integer.toString(comparisonPassedCount()));
        fields.put(normalizedPrefix + ".comparison.failed.count", Integer.toString(comparisonFailedCount()));
        fields.put(normalizedPrefix + ".firstBlocker", firstBlocker());
        fields.put(normalizedPrefix + ".firstFailure", firstFailure());
        for (int index = 0; index < comparisons.size(); index++) {
            fields.putAll(comparisons.get(index).artifactFields(normalizedPrefix + ".comparison." + index));
        }
        writeList(fields, normalizedPrefix + ".blocker", blockers);
        writeList(fields, normalizedPrefix + ".diagnostic", diagnostics);
        return Collections.unmodifiableMap(fields);
    }

    public String toMarkdown() {
        StringBuilder builder = new StringBuilder();
        builder.append("Method test GPU probe execution: ")
                .append(executionPassed ? "passed" : (executionReady ? "failed" : "blocked"))
                .append('\n');
        builder.append("Test id: ").append(testId).append('\n');
        builder.append("Cache hit: ").append(cacheHit).append('\n');
        if (evidenceKey != null) {
            builder.append("Evidence key: ").append(evidenceKey.stableHash()).append('\n');
        }
        if (executionConfig != null) {
            builder.append("Execution config: ")
                    .append(executionConfig.dimensions())
                    .append("D global=")
                    .append(executionConfig.globalX())
                    .append('x')
                    .append(executionConfig.globalY())
                    .append('x')
                    .append(executionConfig.globalZ())
                    .append('\n');
        }
        builder.append("Comparisons: ")
                .append(comparisonPassedCount())
                .append('/')
                .append(comparisons.size())
                .append(" passed")
                .append('\n');
        if (!"none".equals(firstBlocker())) {
            builder.append("First blocker: ").append(firstBlocker()).append('\n');
        }
        if (!"none".equals(firstFailure())) {
            builder.append("First failure: ").append(firstFailure()).append('\n');
        }
        for (GpuRuntimeMethodTestReferenceComparison comparison : comparisons) {
            builder.append("- ")
                    .append(comparison.parameterIndex())
                    .append(' ')
                    .append(comparison.parameterName())
                    .append(':')
                    .append(comparison.javaType())
                    .append(" ready=")
                    .append(comparison.comparisonReady())
                    .append(" passed=")
                    .append(comparison.passed());
            if (comparison.comparisonReady()) {
                builder.append(" actual=")
                        .append(comparison.actualKind())
                        .append('[')
                        .append(comparison.actualItemCount())
                        .append("] expected=")
                        .append(comparison.expectedKind())
                        .append('[')
                        .append(comparison.expectedItemCount())
                        .append(']');
            }
            if (!"none".equals(comparison.blocker())) {
                builder.append(" blocker=").append(comparison.blocker());
            }
            if (!"none".equals(comparison.failure())) {
                builder.append(" failure=").append(comparison.failure());
            }
            builder.append('\n');
        }
        if (!diagnostics.isEmpty()) {
            builder.append('\n').append("Diagnostics:").append('\n');
            for (String diagnostic : diagnostics) {
                builder.append("- ").append(diagnostic).append('\n');
            }
        }
        return builder.toString();
    }

    static void writeExecutionConfig(LinkedHashMap<String, String> fields, String prefix, GpuExecutionConfig config) {
        fields.put(prefix + ".present", Boolean.toString(config != null));
        if (config == null) {
            return;
        }
        fields.put(prefix + ".dimensions", Integer.toString(config.dimensions()));
        fields.put(prefix + ".globalX", Long.toString(config.globalX()));
        fields.put(prefix + ".globalY", Long.toString(config.globalY()));
        fields.put(prefix + ".globalZ", Long.toString(config.globalZ()));
        fields.put(prefix + ".localX", Long.toString(config.localX()));
        fields.put(prefix + ".localY", Long.toString(config.localY()));
        fields.put(prefix + ".localZ", Long.toString(config.localZ()));
    }

    private static void writeList(LinkedHashMap<String, String> fields, String prefix, List<String> values) {
        fields.put(prefix + ".count", Integer.toString(values.size()));
        for (int index = 0; index < values.size(); index++) {
            fields.put(prefix + "." + index, values.get(index));
        }
    }

    private static List<String> normalizeList(List<String> values) {
        return values == null
                ? List.of()
                : values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
