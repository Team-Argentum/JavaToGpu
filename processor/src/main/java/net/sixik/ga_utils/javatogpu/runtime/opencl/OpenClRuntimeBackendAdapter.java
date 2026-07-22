package net.sixik.ga_utils.javatogpu.runtime.opencl;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendLowerer;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendLowerers;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeBackendAdapter;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeBackendCatalog;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeBackendCatalogEntry;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileOptions;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDeviceDiscovery;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDeviceDiscoveryResult;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDevicePolicyRegistry;

/**
 * Built-in OpenCL backend adapter.
 */
public final class OpenClRuntimeBackendAdapter implements GpuRuntimeBackendAdapter {

    @Override
    public GpuBackendTarget backendTarget() {
        return GpuBackendTarget.OPENCL;
    }

    @Override
    public String backendName() {
        return "OpenCL";
    }

    @Override
    public GpuRuntimeBackendCatalogEntry catalogEntry() {
        return GpuRuntimeBackendCatalog.openClSharedCache();
    }

    @Override
    public GpuRuntimeDeviceDiscoveryResult discoverDevices(
            GpuRuntimeCompileOptions compileOptions,
            GpuRuntimeDevicePolicyRegistry devicePolicyRegistry
    ) {
        return GpuRuntimeDeviceDiscovery.discoverOpenCl(compileOptions, devicePolicyRegistry);
    }

    @Override
    public GpuBackendLowerer lowerer() {
        return GpuBackendLowerers.forTarget(GpuBackendTarget.OPENCL);
    }
}
