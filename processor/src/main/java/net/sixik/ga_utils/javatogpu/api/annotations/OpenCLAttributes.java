package net.sixik.ga_utils.javatogpu.api.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Attaches raw OpenCL attributes to an emitted method, struct or struct field.
 *
 * <p>This is intentionally low-level and forwards the provided strings almost directly into generated source.
 *
 * <pre>{@code
 * @OpenCLAttributes("reqd_work_group_size(16, 1, 1)")
 * @GPU
 * static void kernel(@GPUGlobal float[] output) {
 *     output[0] = 1.0f;
 * }
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.FIELD, ElementType.METHOD})
public @interface OpenCLAttributes {

    /**
     * Raw attribute names or invocations such as {@code packed}, {@code aligned(8)} or
     * {@code reqd_work_group_size(16, 1, 1)}.
     */
    String[] value();
}
