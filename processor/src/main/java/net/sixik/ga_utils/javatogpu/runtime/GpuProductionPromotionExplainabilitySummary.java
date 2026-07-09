package net.sixik.ga_utils.javatogpu.runtime;

import java.util.Properties;

/**
 * Compact runtime-facing summary of production-promotion explainability evidence.
 *
 * <p>The explainability artifact remains the detailed gate output. This record gives reports, validation history, and CI
 * workflows one stable scalar summary surface instead of reparsing the full production-promotion contract each time.</p>
 */
public record GpuProductionPromotionExplainabilitySummary(
        String status,
        boolean contractValid,
        String firstViolation,
        String decisionMode,
        String productionSourceSwitchingAllowed,
        String productionSourceSwitchingEnabled,
        String productionMutationAllowed,
        String productionMutationEnabled,
        int kernelCount,
        int blockerCount,
        String firstBlocker,
        int i3ReviewReadyCount,
        int i3BlockedCount,
        int i3SourceReadyCount,
        String i3SourceReadyAll,
        String backendPromotionArtifactSupportComplete,
        int backendPromotionArtifactSupportMissingCount
) {

    public static GpuProductionPromotionExplainabilitySummary notRecorded() {
        return new GpuProductionPromotionExplainabilitySummary(
                "not-recorded",
                false,
                "not-recorded",
                GpuProductionPromotionDecision.DIAGNOSTIC_ONLY,
                "false",
                "false",
                "false",
                "false",
                0,
                0,
                "none",
                0,
                0,
                0,
                "false",
                "unknown",
                0
        );
    }

    public static GpuProductionPromotionExplainabilitySummary fromProperties(Properties properties) {
        if (properties == null || properties.isEmpty()) {
            return notRecorded();
        }
        GpuProductionPromotionExplainabilityValidation.Result contract =
                GpuProductionPromotionExplainabilityValidation.validate(properties);
        int blockerCount = parsePositiveInt(properties.getProperty("blocker.count", "0"));
        return new GpuProductionPromotionExplainabilitySummary(
                properties.getProperty("status", contract.status()),
                contract.valid(),
                contract.firstViolation(),
                properties.getProperty("decision.mode", "unknown"),
                properties.getProperty("productionSourceSwitchingAllowed", Boolean.toString(contract.sourceSwitchingAllowed())),
                properties.getProperty("productionSourceSwitchingEnabled", Boolean.toString(contract.sourceSwitchingEnabled())),
                properties.getProperty("productionMutationAllowed", Boolean.toString(contract.mutationAllowed())),
                properties.getProperty("productionMutationEnabled", Boolean.toString(contract.mutationEnabled())),
                contract.kernelCount(),
                blockerCount,
                blockerCount == 0 ? "none" : properties.getProperty("blocker.0", "unknown"),
                parsePositiveInt(properties.getProperty("i3ReviewReady.count", Integer.toString(contract.i3ReviewReadyCount()))),
                parsePositiveInt(properties.getProperty("i3Blocked.count", Integer.toString(contract.i3BlockedCount()))),
                parsePositiveInt(properties.getProperty("i3SourceReady.count", Integer.toString(contract.i3SourceReadyCount()))),
                properties.getProperty("i3SourceReady.all", "false"),
                properties.getProperty("backendPromotionArtifactSupport.complete", "unknown"),
                parsePositiveInt(properties.getProperty("backendPromotionArtifactSupport.missing.count", "0"))
        );
    }

    public String contractStatus() {
        return contractValid ? "valid" : "invalid";
    }

    public String contractViolationText() {
        return contractValid ? "" : ", violation=" + firstViolation;
    }

    public String firstBlockerText() {
        return blockerCount == 0 ? "" : ", first=" + firstBlocker;
    }

    public String historyStatus() {
        if ("not-recorded".equals(status)) {
            return "not recorded";
        }
        return status
                + " (contract=" + contractStatus()
                + contractViolationText()
                + ", decisionMode=" + decisionMode
                + ", sourceSwitchingAllowed=" + productionSourceSwitchingAllowed
                + ", sourceSwitchingEnabled=" + productionSourceSwitchingEnabled
                + ", mutationAllowed=" + productionMutationAllowed
                + ", mutationEnabled=" + productionMutationEnabled
                + ", blockers=" + blockerCount
                + firstBlockerText()
                + ", i3ReviewReady=" + i3ReviewReadyCount
                + ", i3Blocked=" + i3BlockedCount
                + ", i3SourceReady=" + i3SourceReadyCount
                + ", i3SourceReadyAll=" + i3SourceReadyAll
                + ", backendPromotionArtifactSupportComplete=" + backendPromotionArtifactSupportComplete
                + ", backendPromotionArtifactSupportMissing=" + backendPromotionArtifactSupportMissingCount
                + ")";
    }

    private static int parsePositiveInt(String value) {
        try {
            return Math.max(0, Integer.parseInt(value == null ? "0" : value.trim()));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }
}
