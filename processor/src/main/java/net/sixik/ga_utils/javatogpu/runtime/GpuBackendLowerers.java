package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.runtime.cuda.CudaBackendLowerer;
import net.sixik.ga_utils.javatogpu.runtime.opencl.OpenClBackendLowerer;

public final class GpuBackendLowerers {

    private static final GpuBackendLowerer OPENCL = new OpenClBackendLowerer();
    private static final GpuBackendLowerer CUDA = new CudaBackendLowerer();
    private static final GpuBackendLowerer VULKAN = new UnsupportedGpuBackendLowerer(GpuBackendTarget.VULKAN);
    private static final GpuBackendLowerer METAL = new UnsupportedGpuBackendLowerer(GpuBackendTarget.METAL);
    private static final GpuBackendLowerer UNKNOWN = new UnsupportedGpuBackendLowerer(GpuBackendTarget.UNKNOWN);

    private GpuBackendLowerers() {
    }

    public static GpuBackendLowerer forTarget(GpuBackendTarget backendTarget) {
        return switch (backendTarget == null ? GpuBackendTarget.UNKNOWN : backendTarget) {
            case OPENCL -> OPENCL;
            case CUDA -> CUDA;
            case VULKAN -> VULKAN;
            case METAL -> METAL;
            case UNKNOWN -> UNKNOWN;
        };
    }
}
