package net.sixik.ga_utils.javatogpu.api.annotations;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;

/**
 * Marks a class whose {@link CCode}-annotated methods may be reused from other compilations.
 *
 * <p>The annotation processor exports helper metadata for such classes, allowing another project or another module
 * to reference helpers without keeping their Java source in the same compilation unit.
 *
 * <pre>{@code
 * @CCodeLibrary
 * public final class MathHelpers {
 *     @CCode
 *     public static float square(float value) {
 *         return value * value;
 *     }
 * }
 * }</pre>
 */
public @interface CCodeLibrary {

    /**
     * Backends for which metadata from this reusable helper library is valid.
     */
    GpuBackendTarget[] backends() default {GpuBackendTarget.OPENCL};
}
