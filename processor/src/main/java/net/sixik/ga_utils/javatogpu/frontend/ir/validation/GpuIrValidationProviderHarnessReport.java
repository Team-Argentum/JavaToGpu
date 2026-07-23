package net.sixik.ga_utils.javatogpu.frontend.ir.validation;

import net.sixik.ga_utils.javatogpu.extension.GpuExtensionExecutionOutcome;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionExecutionReport;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Hardware-free report produced by {@link GpuIrValidationProviderHarness}.
 */
public record GpuIrValidationProviderHarnessReport(
        GpuIrValidationMode mode,
        Map<String, String> extensionFields,
        List<GpuExtensionExecutionReport> executionReports,
        List<GpuIrValidationReportEntry> validationEntries,
        List<String> diagnostics
) {

    public GpuIrValidationProviderHarnessReport {
        mode = mode == null ? GpuIrValidationMode.DIAGNOSTIC : mode;
        extensionFields = extensionFields == null ? Map.of() : Map.copyOf(extensionFields);
        executionReports = executionReports == null ? List.of() : List.copyOf(executionReports);
        validationEntries = validationEntries == null ? List.of() : List.copyOf(validationEntries);
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }

    public int providerCount() {
        String value = extensionFields.get("irValidationExtension.count");
        if (value == null || value.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    public boolean allProvidersCompleted() {
        return executionReports.stream()
                .noneMatch(report -> report.outcome() == GpuExtensionExecutionOutcome.FAILED_CONTINUED
                        || report.outcome() == GpuExtensionExecutionOutcome.FAILED_CLOSED);
    }

    public String firstBlocker() {
        String contractError = extensionFields.get("irValidationExtension.contractError");
        if (contractError != null && !contractError.isBlank()) {
            return "contract:" + contractError;
        }
        return executionReports.stream()
                .filter(report -> report.outcome() == GpuExtensionExecutionOutcome.FAILED_CONTINUED
                        || report.outcome() == GpuExtensionExecutionOutcome.FAILED_CLOSED)
                .map(report -> report.extensionId() + ":" + report.outcome() + ":" + report.message())
                .findFirst()
                .orElse("none");
    }

    public String status() {
        if (mode == GpuIrValidationMode.OFF) {
            return "disabled";
        }
        if (providerCount() == 0) {
            return "no-providers";
        }
        if (extensionFields.containsKey("irValidationExtension.contractError")) {
            return "contract-error";
        }
        if (executionReports.stream().anyMatch(report -> report.outcome() == GpuExtensionExecutionOutcome.FAILED_CLOSED)) {
            return "failed-closed";
        }
        if (executionReports.stream().anyMatch(report -> report.outcome() == GpuExtensionExecutionOutcome.FAILED_CONTINUED)) {
            return "completed-with-provider-failures";
        }
        return "completed";
    }

    public Map<String, String> artifactFields(String prefix) {
        String normalizedPrefix = prefix == null || prefix.isBlank()
                ? "irValidation.harness"
                : prefix.trim();
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        fields.put(normalizedPrefix + ".present", "true");
        fields.put(normalizedPrefix + ".status", status());
        fields.put(normalizedPrefix + ".mode", mode.name());
        fields.put(normalizedPrefix + ".provider.count", Integer.toString(providerCount()));
        fields.put(normalizedPrefix + ".execution.count", Integer.toString(executionReports.size()));
        fields.put(normalizedPrefix + ".entry.count", Integer.toString(validationEntries.size()));
        fields.put(normalizedPrefix + ".diagnostic.count", Integer.toString(diagnostics.size()));
        fields.put(normalizedPrefix + ".allProvidersCompleted", Boolean.toString(allProvidersCompleted()));
        fields.put(normalizedPrefix + ".firstBlocker", firstBlocker());
        fields.putAll(prefixed(extensionFields, normalizedPrefix + ".extension"));
        for (int index = 0; index < executionReports.size(); index++) {
            fields.putAll(executionReports.get(index).artifactFields(normalizedPrefix + ".execution." + index));
        }
        for (int index = 0; index < validationEntries.size(); index++) {
            fields.putAll(validationEntries.get(index).artifactFields(normalizedPrefix + ".entry." + index));
        }
        for (int index = 0; index < diagnostics.size(); index++) {
            fields.put(normalizedPrefix + ".diagnostic." + index, diagnostics.get(index));
        }
        fields.put("irValidation.harness.present", "true");
        fields.put("irValidation.harness.status", status());
        fields.put("irValidation.harness.mode", mode.name());
        fields.put("irValidation.harness.provider.count", Integer.toString(providerCount()));
        fields.put("irValidation.harness.entry.count", Integer.toString(validationEntries.size()));
        fields.put("irValidation.harness.firstBlocker", firstBlocker());
        return Collections.unmodifiableMap(fields);
    }

    public String toMarkdown() {
        StringBuilder builder = new StringBuilder();
        builder.append("IR validation provider harness: ").append(status()).append('\n');
        builder.append("Mode: ").append(mode).append('\n');
        builder.append("Providers: ").append(providerCount()).append('\n');
        builder.append("Executions: ").append(executionReports.size())
                .append(", allCompleted=")
                .append(allProvidersCompleted())
                .append('\n');
        builder.append("Validation entries: ").append(validationEntries.size()).append('\n');
        builder.append("First blocker: ").append(firstBlocker()).append('\n');
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
