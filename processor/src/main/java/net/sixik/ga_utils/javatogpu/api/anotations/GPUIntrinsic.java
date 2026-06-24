package net.sixik.ga_utils.javatogpu.api.anotations;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a built-in GPU intrinsic mapping.
 *
 * <p>This annotation is mainly used by API/provider code rather than ordinary kernel code. It tells the processor that a
 * Java method should not be translated as a helper call but instead mapped directly to a backend intrinsic.
 *
 * <p>Examples:
 *
 * <pre>{@code
 * @GPUIntrinsic(name = "sin")
 * public static float sin(float value) {
 *     return (float) Math.sin(value);
 * }
 *
 * @GPUIntrinsic(code = "(({0}) - floor({0}))")
 * public static float fract(float value) {
 *     return value - (float) Math.floor(value);
 * }
 *
 * @GPUIntrinsic(operator = "+")
 * public Float4 add(Float4 other) {
 *     return new Float4(x + other.x, y + other.y, z + other.z, w + other.w);
 * }
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface GPUIntrinsic {

    /**
     * Backend intrinsic name such as {@code sin}, {@code get_global_id} or {@code barrier}.
     */
    String name() default "";

    /**
     * Inline backend expression template.
     *
     * <p>Arguments may be referenced as {@code {0}}, {@code {1}}, and so on.
     */
    String code() default "";

    /**
     * Operator form for concise intrinsic mappings.
     *
     * <p>Examples: {@code +}, {@code -}, {@code *}, {@code /}, {@code %}, {@code &}, {@code |}, {@code ^}.
     * Instance methods map to expressions such as {@code ({this} + {0})}; static binary methods map to
     * {@code ({0} + {1})}.
     */
    String operator() default "";

    /**
     * Backends for which this intrinsic mapping is valid.
     */
    GpuBackendTarget[] backends() default {GpuBackendTarget.OPENCL};
}
