package net.sixik.ga_utils.javatogpu.api.annotations;

/**
 * Declares that an array kernel parameter lives in the OpenCL {@code __constant} address space.
 *
 * <pre>{@code
 * @GPU
 * static void kernel(@GPUConstant float[] weights, @GPUGlobal float[] output) {
 *     output[0] = weights[0];
 * }
 * }</pre>
 */
public @interface GPUConstant {
}
