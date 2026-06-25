package net.sixik.ga_utils.javatogpu.api.annotations;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a Java wrapper class as a backend scalar alias such as {@code uint} or {@code ulong}.
 *
 * <p>This keeps unsigned-like wrappers declarative in the same style as {@link GPUVectorType} and
 * {@link GPUPointerType}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface GPUScalarAliasType {

    /**
     * Backend/native scalar spelling, for example {@code uchar}, {@code ushort}, {@code uint}, or {@code ulong}.
     */
    String backendType();

    /**
     * Java scalar type used to store the raw value bits inside the wrapper.
     */
    String valueType();

    /**
     * Backends that recognize this scalar alias wrapper.
     */
    GpuBackendTarget[] backends() default {GpuBackendTarget.OPENCL};
}
