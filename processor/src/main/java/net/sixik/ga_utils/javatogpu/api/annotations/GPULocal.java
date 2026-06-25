package net.sixik.ga_utils.javatogpu.api.annotations;

/**
 * Declares that an array kernel parameter lives in the OpenCL {@code __local} address space.
 *
 * <p>This is typically used for work-group scratch memory.
 *
 * <pre>{@code
 * @GPU
 * static void kernel(@GPUGlobal float[] input, @GPULocal float[] scratch, @GPUGlobal float[] output) {
 *     int id = GPU.get_global_id(0);
 *     int lid = GPU.get_local_id(0);
 *     scratch[lid] = input[id];
 *     GPU.barrier(GPU.CLK_LOCAL_MEM_FENCE);
 *     output[id] = scratch[lid];
 * }
 * }</pre>
 */
public @interface GPULocal {
}
