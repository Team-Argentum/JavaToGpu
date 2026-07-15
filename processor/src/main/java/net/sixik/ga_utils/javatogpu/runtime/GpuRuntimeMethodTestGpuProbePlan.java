package net.sixik.ga_utils.javatogpu.runtime;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Bounded GPU execution probe plan for materialized {@code @GPUTest} vectors.
 */
public record GpuRuntimeMethodTestGpuProbePlan(
        String kernelName,
        String irGpuResource,
        List<GpuRuntimeMethodTestGpuProbeExecution> executions,
        List<String> blockers,
        List<String> diagnostics
) {

    public GpuRuntimeMethodTestGpuProbePlan {
        kernelName = normalize(kernelName, "unknown");
        irGpuResource = normalize(irGpuResource, "");
        executions = executions == null ? List.of() : List.copyOf(executions);
        blockers = normalizeList(blockers);
        diagnostics = normalizeList(diagnostics);
    }

    public boolean gpuProbeReady() {
        return !executions.isEmpty() && blockers.isEmpty()
                && executions.stream().allMatch(GpuRuntimeMethodTestGpuProbeExecution::executionReady);
    }

    public boolean gpuProbePassed() {
        return gpuProbeReady() && executions.stream()
                .allMatch(GpuRuntimeMethodTestGpuProbeExecution::executionPassed);
    }

    public int executionReadyCount() {
        return (int) executions.stream()
                .filter(GpuRuntimeMethodTestGpuProbeExecution::executionReady)
                .count();
    }

    public int executionPassedCount() {
        return (int) executions.stream()
                .filter(GpuRuntimeMethodTestGpuProbeExecution::executionPassed)
                .count();
    }

    public int executionFailedCount() {
        return (int) executions.stream()
                .filter(execution -> execution.executionReady() && !execution.executionPassed())
                .count();
    }

    public int comparisonPassedCount() {
        return executions.stream()
                .mapToInt(GpuRuntimeMethodTestGpuProbeExecution::comparisonPassedCount)
                .sum();
    }

    public int comparisonCount() {
        return executions.stream()
                .mapToInt(execution -> execution.comparisons().size())
                .sum();
    }

    public String status() {
        if (!blockers.isEmpty() || executions.stream().anyMatch(execution -> !execution.executionReady())) {
            return "blocked";
        }
        if (executionFailedCount() > 0) {
            return "failed";
        }
        return gpuProbePassed() ? "passed" : "blocked";
    }

    public String firstBlocker() {
        if (!blockers.isEmpty()) {
            return blockers.get(0);
        }
        return executions.stream()
                .map(GpuRuntimeMethodTestGpuProbeExecution::firstBlocker)
                .filter(blocker -> !"none".equals(blocker))
                .findFirst()
                .orElse("none");
    }

    public String firstFailure() {
        return executions.stream()
                .map(GpuRuntimeMethodTestGpuProbeExecution::firstFailure)
                .filter(failure -> !"none".equals(failure))
                .findFirst()
                .orElse("none");
    }

    public Map<String, String> artifactFields(String prefix) {
        String normalizedPrefix = prefix == null || prefix.isBlank() ? "methodTestGpuProbes" : prefix.trim();
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        fields.put(normalizedPrefix + ".kernelName", kernelName);
        fields.put(normalizedPrefix + ".irGpuResource", irGpuResource);
        fields.put(normalizedPrefix + ".status", status());
        fields.put(normalizedPrefix + ".gpuProbeReady", Boolean.toString(gpuProbeReady()));
        fields.put(normalizedPrefix + ".gpuProbePassed", Boolean.toString(gpuProbePassed()));
        fields.put(normalizedPrefix + ".execution.count", Integer.toString(executions.size()));
        fields.put(normalizedPrefix + ".execution.ready.count", Integer.toString(executionReadyCount()));
        fields.put(normalizedPrefix + ".execution.passed.count", Integer.toString(executionPassedCount()));
        fields.put(normalizedPrefix + ".execution.failed.count", Integer.toString(executionFailedCount()));
        fields.put(normalizedPrefix + ".comparison.count", Integer.toString(comparisonCount()));
        fields.put(normalizedPrefix + ".comparison.passed.count", Integer.toString(comparisonPassedCount()));
        fields.put(normalizedPrefix + ".firstBlocker", firstBlocker());
        fields.put(normalizedPrefix + ".firstFailure", firstFailure());
        for (int index = 0; index < executions.size(); index++) {
            fields.putAll(executions.get(index).artifactFields(normalizedPrefix + ".execution." + index));
        }
        writeList(fields, normalizedPrefix + ".blocker", blockers);
        writeList(fields, normalizedPrefix + ".diagnostic", diagnostics);
        return Collections.unmodifiableMap(fields);
    }

    public String toMarkdown() {
        StringBuilder builder = new StringBuilder();
        builder.append("Method test GPU probe plan: ")
                .append(status())
                .append('\n');
        builder.append("Executions: ")
                .append(executionPassedCount())
                .append('/')
                .append(executions.size())
                .append(" passed")
                .append('\n');
        builder.append("Comparisons: ")
                .append(comparisonPassedCount())
                .append('/')
                .append(comparisonCount())
                .append(" passed")
                .append('\n');
        if (!"none".equals(firstBlocker())) {
            builder.append("First blocker: ").append(firstBlocker()).append('\n');
        }
        if (!"none".equals(firstFailure())) {
            builder.append("First failure: ").append(firstFailure()).append('\n');
        }
        if (!executions.isEmpty()) {
            builder.append('\n');
            for (GpuRuntimeMethodTestGpuProbeExecution execution : executions) {
                builder.append(execution.toMarkdown());
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
