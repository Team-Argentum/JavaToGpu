package net.sixik.ga_utils.javatogpu.runtime;

import java.util.LinkedHashMap;
import java.util.Map;

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
        int optimizerFamilyCount,
        int optimizerFamilyPromotionReadyCount,
        String optimizerFamilySummary,
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
                    0,
                    0,
                    "",
                    "not-requested",
                    false
            );
        }

        GpuRuntimeIrOptimizationReport report = snapshot.optimizationReport();
        GpuOptimizationStrategyDecision strategy = report.strategyDecision();
        GpuOptimizationVendorBaseline baseline = strategy.vendorBaseline();
        GpuRuntimeProductionOptimizerGate gate = snapshot.productionOptimizerGate();
        GpuRuntimeIrSelection selection = snapshot.runtimeIrSelection();
        Map<String, OptimizerFamilyEvidence> optimizerFamilies = optimizerFamilies(report);
        return new GpuRuntimeOptimizerDriftArtifact(
                optimizationPassCount(report),
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
                optimizerFamilies.size(),
                promotionReadyFamilyCount(optimizerFamilies),
                formatOptimizerFamilySummary(optimizerFamilies),
                gate.status(),
                gate.productionProfileRequested()
        );
    }

    private static int optimizationPassCount(GpuRuntimeIrOptimizationReport report) {
        return (int) report.passReports().stream()
                .filter(passReport -> !passReport.analysisOnly())
                .count();
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
        builder.append("optimizerFamily.count=").append(optimizerFamilyCount).append('\n');
        builder.append("optimizerFamily.promotionReady.count=").append(optimizerFamilyPromotionReadyCount).append('\n');
        builder.append("optimizerFamily.summary=").append(optimizerFamilySummary).append('\n');
        builder.append("productionGateStatus=").append(productionGateStatus).append('\n');
        builder.append("productionProfileRequested=").append(productionProfileRequested).append('\n');
        return builder.toString();
    }

    private static int count(GpuRuntimeIrOptimizationReport report, GpuRuntimeIrOptimizationOutcome outcome) {
        return (int) report.passReports().stream()
                .filter(passReport -> !passReport.analysisOnly())
                .filter(passReport -> passReport.outcome() == outcome)
                .count();
    }

    private static int proofArtifactCount(GpuRuntimeIrOptimizationReport report) {
        return (int) report.passReports().stream()
                .filter(passReport -> !passReport.analysisOnly())
                .filter(GpuRuntimeOptimizerDriftArtifact::hasProofArtifact)
                .count();
    }

    private static int acceptedProofArtifactCount(GpuRuntimeIrOptimizationReport report) {
        return (int) report.passReports().stream()
                .filter(passReport -> !passReport.analysisOnly())
                .filter(GpuRuntimeOptimizerDriftArtifact::hasProofArtifact)
                .filter(passReport -> isAcceptedVerdict(passReport.proofArtifact().verdict()))
                .count();
    }

    private static int blockingProofArtifactCount(GpuRuntimeIrOptimizationReport report) {
        return (int) report.passReports().stream()
                .filter(passReport -> !passReport.analysisOnly())
                .filter(GpuRuntimeOptimizerDriftArtifact::hasProofArtifact)
                .filter(passReport -> isBlockingVerdict(passReport.proofArtifact().verdict()))
                .count();
    }

    private static boolean hasProofArtifact(GpuRuntimeIrOptimizationPassReport passReport) {
        GpuRuntimeIrOptimizationProofArtifact proofArtifact = passReport.proofArtifact();
        return proofArtifact != null
                && (!"none".equals(proofArtifact.source()) || !proofArtifact.fields().isEmpty());
    }

    private static Map<String, OptimizerFamilyEvidence> optimizerFamilies(GpuRuntimeIrOptimizationReport report) {
        Map<String, OptimizerFamilyEvidence> families = new LinkedHashMap<>();
        for (GpuRuntimeIrOptimizationPassReport passReport : report.passReports()) {
            if (passReport.analysisOnly()) {
                continue;
            }
            String familyName = optimizerFamilyName(passReport);
            OptimizerFamilyEvidence existing = families.getOrDefault(familyName, OptimizerFamilyEvidence.empty(familyName));
            families.put(familyName, existing.add(passReport));
        }
        return families;
    }

    private static String optimizerFamilyName(GpuRuntimeIrOptimizationPassReport passReport) {
        GpuRuntimeIrOptimizationProofArtifact proofArtifact = passReport.proofArtifact();
        String explicitFamily = proofArtifact == null ? "" : proofArtifact.fields().getOrDefault("optimizerFamily", "");
        if (!explicitFamily.isBlank()) {
            return explicitFamily;
        }
        String optimizerVersion = passReport.optimizerVersion();
        int separator = optimizerVersion.indexOf(':');
        return separator >= 0 && separator < optimizerVersion.length() - 1
                ? optimizerVersion.substring(separator + 1)
                : optimizerVersion;
    }

    private static int promotionReadyFamilyCount(Map<String, OptimizerFamilyEvidence> families) {
        int count = 0;
        for (OptimizerFamilyEvidence family : families.values()) {
            if (family.promotionReady()) {
                count++;
            }
        }
        return count;
    }

    private static String formatOptimizerFamilySummary(Map<String, OptimizerFamilyEvidence> families) {
        if (families.isEmpty()) {
            return "none";
        }
        StringBuilder builder = new StringBuilder();
        for (OptimizerFamilyEvidence family : families.values()) {
            if (!builder.isEmpty()) {
                builder.append(", ");
            }
            builder.append(family.name())
                    .append("[passes=")
                    .append(family.passCount())
                    .append(", acceptedProof=")
                    .append(family.acceptedProofCount())
                    .append(", blockingProof=")
                    .append(family.blockingProofCount())
                    .append(", rolledBack=")
                    .append(family.rolledBackCount())
                    .append(", failed=")
                    .append(family.failedCount())
                    .append(", promotionReady=")
                    .append(family.promotionReady())
                    .append(']');
        }
        return builder.toString();
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

    private record OptimizerFamilyEvidence(
            String name,
            int passCount,
            int acceptedProofCount,
            int blockingProofCount,
            int rolledBackCount,
            int failedCount
    ) {

        private static OptimizerFamilyEvidence empty(String name) {
            return new OptimizerFamilyEvidence(name, 0, 0, 0, 0, 0);
        }

        private OptimizerFamilyEvidence add(GpuRuntimeIrOptimizationPassReport passReport) {
            boolean hasProofArtifact = hasProofArtifact(passReport);
            return new OptimizerFamilyEvidence(
                    name,
                    passCount + 1,
                    acceptedProofCount + (hasProofArtifact && isAcceptedVerdict(passReport.proofArtifact().verdict()) ? 1 : 0),
                    blockingProofCount + (hasProofArtifact && isBlockingVerdict(passReport.proofArtifact().verdict()) ? 1 : 0),
                    rolledBackCount + (passReport.outcome() == GpuRuntimeIrOptimizationOutcome.ROLLED_BACK ? 1 : 0),
                    failedCount + (passReport.outcome() == GpuRuntimeIrOptimizationOutcome.FAILED ? 1 : 0)
            );
        }

        private boolean promotionReady() {
            return acceptedProofCount > 0 && blockingProofCount == 0 && rolledBackCount == 0 && failedCount == 0;
        }
    }
}
