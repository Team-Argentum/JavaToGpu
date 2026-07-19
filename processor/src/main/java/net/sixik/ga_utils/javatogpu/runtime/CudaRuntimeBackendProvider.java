package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;

import java.util.Set;

/**
 * Built-in CUDA backend provider for discovery-only alpha support.
 */
public final class CudaRuntimeBackendProvider implements GpuRuntimeBackendProvider {

    @Override
    public GpuBackendTarget backendTarget() {
        return GpuBackendTarget.CUDA;
    }

    @Override
    public String providerId() {
        return "backend-provider:cuda";
    }

    @Override
    public String providerVersion() {
        return "1";
    }

    @Override
    public int providerOrder() {
        return 100;
    }

    @Override
    public GpuRuntimeBackendAdapter createAdapter() {
        return new CudaRuntimeBackendAdapter();
    }

    @Override
    public GpuRuntimeBackendExecutionSupport executionSupport() {
        return GpuRuntimeBackendExecutionSupport.discoveryOnly(
                backendTarget(),
                providerId(),
                Set.of(GpuBackendModuleFormat.CUDA_C, GpuBackendModuleFormat.PTX),
                Set.of(
                        GpuRuntimeCapability.DEVICE_CLASS,
                        GpuRuntimeCapability.DRIVER_VERSION,
                        GpuRuntimeCapability.RUNTIME_VERSION,
                        GpuRuntimeCapability.COMPUTE_CAPABILITY,
                        GpuRuntimeCapability.GLOBAL_MEMORY
                ),
                "CUDA is inventory-only in this alpha; execution pipeline is intentionally not enabled yet"
        );
    }
}
