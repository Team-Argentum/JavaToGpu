package net.sixik.ga_utils.javatogpu.runtime;

import java.util.List;
import java.util.Objects;

/**
 * Explains how one optimizer hook affected the runtime IR artifact.
 */
public record GpuRuntimeIrOptimizationPassReport(
        GpuRuntimeIrOptimizationStage stage,
        String optimizerVersion,
        GpuRuntimeIrOptimizationOutcome outcome,
        String originalIrIdentity,
        String transformedIrIdentity,
        String proofStatus,
        String rollbackReason,
        GpuRuntimeIrOptimizationProofArtifact proofArtifact,
        List<String> diagnostics
) {

    public GpuRuntimeIrOptimizationPassReport {
        stage = stage == null ? GpuRuntimeIrOptimizationStage.TRANSFORM : stage;
        optimizerVersion = normalize(optimizerVersion, "optimizer:unknown");
        outcome = outcome == null ? GpuRuntimeIrOptimizationOutcome.SKIPPED : outcome;
        originalIrIdentity = normalize(originalIrIdentity, "irgpu:missing");
        transformedIrIdentity = normalize(transformedIrIdentity, originalIrIdentity);
        proofStatus = normalize(proofStatus, "not-proven");
        rollbackReason = normalize(rollbackReason, "");
        proofArtifact = proofArtifact == null
                ? GpuRuntimeIrOptimizationProofArtifact.fromFields("none", "none", java.util.Map.of())
                : proofArtifact;
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }

    public static GpuRuntimeIrOptimizationPassReport applied(
            String optimizerVersion,
            String originalIrIdentity,
            String transformedIrIdentity,
            String proofStatus,
            List<String> diagnostics
    ) {
        return new GpuRuntimeIrOptimizationPassReport(
                GpuRuntimeIrOptimizationStage.TRANSFORM,
                optimizerVersion,
                GpuRuntimeIrOptimizationOutcome.APPLIED,
                originalIrIdentity,
                transformedIrIdentity,
                proofStatus,
                "",
                GpuRuntimeIrOptimizationProofArtifact.fromFields("none", "none", java.util.Map.of()),
                diagnostics
        );
    }

    public static GpuRuntimeIrOptimizationPassReport skipped(
            String optimizerVersion,
            String originalIrIdentity,
            String reason
    ) {
        return new GpuRuntimeIrOptimizationPassReport(
                GpuRuntimeIrOptimizationStage.TRANSFORM,
                optimizerVersion,
                GpuRuntimeIrOptimizationOutcome.SKIPPED,
                originalIrIdentity,
                originalIrIdentity,
                "not-mutating",
                "",
                GpuRuntimeIrOptimizationProofArtifact.fromFields("none", "none", java.util.Map.of()),
                reason == null || reason.isBlank() ? List.of() : List.of(reason)
        );
    }

    public static GpuRuntimeIrOptimizationPassReport rolledBack(
            String optimizerVersion,
            String originalIrIdentity,
            String transformedIrIdentity,
            String proofStatus,
            String rollbackReason,
            List<String> diagnostics
    ) {
        return new GpuRuntimeIrOptimizationPassReport(
                GpuRuntimeIrOptimizationStage.TRANSFORM,
                optimizerVersion,
                GpuRuntimeIrOptimizationOutcome.ROLLED_BACK,
                originalIrIdentity,
                transformedIrIdentity,
                proofStatus,
                rollbackReason,
                GpuRuntimeIrOptimizationProofArtifact.fromFields("none", "none", java.util.Map.of()),
                diagnostics
        );
    }

    public static GpuRuntimeIrOptimizationPassReport failed(
            String optimizerVersion,
            String originalIrIdentity,
            RuntimeException exception
    ) {
        return new GpuRuntimeIrOptimizationPassReport(
                GpuRuntimeIrOptimizationStage.TRANSFORM,
                optimizerVersion,
                GpuRuntimeIrOptimizationOutcome.FAILED,
                originalIrIdentity,
                originalIrIdentity,
                "failed-before-proof",
                exception == null ? "optimizer failed" : exception.getClass().getSimpleName(),
                GpuRuntimeIrOptimizationProofArtifact.fromFields("none", "none", java.util.Map.of()),
                exception == null || exception.getMessage() == null || exception.getMessage().isBlank()
                        ? List.of("optimizer failed")
                        : List.of(exception.getMessage())
        );
    }

    public String toLine() {
        StringBuilder builder = new StringBuilder();
        builder.append("stage=")
                .append(stage)
                .append(' ')
                .append(optimizerVersion)
                .append(" outcome=")
                .append(outcome)
                .append(" original=")
                .append(originalIrIdentity)
                .append(" transformed=")
                .append(transformedIrIdentity)
                .append(" proof=")
                .append(proofStatus);
        if (!rollbackReason.isBlank()) {
            builder.append(" rollback=").append(rollbackReason);
        }
        if (!"none".equals(proofArtifact.source()) || !proofArtifact.fields().isEmpty()) {
            builder.append(' ').append(proofArtifact.toLine());
        }
        if (!diagnostics.isEmpty()) {
            builder.append(" diagnostics=").append(String.join(" | ", diagnostics));
        }
        return builder.toString();
    }

    public GpuRuntimeIrOptimizationPassReport withStage(GpuRuntimeIrOptimizationStage stage) {
        return new GpuRuntimeIrOptimizationPassReport(
                stage,
                optimizerVersion,
                outcome,
                originalIrIdentity,
                transformedIrIdentity,
                proofStatus,
                rollbackReason,
                proofArtifact,
                diagnostics
        );
    }

    public GpuRuntimeIrOptimizationPassReport withProofArtifact(GpuRuntimeIrOptimizationProofArtifact proofArtifact) {
        return new GpuRuntimeIrOptimizationPassReport(
                stage,
                optimizerVersion,
                outcome,
                originalIrIdentity,
                transformedIrIdentity,
                proofStatus,
                rollbackReason,
                proofArtifact,
                diagnostics
        );
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? Objects.requireNonNull(fallback, "fallback") : value;
    }
}
