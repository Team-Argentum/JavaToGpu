package com.sixik.gpu.api.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Параметр {@link Kernel}, размещаемый в глобальной памяти устройства (OpenCL {@code __global}).
 *
 * <p>С {@link #constant()} {@code = true}: массив размещается в {@code __constant} (только чтение; рантайм сверяет
 * размер с {@code CL_DEVICE_MAX_CONSTANT_BUFFER_SIZE}); скаляр передаётся по значению и означает «uniform без
 * мутации» - присваивание такому параметру в ядре запрещено (REWORK_ARCHITECTURE.md, 3.B).</p>
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.PARAMETER)
public @interface Global {

    /**
     * Признак константной памяти.
     *
     * @return {@code true} - константная память / неизменяемый uniform-параметр
     */
    boolean constant() default false;
}
