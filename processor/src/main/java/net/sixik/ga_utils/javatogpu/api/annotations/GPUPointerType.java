package net.sixik.ga_utils.javatogpu.api.annotations;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a Java wrapper class as a GPU pointer-like helper type.
 *
 * <p>Examples include {@code FloatPtr}, {@code IntPtr}, and custom pointer wrappers added later by API authors. The
 * current pipeline uses {@link #valueType()} to understand which scalar the wrapper points to.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface GPUPointerType {

    /**
     * Java scalar type referenced by the wrapper, for example {@code int}, {@code float}, or {@code double}.
     */
    String valueType();

    /**
     * Backends that recognize this pointer wrapper.
     */
    GpuBackendTarget[] backends() default {GpuBackendTarget.OPENCL};
}
