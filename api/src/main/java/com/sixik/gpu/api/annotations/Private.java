package com.sixik.gpu.api.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Параметр-массив {@link DeviceFunction} в private-памяти work-item (OpenCL {@code __private}): функция принимает
 * массив, созданный в теле вызывающего кода ({@code int[] scratch = new int[16];}), и работает с ним по ссылке -
 * записи видны вызывающему. Парна {@link Global} и {@link Local}: без аннотации параметр-массив считается
 * {@code __global}, и передача private-массива даёт {@code GPU0121}.
 *
 * <pre>{@code
 * @DeviceFunction
 * static int push(@Private int[] stack, int top, int v) {
 *     stack[top] = v;
 *     return top + 1;
 * }
 * }</pre>
 *
 * <p>Только для параметров-массивов device-функций: параметр ядра в private-памяти невозможен (правило IR-M04),
 * такие случаи - {@code GPU0123}. Длина массива передаётся вместе с ним, {@code stack.length} работает.</p>
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.PARAMETER)
public @interface Private {
}
