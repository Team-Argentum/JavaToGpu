package net.sixik.ga_utils.javatogpu.api.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a preferred work-group size hint for a GPU entry method.
 *
 * <p>This is the portable JavaToGpu form of backend-specific hints such as OpenCL
 * {@code work_group_size_hint(x, y, z)}. Backends may use the hint when it maps cleanly to their metadata model, but
 * it is advisory and must not be treated as a required launch contract.</p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface GPUWorkGroupSizeHint {

    /**
     * Preferred work-group size in the X dimension.
     */
    int x() default 1;

    /**
     * Preferred work-group size in the Y dimension.
     */
    int y() default 1;

    /**
     * Preferred work-group size in the Z dimension.
     */
    int z() default 1;
}
