package net.sixik.ga_utils.javatogpu.api.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Requests backend helper emission as an always-inline function when the selected backend supports it.
 *
 * <p>This is the portable JavaToGpu form of backend-specific attributes such as OpenCL {@code always_inline}.</p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface GPUAlwaysInline {
}
