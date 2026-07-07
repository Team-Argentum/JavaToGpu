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
        String strategyName,
        String selectedProfile,
        String baselineStatus,
        boolean promotionEligible,
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
                    "strategy:none",
                    "off",
                    "missing",
                    false,
                    "not-requested",
                    false
            );
        }

        GpuRuntimeIrOptimizationReport report = snapshot.optimizationReport();
        GpuOptimizationStrategyDecision strategy = report.strategyDecision();
        GpuOptimizationVendorBaseline baseline = strategy.vendorBaseline();
        GpuRuntimeProductionOptimizerGate gate = snapshot.productionOptimizerGate();
        return new GpuRuntimeOptimizerDriftArtifact(
                report.passReports().size(),
                count(report, GpuRuntimeIrOptimizationOutcome.APPLIED),
                count(report, GpuRuntimeIrOptimizationOutcome.SKIPPED),
                count(report, GpuRuntimeIrOptimizationOutcome.ROLLED_BACK),
                count(report, GpuRuntimeIrOptimizationOutcome.FAILED),
                snapshot.fallbackEvidence().decision(),
                strategy.strategyName(),
                strategy.selectedProfile(),
                baseline.status(),
                baseline.promotionEligible(),
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
        builder.append("strategyName=").append(strategyName).append('\n');
        builder.append("selectedProfile=").append(selectedProfile).append('\n');
        builder.append("baselineStatus=").append(baselineStatus).append('\n');
        builder.append("promotionEligible=").append(promotionEligible).append('\n');
        builder.append("productionGateStatus=").append(productionGateStatus).append('\n');
        builder.append("productionProfileRequested=").append(productionProfileRequested).append('\n');
        return builder.toString();
    }

    private static int count(GpuRuntimeIrOptimizationReport report, GpuRuntimeIrOptimizationOutcome outcome) {
        return (int) report.passReports().stream()
                .filter(passReport -> passReport.outcome() == outcome)
                .count();
    }
}
