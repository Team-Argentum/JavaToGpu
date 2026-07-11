package net.sixik.ga_utils.javatogpu.api.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares the required work-group size for a GPU entry method.
 *
 * <p>This is the portable JavaToGpu form of backend-specific attributes such as OpenCL
 * {@code reqd_work_group_size(x, y, z)}. Backend lowerers are responsible for translating this intent into the
 * selected backend's metadata model.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface GPUWorkGroupSize {

    /**
     * Required work-group size in the X dimension.
     */
    int x() default 1;

    /**
     * Required work-group size in the Y dimension.
     */
    int y() default 1;

    /**
     * Required work-group size in the Z dimension.
     */
    int z() default 1;
}
