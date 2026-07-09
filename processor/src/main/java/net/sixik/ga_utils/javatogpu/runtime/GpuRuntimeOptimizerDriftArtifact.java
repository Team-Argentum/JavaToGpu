package net.sixik.ga_utils.javatogpu.runtime;

/**
 * Compact CI-facing summary for detecting runtime optimizer behavior drift across validation runs.
 */
public record GpuRuntimeOptimizerDriftArtifact(
        int passCount,
        int appliedPassCount,
        int skippedPassCount,
        int rolledBackPassCount,
        int failedPassCount,
        String fallbackDecision,
        String selectedRuntimeIrStage,
        String selectedRuntimeIrIdentity,
        boolean optimizedIrRejected,
        String strategyName,
        String selectedProfile,
        String baselineStatus,
        boolean promotionEligible,
        int proofArtifactCount,
        int acceptedProofArtifactCount,
        int blockingProofArtifactCount,
        String productionGateStatus,
        boolean productionProfileRequested
) {

    public static GpuRuntimeOptimizerDriftArtifact from(GpuRuntimeCompileArtifactSnapshot snapshot) {
        if (snapshot == null) {
            return new GpuRuntimeOptimizerDriftArtifact(
                    0,
                    0,
                    0,
                    0,
                    0,
                    GpuRuntimeFallbackEvidence.NONE,
                    "missing",
                    "irgpu:missing",
                    false,
                    "strategy:none",
                    "off",
                    "missing",
                    false,
                    0,
                    0,
                    0,
                    "not-requested",
                    false
            );
        }

        GpuRuntimeIrOptimizationReport report = snapshot.optimizationReport();
        GpuOptimizationStrategyDecision strategy = report.strategyDecision();
        GpuOptimizationVendorBaseline baseline = strategy.vendorBaseline();
        GpuRuntimeProductionOptimizerGate gate = snapshot.productionOptimizerGate();
        GpuRuntimeIrSelection selection = snapshot.runtimeIrSelection();
        return new GpuRuntimeOptimizerDriftArtifact(
                report.passReports().size(),
                count(report, GpuRuntimeIrOptimizationOutcome.APPLIED),
                count(report, GpuRuntimeIrOptimizationOutcome.SKIPPED),
                count(report, GpuRuntimeIrOptimizationOutcome.ROLLED_BACK),
                count(report, GpuRuntimeIrOptimizationOutcome.FAILED),
                selection.fallbackDecision(),
                selection.selectedStage(),
                selection.selectedIdentity(),
                selection.optimizedRejected(),
                strategy.strategyName(),
                strategy.selectedProfile(),
                baseline.status(),
                baseline.promotionEligible(),
                proofArtifactCount(report),
                acceptedProofArtifactCount(report),
                blockingProofArtifactCount(report),
                gate.status(),
                gate.productionProfileRequested()
        );
    }

    public String toPropertiesText() {
        StringBuilder builder = new StringBuilder();
        builder.append("pass.count=").append(passCount).append('\n');
        builder.append("pass.applied.count=").append(appliedPassCount).append('\n');
        builder.append("pass.skipped.count=").append(skippedPassCount).append('\n');
        builder.append("pass.rolledBack.count=").append(rolledBackPassCount).append('\n');
        builder.append("pass.failed.count=").append(failedPassCount).append('\n');
        builder.append("fallbackDecision=").append(fallbackDecision).append('\n');
        builder.append("selectedRuntimeIrStage=").append(selectedRuntimeIrStage).append('\n');
        builder.append("selectedRuntimeIrIdentity=").append(selectedRuntimeIrIdentity).append('\n');
        builder.append("optimizedIrRejected=").append(optimizedIrRejected).append('\n');
        builder.append("strategyName=").append(strategyName).append('\n');
        builder.append("selectedProfile=").append(selectedProfile).append('\n');
        builder.append("baselineStatus=").append(baselineStatus).append('\n');
        builder.append("promotionEligible=").append(promotionEligible).append('\n');
        builder.append("proofArtifact.count=").append(proofArtifactCount).append('\n');
        builder.append("proofArtifact.accepted.count=").append(acceptedProofArtifactCount).append('\n');
        builder.append("proofArtifact.blocking.count=").append(blockingProofArtifactCount).append('\n');
        builder.append("productionGateStatus=").append(productionGateStatus).append('\n');
        builder.append("productionProfileRequested=").append(productionProfileRequested).append('\n');
        return builder.toString();
    }

    private static int count(GpuRuntimeIrOptimizationReport report, GpuRuntimeIrOptimizationOutcome outcome) {
        return (int) report.passReports().stream()
                .filter(passReport -> passReport.outcome() == outcome)
                .count();
    }

    private static int proofArtifactCount(GpuRuntimeIrOptimizationReport report) {
        return (int) report.passReports().stream()
                .filter(GpuRuntimeOptimizerDriftArtifact::hasProofArtifact)
                .count();
    }

    private static int acceptedProofArtifactCount(GpuRuntimeIrOptimizationReport report) {
        return (int) report.passReports().stream()
                .filter(GpuRuntimeOptimizerDriftArtifact::hasProofArtifact)
                .filter(passReport -> isAcceptedVerdict(passReport.proofArtifact().verdict()))
                .count();
    }

    private static int blockingProofArtifactCount(GpuRuntimeIrOptimizationReport report) {
        return (int) report.passReports().stream()
                .filter(GpuRuntimeOptimizerDriftArtifact::hasProofArtifact)
                .filter(passReport -> isBlockingVerdict(passReport.proofArtifact().verdict()))
                .count();
    }

    private static boolean hasProofArtifact(GpuRuntimeIrOptimizationPassReport passReport) {
        GpuRuntimeIrOptimizationProofArtifact proofArtifact = passReport.proofArtifact();
        return proofArtifact != null
                && (!"none".equals(proofArtifact.source()) || !proofArtifact.fields().isEmpty());
    }

    private static boolean isAcceptedVerdict(String verdict) {
        String normalized = verdict == null ? "" : verdict.toLowerCase(java.util.Locale.ROOT);
        return normalized.contains("accepted") || normalized.contains("passed") || normalized.contains("ready");
    }

    private static boolean isBlockingVerdict(String verdict) {
        String normalized = verdict == null ? "" : verdict.toLowerCase(java.util.Locale.ROOT);
        return normalized.contains("reject")
                || normalized.contains("block")
                || normalized.contains("fail")
                || normalized.contains("invalid");
    }
}
