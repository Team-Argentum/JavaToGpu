package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.runtime.cuda.CudaRuntimeDeviceDiscovery;
import net.sixik.ga_utils.javatogpu.runtime.opencl.OpenClRuntimeDeviceDiscovery;

/**
 * Public entry point for backend/device discovery snapshots.
 *
 * <p>OpenCL can perform native runtime discovery and CUDA can perform inventory discovery through {@code nvidia-smi}.
 * The API shape is intentionally backend-neutral so Vulkan/SPIR-V, Metal, or custom backend adapters can later return
 * the same result type.</p>
 */
public final class GpuRuntimeDeviceDiscovery {

    private GpuRuntimeDeviceDiscovery() {
    }

    /**
     * Discovers OpenCL devices using default compile options and built-in device policies.
     */
    public static GpuRuntimeDeviceDiscoveryResult discoverOpenCl() {
        return discoverOpenCl(GpuRuntimeCompileOptions.defaults(GpuBackendTarget.OPENCL));
    }

    /**
     * Discovers OpenCL devices and previews deterministic device selection for the supplied controls.
     */
    public static GpuRuntimeDeviceDiscoveryResult discoverOpenCl(GpuRuntimeCompileOptions compileOptions) {
        return discoverOpenCl(compileOptions, GpuRuntimeDevicePolicyRegistry.loadWithBuiltIns());
    }

    /**
     * Discovers OpenCL devices and previews deterministic device selection with an explicit policy registry.
     */
    public static GpuRuntimeDeviceDiscoveryResult discoverOpenCl(
            GpuRuntimeCompileOptions compileOptions,
            GpuRuntimeDevicePolicyRegistry devicePolicyRegistry
    ) {
        return discoverOpenCl(
                compileOptions,
                devicePolicyRegistry,
                GpuBackendHookRegistry.loadWithServiceLoader()
        );
    }

    /**
     * Discovers OpenCL devices with an explicit backend hook registry, primarily for tools and tests.
     */
    public static GpuRuntimeDeviceDiscoveryResult discoverOpenCl(
            GpuRuntimeCompileOptions compileOptions,
            GpuRuntimeDevicePolicyRegistry devicePolicyRegistry,
            GpuBackendHookRegistry backendHookRegistry
    ) {
        GpuRuntimeCompileOptions normalizedOptions = normalizeOpenClOptions(compileOptions);
        return observeDiscoveryHooks(
                normalizedOptions,
                OpenClRuntimeDeviceDiscovery.discover(normalizedOptions, devicePolicyRegistry),
                backendHookRegistry
        );
    }

    /**
     * Discovers CUDA-visible NVIDIA devices using default compile options and built-in device policies.
     */
    public static GpuRuntimeDeviceDiscoveryResult discoverCuda() {
        return discoverCuda(GpuRuntimeCompileOptions.defaults(GpuBackendTarget.CUDA));
    }

    /**
     * Discovers CUDA-visible NVIDIA devices and previews deterministic device selection for the supplied controls.
     */
    public static GpuRuntimeDeviceDiscoveryResult discoverCuda(GpuRuntimeCompileOptions compileOptions) {
        return discoverCuda(compileOptions, GpuRuntimeDevicePolicyRegistry.loadWithBuiltIns());
    }

    /**
     * Discovers CUDA-visible NVIDIA devices and previews deterministic device selection with an explicit policy registry.
     */
    public static GpuRuntimeDeviceDiscoveryResult discoverCuda(
            GpuRuntimeCompileOptions compileOptions,
            GpuRuntimeDevicePolicyRegistry devicePolicyRegistry
    ) {
        return discoverCuda(
                compileOptions,
                devicePolicyRegistry,
                GpuBackendHookRegistry.loadWithServiceLoader()
        );
    }

    /**
     * Discovers CUDA-visible devices with an explicit backend hook registry, primarily for tools and tests.
     */
    public static GpuRuntimeDeviceDiscoveryResult discoverCuda(
            GpuRuntimeCompileOptions compileOptions,
            GpuRuntimeDevicePolicyRegistry devicePolicyRegistry,
            GpuBackendHookRegistry backendHookRegistry
    ) {
        GpuRuntimeCompileOptions resolvedOptions = compileOptions == null
                ? GpuRuntimeCompileOptions.defaults(GpuBackendTarget.CUDA)
                : compileOptions;
        return observeDiscoveryHooks(
                resolvedOptions,
                CudaRuntimeDeviceDiscovery.discover(resolvedOptions, devicePolicyRegistry),
                backendHookRegistry
        );
    }

    /**
     * Discovers the standard backend device inventory shape.
     *
     * <p>OpenCL is queried through the native adapter, CUDA is queried through {@code nvidia-smi} when available, and
     * planned Vulkan/SPIR-V and Metal backends are returned as explicit unavailable discovery states until real adapters
     * land.</p>
     */
    public static GpuRuntimeDeviceDiscoveryCatalog discoverStandardBackends() {
        return discoverStandardBackends(GpuRuntimeCompileOptions.defaults(GpuBackendTarget.OPENCL));
    }

    /**
     * Discovers the standard backend device inventory shape using caller-provided device-selection controls.
     */
    public static GpuRuntimeDeviceDiscoveryCatalog discoverStandardBackends(GpuRuntimeCompileOptions compileOptions) {
        return GpuRuntimeBackendAdapters.discoverDevices(
                GpuRuntimeBackendAdapters.standardWithPlannedBackends(),
                compileOptions
        );
    }

    /**
     * Returns an explicit planned/unavailable discovery state for a backend without a native device adapter yet.
     */
    public static GpuRuntimeDeviceDiscoveryResult plannedUnavailable(GpuBackendTarget backendTarget) {
        return plannedUnavailable(backendTarget, GpuBackendHookRegistry.loadWithServiceLoader());
    }

    /**
     * Returns an explicit planned/unavailable discovery state and observes it with an explicit hook registry.
     */
    public static GpuRuntimeDeviceDiscoveryResult plannedUnavailable(
            GpuBackendTarget backendTarget,
            GpuBackendHookRegistry backendHookRegistry
    ) {
        GpuBackendTarget target = backendTarget == null ? GpuBackendTarget.UNKNOWN : backendTarget;
        String backendName = target.name();
        String diagnostic = "Runtime device discovery adapter is not implemented for "
                + target
                + "; backend remains planned/unavailable in this alpha";
        GpuRuntimeCompileOptions compileOptions = GpuRuntimeCompileOptions.defaults(target);
        return observeDiscoveryHooks(
                compileOptions,
                GpuRuntimeDeviceDiscoveryResult.unavailable(
                        target,
                        backendName,
                        "backend-device-discovery-not-implemented",
                        new UnsupportedOperationException(diagnostic)
                ),
                backendHookRegistry
        );
    }

    private static GpuRuntimeDeviceDiscoveryResult observeDiscoveryHooks(
            GpuRuntimeCompileOptions compileOptions,
            GpuRuntimeDeviceDiscoveryResult discoveryResult,
            GpuBackendHookRegistry backendHookRegistry
    ) {
        GpuBackendHookRegistry registry = backendHookRegistry == null
                ? GpuBackendHookRegistry.empty()
                : backendHookRegistry;
        if (registry.isEmpty() || discoveryResult == null) {
            return discoveryResult;
        }
        return discoveryResult.withHookExecutionFields(registry.observeDiscovery(
                compileOptions,
                discoveryResult,
                "runtime.backend.hookExecution.discovery"
        ));
    }

    private static GpuRuntimeCompileOptions normalizeOpenClOptions(GpuRuntimeCompileOptions compileOptions) {
        if (compileOptions == null) {
            return GpuRuntimeCompileOptions.defaults(GpuBackendTarget.OPENCL);
        }
        GpuBackendCompileOptions backendOptions = compileOptions.backendOptions().backendTarget() == GpuBackendTarget.OPENCL
                ? compileOptions.backendOptions()
                : GpuBackendCompileOptions.openCl(compileOptions.compileArgs());
        return new GpuRuntimeCompileOptions(
                GpuBackendTarget.OPENCL,
                compileOptions.compileArgs(),
                compileOptions.optimizationProfile(),
                backendOptions,
                compileOptions.deviceOverride(),
                compileOptions.devicePreference()
        );
    }
}
