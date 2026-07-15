package net.sixik.ga_utils.javatogpu.api.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares method-level optimization policy for runtime IR optimizer passes.
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
     * Enables method-level optimizer participation metadata for this method.
     */
    boolean enabled() default true;

    /**
     * Optional profile hint, for example {@code review}, {@code balanced}, or a project-defined profile name.
     */
    String profile() default "";

    /**
     * Allows future fast-math IR rewrites when the active optimizer profile and evidence gates also permit them.
     */
    boolean fastMath() default false;

    /**
     * Optional allow-list of optimizer families such as {@code clamp}, {@code step}, {@code mix}, or {@code mad-fma}.
     */
    String[] enabledFamilies() default {};

    /**
     * Optional deny-list of optimizer families. Deny-list entries take precedence over enabled families.
     */
    String[] disabledFamilies() default {};

    /**
     * Requests a runtime optimizer journal when the host runtime exposes a journaling path.
     */
    boolean journal() default false;

    /**
     * Requests before/after optimizer artifact dumps when supported by the host runtime.
     */
    boolean dumpArtifacts() default false;

    /**
     * Records production-selection intent only; it does not bypass proof, approval, rollback, or runtime gates.
     */
    boolean productionIntent() default false;

    /**
     * Allows future vendor-specific optimizer proposals when device evidence and runtime gates also allow them.
     */
    boolean vendorAdaptation() default false;

    /**
     * Optional vectorization preference, for example {@code auto}, {@code prefer}, or {@code avoid}.
     */
    String vectorization() default "auto";

    /**
     * Allows future resource/register-pressure shaping proposals when evidence gates also permit them.
     */
    boolean resourceShaping() default false;
}
