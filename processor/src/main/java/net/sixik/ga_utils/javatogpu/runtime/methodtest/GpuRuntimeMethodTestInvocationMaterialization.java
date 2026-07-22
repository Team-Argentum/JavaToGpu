package net.sixik.ga_utils.javatogpu.runtime.methodtest;

import net.sixik.ga_utils.javatogpu.runtime.*;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Materialized Java argument set for one future {@code @GPUTest} invocation.
 */
public record GpuRuntimeMethodTestInvocationMaterialization(
        String testId,
        boolean invocationReady,
        List<GpuRuntimeMethodTestInvocationArgument> arguments,
        List<String> blockers,
        List<String> diagnostics
) {

    public GpuRuntimeMethodTestInvocationMaterialization {
        testId = normalize(testId, "unknown");
        arguments = arguments == null ? List.of() : List.copyOf(arguments);
        blockers = normalizeList(blockers);
        diagnostics = normalizeList(diagnostics);
        invocationReady = invocationReady && !arguments.isEmpty() && blockers.isEmpty()
                && arguments.stream().allMatch(GpuRuntimeMethodTestInvocationArgument::argumentReady);
    }

    public int argumentReadyCount() {
        return (int) arguments.stream()
                .filter(GpuRuntimeMethodTestInvocationArgument::argumentReady)
                .count();
    }

    public int expectedOutputReadyCount() {
        return (int) arguments.stream()
                .filter(GpuRuntimeMethodTestInvocationArgument::expectedOutputReady)
                .count();
    }

    public String firstBlocker() {
        return blockers.isEmpty() ? "none" : blockers.get(0);
    }

    public Object[] invocationArguments() {
        if (!invocationReady) {
            return new Object[0];
        }
        Object[] values = new Object[arguments.size()];
        for (int index = 0; index < arguments.size(); index++) {
            values[index] = arguments.get(index).argumentValue();
        }
        return values;
    }

    public Map<String, String> artifactFields(String prefix) {
        String normalizedPrefix = prefix == null || prefix.isBlank() ? "methodTestInvocation" : prefix.trim();
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        fields.put(normalizedPrefix + ".testId", testId);
        fields.put(normalizedPrefix + ".invocationReady", Boolean.toString(invocationReady));
        fields.put(normalizedPrefix + ".argument.count", Integer.toString(arguments.size()));
        fields.put(normalizedPrefix + ".argument.ready.count", Integer.toString(argumentReadyCount()));
        fields.put(normalizedPrefix + ".expectedOutput.ready.count", Integer.toString(expectedOutputReadyCount()));
        fields.put(normalizedPrefix + ".firstBlocker", firstBlocker());
        for (int index = 0; index < arguments.size(); index++) {
            fields.putAll(arguments.get(index).artifactFields(normalizedPrefix + ".argument." + index));
        }
        writeList(fields, normalizedPrefix + ".blocker", blockers);
        writeList(fields, normalizedPrefix + ".diagnostic", diagnostics);
        return Collections.unmodifiableMap(fields);
    }

    public String toMarkdown() {
        StringBuilder builder = new StringBuilder();
        builder.append("Method test invocation materialization: ")
                .append(invocationReady ? "ready" : "blocked")
                .append('\n');
        builder.append("Test id: ").append(testId).append('\n');
        builder.append("Invocation arguments: ")
                .append(argumentReadyCount())
                .append('/')
                .append(arguments.size())
                .append(" ready")
                .append('\n');
        builder.append("Expected outputs: ")
                .append(expectedOutputReadyCount())
                .append(" ready")
                .append('\n');
        if (!"none".equals(firstBlocker())) {
            builder.append("First blocker: ").append(firstBlocker()).append('\n');
        }
        if (!arguments.isEmpty()) {
            builder.append('\n').append("Invocation arguments:").append('\n');
            for (GpuRuntimeMethodTestInvocationArgument argument : arguments) {
                builder.append("- ")
                        .append(argument.parameterIndex())
                        .append(' ')
                        .append(argument.parameterName())
                        .append(':')
                        .append(argument.javaType())
                        .append(" ready=")
                        .append(argument.argumentReady());
                if (argument.argumentReady()) {
                    builder.append(" ")
                            .append(argument.argumentKind())
                            .append('[')
                            .append(argument.itemCount())
                            .append(']');
                }
                if (argument.expectedOutputReady()) {
                    builder.append(" expected=")
                            .append(argument.expectedOutputKind())
                            .append('[')
                            .append(argument.expectedOutputItemCount())
                            .append(']');
                }
                if (!"none".equals(argument.blocker())) {
                    builder.append(" blocker=").append(argument.blocker());
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
