package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.extension.GpuExtensionExecutionReport;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Auditable aggregate produced by all backend compiler feedback providers.
 */
public record GpuBackendCompilerFeedbackReport(
        GpuBackendCompilerFeedbackRequest request,
        List<GpuBackendCompilerFeedback> feedback,
        List<GpuExtensionExecutionReport> executions
) {

    public GpuBackendCompilerFeedbackReport {
        request = request == null
                ? new GpuBackendCompilerFeedbackRequest(null, "unknown", "", "")
                : request;
        feedback = feedback == null ? List.of() : List.copyOf(feedback);
        executions = executions == null ? List.of() : List.copyOf(executions);
    }

    public boolean available() {
        return selected().isPresent();
    }

    public Optional<GpuBackendCompilerFeedback> selected() {
        return feedback.stream().filter(GpuBackendCompilerFeedback::available).findFirst();
    }

    public Map<String, String> artifactFields() {
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        fields.put("status", available() ? "recorded" : "unavailable");
        fields.put("backendTarget", request.backendTarget().name());
        fields.put("backendFormat", request.backendFormat());
        fields.put("backendResource", request.backendResource());
        fields.put("compileLogAvailable", Boolean.toString(!request.compileLog().isBlank()));
        Map<String, String> diagnosticCompilation = diagnosticCompilationFields(request.compileLog());
        fields.put("diagnosticCompilation.present", Boolean.toString(!diagnosticCompilation.isEmpty()));
        diagnosticCompilation.forEach((key, value) -> fields.put("diagnosticCompilation." + key, value));
        fields.put("feedback.count", Integer.toString(feedback.size()));
        fields.put("selected.present", Boolean.toString(available()));
        selected().ifPresent(value -> fields.putAll(value.artifactFields("selected")));
        for (int index = 0; index < feedback.size(); index++) {
            fields.putAll(feedback.get(index).artifactFields("feedback." + index));
        }
        fields.put("execution.count", Integer.toString(executions.size()));
        for (int index = 0; index < executions.size(); index++) {
            fields.putAll(executions.get(index).artifactFields("execution." + index));
        }
        return Collections.unmodifiableMap(fields);
    }

    public String toPropertiesText() {
        StringBuilder builder = new StringBuilder();
        artifactFields().forEach((key, value) -> builder
                .append(key)
                .append('=')
                .append(safeValue(value))
                .append('\n'));
        return builder.toString();
    }

    private static String safeValue(String value) {
        return value == null ? "" : value.replace("\r", " ").replace("\n", " ");
    }

    private static Map<String, String> diagnosticCompilationFields(String compileLog) {
        if (compileLog == null || compileLog.isBlank()) {
            return Map.of();
        }
        String[] lines = compileLog.split("\\R");
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        boolean insideSection = false;
        for (String line : lines) {
            if ("[javatogpu-opencl-compiler-diagnostics]".equals(line.trim())) {
                insideSection = true;
                continue;
            }
            if (insideSection && line.startsWith("[")) {
                break;
            }
            if (!insideSection) {
                continue;
            }
            int separator = line.indexOf('=');
            if (separator <= 0) {
                continue;
            }
            fields.put(line.substring(0, separator).trim(), line.substring(separator + 1).trim());
        }
        return fields.isEmpty() ? Map.of() : Collections.unmodifiableMap(fields);
    }
}
