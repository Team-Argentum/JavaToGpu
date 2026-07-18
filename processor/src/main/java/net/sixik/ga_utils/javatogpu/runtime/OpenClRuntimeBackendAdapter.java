package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;

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
