package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Backend-specific compile options kept separate from legacy OpenCL-style command-line args.
 */
public record GpuBackendCompileOptions(
        GpuBackendTarget backendTarget,
        List<String> flags,
        Map<String, String> properties
) {

    public static final String OPENCL_SOURCE_SELECTION_PROPERTY = "opencl.sourceSelection";
    public static final String OPENCL_SOURCE_SELECTION_DESCRIPTOR = "descriptor";
    public static final String OPENCL_SOURCE_SELECTION_IRGPU = "irgpu";
    public static final String OPENCL_PRODUCTION_SOURCE_SWITCHING_PROPERTY = "opencl.productionSourceSwitching";
    public static final String OPENCL_PRODUCTION_SOURCE_SWITCHING_DISABLED = "disabled";
    public static final String OPENCL_PRODUCTION_SOURCE_SWITCHING_ENABLED = "enabled";
    public static final String PRODUCTION_PROMOTION_DECISION_MODE_PROPERTY = "productionPromotion.decisionMode";
    public static final String PRODUCTION_PROMOTION_OPERATOR_ACCEPTED_PROPERTY = "productionPromotion.operatorAccepted";
    public static final String RUNTIME_DEVICE_SELF_TEST_PROPERTY = "runtime.deviceSelfTest";

    public GpuBackendCompileOptions {
        backendTarget = backendTarget == null ? GpuBackendTarget.UNKNOWN : backendTarget;
        flags = flags == null ? List.of() : List.copyOf(flags);
        properties = properties == null ? Map.of() : Map.copyOf(properties);
    }

    public static GpuBackendCompileOptions empty(GpuBackendTarget backendTarget) {
        return new GpuBackendCompileOptions(backendTarget, List.of(), Map.of());
    }

    public static GpuBackendCompileOptions openCl(List<String> compileArgs) {
        return new GpuBackendCompileOptions(GpuBackendTarget.OPENCL, compileArgs, Map.of());
    }

    public static GpuBackendCompileOptions openCl(List<String> compileArgs, Map<String, String> properties) {
        return new GpuBackendCompileOptions(GpuBackendTarget.OPENCL, compileArgs, properties);
    }

    public static GpuBackendCompileOptions openClIrGpuSource(List<String> compileArgs) {
        return openCl(
                compileArgs,
                Map.of(OPENCL_SOURCE_SELECTION_PROPERTY, OPENCL_SOURCE_SELECTION_IRGPU)
        );
    }

    public static GpuBackendCompileOptions openClProductionIrGpuSource(List<String> compileArgs) {
        return openCl(
                compileArgs,
                Map.of(
                        OPENCL_SOURCE_SELECTION_PROPERTY,
                        OPENCL_SOURCE_SELECTION_IRGPU,
                        OPENCL_PRODUCTION_SOURCE_SWITCHING_PROPERTY,
                        OPENCL_PRODUCTION_SOURCE_SWITCHING_ENABLED
                )
        );
    }

    public static GpuBackendCompileOptions cuda(List<String> nvrtcOptions, Map<String, String> properties) {
        return new GpuBackendCompileOptions(GpuBackendTarget.CUDA, nvrtcOptions, properties);
    }

    public static GpuBackendCompileOptions vulkan(List<String> spirvOptions, Map<String, String> properties) {
        return new GpuBackendCompileOptions(GpuBackendTarget.VULKAN, spirvOptions, properties);
    }

    public static GpuBackendCompileOptions metal(List<String> metalOptions, Map<String, String> properties) {
        return new GpuBackendCompileOptions(GpuBackendTarget.METAL, metalOptions, properties);
    }

    public boolean empty() {
        return flags.isEmpty() && properties.isEmpty();
    }

    public boolean requestsOpenClIrGpuSource() {
        return backendTarget == GpuBackendTarget.OPENCL
                && OPENCL_SOURCE_SELECTION_IRGPU.equals(properties.get(OPENCL_SOURCE_SELECTION_PROPERTY));
    }

    public boolean enablesOpenClProductionSourceSwitching() {
        return backendTarget == GpuBackendTarget.OPENCL
                && OPENCL_PRODUCTION_SOURCE_SWITCHING_ENABLED.equals(
                properties.get(OPENCL_PRODUCTION_SOURCE_SWITCHING_PROPERTY)
        );
    }

    public String productionPromotionDecisionMode() {
        return properties.getOrDefault(
                PRODUCTION_PROMOTION_DECISION_MODE_PROPERTY,
                GpuProductionPromotionDecision.DIAGNOSTIC_ONLY
        );
    }

    public boolean productionPromotionOperatorAccepted() {
        return "true".equals(properties.getOrDefault(PRODUCTION_PROMOTION_OPERATOR_ACCEPTED_PROPERTY, "false"));
    }

    public GpuRuntimeDeviceSelfTestMode deviceSelfTestMode() {
        return GpuRuntimeDeviceSelfTestMode.parse(properties.get(RUNTIME_DEVICE_SELF_TEST_PROPERTY));
    }

    public GpuBackendCompileOptions withProductionPromotionDecision(GpuProductionPromotionDecision decision) {
        GpuProductionPromotionDecision normalized = decision == null
                ? GpuProductionPromotionDecision.diagnosticOnly()
                : decision;
        Map<String, String> updated = new LinkedHashMap<>(properties);
        updated.put(PRODUCTION_PROMOTION_DECISION_MODE_PROPERTY, normalized.mode());
        return new GpuBackendCompileOptions(backendTarget, flags, updated);
    }

    public GpuBackendCompileOptions withProductionPromotionOperatorAccepted(boolean accepted) {
        Map<String, String> updated = new LinkedHashMap<>(properties);
        updated.put(PRODUCTION_PROMOTION_OPERATOR_ACCEPTED_PROPERTY, Boolean.toString(accepted));
        return new GpuBackendCompileOptions(backendTarget, flags, updated);
    }

    public GpuBackendCompileOptions withDeviceSelfTestMode(GpuRuntimeDeviceSelfTestMode mode) {
        GpuRuntimeDeviceSelfTestMode normalized = mode == null ? GpuRuntimeDeviceSelfTestMode.AUTO : mode;
        Map<String, String> updated = new LinkedHashMap<>(properties);
        updated.put(RUNTIME_DEVICE_SELF_TEST_PROPERTY, normalized.optionValue());
        return new GpuBackendCompileOptions(backendTarget, flags, updated);
    }

    public Map<String, String> stableProperties() {
        return new LinkedHashMap<>(new java.util.TreeMap<>(properties));
    }
}
