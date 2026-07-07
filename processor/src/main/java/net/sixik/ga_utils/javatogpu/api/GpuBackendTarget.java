package net.sixik.ga_utils.javatogpu.api;

/**
 * Backend target understood by reusable metadata annotations such as
 * {@link net.sixik.ga_utils.javatogpu.api.annotations.CCodeLibrary} and
 * {@link net.sixik.ga_utils.javatogpu.api.annotations.GPUIntrinsicLibrary}.
 *
 * <p>At the moment the main production path is {@link #OPENCL}. The other targets exist to keep API contracts
 * backend-aware while additional backend support is developed incrementally.
 */
public enum GpuBackendTarget {
    /**
     * Unknown or custom backend target.
     */
    UNKNOWN,

    /**
     * OpenCL backend.
     */
    OPENCL,

    /**
     * CUDA backend.
     */
    CUDA,

    /**
     * Vulkan/SPIR-V backend.
     */
    VULKAN,

    /**
     * Metal backend.
     */
    METAL
}
