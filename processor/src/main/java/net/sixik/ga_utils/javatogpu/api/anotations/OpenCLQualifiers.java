package net.sixik.ga_utils.javatogpu.api.anotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Attaches raw OpenCL parameter qualifiers to emitted kernel/helper parameters.
 *
 * <p>This is primarily intended for pointer-like parameters such as {@code @GPUGlobal float[]} or pointer wrapper
 * helpers like {@code FloatPtr}. The current pipeline validates a practical subset, for example {@code const},
 * {@code restrict} and {@code volatile}.
 *
 * <pre>{@code
 * @GPU
 * static void kernel(
 *         @OpenCLQualifiers({"restrict"})
 *         @GPUGlobal(constant = true) float[] input,
 *         @OpenCLQualifiers({"restrict"})
 *         @GPUGlobal float[] output
 * ) {
 *     int id = GPU.get_global_id(0);
 *     output[id] = input[id] * 2.0f;
 * }
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface OpenCLQualifiers {

    /**
     * Raw qualifier names such as {@code const}, {@code restrict} or {@code volatile}.
     */
    String[] value();
}
