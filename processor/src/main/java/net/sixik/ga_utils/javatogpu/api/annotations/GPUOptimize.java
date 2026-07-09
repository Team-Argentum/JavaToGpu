package net.sixik.ga_utils.javatogpu.api.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares method-level optimization policy for future runtime IR optimizer passes.
 *
 * <p>The default is strict floating-point behavior. Enabling fast math allows future proof-backed optimizers to consider
 * transformations such as fused multiply-add, reassociation, interpolation folding, and other rewrites that may change
 * IEEE-754 rounding. This annotation records intent only; production mutation still requires runtime-equivalence and
 * promotion evidence.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface GPUOptimize {

    /**
     * Allows future fast-math IR rewrites when the active optimizer profile and evidence gates also permit them.
     */
    boolean fastMath() default false;
}
