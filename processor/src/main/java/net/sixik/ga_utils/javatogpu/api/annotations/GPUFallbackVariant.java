package net.sixik.ga_utils.javatogpu.api.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares one deterministic runtime variant of a logical GPU method.
 *
 * <p>Methods in the same group must expose the same launch ABI. Higher priorities are preferred after device and
 * backend compatibility checks pass. Ties are resolved by variant id and emitted method name.</p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.CLASS)
public @interface GPUFallbackVariant {

    String group();

    String id() default "";

    int priority() default 0;

    String compatibilityNote() default "";
}
