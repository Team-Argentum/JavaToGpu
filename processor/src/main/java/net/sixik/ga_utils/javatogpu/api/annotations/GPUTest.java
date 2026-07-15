package net.sixik.ga_utils.javatogpu.api.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a named validation fixture for one GPU method.
 *
 * <p>The annotation intentionally stores fixture references rather than Java objects. Future runtime tooling can load
 * those references, execute the method on several backend/device candidates, and compare the captured outputs.</p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.CLASS)
@Repeatable(GPUTests.class)
public @interface GPUTest {

    /** Stable case id. If omitted, tooling derives one from the method name and declaration order. */
    String id() default "";

    /** Input fixture references, in launch-parameter order when possible. */
    String[] inputs() default {};

    /** Expected output fixture references, in launch-parameter order when possible. */
    String[] expectedOutputs() default {};

    /** Optional tolerance expression, for example "1e-5" or "abs=1e-5,rel=1e-4". */
    String tolerance() default "";

    /** Free-form grouping labels for selection probes, smoke tests, or slow validation suites. */
    String[] tags() default {};

    /** Whether this fixture may be used as a quick backend/device selection probe. */
    boolean selectionProbe() default true;
}
