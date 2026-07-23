package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.extension.GpuExtensionExecutionOutcome;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionExecutionReport;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Dispatch report for one runtime log publication.
 */
public record GpuRuntimeLogDispatchReport(
        GpuRuntimeLogRecord record,
        List<GpuExtensionExecutionReport> serviceReports
) {

    public GpuRuntimeLogDispatchReport {
        record = record == null
                ? GpuRuntimeLogRecord.of(GpuRuntimeLogLevel.INFO, "net.sixik.ga_utils.javatogpu", "runtime log")
                : record;
        serviceReports = serviceReports == null ? List.of() : List.copyOf(serviceReports);
    }

    public boolean allServicesSucceeded() {
        return serviceReports.stream()
                .allMatch(report -> report.outcome() == GpuExtensionExecutionOutcome.SUCCEEDED);
    }

    public Map<String, String> artifactFields(String prefix) {
        String safePrefix = prefix == null || prefix.isBlank() ? "runtimeLogDispatch" : prefix.trim();
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        fields.put(safePrefix + ".service.count", Integer.toString(serviceReports.size()));
        fields.put(safePrefix + ".service.allSucceeded", Boolean.toString(allServicesSucceeded()));
        fields.putAll(record.artifactFields(safePrefix + ".record"));
        for (int index = 0; index < serviceReports.size(); index++) {
            fields.putAll(serviceReports.get(index).artifactFields(safePrefix + ".service." + index));
        }
        return Collections.unmodifiableMap(fields);
    }
}
