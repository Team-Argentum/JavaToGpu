package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionExecutionOutcome;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Hardware-free report produced by {@link GpuBackendCompilerFeedbackHarness}.
 */
public record GpuBackendCompilerFeedbackHarnessReport(
        GpuBackendTarget backendTarget,
        Map<String, String> extensionFields,
        GpuBackendCompilerFeedbackReport feedbackReport
) {

    public GpuBackendCompilerFeedbackHarnessReport {
        backendTarget = backendTarget == null ? GpuBackendTarget.UNKNOWN : backendTarget;
        extensionFields = extensionFields == null ? Map.of() : Map.copyOf(extensionFields);
        feedbackReport = feedbackReport == null ? new GpuBackendCompilerFeedbackReport(null, null, null) : feedbackReport;
    }

    public boolean available() {
        return feedbackReport.available();
    }

    public boolean allProvidersCompleted() {
        return feedbackReport.executions().stream()
                .noneMatch(report -> report.outcome() == GpuExtensionExecutionOutcome.FAILED_CONTINUED
                        || report.outcome() == GpuExtensionExecutionOutcome.FAILED_CLOSED);
    }

    public String selectedProviderId() {
        return feedbackReport.selected()
                .map(GpuBackendCompilerFeedback::providerId)
                .orElse("none");
    }

    public String status() {
        if (available() && allProvidersCompleted()) {
            return "recorded";
        }
        if (available()) {
            return "recorded-with-provider-failures";
        }
        return allProvidersCompleted() ? "unavailable" : "unavailable-with-provider-failures";
    }

    public Map<String, String> artifactFields(String prefix) {
        String normalizedPrefix = prefix == null || prefix.isBlank()
                ? "runtime.compilerFeedback.harness"
                : prefix.trim();
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        fields.put(normalizedPrefix + ".present", "true");
        fields.put(normalizedPrefix + ".status", status());
        fields.put(normalizedPrefix + ".backendTarget", backendTarget.name());
        fields.put(normalizedPrefix + ".available", Boolean.toString(available()));
        fields.put(normalizedPrefix + ".selectedProviderId", selectedProviderId());
        fields.put(normalizedPrefix + ".feedback.count", Integer.toString(feedbackReport.feedback().size()));
        fields.put(normalizedPrefix + ".execution.count", Integer.toString(feedbackReport.executions().size()));
        fields.put(normalizedPrefix + ".allProvidersCompleted", Boolean.toString(allProvidersCompleted()));
        fields.put(normalizedPrefix + ".extension.field.count", Integer.toString(extensionFields.size()));
        fields.putAll(prefixed(extensionFields, normalizedPrefix + ".extension"));
        fields.putAll(prefixed(feedbackReport.artifactFields(), normalizedPrefix + ".feedback"));
        fields.put("runtime.compilerFeedback.harness.present", "true");
        fields.put("runtime.compilerFeedback.harness.status", status());
        fields.put("runtime.compilerFeedback.harness.backendTarget", backendTarget.name());
        fields.put("runtime.compilerFeedback.harness.available", Boolean.toString(available()));
        fields.put("runtime.compilerFeedback.harness.selectedProviderId", selectedProviderId());
        return Collections.unmodifiableMap(fields);
    }

    public String toMarkdown() {
        StringBuilder builder = new StringBuilder();
        builder.append("Backend compiler feedback harness: ").append(status()).append('\n');
        builder.append("Backend: ").append(backendTarget).append('\n');
        builder.append("Provider executions: ").append(feedbackReport.executions().size())
                .append(", allCompleted=")
                .append(allProvidersCompleted())
                .append('\n');
        builder.append("Feedback entries: ").append(feedbackReport.feedback().size()).append('\n');
        builder.append("Selected provider: ").append(selectedProviderId()).append('\n');
        feedbackReport.selected().ifPresent(feedback -> builder
                .append("Selected metrics: effectiveRegisters=")
                .append(metric(feedback.effectiveRegisterCount()))
                .append(", knownSpillBytes=")
                .append(metric(feedback.knownSpillBytes()))
                .append(", localMemoryBytes=")
                .append(metric(feedback.localMemoryBytes()))
                .append(", occupancyPermille=")
                .append(metric(feedback.occupancyPermille()))
                .append('\n'));
        return builder.toString();
    }

    private static String metric(int value) {
        return value < 0 ? "unknown" : Integer.toString(value);
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
