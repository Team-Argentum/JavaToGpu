package net.sixik.ga_utils.javatogpu.api.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a Java class that should be emitted as an OpenCL {@code struct}.
 *
 * <p>Use scalar fields, vector fields or other {@code @GPUStruct} types. Regular Java constructors are allowed and are
 * useful because user code must still compile as Java before it is translated.
 *
 * <pre>{@code
 * @GPUStruct
 * static final class Vec2 {
 *     public float x;
 *     public float y;
 *
 *     Vec2(float x, float y) {
 *         this.x = x;
 *         this.y = y;
 *     }
 * }
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface GPUStruct {
}
