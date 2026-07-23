package net.sixik.ga_utils.javatogpu.runtime.methodtest;

import net.sixik.ga_utils.javatogpu.runtime.*;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CPU/reference comparison plan for materialized {@code @GPUTest} invocations.
 */
public record GpuRuntimeMethodTestReferenceComparisonPlan(
        String kernelName,
        String irGpuResource,
        List<GpuRuntimeMethodTestReferenceComparison> comparisons,
        List<String> blockers,
        List<String> diagnostics
) {

    public GpuRuntimeMethodTestReferenceComparisonPlan {
        kernelName = normalize(kernelName, "unknown");
        irGpuResource = normalize(irGpuResource, "");
        comparisons = comparisons == null ? List.of() : List.copyOf(comparisons);
        blockers = normalizeList(blockers);
        diagnostics = normalizeList(diagnostics);
    }

    public boolean referenceComparisonReady() {
        return !comparisons.isEmpty() && blockers.isEmpty()
                && comparisons.stream().allMatch(GpuRuntimeMethodTestReferenceComparison::comparisonReady);
    }

    public boolean referenceComparisonPassed() {
        return referenceComparisonReady() && comparisons.stream()
                .allMatch(GpuRuntimeMethodTestReferenceComparison::passed);
    }

    public int comparisonReadyCount() {
        return (int) comparisons.stream()
                .filter(GpuRuntimeMethodTestReferenceComparison::comparisonReady)
                .count();
    }

    public int comparisonBlockedCount() {
        return comparisons.size() - comparisonReadyCount();
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

    public String status() {
        if (!blockers.isEmpty()) {
            return "blocked";
        }
        if (comparisonFailedCount() > 0) {
            return "failed";
        }
        return referenceComparisonPassed() ? "passed" : "blocked";
    }

    public String firstBlocker() {
        if (!blockers.isEmpty()) {
            return blockers.get(0);
        }
        return comparisons.stream()
                .filter(comparison -> !comparison.comparisonReady())
                .map(GpuRuntimeMethodTestReferenceComparison::blocker)
                .filter(blocker -> !"none".equals(blocker))
                .findFirst()
                .orElse("none");
    }

    public String firstFailure() {
        return comparisons.stream()
                .filter(comparison -> comparison.comparisonReady() && !comparison.passed())
                .map(GpuRuntimeMethodTestReferenceComparison::failure)
                .filter(failure -> !"none".equals(failure))
                .findFirst()
                .orElse("none");
    }

    public Map<String, String> artifactFields(String prefix) {
        String normalizedPrefix = prefix == null || prefix.isBlank() ? "methodTestReferenceComparisons" : prefix.trim();
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        fields.put(normalizedPrefix + ".kernelName", kernelName);
        fields.put(normalizedPrefix + ".irGpuResource", irGpuResource);
        fields.put(normalizedPrefix + ".status", status());
        fields.put(normalizedPrefix + ".referenceComparisonReady", Boolean.toString(referenceComparisonReady()));
        fields.put(normalizedPrefix + ".referenceComparisonPassed", Boolean.toString(referenceComparisonPassed()));
        fields.put(normalizedPrefix + ".comparison.count", Integer.toString(comparisons.size()));
        fields.put(normalizedPrefix + ".comparison.ready.count", Integer.toString(comparisonReadyCount()));
        fields.put(normalizedPrefix + ".comparison.blocked.count", Integer.toString(comparisonBlockedCount()));
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
        builder.append("Method test reference comparison: ")
                .append(status())
                .append('\n');
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
        if (!comparisons.isEmpty()) {
            builder.append('\n').append("Reference comparisons:").append('\n');
            for (GpuRuntimeMethodTestReferenceComparison comparison : comparisons) {
                builder.append("- ")
                        .append(comparison.testId())
                        .append(' ')
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
                if (comparison.absoluteTolerance() > 0.0d || comparison.relativeTolerance() > 0.0d) {
                    builder.append(" tolerance=abs=")
                            .append(comparison.absoluteTolerance())
                            .append(",rel=")
                            .append(comparison.relativeTolerance());
                }
                if (!"none".equals(comparison.diagnostic())) {
                    builder.append(" diagnostic=").append(comparison.diagnostic());
                }
                builder.append('\n');
            }
        }
        if (!diagnostics.isEmpty()) {
            builder.append('\n').append("Diagnostics:").append('\n');
            for (String diagnostic : diagnostics) {
                builder.append("- ").append(diagnostic).append('\n');
            }
        }
        return builder.toString();
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
