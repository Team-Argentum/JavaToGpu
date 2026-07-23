package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.runtime.methodtest.*;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.api.GpuDeviceClassTarget;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Immutable runtime compile and placement options for one generated GPU kernel invocation.
 *
 * <p>Most applications do not need to construct this type for the first run. Start with
 * {@link net.sixik.ga_utils.javatogpu.api.JavaToGpu#useOpenClSharedCache()} or
 * {@link net.sixik.ga_utils.javatogpu.api.JavaToGpu#useStandardBackendAndDevice()} and add compile options only when a
 * kernel needs explicit compiler flags, device preferences, method-test placement evidence, artifact review, or staged
 * backend experiments.</p>
 *
 * <p>OpenCL is the normal alpha execution path. CUDA/Vulkan/Metal helpers in this record are planning and staged
 * integration surfaces: they should keep unsupported execution fail-closed unless the matching backend stage is
 * deliberately enabled and available.</p>
 *
 * <p>Instances are value objects. Modifier methods return a new options instance and leave the current instance
 * unchanged.</p>
 */
public record GpuRuntimeCompileOptions(
        GpuBackendTarget backendTarget,
        List<String> compileArgs,
        String optimizationProfile,
        GpuBackendCompileOptions backendOptions,
        GpuRuntimeDeviceOverride deviceOverride,
        GpuRuntimeDevicePreference devicePreference
) {

    /**
     * Review-only profile that asks OpenCL to try reconstructed IrGpu source instead of descriptor source.
     *
     * <p>This is for diagnostics and source-promotion work. It is not the default production OpenCL source path.</p>
     */
    public static final String OPENCL_IRGPU_SOURCE_REVIEW_PROFILE = "source-reconstruction-review";

    /**
     * Experimental profile for applying runtime IR optimizer mutations.
     *
     * <p>Optimizer mutation remains opt-in and fail-closed; normal users should leave optimization off unless they are
     * explicitly reviewing generated artifacts and equivalence evidence.</p>
     */
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

    /**
     * Creates OpenCL compile options with raw OpenCL compiler flags and an optimization profile.
     */
    public static GpuRuntimeCompileOptions openCl(List<String> compileArgs, String optimizationProfile) {
        return new GpuRuntimeCompileOptions(
                GpuBackendTarget.OPENCL,
                compileArgs,
                optimizationProfile,
                GpuBackendCompileOptions.openCl(compileArgs)
        );
    }

    /**
     * Creates review-mode OpenCL options that request reconstructed IrGpu source selection.
     *
     * <p>The backend still fails closed when reconstruction or source parity evidence is missing.</p>
     */
    public static GpuRuntimeCompileOptions openClIrGpuSource(List<String> compileArgs, String optimizationProfile) {
        return new GpuRuntimeCompileOptions(
                GpuBackendTarget.OPENCL,
                compileArgs,
                optimizationProfile,
                GpuBackendCompileOptions.openClIrGpuSource(compileArgs)
        );
    }

    /**
     * Convenience preset for non-production OpenCL reconstructed-source review.
     */
    public static GpuRuntimeCompileOptions openClIrGpuSourceReview(List<String> compileArgs) {
        return openClIrGpuSource(compileArgs, OPENCL_IRGPU_SOURCE_REVIEW_PROFILE);
    }

    /**
     * Creates production-gated OpenCL reconstructed-source options.
     *
     * <p>This still requires the separate production source-switching and promotion gates before descriptor source can
     * be replaced in production-like profiles.</p>
     */
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

    /**
     * Creates OpenCL options that request the experimental runtime IR optimizer apply path.
     *
     * <p>Use this only when reviewing original and optimized artifacts. The optimizer is optional and must fail closed
     * rather than silently changing production code.</p>
     */
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

    /**
     * Convenience preset for the experimental runtime IR optimizer apply path.
     */
    public static GpuRuntimeCompileOptions openClIrOptimizerExperimentalApply(List<String> compileArgs) {
        return openClIrOptimizerExperimentalApply(compileArgs, IR_OPTIMIZER_EXPERIMENTAL_APPLY_PROFILE);
    }

    /**
     * Returns a copy with experimental runtime IR optimizer application enabled.
     */
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

    /**
     * Returns a copy that performs standard backend/device preflight before backend compilation.
     */
    public GpuRuntimeCompileOptions withStandardBackendDevicePreflight() {
        return new GpuRuntimeCompileOptions(
                backendTarget,
                compileArgs,
                optimizationProfile,
                backendOptions.withStandardBackendDevicePreflight(),
                deviceOverride,
                devicePreference
        );
    }

    /**
     * Returns a copy that skips automatic backend/device preflight.
     */
    public GpuRuntimeCompileOptions withoutBackendDevicePreflight() {
        return new GpuRuntimeCompileOptions(
                backendTarget,
                compileArgs,
                optimizationProfile,
                backendOptions.withoutBackendDevicePreflight(),
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
        return withMethodTestProbeMode(GpuRuntimeMethodTestProbeMode.CACHE_ONLY);
    }

    public GpuRuntimeCompileOptions withMethodTestProbeMode(GpuRuntimeMethodTestProbeMode mode) {
        return new GpuRuntimeCompileOptions(
                backendTarget,
                compileArgs,
                optimizationProfile,
                backendOptions.withMethodTestProbeMode(mode),
                deviceOverride,
                devicePreference
        );
    }

    public GpuRuntimeCompileOptions withCudaCompilePreview() {
        return new GpuRuntimeCompileOptions(
                GpuBackendTarget.CUDA,
                compileArgs,
                optimizationProfile,
                backendOptions.withCudaCompilePreview(),
                deviceOverride,
                devicePreference
        );
    }

    public GpuRuntimeCompileOptions withCudaNvccCompilerBridge(String nvccPath) {
        return new GpuRuntimeCompileOptions(
                GpuBackendTarget.CUDA,
                compileArgs,
                optimizationProfile,
                backendOptions.withCudaNvccCompilerBridge(nvccPath),
                deviceOverride,
                devicePreference
        );
    }

    public GpuRuntimeCompileOptions withCudaNativeModuleLoader(String loaderMode) {
        return new GpuRuntimeCompileOptions(
                GpuBackendTarget.CUDA,
                compileArgs,
                optimizationProfile,
                backendOptions.withCudaNativeModuleLoader(loaderMode),
                deviceOverride,
                devicePreference
        );
    }

    public GpuRuntimeCompileOptions withCudaDriverModuleLoader() {
        return withCudaNativeModuleLoader(GpuBackendCompileOptions.CUDA_MODULE_LOADER_DRIVER);
    }

    public GpuRuntimeCompileOptions withCudaNativeArgumentBinder(String binderMode) {
        return new GpuRuntimeCompileOptions(
                GpuBackendTarget.CUDA,
                compileArgs,
                optimizationProfile,
                backendOptions.withCudaNativeArgumentBinder(binderMode),
                deviceOverride,
                devicePreference
        );
    }

    public GpuRuntimeCompileOptions withCudaDriverArgumentBinder() {
        return withCudaNativeArgumentBinder(GpuBackendCompileOptions.CUDA_ARGUMENT_BINDER_DRIVER);
    }

    public GpuRuntimeCompileOptions withCudaNativeKernelLauncher(String launcherMode) {
        return new GpuRuntimeCompileOptions(
                GpuBackendTarget.CUDA,
                compileArgs,
                optimizationProfile,
                backendOptions.withCudaNativeKernelLauncher(launcherMode),
                deviceOverride,
                devicePreference
        );
    }

    public GpuRuntimeCompileOptions withCudaDriverKernelLauncher() {
        return withCudaNativeKernelLauncher(GpuBackendCompileOptions.CUDA_KERNEL_LAUNCHER_DRIVER);
    }

    public GpuRuntimeCompileOptions withCudaNativeReadback(String readbackMode) {
        return new GpuRuntimeCompileOptions(
                GpuBackendTarget.CUDA,
                compileArgs,
                optimizationProfile,
                backendOptions.withCudaNativeReadback(readbackMode),
                deviceOverride,
                devicePreference
        );
    }

    public GpuRuntimeCompileOptions withCudaDriverReadback() {
        return withCudaNativeReadback(GpuBackendCompileOptions.CUDA_READBACK_DRIVER);
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

    public static GpuRuntimeCompileOptions cudaNvcc(
            List<String> nvccOptions,
            String nvccPath,
            String optimizationProfile
    ) {
        return backendSpecific(GpuBackendCompileOptions.cudaNvcc(nvccOptions, nvccPath), optimizationProfile);
    }

    public static GpuRuntimeCompileOptions cudaNvcc(
            List<String> nvccOptions,
            String nvccPath,
            String outputFormat,
            String optimizationProfile
    ) {
        return backendSpecific(
                GpuBackendCompileOptions.cudaNvcc(nvccOptions, nvccPath, outputFormat),
                optimizationProfile
        );
    }

    public GpuRuntimeCompileOptions withCudaNvccOutputFormat(String outputFormat) {
        return new GpuRuntimeCompileOptions(
                GpuBackendTarget.CUDA,
                compileArgs,
                optimizationProfile,
                backendOptions.withCudaNvccOutputFormat(outputFormat),
                deviceOverride,
                devicePreference
        );
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
