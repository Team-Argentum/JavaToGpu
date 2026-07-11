package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.extension.GpuExtensionExecutionOutcome;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionExecutionReport;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Durable aggregate of extension participation across one runtime compile snapshot.
 */
public record GpuRuntimeExtensionParticipationArtifact(
        List<Entry> entries
) {

    public GpuRuntimeExtensionParticipationArtifact {
        entries = entries == null ? List.of() : List.copyOf(entries);
    }

    public static GpuRuntimeExtensionParticipationArtifact from(
            GpuRuntimeCompileArtifactSnapshot snapshot,
            GpuBackendCompilerFeedbackReport compilerFeedbackReport
    ) {
        if (snapshot == null) {
            return new GpuRuntimeExtensionParticipationArtifact(List.of());
        }
        ArrayList<Entry> entries = new ArrayList<>();
        snapshot.deviceSelection().ifPresent(selection -> append(
                entries,
                "device-selection",
                selection.executionReports()
        ));
        append(
                entries,
                "runtime-ir-optimization",
                snapshot.optimizationReport().extensionExecutionReports()
        );
        append(
                entries,
                "backend-compiler-feedback",
                compilerFeedbackReport == null ? List.of() : compilerFeedbackReport.executions()
        );
        return new GpuRuntimeExtensionParticipationArtifact(entries);
    }

    public Map<String, String> artifactFields() {
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        fields.put("status", entries.isEmpty() ? "not-recorded" : "recorded");
        fields.put("entry.count", Integer.toString(entries.size()));
        fields.put("succeeded.count", Long.toString(count(GpuExtensionExecutionOutcome.SUCCEEDED)));
        fields.put("skipped.count", Long.toString(count(GpuExtensionExecutionOutcome.SKIPPED)));
        fields.put("failedContinued.count", Long.toString(count(GpuExtensionExecutionOutcome.FAILED_CONTINUED)));
        fields.put("failedClosed.count", Long.toString(count(GpuExtensionExecutionOutcome.FAILED_CLOSED)));
        fields.put("pipelineContinued.all", Boolean.toString(entries.stream().allMatch(entry -> entry.report().pipelineContinued())));
        fields.put("firstFailure", firstFailure());
        for (int index = 0; index < entries.size(); index++) {
            Entry entry = entries.get(index);
            String prefix = "entry." + index;
            fields.put(prefix + ".source", entry.source());
            fields.putAll(entry.report().artifactFields(prefix));
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

    private static void append(
            ArrayList<Entry> entries,
            String source,
            List<GpuExtensionExecutionReport> reports
    ) {
        if (reports == null || reports.isEmpty()) {
            return;
        }
        for (GpuExtensionExecutionReport report : reports) {
            if (report != null) {
                entries.add(new Entry(source, report));
            }
        }
    }

    private long count(GpuExtensionExecutionOutcome outcome) {
        return entries.stream().filter(entry -> entry.report().outcome() == outcome).count();
    }

    private String firstFailure() {
        return entries.stream()
                .map(Entry::report)
                .filter(report -> report.outcome() == GpuExtensionExecutionOutcome.FAILED_CONTINUED
                        || report.outcome() == GpuExtensionExecutionOutcome.FAILED_CLOSED)
                .map(report -> report.extensionId() + ":" + report.outcome().name())
                .findFirst()
                .orElse("none");
    }

    private static String safeValue(String value) {
        return value == null ? "" : value.replace("\r", " ").replace("\n", " ");
    }

    public record Entry(
            String source,
            GpuExtensionExecutionReport report
    ) {

        public Entry {
            source = source == null || source.isBlank() ? "unknown" : source;
            report = java.util.Objects.requireNonNull(report, "report");
        }
    }
}
