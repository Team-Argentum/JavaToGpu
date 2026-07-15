package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifact;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionExecutionReport;

import java.util.List;
import java.util.Optional;

/**
 * Aggregates the optimizer pipeline output and the diagnostics that justify it.
 */
public record GpuRuntimeIrOptimizationReport(
        Optional<IrGpuArtifact> artifact,
        List<GpuRuntimeIrOptimizationPassReport> passReports,
        GpuOptimizationStrategyDecision strategyDecision,
        List<GpuExtensionExecutionReport> extensionExecutionReports,
        Optional<IrGpuArtifact> candidateArtifact
) {

    public GpuRuntimeIrOptimizationReport(
            Optional<IrGpuArtifact> artifact,
            List<GpuRuntimeIrOptimizationPassReport> passReports
    ) {
        this(artifact, passReports, null, List.of(), Optional.empty());
    }

    public GpuRuntimeIrOptimizationReport(
            Optional<IrGpuArtifact> artifact,
            List<GpuRuntimeIrOptimizationPassReport> passReports,
            GpuOptimizationStrategyDecision strategyDecision
    ) {
        this(artifact, passReports, strategyDecision, List.of(), Optional.empty());
    }

    public GpuRuntimeIrOptimizationReport(
            Optional<IrGpuArtifact> artifact,
            List<GpuRuntimeIrOptimizationPassReport> passReports,
            GpuOptimizationStrategyDecision strategyDecision,
            List<GpuExtensionExecutionReport> extensionExecutionReports
    ) {
        this(artifact, passReports, strategyDecision, extensionExecutionReports, Optional.empty());
    }

    public GpuRuntimeIrOptimizationReport(
            Optional<IrGpuArtifact> artifact,
            Optional<IrGpuArtifact> candidateArtifact,
            List<GpuRuntimeIrOptimizationPassReport> passReports
    ) {
        this(artifact, passReports, null, List.of(), candidateArtifact);
    }

    public GpuRuntimeIrOptimizationReport {
        artifact = artifact == null ? Optional.empty() : artifact;
        passReports = passReports == null ? List.of() : List.copyOf(passReports);
        strategyDecision = strategyDecision == null
                ? GpuOptimizationStrategyDecision.none(null)
                : strategyDecision;
        extensionExecutionReports = extensionExecutionReports == null ? List.of() : List.copyOf(extensionExecutionReports);
        candidateArtifact = candidateArtifact == null ? Optional.empty() : candidateArtifact;
    }

    public static GpuRuntimeIrOptimizationReport empty(Optional<IrGpuArtifact> artifact) {
        return new GpuRuntimeIrOptimizationReport(artifact, List.of(), GpuOptimizationStrategyDecision.none(null));
    }

    public GpuRuntimeIrOptimizationReport append(GpuRuntimeIrOptimizationPassReport passReport) {
        if (passReport == null) {
            return this;
        }
        java.util.ArrayList<GpuRuntimeIrOptimizationPassReport> reports = new java.util.ArrayList<>(passReports);
        reports.add(passReport);
        return new GpuRuntimeIrOptimizationReport(
                artifact,
                reports,
                strategyDecision,
                extensionExecutionReports,
                candidateArtifact
        );
    }

    public GpuRuntimeIrOptimizationReport withCandidateArtifact(Optional<IrGpuArtifact> candidateArtifact) {
        return new GpuRuntimeIrOptimizationReport(
                artifact,
                passReports,
                strategyDecision,
                extensionExecutionReports,
                candidateArtifact
        );
    }

    public Optional<IrGpuArtifact> artifactForOptimizedReview() {
        if (requiresRollback()) {
            return artifact;
        }
        return candidateArtifact.or(() -> artifact);
    }

    public boolean hasReports() {
        return !passReports.isEmpty();
    }

    public boolean requiresRollback() {
        return passReports.stream().anyMatch(GpuRuntimeIrOptimizationReport::requiresRollback);
    }

    private static boolean requiresRollback(GpuRuntimeIrOptimizationPassReport passReport) {
        return passReport.outcome() == GpuRuntimeIrOptimizationOutcome.ROLLED_BACK
                || passReport.outcome() == GpuRuntimeIrOptimizationOutcome.FAILED;
    }

    public String toText() {
        StringBuilder builder = new StringBuilder("optimizer report");
        builder.append(System.lineSeparator())
                .append("strategy: ")
                .append(strategyDecision.toLine());
        if (passReports.isEmpty()) {
            return builder.append(System.lineSeparator())
                    .append("passes: no runtime IR optimizer passes ran")
                    .toString();
        }
        for (int index = 0; index < passReports.size(); index++) {
            builder.append(System.lineSeparator())
                    .append(index)
                    .append(':')
                    .append(' ')
                    .append(passReports.get(index).toLine());
        }
        for (int index = 0; index < extensionExecutionReports.size(); index++) {
            builder.append(System.lineSeparator())
                    .append("extensionExecution.")
                    .append(index)
                    .append(':')
                    .append(' ')
                    .append(extensionExecutionReports.get(index).toLine());
        }
        return builder.toString();
    }
}
