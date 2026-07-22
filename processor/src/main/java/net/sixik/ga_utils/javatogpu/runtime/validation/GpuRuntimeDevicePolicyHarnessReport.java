package net.sixik.ga_utils.javatogpu.runtime.validation;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.runtime.*;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionExecutionOutcome;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Hardware-free report produced by {@link GpuRuntimeDevicePolicyHarness}.
 */
public record GpuRuntimeDevicePolicyHarnessReport(
        GpuBackendTarget backendTarget,
        List<GpuRuntimeDeviceProfile> candidates,
        Map<String, String> extensionFields,
        GpuRuntimeDeviceSelection selection
) {

    public GpuRuntimeDevicePolicyHarnessReport {
        backendTarget = backendTarget == null ? GpuBackendTarget.UNKNOWN : backendTarget;
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
        extensionFields = extensionFields == null ? Map.of() : Map.copyOf(extensionFields);
        selection = selection == null
                ? new GpuRuntimeDeviceSelection(null, null, null, null, true, false, "none", List.of())
                : selection;
    }

    public boolean selected() {
        return selection.selectedDevice().isPresent();
    }

    public String selectedDeviceKey() {
        return selection.selectedDevice()
                .map(GpuRuntimeDevicePolicyContext::deviceKey)
                .orElse("none");
    }

    public String selectedDeviceLabel() {
        return selection.selectedDevice()
                .map(GpuRuntimeDeviceProfile::deviceLabel)
                .orElse("none");
    }

    public boolean allPoliciesSucceeded() {
        return selection.executionReports().stream()
                .allMatch(report -> report.outcome() == GpuExtensionExecutionOutcome.SUCCEEDED);
    }

    public String status() {
        if (selection.failedClosed()) {
            return "failed-closed";
        }
        if (!selection.compileOptionsValid()) {
            return "compile-options-blocked";
        }
        if (!allPoliciesSucceeded()) {
            return selected() ? "selected-with-policy-failures" : "blocked-with-policy-failures";
        }
        return selected() ? "selected" : "blocked";
    }

    public Map<String, String> artifactFields(String prefix) {
        String normalizedPrefix = prefix == null || prefix.isBlank()
                ? "runtime.devicePolicy.harness"
                : prefix.trim();
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        fields.put(normalizedPrefix + ".present", "true");
        fields.put(normalizedPrefix + ".status", status());
        fields.put(normalizedPrefix + ".backendTarget", backendTarget.name());
        fields.put(normalizedPrefix + ".candidate.count", Integer.toString(candidates.size()));
        fields.put(normalizedPrefix + ".policy.count", Integer.toString(selection.policyDecisions().size()));
        fields.put(normalizedPrefix + ".execution.count", Integer.toString(selection.executionReports().size()));
        fields.put(normalizedPrefix + ".allPoliciesSucceeded", Boolean.toString(allPoliciesSucceeded()));
        fields.put(normalizedPrefix + ".selected", Boolean.toString(selected()));
        fields.put(normalizedPrefix + ".selectedDeviceKey", selectedDeviceKey());
        fields.put(normalizedPrefix + ".selectedDeviceLabel", selectedDeviceLabel());
        fields.put(normalizedPrefix + ".firstBlocker", selection.firstBlocker());
        fields.put(normalizedPrefix + ".extension.field.count", Integer.toString(extensionFields.size()));
        fields.putAll(prefixed(extensionFields, normalizedPrefix + ".extension"));
        fields.putAll(selection.artifactFields(normalizedPrefix + ".selection"));
        fields.put("runtime.devicePolicy.harness.present", "true");
        fields.put("runtime.devicePolicy.harness.status", status());
        fields.put("runtime.devicePolicy.harness.backendTarget", backendTarget.name());
        fields.put("runtime.devicePolicy.harness.selected", Boolean.toString(selected()));
        fields.put("runtime.devicePolicy.harness.selectedDeviceKey", selectedDeviceKey());
        fields.put("runtime.devicePolicy.harness.firstBlocker", selection.firstBlocker());
        return Collections.unmodifiableMap(fields);
    }

    public String toMarkdown() {
        StringBuilder builder = new StringBuilder();
        builder.append("Runtime device policy harness: ").append(status()).append('\n');
        builder.append("Backend: ").append(backendTarget).append('\n');
        builder.append("Candidates: ").append(candidates.size()).append('\n');
        builder.append("Policies: ").append(selection.policyDecisions().size())
                .append(", executions=")
                .append(selection.executionReports().size())
                .append(", allSucceeded=")
                .append(allPoliciesSucceeded())
                .append('\n');
        builder.append("Selected: ").append(selectedDeviceLabel())
                .append(" (`")
                .append(selectedDeviceKey())
                .append("`)")
                .append('\n');
        builder.append("First blocker: ").append(selection.firstBlocker()).append('\n');
        if (!selection.rankedCandidates().isEmpty()) {
            builder.append('\n').append("Ranked candidates:").append('\n');
            for (GpuRuntimeDeviceCandidateRanking ranking : selection.rankedCandidates()) {
                builder.append("- ")
                        .append(ranking.deviceKey())
                        .append(": total=")
                        .append(ranking.totalScore())
                        .append(", policyAdjustment=")
                        .append(ranking.policyScoreAdjustment())
                        .append(", rejected=")
                        .append(ranking.rejected())
                        .append('\n');
            }
        }
        return builder.toString();
    }

    private static Map<String, String> prefixed(Map<String, String> source, String prefix) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        source.forEach((key, value) -> fields.put(prefix + "." + key, value));
        return fields;
    }
}
