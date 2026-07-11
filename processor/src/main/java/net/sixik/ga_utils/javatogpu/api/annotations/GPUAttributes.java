package net.sixik.ga_utils.javatogpu.api.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Container annotation that allows multiple backend-specific {@link GPUAttribute} entries on one element.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
public @interface GPUAttributes {

    /**
     * Backend-specific raw attributes or qualifiers attached to the same source element.
     */
    GPUAttribute[] value();
}
