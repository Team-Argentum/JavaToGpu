package net.sixik.ga_utils.javatogpu.api.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Requests packed layout for a GPU struct when the selected backend supports explicit layout packing.
 *
 * <p>This is the portable JavaToGpu form of backend-specific attributes such as OpenCL {@code packed}.</p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface GPUPacked {
}
