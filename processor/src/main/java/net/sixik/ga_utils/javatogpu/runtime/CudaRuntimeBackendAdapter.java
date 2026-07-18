package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.runtime.cuda.CudaRuntimeDeviceDiscovery;

/**
 * Built-in CUDA backend adapter for discovery-only alpha support.
 */
public final class CudaRuntimeBackendAdapter implements GpuRuntimeBackendAdapter {

    @Override
    public GpuBackendTarget backendTarget() {
        return GpuBackendTarget.CUDA;
    }

    @Override
    public String backendName() {
        return "CUDA";
    }

    @Override
    public GpuRuntimeBackendCatalogEntry catalogEntry() {
        return GpuRuntimeBackendCatalog.plannedUnsupported(GpuBackendTarget.CUDA);
    }

    @Override
    public GpuRuntimeDeviceDiscoveryResult discoverDevices(
            GpuRuntimeCompileOptions compileOptions,
            GpuRuntimeDevicePolicyRegistry devicePolicyRegistry
    ) {
        return CudaRuntimeDeviceDiscovery.discover(compileOptions, devicePolicyRegistry);
    }

    @Override
    public GpuBackendLowerer lowerer() {
        return GpuBackendLowerers.forTarget(GpuBackendTarget.CUDA);
    }
}
