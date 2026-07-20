package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Hardware-free report produced by {@link GpuBackendHookTestHarness}.
 */
public record GpuBackendHookTestHarnessReport(
        GpuBackendTarget backendTarget,
        int loadedHookCount,
        Map<String, String> registryFields,
        Map<String, String> authorizationFields,
        Map<String, String> discoveryFields,
        Map<String, String> loweringFields,
        Map<String, String> compilationFields,
        Map<String, String> invocationFields,
        Map<String, String> artifactFields
) {

    public GpuBackendHookTestHarnessReport {
        backendTarget = backendTarget == null ? GpuBackendTarget.UNKNOWN : backendTarget;
        loadedHookCount = Math.max(0, loadedHookCount);
        registryFields = immutableCopy(registryFields);
        authorizationFields = immutableCopy(authorizationFields);
        discoveryFields = immutableCopy(discoveryFields);
        loweringFields = immutableCopy(loweringFields);
        compilationFields = immutableCopy(compilationFields);
        invocationFields = immutableCopy(invocationFields);
        artifactFields = immutableCopy(artifactFields);
    }

    public Map<String, String> artifactFields(String prefix) {
        String normalizedPrefix = prefix == null || prefix.isBlank() ? "backendHookHarness" : prefix.trim();
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        fields.put(normalizedPrefix + ".present", "true");
        fields.put(normalizedPrefix + ".backendTarget", backendTarget.name());
        fields.put(normalizedPrefix + ".loadedHook.count", Integer.toString(loadedHookCount));
        appendIndexedFields(fields, normalizedPrefix + ".registry", registryFields);
        appendIndexedFields(fields, normalizedPrefix + ".authorization", authorizationFields);
        appendIndexedFields(fields, normalizedPrefix + ".discovery", discoveryFields);
        appendIndexedFields(fields, normalizedPrefix + ".lowering", loweringFields);
        appendIndexedFields(fields, normalizedPrefix + ".compilation", compilationFields);
        appendIndexedFields(fields, normalizedPrefix + ".invocation", invocationFields);
        appendIndexedFields(fields, normalizedPrefix + ".artifact", artifactFields);
        return Collections.unmodifiableMap(fields);
    }

    public String toMarkdown() {
        StringBuilder builder = new StringBuilder();
        builder.append("Backend hook harness: ").append(backendTarget).append(System.lineSeparator());
        builder.append("- Loaded hooks: ").append(loadedHookCount).append(System.lineSeparator());
        appendAuthorizationSummary(builder, "Discovery authorization", authorizationFields, "runtime.backend.hookAuthorization.discovery");
        appendAuthorizationSummary(builder, "Lowering authorization", authorizationFields, "runtime.backend.hookAuthorization.lowering");
        appendAuthorizationSummary(builder, "Compilation authorization", authorizationFields, "runtime.backend.hookAuthorization.compilation");
        appendAuthorizationSummary(builder, "Invocation authorization", authorizationFields, "runtime.backend.hookAuthorization.invocation");
        appendAuthorizationSummary(builder, "Artifact authorization", authorizationFields, "runtime.backend.hookAuthorization.artifact");
        appendStageSummary(builder, "Discovery", discoveryFields, "runtime.backend.hookExecution.discovery");
        appendStageSummary(builder, "Lowering", loweringFields, "runtime.backend.hookExecution.lowering");
        appendStageSummary(builder, "Compilation", compilationFields, "runtime.backend.hookExecution.compilation");
        appendStageSummary(builder, "Invocation", invocationFields, "runtime.backend.hookExecution.invocation");
        appendStageSummary(builder, "Artifact", artifactFields, "runtime.backend.hookExecution.artifact");
        return builder.toString();
    }

    private static void appendStageSummary(
            StringBuilder builder,
            String label,
            Map<String, String> fields,
            String prefix
    ) {
        builder.append("- ")
                .append(label)
                .append(": hooks=")
                .append(fields.getOrDefault(prefix + ".hook.count", "0"))
                .append(", applied=")
                .append(fields.getOrDefault(prefix + ".applied.count", "0"))
                .append(", skipped=")
                .append(fields.getOrDefault(prefix + ".skipped.count", "0"))
                .append(", failed=")
                .append(fields.getOrDefault(prefix + ".failed.count", "0"))
                .append(", mutationIgnored=")
                .append(fields.getOrDefault(prefix + ".mutationIgnored.count", "0"))
                .append(System.lineSeparator());
    }

    private static void appendAuthorizationSummary(
            StringBuilder builder,
            String label,
            Map<String, String> fields,
            String prefix
    ) {
        builder.append("- ")
                .append(label)
                .append(": status=")
                .append(fields.getOrDefault(prefix + ".status", "unknown"))
                .append(", executable=")
                .append(fields.getOrDefault(prefix + ".currentRegistryExecutable.count", "0"))
                .append(", blocked=")
                .append(fields.getOrDefault(prefix + ".blocked.count", "0"))
                .append(", firstBlocker=")
                .append(fields.getOrDefault(prefix + ".firstBlocker", "none"))
                .append(System.lineSeparator());
    }

    private static void appendIndexedFields(
            LinkedHashMap<String, String> output,
            String prefix,
            Map<String, String> source
    ) {
        output.put(prefix + ".field.count", Integer.toString(source.size()));
        int index = 0;
        for (Map.Entry<String, String> entry : source.entrySet()) {
            output.put(prefix + ".field." + index + ".key", entry.getKey());
            output.put(prefix + ".field." + index + ".value", entry.getValue());
            index++;
        }
    }

    private static Map<String, String> immutableCopy(Map<String, String> fields) {
        if (fields == null || fields.isEmpty()) {
            return Map.of();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(fields));
    }
}
