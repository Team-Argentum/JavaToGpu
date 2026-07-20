package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Hardware-free report produced by {@link GpuRuntimeObservabilityServiceHarness}.
 */
public record GpuRuntimeObservabilityServiceHarnessReport(
        GpuBackendTarget backendTarget,
        Map<String, String> lifecycleExtensionFields,
        Map<String, String> logExtensionFields,
        GpuRuntimeLifecycleEventReport lifecycleReport,
        GpuRuntimeLogDispatchReport logReport
) {

    public GpuRuntimeObservabilityServiceHarnessReport {
        backendTarget = backendTarget == null ? GpuBackendTarget.UNKNOWN : backendTarget;
        lifecycleExtensionFields = lifecycleExtensionFields == null ? Map.of() : Map.copyOf(lifecycleExtensionFields);
        logExtensionFields = logExtensionFields == null ? Map.of() : Map.copyOf(logExtensionFields);
        lifecycleReport = lifecycleReport == null
                ? new GpuRuntimeLifecycleEventReport(GpuRuntimeLifecycleEvent.of(
                GpuRuntimeLifecycleEventKind.RUNTIME_SHUTDOWN_COMPLETED), List.of())
                : lifecycleReport;
        logReport = logReport == null ? new GpuRuntimeLogDispatchReport(null, null) : logReport;
    }

    public int lifecycleListenerCount() {
        return lifecycleReport.listenerReports().size();
    }

    public int logServiceCount() {
        return logReport.serviceReports().size();
    }

    public boolean allSucceeded() {
        return lifecycleReport.allListenersSucceeded() && logReport.allServicesSucceeded();
    }

    public String status() {
        return allSucceeded() ? "succeeded" : "failed-continued";
    }

    public Map<String, String> artifactFields(String prefix) {
        String normalizedPrefix = prefix == null || prefix.isBlank()
                ? "runtime.observability.harness"
                : prefix.trim();
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        fields.put(normalizedPrefix + ".present", "true");
        fields.put(normalizedPrefix + ".status", status());
        fields.put(normalizedPrefix + ".backendTarget", backendTarget.name());
        fields.put(normalizedPrefix + ".lifecycle.listener.count", Integer.toString(lifecycleListenerCount()));
        fields.put(normalizedPrefix + ".lifecycle.allSucceeded",
                Boolean.toString(lifecycleReport.allListenersSucceeded()));
        fields.put(normalizedPrefix + ".log.service.count", Integer.toString(logServiceCount()));
        fields.put(normalizedPrefix + ".log.allSucceeded", Boolean.toString(logReport.allServicesSucceeded()));
        fields.put(normalizedPrefix + ".lifecycle.extension.field.count",
                Integer.toString(lifecycleExtensionFields.size()));
        fields.put(normalizedPrefix + ".log.extension.field.count", Integer.toString(logExtensionFields.size()));
        fields.putAll(prefixed(lifecycleExtensionFields, normalizedPrefix + ".lifecycle.extension"));
        fields.putAll(prefixed(logExtensionFields, normalizedPrefix + ".log.extension"));
        fields.putAll(lifecycleReport.artifactFields(normalizedPrefix + ".lifecycle.dispatch"));
        fields.putAll(logReport.artifactFields(normalizedPrefix + ".log.dispatch"));
        fields.put("runtime.observability.harness.present", "true");
        fields.put("runtime.observability.harness.status", status());
        fields.put("runtime.observability.harness.backendTarget", backendTarget.name());
        fields.put("runtime.observability.harness.lifecycle.listener.count", Integer.toString(lifecycleListenerCount()));
        fields.put("runtime.observability.harness.log.service.count", Integer.toString(logServiceCount()));
        return Collections.unmodifiableMap(fields);
    }

    public String toMarkdown() {
        StringBuilder builder = new StringBuilder();
        builder.append("Runtime observability service harness: ").append(status()).append('\n');
        builder.append("Backend: ").append(backendTarget).append('\n');
        builder.append("Lifecycle listeners: ")
                .append(lifecycleListenerCount())
                .append(", allSucceeded=")
                .append(lifecycleReport.allListenersSucceeded())
                .append('\n');
        builder.append("Log services: ")
                .append(logServiceCount())
                .append(", allSucceeded=")
                .append(logReport.allServicesSucceeded())
                .append('\n');
        builder.append("Lifecycle event: ").append(lifecycleReport.event().kind()).append('\n');
        builder.append("Log record: ").append(logReport.record().level()).append(" ")
                .append(logReport.record().loggerName()).append('\n');
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
