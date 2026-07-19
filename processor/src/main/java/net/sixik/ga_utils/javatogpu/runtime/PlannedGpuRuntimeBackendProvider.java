package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;

import java.util.Set;

/**
 * Built-in provider for planned backend families that expose catalog/discovery diagnostics before execution exists.
 */
public final class PlannedGpuRuntimeBackendProvider implements GpuRuntimeBackendProvider {

    private final GpuBackendTarget backendTarget;
    private final int providerOrder;

    public PlannedGpuRuntimeBackendProvider(GpuBackendTarget backendTarget, int providerOrder) {
        this.backendTarget = backendTarget == null ? GpuBackendTarget.UNKNOWN : backendTarget;
        this.providerOrder = providerOrder;
    }

    @Override
    public GpuBackendTarget backendTarget() {
        return backendTarget;
    }

    @Override
    public String providerId() {
        return "backend-provider:" + backendTarget.name().toLowerCase(java.util.Locale.ROOT);
    }

    @Override
    public String providerVersion() {
        return "1";
    }

    @Override
    public int providerOrder() {
        return providerOrder;
    }

    @Override
    public GpuRuntimeBackendAdapter createAdapter() {
        return new PlannedGpuRuntimeBackendAdapter(backendTarget);
    }

    @Override
    public GpuRuntimeBackendExecutionSupport executionSupport() {
        return GpuRuntimeBackendExecutionSupport.discoveryOnly(
                backendTarget(),
                providerId(),
                moduleFormats(),
                capabilityVocabulary(),
                backendTarget.name() + " is a planned backend placeholder; execution pipeline is not implemented"
        );
    }

    private Set<GpuBackendModuleFormat> moduleFormats() {
        return switch (backendTarget) {
            case VULKAN -> Set.of(GpuBackendModuleFormat.SPIR_V);
            case METAL -> Set.of(GpuBackendModuleFormat.METAL_SHADING_LANGUAGE);
            case CUDA -> Set.of(GpuBackendModuleFormat.CUDA_C, GpuBackendModuleFormat.PTX);
            case OPENCL -> Set.of(GpuBackendModuleFormat.OPENCL_C);
            default -> Set.of();
        };
    }

    private Set<GpuRuntimeCapability> capabilityVocabulary() {
        return switch (backendTarget) {
            case VULKAN -> Set.of(
                    GpuRuntimeCapability.DEVICE_CLASS,
                    GpuRuntimeCapability.DRIVER_VERSION,
                    GpuRuntimeCapability.RUNTIME_VERSION,
                    GpuRuntimeCapability.GLOBAL_MEMORY,
                    GpuRuntimeCapability.LOCAL_MEMORY,
                    GpuRuntimeCapability.MAX_WORK_GROUP_SIZE,
                    GpuRuntimeCapability.SUBGROUPS,
                    GpuRuntimeCapability.IMAGES
            );
            case METAL -> Set.of(
                    GpuRuntimeCapability.DEVICE_CLASS,
                    GpuRuntimeCapability.DRIVER_VERSION,
                    GpuRuntimeCapability.RUNTIME_VERSION,
                    GpuRuntimeCapability.GLOBAL_MEMORY,
                    GpuRuntimeCapability.UNIFIED_MEMORY,
                    GpuRuntimeCapability.IMAGES
            );
            case CUDA -> Set.of(
                    GpuRuntimeCapability.DEVICE_CLASS,
                    GpuRuntimeCapability.DRIVER_VERSION,
                    GpuRuntimeCapability.RUNTIME_VERSION,
                    GpuRuntimeCapability.COMPUTE_CAPABILITY,
                    GpuRuntimeCapability.GLOBAL_MEMORY
            );
            case OPENCL -> Set.of(
                    GpuRuntimeCapability.DEVICE_CLASS,
                    GpuRuntimeCapability.DRIVER_VERSION,
                    GpuRuntimeCapability.RUNTIME_VERSION,
                    GpuRuntimeCapability.GLOBAL_MEMORY,
                    GpuRuntimeCapability.LOCAL_MEMORY,
                    GpuRuntimeCapability.MAX_WORK_GROUP_SIZE
            );
            default -> Set.of();
        };
    }
}
