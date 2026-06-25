package net.sixik.ga_utils.javatogpu.api.annotations;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class whose {@link GPUIntrinsic}-annotated methods may be reused from other compilations.
 *
 * <p>This works the same way as {@link CCodeLibrary}, but for intrinsic declarations instead of helper functions.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface GPUIntrinsicLibrary {

    /**
     * Backends for which metadata from this reusable intrinsic library is valid.
     */
    GpuBackendTarget[] backends() default {GpuBackendTarget.OPENCL};
}
