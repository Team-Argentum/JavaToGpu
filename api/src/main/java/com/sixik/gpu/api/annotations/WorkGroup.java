package com.sixik.gpu.api.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Обязательный размер work-group ядра (OpenCL {@code reqd_work_group_size}); он же размер по умолчанию при
 * запуске. Размеры по умолчанию - 1, поэтому {@code @WorkGroup(x = 256)} задаёт одномерную группу.
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
public @interface WorkGroup {

    /**
     * Размер work-group по оси X.
     *
     * @return размер ({@code > 0})
     */
    int x() default 1;

    /**
     * Размер work-group по оси Y.
     *
     * @return размер ({@code > 0})
     */
    int y() default 1;

    /**
     * Размер work-group по оси Z.
     *
     * @return размер ({@code > 0})
     */
    int z() default 1;
}
