package net.sixik.ga_utils.javatogpu.runtime.methodtest;

import net.sixik.ga_utils.javatogpu.runtime.*;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-only fixture value-binding preflight for future {@code @GPUTest} execution.
 */
public record GpuRuntimeMethodTestFixtureValueBindingPlan(
        String kernelName,
        String irGpuResource,
        List<GpuRuntimeMethodTestFixtureValueBinding> bindings,
        List<String> blockers,
        List<String> diagnostics
) {

    public GpuRuntimeMethodTestFixtureValueBindingPlan {
        kernelName = normalize(kernelName, "unknown");
        irGpuResource = normalize(irGpuResource, "");
        bindings = bindings == null ? List.of() : List.copyOf(bindings);
        blockers = normalizeList(blockers);
        diagnostics = normalizeList(diagnostics);
    }

    public boolean bindingsReady() {
        return !bindings.isEmpty() && blockers.isEmpty() && bindings.stream()
                .allMatch(GpuRuntimeMethodTestFixtureValueBinding::bindingReady);
    }

    public int bindingReadyCount() {
        return (int) bindings.stream()
                .filter(GpuRuntimeMethodTestFixtureValueBinding::bindingReady)
                .count();
    }

    public int bindingBlockedCount() {
        return bindings.size() - bindingReadyCount();
    }

    public String firstBlocker() {
        return blockers.isEmpty() ? "none" : blockers.get(0);
    }

    public Map<String, String> artifactFields(String prefix) {
        String normalizedPrefix = prefix == null || prefix.isBlank() ? "methodTestFixtureValueBindings" : prefix.trim();
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        fields.put(normalizedPrefix + ".kernelName", kernelName);
        fields.put(normalizedPrefix + ".irGpuResource", irGpuResource);
        fields.put(normalizedPrefix + ".bindingsReady", Boolean.toString(bindingsReady()));
        fields.put(normalizedPrefix + ".binding.count", Integer.toString(bindings.size()));
        fields.put(normalizedPrefix + ".binding.ready.count", Integer.toString(bindingReadyCount()));
        fields.put(normalizedPrefix + ".binding.blocked.count", Integer.toString(bindingBlockedCount()));
        fields.put(normalizedPrefix + ".firstBlocker", firstBlocker());
        for (int index = 0; index < bindings.size(); index++) {
            fields.putAll(bindings.get(index).artifactFields(normalizedPrefix + ".binding." + index));
        }
        writeList(fields, normalizedPrefix + ".blocker", blockers);
        writeList(fields, normalizedPrefix + ".diagnostic", diagnostics);
        return Collections.unmodifiableMap(fields);
    }

    public String toMarkdown() {
        StringBuilder builder = new StringBuilder();
        builder.append("Method test fixture value bindings: ")
                .append(bindingsReady() ? "ready" : "blocked")
                .append('\n');
        builder.append("Bindings: ")
                .append(bindingReadyCount())
                .append('/')
                .append(bindings.size())
                .append(" ready")
                .append('\n');
        if (!"none".equals(firstBlocker())) {
            builder.append("First blocker: ").append(firstBlocker()).append('\n');
        }
        if (!bindings.isEmpty()) {
            builder.append('\n').append("Value bindings:").append('\n');
            for (GpuRuntimeMethodTestFixtureValueBinding binding : bindings) {
                builder.append("- ")
                        .append(binding.testId())
                        .append(' ')
                        .append(binding.kind())
                        .append('[')
                        .append(binding.fixtureIndex())
                        .append("] `")
                        .append(binding.resourceRef())
                        .append("` -> ")
                        .append(binding.parameterName())
                        .append(':')
                        .append(binding.javaType())
                        .append(" ready=")
                        .append(binding.bindingReady());
                if (binding.bindingReady()) {
                    builder.append(" ")
                            .append(binding.valueKind())
                            .append('[')
                            .append(binding.itemCount())
                            .append(']');
                }
                if (!"none".equals(binding.blocker())) {
                    builder.append(" blocker=").append(binding.blocker());
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
