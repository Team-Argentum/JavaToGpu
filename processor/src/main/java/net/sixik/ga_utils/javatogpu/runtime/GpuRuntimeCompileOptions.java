package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;

import java.util.List;
import java.util.Map;

public record GpuRuntimeCompileOptions(
        GpuBackendTarget backendTarget,
        List<String> compileArgs,
        String optimizationProfile,
        GpuBackendCompileOptions backendOptions,
        GpuRuntimeDeviceOverride deviceOverride
) {

    public static final String OPENCL_IRGPU_SOURCE_REVIEW_PROFILE = "source-reconstruction-review";

    public GpuRuntimeCompileOptions(
            GpuBackendTarget backendTarget,
            List<String> compileArgs,
            String optimizationProfile
    ) {
        this(
                backendTarget,
                compileArgs,
                optimizationProfile,
                backendTarget == GpuBackendTarget.OPENCL
                        ? GpuBackendCompileOptions.openCl(compileArgs)
                        : GpuBackendCompileOptions.empty(backendTarget),
                GpuRuntimeDeviceOverride.automatic()
        );
    }

    public GpuRuntimeCompileOptions(
            GpuBackendTarget backendTarget,
            List<String> compileArgs,
            String optimizationProfile,
            GpuBackendCompileOptions backendOptions
    ) {
        this(
                backendTarget,
                compileArgs,
                optimizationProfile,
                backendOptions,
                GpuRuntimeDeviceOverride.automatic()
        );
    }

    public GpuRuntimeCompileOptions {
        backendTarget = backendTarget == null ? GpuBackendTarget.UNKNOWN : backendTarget;
        compileArgs = compileArgs == null ? List.of() : List.copyOf(compileArgs);
        optimizationProfile = optimizationProfile == null || optimizationProfile.isBlank()
                ? "off"
                : optimizationProfile;
        backendOptions = normalizeBackendOptions(backendTarget, compileArgs, backendOptions);
        backendOptions.deviceSelfTestMode();
        deviceOverride = deviceOverride == null ? GpuRuntimeDeviceOverride.automatic() : deviceOverride;
    }

    public static GpuRuntimeCompileOptions defaults(GpuBackendTarget backendTarget) {
        return new GpuRuntimeCompileOptions(backendTarget, List.of(), "off");
    }

    public static GpuRuntimeCompileOptions openCl(List<String> compileArgs, String optimizationProfile) {
        return new GpuRuntimeCompileOptions(
                GpuBackendTarget.OPENCL,
                compileArgs,
                optimizationProfile,
                GpuBackendCompileOptions.openCl(compileArgs)
        );
    }

    public static GpuRuntimeCompileOptions openClIrGpuSource(List<String> compileArgs, String optimizationProfile) {
        return new GpuRuntimeCompileOptions(
                GpuBackendTarget.OPENCL,
                compileArgs,
                optimizationProfile,
                GpuBackendCompileOptions.openClIrGpuSource(compileArgs)
        );
    }

    public static GpuRuntimeCompileOptions openClIrGpuSourceReview(List<String> compileArgs) {
        return openClIrGpuSource(compileArgs, OPENCL_IRGPU_SOURCE_REVIEW_PROFILE);
    }

    public static GpuRuntimeCompileOptions openClProductionIrGpuSource(
            List<String> compileArgs,
            String optimizationProfile
    ) {
        return new GpuRuntimeCompileOptions(
                GpuBackendTarget.OPENCL,
                compileArgs,
                optimizationProfile,
                GpuBackendCompileOptions.openClProductionIrGpuSource(compileArgs)
        );
    }

    public GpuRuntimeCompileOptions withProductionPromotionDecision(GpuProductionPromotionDecision decision) {
        return new GpuRuntimeCompileOptions(
                backendTarget,
                compileArgs,
                optimizationProfile,
                backendOptions.withProductionPromotionDecision(decision),
                deviceOverride
        );
    }

    public GpuRuntimeCompileOptions withProductionPromotionOperatorAccepted(boolean accepted) {
        return new GpuRuntimeCompileOptions(
                backendTarget,
                compileArgs,
                optimizationProfile,
                backendOptions.withProductionPromotionOperatorAccepted(accepted),
                deviceOverride
        );
    }

    public GpuRuntimeCompileOptions withProductionPromotionOperatorAcceptance(
            GpuProductionPromotionOperatorAcceptance acceptance
    ) {
        return new GpuRuntimeCompileOptions(
                backendTarget,
                compileArgs,
                optimizationProfile,
                backendOptions.withProductionPromotionOperatorAcceptance(acceptance),
                deviceOverride
        );
    }

    public GpuRuntimeCompileOptions withProductionActivationToken(GpuProductionActivationToken token) {
        return new GpuRuntimeCompileOptions(
                backendTarget,
                compileArgs,
                optimizationProfile,
                backendOptions.withProductionActivationToken(token),
                deviceOverride
        );
    }

    public GpuRuntimeCompileOptions withDeviceOverride(GpuRuntimeDeviceOverride override) {
        return new GpuRuntimeCompileOptions(
                backendTarget,
                compileArgs,
                optimizationProfile,
                backendOptions,
                override
        );
    }

    public GpuRuntimeCompileOptions withDeviceSelfTestMode(GpuRuntimeDeviceSelfTestMode mode) {
        return new GpuRuntimeCompileOptions(
                backendTarget,
                compileArgs,
                optimizationProfile,
                backendOptions.withDeviceSelfTestMode(mode),
                deviceOverride
        );
    }

    public static GpuRuntimeCompileOptions cuda(
            List<String> nvrtcOptions,
            Map<String, String> properties,
            String optimizationProfile
    ) {
        return backendSpecific(GpuBackendCompileOptions.cuda(nvrtcOptions, properties), optimizationProfile);
    }

    public static GpuRuntimeCompileOptions vulkan(
            List<String> spirvOptions,
            Map<String, String> properties,
            String optimizationProfile
    ) {
        return backendSpecific(GpuBackendCompileOptions.vulkan(spirvOptions, properties), optimizationProfile);
    }

    public static GpuRuntimeCompileOptions metal(
            List<String> metalOptions,
            Map<String, String> properties,
            String optimizationProfile
    ) {
        return backendSpecific(GpuBackendCompileOptions.metal(metalOptions, properties), optimizationProfile);
    }

    private static GpuRuntimeCompileOptions backendSpecific(
            GpuBackendCompileOptions backendOptions,
            String optimizationProfile
    ) {
        return new GpuRuntimeCompileOptions(
                backendOptions.backendTarget(),
                backendOptions.backendTarget() == GpuBackendTarget.OPENCL ? backendOptions.flags() : List.of(),
                optimizationProfile,
                backendOptions
        );
    }

    private static GpuBackendCompileOptions normalizeBackendOptions(
            GpuBackendTarget backendTarget,
            List<String> compileArgs,
            GpuBackendCompileOptions backendOptions
    ) {
        if (backendOptions == null) {
            return backendTarget == GpuBackendTarget.OPENCL
                    ? GpuBackendCompileOptions.openCl(compileArgs)
                    : GpuBackendCompileOptions.empty(backendTarget);
        }
        if (backendOptions.backendTarget() == GpuBackendTarget.UNKNOWN && backendTarget != GpuBackendTarget.UNKNOWN) {
            return new GpuBackendCompileOptions(backendTarget, backendOptions.flags(), backendOptions.properties());
        }
        return backendOptions;
    }
}
