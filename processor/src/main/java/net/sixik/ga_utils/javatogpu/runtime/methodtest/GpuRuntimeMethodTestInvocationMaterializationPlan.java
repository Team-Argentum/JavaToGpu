package net.sixik.ga_utils.javatogpu.runtime.methodtest;

import net.sixik.ga_utils.javatogpu.runtime.*;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-only materialization plan for future {@code @GPUTest} invocations.
 */
public record GpuRuntimeMethodTestInvocationMaterializationPlan(
        String kernelName,
        String irGpuResource,
        List<GpuRuntimeMethodTestInvocationMaterialization> invocations,
        List<String> blockers,
        List<String> diagnostics
) {

    public GpuRuntimeMethodTestInvocationMaterializationPlan {
        kernelName = normalize(kernelName, "unknown");
        irGpuResource = normalize(irGpuResource, "");
        invocations = invocations == null ? List.of() : List.copyOf(invocations);
        blockers = normalizeList(blockers);
        diagnostics = normalizeList(diagnostics);
    }

    public boolean materializationReady() {
        return !invocations.isEmpty() && blockers.isEmpty()
                && invocations.stream().allMatch(GpuRuntimeMethodTestInvocationMaterialization::invocationReady);
    }

    public int invocationReadyCount() {
        return (int) invocations.stream()
                .filter(GpuRuntimeMethodTestInvocationMaterialization::invocationReady)
                .count();
    }

    public int invocationBlockedCount() {
        return invocations.size() - invocationReadyCount();
    }

    public String firstBlocker() {
        return blockers.isEmpty() ? "none" : blockers.get(0);
    }

    public Map<String, String> artifactFields(String prefix) {
        String normalizedPrefix = prefix == null || prefix.isBlank() ? "methodTestInvocationMaterialization" : prefix.trim();
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        fields.put(normalizedPrefix + ".kernelName", kernelName);
        fields.put(normalizedPrefix + ".irGpuResource", irGpuResource);
        fields.put(normalizedPrefix + ".materializationReady", Boolean.toString(materializationReady()));
        fields.put(normalizedPrefix + ".invocation.count", Integer.toString(invocations.size()));
        fields.put(normalizedPrefix + ".invocation.ready.count", Integer.toString(invocationReadyCount()));
        fields.put(normalizedPrefix + ".invocation.blocked.count", Integer.toString(invocationBlockedCount()));
        fields.put(normalizedPrefix + ".firstBlocker", firstBlocker());
        for (int index = 0; index < invocations.size(); index++) {
            fields.putAll(invocations.get(index).artifactFields(normalizedPrefix + ".invocation." + index));
        }
        writeList(fields, normalizedPrefix + ".blocker", blockers);
        writeList(fields, normalizedPrefix + ".diagnostic", diagnostics);
        return Collections.unmodifiableMap(fields);
    }

    public String toMarkdown() {
        StringBuilder builder = new StringBuilder();
        builder.append("Method test invocation materialization plan: ")
                .append(materializationReady() ? "ready" : "blocked")
                .append('\n');
        builder.append("Invocations: ")
                .append(invocationReadyCount())
                .append('/')
                .append(invocations.size())
                .append(" ready")
                .append('\n');
        if (!"none".equals(firstBlocker())) {
            builder.append("First blocker: ").append(firstBlocker()).append('\n');
        }
        if (!invocations.isEmpty()) {
            builder.append('\n');
            for (GpuRuntimeMethodTestInvocationMaterialization invocation : invocations) {
                builder.append(invocation.toMarkdown());
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
