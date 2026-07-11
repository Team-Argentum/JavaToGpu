package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.extension.GpuExtensionExecutionOutcome;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionExecutionReport;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionFailurePolicy;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionPermission;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionPhase;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifact;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuExtensionParticipationMetadata;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Durable aggregate of extension participation across one runtime compile snapshot.
 */
public record GpuRuntimeExtensionParticipationArtifact(
        String backendTarget,
        String backendFormat,
        String backendResource,
        List<Entry> entries
) {

    public GpuRuntimeExtensionParticipationArtifact {
        backendTarget = backendTarget == null || backendTarget.isBlank() ? "UNKNOWN" : backendTarget;
        backendFormat = backendFormat == null || backendFormat.isBlank() ? "unknown" : backendFormat;
        backendResource = backendResource == null || backendResource.isBlank() ? "unknown" : backendResource;
        entries = entries == null ? List.of() : List.copyOf(entries);
    }

    public GpuRuntimeExtensionParticipationArtifact(List<Entry> entries) {
        this("UNKNOWN", "unknown", "unknown", entries);
    }

    public static GpuRuntimeExtensionParticipationArtifact from(
            GpuRuntimeCompileArtifactSnapshot snapshot,
            GpuBackendCompilerFeedbackReport compilerFeedbackReport
    ) {
        if (snapshot == null) {
            return new GpuRuntimeExtensionParticipationArtifact(List.of());
        }
        ArrayList<Entry> entries = new ArrayList<>();
        snapshot.originalIrGpuArtifact().ifPresent(artifact -> appendIrGpuMetadata(
                entries,
                "original-irgpu",
                artifact
        ));
        snapshot.optimizedIrGpuArtifact()
                .filter(optimized -> snapshot.originalIrGpuArtifact()
                        .map(original -> !original.equals(optimized))
                        .orElse(true))
                .ifPresent(artifact -> appendIrGpuMetadata(
                        entries,
                        "optimized-irgpu",
                        artifact
                ));
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
        appendRuntimeEquivalence(entries, snapshot.runtimeEquivalenceEvidence());
        appendBackendLowerer(entries, snapshot.backendModuleArtifact());
        append(
                entries,
                "backend-compiler-feedback",
                compilerFeedbackReport == null ? List.of() : compilerFeedbackReport.executions()
        );
        return new GpuRuntimeExtensionParticipationArtifact(
                snapshot.backendModuleArtifact().backendTarget().name(),
                snapshot.backendModuleArtifact().format(),
                snapshot.backendModuleArtifact().resource(),
                entries
        );
    }

    public Map<String, String> artifactFields() {
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        fields.put("status", entries.isEmpty() ? "not-recorded" : "recorded");
        fields.put("backendTarget", backendTarget);
        fields.put("backendFormat", backendFormat);
        fields.put("backendResource", backendResource);
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

    private static void appendIrGpuMetadata(
            ArrayList<Entry> entries,
            String artifactStage,
            IrGpuArtifact artifact
    ) {
        if (artifact == null || artifact.extensionParticipationMetadata().isEmpty()) {
            return;
        }
        for (IrGpuExtensionParticipationMetadata metadata : artifact.extensionParticipationMetadata()) {
            if (metadata != null) {
                entries.add(new Entry(
                        artifactStage + ":" + metadata.source(),
                        toExecutionReport(metadata)
                ));
            }
        }
    }

    private static void appendRuntimeEquivalence(
            ArrayList<Entry> entries,
            GpuRuntimeEquivalenceEvidence evidence
    ) {
        if (evidence == null) {
            return;
        }
        entries.add(new Entry(
                "runtime-equivalence",
                new GpuExtensionExecutionReport(
                        "runtime-equivalence:" + evidence.backendTarget().toLowerCase(java.util.Locale.ROOT),
                        "runtime-equivalence-v1",
                        GpuExtensionPhase.RUNTIME_EQUIVALENCE,
                        GpuExtensionPermission.READ_ONLY,
                        "runtime equivalence evidence capture",
                        runtimeEquivalenceOutcome(evidence),
                        GpuExtensionFailurePolicy.CONTINUE,
                        true,
                        evidence.executed() && !evidence.equivalent() ? "runtime-equivalence-failed" : "none",
                        "runtime equivalence " + evidence.status(),
                        runtimeEquivalenceDiagnostics(evidence)
                )
        ));
    }

    private static GpuExtensionExecutionOutcome runtimeEquivalenceOutcome(GpuRuntimeEquivalenceEvidence evidence) {
        if (!evidence.executed()) {
            return GpuExtensionExecutionOutcome.SKIPPED;
        }
        return evidence.equivalent()
                ? GpuExtensionExecutionOutcome.SUCCEEDED
                : GpuExtensionExecutionOutcome.FAILED_CONTINUED;
    }

    private static List<String> runtimeEquivalenceDiagnostics(GpuRuntimeEquivalenceEvidence evidence) {
        ArrayList<String> diagnostics = new ArrayList<>();
        diagnostics.add("status=" + evidence.status());
        diagnostics.add("backendTarget=" + evidence.backendTarget());
        diagnostics.add("vendor=" + evidence.vendor());
        diagnostics.add("deviceLabel=" + evidence.deviceLabel());
        diagnostics.add("optimizationProfile=" + evidence.optimizationProfile());
        diagnostics.add("executed=" + evidence.executed());
        diagnostics.add("equivalent=" + evidence.equivalent());
        diagnostics.add("inputCase.count=" + evidence.inputCaseCount());
        diagnostics.add("comparedOutput.count=" + evidence.comparedOutputCount());
        diagnostics.addAll(evidence.diagnostics());
        return diagnostics;
    }

    private static void appendBackendLowerer(ArrayList<Entry> entries, GpuBackendModuleArtifact artifact) {
        if (artifact == null || artifact.backendTarget() == null || artifact.lowererVersion().isBlank()) {
            return;
        }
        entries.add(new Entry(
                "backend-lowerer",
                new GpuExtensionExecutionReport(
                        "backend-lowerer:" + artifact.backendTarget().name().toLowerCase(java.util.Locale.ROOT),
                        artifact.lowererVersion(),
                        GpuExtensionPhase.BACKEND_LOWERING,
                        GpuExtensionPermission.PRODUCTION_AFFECTING,
                        "backend module lowering",
                        GpuExtensionExecutionOutcome.SUCCEEDED,
                        GpuExtensionFailurePolicy.STOP_PIPELINE,
                        true,
                        "none",
                        "backend lowerer produced " + artifact.kind() + " artifact " + artifact.format(),
                        List.of(
                                "backendTarget=" + artifact.backendTarget().name(),
                                "backendFormat=" + artifact.format(),
                                "backendResource=" + artifact.resource(),
                                "sourceOrigin=" + artifact.sourceOrigin(),
                                "runtimeLoadMode=" + artifact.runtimeLoadMode()
                        )
                )
        ));
    }

    private static GpuExtensionExecutionReport toExecutionReport(IrGpuExtensionParticipationMetadata metadata) {
        return new GpuExtensionExecutionReport(
                metadata.extensionId(),
                metadata.extensionVersion(),
                metadata.phase(),
                metadata.permission(),
                metadata.operation(),
                metadata.outcome(),
                metadata.failurePolicy(),
                metadata.pipelineContinued(),
                metadata.failureType(),
                metadata.message(),
                metadata.diagnostics()
        );
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
