package net.sixik.ga_utils.javatogpu.runtime.cuda;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendLowerer;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendLowerers;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeBackendAdapter;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeBackendCatalog;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeBackendCatalogEntry;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileOptions;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDeviceDiscoveryResult;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDevicePolicyRegistry;

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
