package net.sixik.ga_utils.javatogpu.api.annotations;

/**
 * Declares that an array kernel parameter lives in the OpenCL {@code __global} address space.
 *
 * <p>This is the most common parameter annotation for input and output buffers.
 *
 * <pre>{@code
 * @GPU
 * static void kernel(@GPUGlobal float[] input, @GPUGlobal float[] output) {
 *     int id = GPU.get_global_id(0);
 *     output[id] = input[id] * 2.0f;
 * }
 * }</pre>
 */
public @interface GPUGlobal {

    /**
     * Marks a global buffer as read-only from the kernel point of view.
     *
     * <p>When {@code true}, the emitted parameter becomes {@code __global const T*}.
     */
    boolean constant() default false;
}
