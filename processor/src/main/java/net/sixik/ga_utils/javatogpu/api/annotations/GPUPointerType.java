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
     * Declared OpenCL address space for the pointer wrapper.
     *
     * <p>The default {@code PRIVATE} matches the existing scalar-by-reference helper wrappers such as
     * {@code FloatPtr}. Address-space-aware wrappers such as a future {@code GlobalFloatPtr} can declare
     * {@code GLOBAL}, {@code CONSTANT}, or {@code LOCAL} here.
     */
    GPUPointerAddressSpace addressSpace() default GPUPointerAddressSpace.PRIVATE;

    /**
     * Backends that recognize this pointer wrapper.
     */
    GpuBackendTarget[] backends() default {GpuBackendTarget.OPENCL};

    /**
     * Java instance methods that should be treated as pointer arithmetic.
     *
     * <p>The defaults cover the normal address-space pointer surface exposed by the built-in pointer wrappers.
     * Custom pointer wrappers can override this list when they want a smaller or different API.
     */
    GPUPointerOperator[] operators() default {
            @GPUPointerOperator(method = "add", operator = "+"),
            @GPUPointerOperator(method = "sub", operator = "-")
    };
}
