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
 *
 * <p>Empty selector arrays mean "no restriction" for that selector category. Runtime selection treats declared
 * constraints as hard requirements and rejects incompatible backend/device candidates before compilation.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.CLASS)
public @interface GPUDeviceConstraint {

    /**
     * Supported backend families. Leave empty when any backend may lower this method.
     */
    GpuBackendTarget[] backends() default {};

    /**
     * Supported hardware/runtime vendors. Leave empty when vendor is not relevant.
     */
    GpuVendorTarget[] vendors() default {};

    /**
     * Supported device classes such as discrete GPU, integrated GPU, or CPU backend device.
     */
    GpuDeviceClassTarget[] deviceClasses() default {};

    /**
     * Required runtime/backend features, for example {@code fp64}.
     */
    String[] requiredFeatures() default {};
}
