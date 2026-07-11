package net.sixik.ga_utils.javatogpu.api.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares portable alignment metadata for GPU structs and struct fields.
 *
 * <p>This is the portable JavaToGpu form of backend-specific attributes such as OpenCL {@code aligned(n)}.
 * Backend lowerers are responsible for translating this intent into the selected backend's layout metadata.</p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.FIELD})
public @interface GPUAligned {

    /**
     * Required alignment in bytes.
     */
    int value();
}
