package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.runtime.selection.GpuRuntimeDeviceDiscoverySupport;

/**
 * Compatibility facade for backend/device discovery snapshots.
 *
 * <p>New advanced selection/discovery code should prefer
 * {@link net.sixik.ga_utils.javatogpu.runtime.selection.GpuRuntimeSelection} or
 * {@link GpuRuntimeDeviceDiscoverySupport}. This root-runtime facade remains source/binary compatible for existing
 * callers and generated/runtime internals while discovery implementation moves into the {@code runtime.selection}
 * domain package.</p>
 */
public final class GpuRuntimeDeviceDiscovery {

    private GpuRuntimeDeviceDiscovery() {
    }

    /**
     * Discovers OpenCL devices using default compile options and built-in device policies.
     */
    public static GpuRuntimeDeviceDiscoveryResult discoverOpenCl() {
        return GpuRuntimeDeviceDiscoverySupport.discoverOpenCl();
    }

    /**
     * Discovers OpenCL devices and previews deterministic device selection for the supplied controls.
     */
    public static GpuRuntimeDeviceDiscoveryResult discoverOpenCl(GpuRuntimeCompileOptions compileOptions) {
        return GpuRuntimeDeviceDiscoverySupport.discoverOpenCl(compileOptions);
    }

    /**
     * Discovers OpenCL devices and previews deterministic device selection with an explicit policy registry.
     */
    public static GpuRuntimeDeviceDiscoveryResult discoverOpenCl(
            GpuRuntimeCompileOptions compileOptions,
            GpuRuntimeDevicePolicyRegistry devicePolicyRegistry
    ) {
        return GpuRuntimeDeviceDiscoverySupport.discoverOpenCl(compileOptions, devicePolicyRegistry);
    }

    /**
     * Discovers OpenCL devices with an explicit backend hook registry, primarily for tools and tests.
     */
    public static GpuRuntimeDeviceDiscoveryResult discoverOpenCl(
            GpuRuntimeCompileOptions compileOptions,
            GpuRuntimeDevicePolicyRegistry devicePolicyRegistry,
            GpuBackendHookRegistry backendHookRegistry
    ) {
        return GpuRuntimeDeviceDiscoverySupport.discoverOpenCl(
                compileOptions,
                devicePolicyRegistry,
                backendHookRegistry
        );
    }

    /**
     * Discovers CUDA-visible NVIDIA devices using default compile options and built-in device policies.
     */
    public static GpuRuntimeDeviceDiscoveryResult discoverCuda() {
        return GpuRuntimeDeviceDiscoverySupport.discoverCuda();
    }

    /**
     * Discovers CUDA-visible NVIDIA devices and previews deterministic device selection for the supplied controls.
     */
    public static GpuRuntimeDeviceDiscoveryResult discoverCuda(GpuRuntimeCompileOptions compileOptions) {
        return GpuRuntimeDeviceDiscoverySupport.discoverCuda(compileOptions);
    }

    /**
     * Discovers CUDA-visible NVIDIA devices and previews deterministic device selection with an explicit policy registry.
     */
    public static GpuRuntimeDeviceDiscoveryResult discoverCuda(
            GpuRuntimeCompileOptions compileOptions,
            GpuRuntimeDevicePolicyRegistry devicePolicyRegistry
    ) {
        return GpuRuntimeDeviceDiscoverySupport.discoverCuda(compileOptions, devicePolicyRegistry);
    }

    /**
     * Discovers CUDA-visible devices with an explicit backend hook registry, primarily for tools and tests.
     */
    public static GpuRuntimeDeviceDiscoveryResult discoverCuda(
            GpuRuntimeCompileOptions compileOptions,
            GpuRuntimeDevicePolicyRegistry devicePolicyRegistry,
            GpuBackendHookRegistry backendHookRegistry
    ) {
        return GpuRuntimeDeviceDiscoverySupport.discoverCuda(
                compileOptions,
                devicePolicyRegistry,
                backendHookRegistry
        );
    }

    /**
     * Discovers the standard backend device inventory shape.
     */
    public static GpuRuntimeDeviceDiscoveryCatalog discoverStandardBackends() {
        return GpuRuntimeDeviceDiscoverySupport.discoverStandardBackends();
    }

    /**
     * Discovers the standard backend device inventory shape using caller-provided device-selection controls.
     */
    public static GpuRuntimeDeviceDiscoveryCatalog discoverStandardBackends(GpuRuntimeCompileOptions compileOptions) {
        return GpuRuntimeDeviceDiscoverySupport.discoverStandardBackends(compileOptions);
    }

    /**
     * Returns an explicit planned/unavailable discovery state for a backend without a native device adapter yet.
     */
    public static GpuRuntimeDeviceDiscoveryResult plannedUnavailable(GpuBackendTarget backendTarget) {
        return GpuRuntimeDeviceDiscoverySupport.plannedUnavailable(backendTarget);
    }

    /**
     * Returns an explicit planned/unavailable discovery state and observes it with an explicit hook registry.
     */
    public static GpuRuntimeDeviceDiscoveryResult plannedUnavailable(
            GpuBackendTarget backendTarget,
            GpuBackendHookRegistry backendHookRegistry
    ) {
        return GpuRuntimeDeviceDiscoverySupport.plannedUnavailable(backendTarget, backendHookRegistry);
    }
}
