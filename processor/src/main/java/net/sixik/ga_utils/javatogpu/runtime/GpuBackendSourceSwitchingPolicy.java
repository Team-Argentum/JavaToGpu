package net.sixik.ga_utils.javatogpu.runtime;

/**
 * Normalized backend source-selection policy used by the backend-neutral switching decision.
 *
 * <p>Backend-specific option keys, such as the current OpenCL properties, are translated into this compact policy
 * before the fail-closed decision runs. That keeps future CUDA, Vulkan/SPIR-V, and Metal adapters from inheriting
 * OpenCL-specific property names in the shared runtime contract.</p>
 */
public record GpuBackendSourceSwitchingPolicy(
        String sourceSelection,
        boolean irGpuSourceRequested,
        String productionSourceSwitching,
        boolean productionSourceSwitchingEnabled,
        String productionPromotionDecisionMode,
        boolean productionPromotionOperatorAccepted
) {

    public static final String SOURCE_SELECTION_DESCRIPTOR = "descriptor";
    public static final String SOURCE_SELECTION_IRGPU = "irgpu";
    public static final String PRODUCTION_SOURCE_SWITCHING_DISABLED = "disabled";
    public static final String PRODUCTION_SOURCE_SWITCHING_ENABLED = "enabled";

    public GpuBackendSourceSwitchingPolicy {
        sourceSelection = normalize(sourceSelection, SOURCE_SELECTION_DESCRIPTOR);
        productionSourceSwitching = normalize(productionSourceSwitching, PRODUCTION_SOURCE_SWITCHING_DISABLED);
        productionPromotionDecisionMode = normalize(
                productionPromotionDecisionMode,
                GpuProductionPromotionDecision.DIAGNOSTIC_ONLY
        );
    }

    public static GpuBackendSourceSwitchingPolicy descriptorDefault() {
        return new GpuBackendSourceSwitchingPolicy(
                SOURCE_SELECTION_DESCRIPTOR,
                false,
                PRODUCTION_SOURCE_SWITCHING_DISABLED,
                false,
                GpuProductionPromotionDecision.DIAGNOSTIC_ONLY,
                false
        );
    }

    public static GpuBackendSourceSwitchingPolicy from(GpuBackendCompileOptions backendOptions) {
        if (backendOptions == null) {
            return descriptorDefault();
        }
        if (backendOptions.backendTarget() == net.sixik.ga_utils.javatogpu.api.GpuBackendTarget.OPENCL) {
            return openCl(backendOptions);
        }
        return descriptorDefault().withProductionPromotionDecisionMode(backendOptions.productionPromotionDecisionMode());
    }

    public static GpuBackendSourceSwitchingPolicy openCl(GpuBackendCompileOptions backendOptions) {
        GpuBackendCompileOptions resolved = backendOptions == null
                ? GpuBackendCompileOptions.empty(net.sixik.ga_utils.javatogpu.api.GpuBackendTarget.OPENCL)
                : backendOptions;
        String sourceSelection = resolved.properties().getOrDefault(
                GpuBackendCompileOptions.OPENCL_SOURCE_SELECTION_PROPERTY,
                SOURCE_SELECTION_DESCRIPTOR
        );
        String productionSourceSwitching = resolved.properties().getOrDefault(
                GpuBackendCompileOptions.OPENCL_PRODUCTION_SOURCE_SWITCHING_PROPERTY,
                PRODUCTION_SOURCE_SWITCHING_DISABLED
        );
        return new GpuBackendSourceSwitchingPolicy(
                sourceSelection,
                SOURCE_SELECTION_IRGPU.equals(sourceSelection),
                productionSourceSwitching,
                PRODUCTION_SOURCE_SWITCHING_ENABLED.equals(productionSourceSwitching),
                resolved.productionPromotionDecisionMode(),
                resolved.productionPromotionOperatorAccepted()
        );
    }

    public GpuBackendSourceSwitchingPolicy withProductionPromotionDecisionMode(String decisionMode) {
        return new GpuBackendSourceSwitchingPolicy(
                sourceSelection,
                irGpuSourceRequested,
                productionSourceSwitching,
                productionSourceSwitchingEnabled,
                decisionMode,
                productionPromotionOperatorAccepted
        );
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
