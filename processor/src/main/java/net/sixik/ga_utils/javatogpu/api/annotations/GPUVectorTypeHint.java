package net.sixik.ga_utils.javatogpu.api.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a preferred vector type hint for a GPU entry method.
 *
 * <p>This is the portable JavaToGpu form of backend-specific attributes such as OpenCL {@code vec_type_hint(T)}.
 * The hint is advisory and must not be treated as a correctness contract.</p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface GPUVectorTypeHint {

    /**
     * Backend-facing vector type token, for example {@code float4} or {@code int4}.
     */
    String value();
}
