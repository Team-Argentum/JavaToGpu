package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;

/**
 * Explicit planned-backend adapter for targets that have a public contract but no runtime implementation yet.
 */
public final class PlannedGpuRuntimeBackendAdapter implements GpuRuntimeBackendAdapter {

    private final GpuBackendTarget backendTarget;

    public PlannedGpuRuntimeBackendAdapter(GpuBackendTarget backendTarget) {
        this.backendTarget = backendTarget == null ? GpuBackendTarget.UNKNOWN : backendTarget;
    }

    @Override
    public GpuBackendTarget backendTarget() {
        return backendTarget;
    }

    @Override
    public String backendName() {
        return backendTarget.name();
    }

    @Override
    public GpuRuntimeBackendCatalogEntry catalogEntry() {
        return GpuRuntimeBackendCatalog.plannedUnsupported(backendTarget);
    }

    @Override
    public GpuRuntimeDeviceDiscoveryResult discoverDevices(
            GpuRuntimeCompileOptions compileOptions,
            GpuRuntimeDevicePolicyRegistry devicePolicyRegistry
    ) {
        return GpuRuntimeDeviceDiscovery.plannedUnavailable(backendTarget);
    }

    @Override
    public GpuBackendLowerer lowerer() {
        return GpuBackendLowerers.forTarget(backendTarget);
    }
}
