package net.sixik.ga_utils.javatogpu.api.annotations;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;

/**
 * Marks a Java helper method that can be called from {@link GPU @GPU} kernels.
 *
 * <p>A helper may be expressed in one of two ways:
 *
 * <ul>
 *     <li>as a normal Java method body that is translated by the frontend;</li>
 *     <li>as native backend code through {@link #code()}.</li>
 * </ul>
 *
 * <p>Examples:
 *
 * <pre>{@code
 * @CCode
 * static float square(float value) {
 *     return value * value;
 * }
 *
 * @CCode(inline = true)
 * static float lerp(float a, float b, float t) {
 *     return a + (b - a) * t;
 * }
 *
 * @CCode(code = """
 *     return (*ptr) * scale;
 *     """)
 * static native float scaled(FloatPtr ptr, float scale);
 * }</pre>
 */
public @interface CCode {

    /**
     * Requests that the helper is emitted as {@code inline} in the generated backend source.
     *
     * <p>This is useful for tiny helpers that are intended to behave like macros or simple utility functions.
     */
    boolean inline() default false;

    /**
     * Raw backend code used instead of translating the Java body.
     *
     * <p>Leave this empty when the helper should be translated from Java. Set it when you want to provide the exact
     * emitted OpenCL/C-like body yourself.
     */
    String code() default "";

    /**
     * Backends on which this helper is allowed to participate.
     */
    GpuBackendTarget[] backends() default {GpuBackendTarget.OPENCL};

    /**
     * Optional backend capability/version guard understood by the emitter.
     *
     * <p>The current OpenCL emitter accepts values such as {@code OpenCL_3}, {@code OpenCL_3_0}, or
     * {@code OpenCL_2_1}. When set together with {@link #callback()}, the generated helper keeps the primary body for
     * newer environments and forwards to the callback helper otherwise.
     */
    String support() default "";

    /**
     * Fallback helper name used when {@link #support()} is not satisfied by the backend source environment.
     *
     * <p>The fallback helper must live in the same owner and currently must have the same parameter list and return
     * type as the guarded helper.
     */
    String callback() default "";
}
