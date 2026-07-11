package net.sixik.ga_utils.javatogpu.api.annotations;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.api.GpuDeviceClassTarget;
import net.sixik.ga_utils.javatogpu.api.GpuVendorTarget;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares portable runtime compatibility constraints for one GPU method.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.CLASS)
public @interface GPUDeviceConstraint {

    GpuBackendTarget[] backends() default {};

    GpuVendorTarget[] vendors() default {};

    GpuDeviceClassTarget[] deviceClasses() default {};

    String[] requiredFeatures() default {};
}
