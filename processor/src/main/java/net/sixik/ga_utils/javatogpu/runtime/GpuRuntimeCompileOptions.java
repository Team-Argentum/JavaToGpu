package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.api.GpuDeviceClassTarget;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

public record GpuRuntimeCompileOptions(
        GpuBackendTarget backendTarget,
        List<String> compileArgs,
        String optimizationProfile,
        GpuBackendCompileOptions backendOptions,
        GpuRuntimeDeviceOverride deviceOverride,
        GpuRuntimeDevicePreference devicePreference
) {

    public static final String OPENCL_IRGPU_SOURCE_REVIEW_PROFILE = "source-reconstruction-review";
    public static final String IR_OPTIMIZER_EXPERIMENTAL_APPLY_PROFILE = "ir-optimizer-experimental-apply";

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
                GpuRuntimeDeviceOverride.automatic(),
                GpuRuntimeDevicePreference.automatic()
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
                GpuRuntimeDeviceOverride.automatic(),
                GpuRuntimeDevicePreference.automatic()
        );
    }

    public GpuRuntimeCompileOptions(
            GpuBackendTarget backendTarget,
            List<String> compileArgs,
            String optimizationProfile,
            GpuBackendCompileOptions backendOptions,
            GpuRuntimeDeviceOverride deviceOverride
    ) {
        this(
                backendTarget,
                compileArgs,
                optimizationProfile,
                backendOptions,
                deviceOverride,
                GpuRuntimeDevicePreference.automatic()
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
        devicePreference = devicePreference == null ? GpuRuntimeDevicePreference.automatic() : devicePreference;
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

    public static GpuRuntimeCompileOptions openClIrOptimizerExperimentalApply(
            List<String> compileArgs,
            String optimizationProfile
    ) {
        return new GpuRuntimeCompileOptions(
                GpuBackendTarget.OPENCL,
                compileArgs,
                optimizationProfile,
                GpuBackendCompileOptions.openCl(compileArgs).withRuntimeIrOptimizerExperimentalApply()
        );
    }

    public static GpuRuntimeCompileOptions openClIrOptimizerExperimentalApply(List<String> compileArgs) {
        return openClIrOptimizerExperimentalApply(compileArgs, IR_OPTIMIZER_EXPERIMENTAL_APPLY_PROFILE);
    }

    public GpuRuntimeCompileOptions withRuntimeIrOptimizerExperimentalApply() {
        return new GpuRuntimeCompileOptions(
                backendTarget,
                compileArgs,
                optimizationProfile,
                backendOptions.withRuntimeIrOptimizerExperimentalApply(),
                deviceOverride,
                devicePreference
        );
    }

    public GpuRuntimeCompileOptions withProductionPromotionDecision(GpuProductionPromotionDecision decision) {
        return new GpuRuntimeCompileOptions(
                backendTarget,
                compileArgs,
                optimizationProfile,
                backendOptions.withProductionPromotionDecision(decision),
                deviceOverride,
                devicePreference
        );
    }

    public GpuRuntimeCompileOptions withProductionPromotionOperatorAccepted(boolean accepted) {
        return new GpuRuntimeCompileOptions(
                backendTarget,
                compileArgs,
                optimizationProfile,
                backendOptions.withProductionPromotionOperatorAccepted(accepted),
                deviceOverride,
                devicePreference
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
                deviceOverride,
                devicePreference
        );
    }

    public GpuRuntimeCompileOptions withProductionActivationToken(GpuProductionActivationToken token) {
        return new GpuRuntimeCompileOptions(
                backendTarget,
                compileArgs,
                optimizationProfile,
                backendOptions.withProductionActivationToken(token),
                deviceOverride,
                devicePreference
        );
    }

    public GpuRuntimeCompileOptions withDeviceOverride(GpuRuntimeDeviceOverride override) {
        return new GpuRuntimeCompileOptions(
                backendTarget,
                compileArgs,
                optimizationProfile,
                backendOptions,
                override,
                devicePreference
        );
    }

    public GpuRuntimeCompileOptions withDevicePreference(GpuRuntimeDevicePreference preference) {
        return new GpuRuntimeCompileOptions(
                backendTarget,
                compileArgs,
                optimizationProfile,
                backendOptions,
                deviceOverride,
                preference
        );
    }

    public GpuRuntimeCompileOptions preferDeviceId(String deviceId) {
        return withDevicePreference(devicePreference.withPreferredDeviceId(deviceId));
    }

    public GpuRuntimeCompileOptions preferDeviceVendor(String vendorContains) {
        return withDevicePreference(devicePreference.withPreferredVendor(vendorContains));
    }

    public GpuRuntimeCompileOptions preferDeviceLabel(String labelContains) {
        return withDevicePreference(devicePreference.withPreferredDeviceLabel(labelContains));
    }

    public GpuRuntimeCompileOptions preferDeviceClass(GpuDeviceClassTarget deviceClass) {
        return withDevicePreference(devicePreference.withPreferredDeviceClass(deviceClass));
    }

    public GpuRuntimeCompileOptions excludeDeviceId(String deviceId) {
        return withDevicePreference(devicePreference.withExcludedDeviceId(deviceId));
    }

    public GpuRuntimeCompileOptions excludeDeviceVendor(String vendorContains) {
        return withDevicePreference(devicePreference.withExcludedVendor(vendorContains));
    }

    public GpuRuntimeCompileOptions excludeDeviceLabel(String labelContains) {
        return withDevicePreference(devicePreference.withExcludedDeviceLabel(labelContains));
    }

    public GpuRuntimeCompileOptions excludeDeviceClass(GpuDeviceClassTarget deviceClass) {
        return withDevicePreference(devicePreference.withExcludedDeviceClass(deviceClass));
    }

    public GpuRuntimeCompileOptions excludeCpuDevices() {
        return excludeDeviceClass(GpuDeviceClassTarget.CPU);
    }

    public GpuRuntimeCompileOptions excludeIntegratedGpuDevices() {
        return excludeDeviceClass(GpuDeviceClassTarget.IGPU);
    }

    public GpuRuntimeCompileOptions excludeIntegratedAndCpuDevices() {
        return withDevicePreference(devicePreference
                .withExcludedDeviceClass(GpuDeviceClassTarget.IGPU)
                .withExcludedDeviceClass(GpuDeviceClassTarget.CPU)
        );
    }

    public GpuRuntimeCompileOptions withDeviceSelfTestMode(GpuRuntimeDeviceSelfTestMode mode) {
        return new GpuRuntimeCompileOptions(
                backendTarget,
                compileArgs,
                optimizationProfile,
                backendOptions.withDeviceSelfTestMode(mode),
                deviceOverride,
                devicePreference
        );
    }

    public GpuRuntimeCompileOptions withMethodTestProbeEvidenceRankingCached() {
        return new GpuRuntimeCompileOptions(
                backendTarget,
                compileArgs,
                optimizationProfile,
                backendOptions.withMethodTestProbeEvidenceRankingCached(),
                deviceOverride,
                devicePreference
        );
    }

    public GpuRuntimeCompileOptions withPersistentMethodTestProbeEvidenceRanking(Path cacheDirectory) {
        return withPersistentMethodTestProbeEvidenceRanking(cacheDirectory, null);
    }

    public GpuRuntimeCompileOptions withPersistentMethodTestProbeEvidenceRanking(
            Path cacheDirectory,
            Duration maxEntryAge
    ) {
        return new GpuRuntimeCompileOptions(
                backendTarget,
                compileArgs,
                optimizationProfile,
                backendOptions.withPersistentMethodTestProbeEvidenceRanking(cacheDirectory, maxEntryAge),
                deviceOverride,
                devicePreference
        );
    }

    public GpuRuntimeCompileOptions withoutMethodTestProbeEvidenceRanking() {
        return new GpuRuntimeCompileOptions(
                backendTarget,
                compileArgs,
                optimizationProfile,
                backendOptions.withoutMethodTestProbeEvidenceRanking(),
                deviceOverride,
                devicePreference
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
