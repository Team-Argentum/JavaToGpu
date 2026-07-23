package net.sixik.ga_utils.javatogpu.runtime;

import java.util.Properties;

/**
 * Compatibility facade for backend-neutral production-promotion explainability formatting.
 */
public final class GpuProductionPromotionExplainabilityFormatter {

    private GpuProductionPromotionExplainabilityFormatter() {
    }

    public static String format(Properties workloadGate, Properties i3Summary) {
        return net.sixik.ga_utils.javatogpu.runtime.diagnostics.GpuProductionPromotionExplainabilityFormatter.format(
                workloadGate,
                i3Summary
        );
    }

    public static String format(
            Properties workloadGate,
            Properties i3Summary,
            Properties backendPromotionArtifactSupport
    ) {
        return net.sixik.ga_utils.javatogpu.runtime.diagnostics.GpuProductionPromotionExplainabilityFormatter.format(
                workloadGate,
                i3Summary,
                backendPromotionArtifactSupport
        );
    }

    public static String format(
            Properties workloadGate,
            Properties i3Summary,
            Properties backendPromotionArtifactSupport,
            Properties controlledProductionSourceSwitchingValidation
    ) {
        return net.sixik.ga_utils.javatogpu.runtime.diagnostics.GpuProductionPromotionExplainabilityFormatter.format(
                workloadGate,
                i3Summary,
                backendPromotionArtifactSupport,
                controlledProductionSourceSwitchingValidation
        );
    }

    public static String format(
            Properties workloadGate,
            Properties i3Summary,
            Properties backendPromotionArtifactSupport,
            Properties controlledProductionSourceSwitchingValidation,
            Properties controlledProductionActivationTokenSmoke
    ) {
        return net.sixik.ga_utils.javatogpu.runtime.diagnostics.GpuProductionPromotionExplainabilityFormatter.format(
                workloadGate,
                i3Summary,
                backendPromotionArtifactSupport,
                controlledProductionSourceSwitchingValidation,
                controlledProductionActivationTokenSmoke
        );
    }

    public static String format(
            Properties workloadGate,
            Properties i3Summary,
            Properties backendPromotionArtifactSupport,
            Properties controlledProductionSourceSwitchingValidation,
            Properties controlledProductionActivationTokenSmoke,
            Properties controlledProductionActivationTokenNegative
    ) {
        return net.sixik.ga_utils.javatogpu.runtime.diagnostics.GpuProductionPromotionExplainabilityFormatter.format(
                workloadGate,
                i3Summary,
                backendPromotionArtifactSupport,
                controlledProductionSourceSwitchingValidation,
                controlledProductionActivationTokenSmoke,
                controlledProductionActivationTokenNegative
        );
    }

    public static String format(
            Properties workloadGate,
            Properties i3Summary,
            Properties backendPromotionArtifactSupport,
            Properties controlledProductionSourceSwitchingValidation,
            Properties controlledProductionMutationValidation,
            Properties controlledProductionActivationTokenSmoke,
            Properties controlledProductionActivationTokenNegative
    ) {
        return net.sixik.ga_utils.javatogpu.runtime.diagnostics.GpuProductionPromotionExplainabilityFormatter.format(
                workloadGate,
                i3Summary,
                backendPromotionArtifactSupport,
                controlledProductionSourceSwitchingValidation,
                controlledProductionMutationValidation,
                controlledProductionActivationTokenSmoke,
                controlledProductionActivationTokenNegative
        );
    }
}
