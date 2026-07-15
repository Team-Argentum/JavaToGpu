package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.extension.GpuExtensionExecutionOutcome;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionExecutionReport;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Dispatch report for one lifecycle event publication.
 */
public record GpuRuntimeLifecycleEventReport(
        GpuRuntimeLifecycleEvent event,
        List<GpuExtensionExecutionReport> listenerReports
) {

    public GpuRuntimeLifecycleEventReport {
        event = event == null ? GpuRuntimeLifecycleEvent.of(GpuRuntimeLifecycleEventKind.RUNTIME_SHUTDOWN_COMPLETED) : event;
        listenerReports = listenerReports == null ? List.of() : List.copyOf(listenerReports);
    }

    public boolean allListenersSucceeded() {
        return listenerReports.stream()
                .allMatch(report -> report.outcome() == GpuExtensionExecutionOutcome.SUCCEEDED);
    }

    public Map<String, String> artifactFields(String prefix) {
        String safePrefix = prefix == null || prefix.isBlank() ? "runtimeLifecycle" : prefix.trim();
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        fields.put(safePrefix + ".listener.count", Integer.toString(listenerReports.size()));
        fields.put(safePrefix + ".listener.allSucceeded", Boolean.toString(allListenersSucceeded()));
        fields.putAll(event.artifactFields(safePrefix + ".event"));
        for (int index = 0; index < listenerReports.size(); index++) {
            fields.putAll(listenerReports.get(index).artifactFields(safePrefix + ".listener." + index));
        }
        return Collections.unmodifiableMap(fields);
    }

    public String toPropertiesText() {
        StringBuilder builder = new StringBuilder();
        artifactFields("runtimeLifecycle").forEach((key, value) -> builder
                .append(key)
                .append('=')
                .append(safePropertyValue(value))
                .append('\n'));
        return builder.toString();
    }

    private static String safePropertyValue(String value) {
        return value == null ? "" : value.replace("\r", "\\r").replace("\n", "\\n");
    }
}
