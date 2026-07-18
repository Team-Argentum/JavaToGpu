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
        return OpenClRuntimeDeviceDiscovery.discover(normalizeOpenClOptions(compileOptions), devicePolicyRegistry);
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
        return CudaRuntimeDeviceDiscovery.discover(compileOptions, devicePolicyRegistry);
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
        GpuBackendTarget target = backendTarget == null ? GpuBackendTarget.UNKNOWN : backendTarget;
        String backendName = target.name();
        String diagnostic = "Runtime device discovery adapter is not implemented for "
                + target
                + "; backend remains planned/unavailable in this alpha";
        return GpuRuntimeDeviceDiscoveryResult.unavailable(
                target,
                backendName,
                "backend-device-discovery-not-implemented",
                new UnsupportedOperationException(diagnostic)
        );
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
