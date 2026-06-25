package net.sixik.ga_utils.javatogpu.api.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a Java class as a GPU vector type that should map to an OpenCL vector value.
 *
 * <p>This annotation is intended for API/provider types such as {@code Float4}, {@code Double3}, or custom additions
 * like {@code UInt3}. Once such a class is visible on the classpath, the JavaToGpu type layer can discover it without
 * hardcoding another entry in the vector registry.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface GPUVectorType {

    /**
     * Backend type name, for example {@code float4}, {@code double3}, or {@code uint3}.
     */
    String openClType();

    /**
     * Java scalar type used for every component, for example {@code float}, {@code double}, or {@code int}.
     */
    String componentType();

    /**
     * Public component field names exposed by the Java-side vector wrapper.
     */
    String[] fields();

    /**
     * Explicit storage width in scalar lanes.
     *
     * <p>Leave this as {@code 0} for the default OpenCL behavior where {@code *3} vectors occupy four scalar lanes.
     */
    int storageWidth() default 0;
}
