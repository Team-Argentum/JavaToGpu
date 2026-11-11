package com.sixik.gpu.api.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Свёртка на устройстве: обычный последовательный цикл, который рантайм исполняет параллельно и возвращает результат
 * (REWORK_ARCHITECTURE.md, 7.1). Метод - {@code static}, возвращает {@code int}, {@code long}, {@code float} или
 * {@code double}, а тело имеет вид
 * <pre>{@code
 * @Reduction
 * static int countPositive(GpuFloatArray values, int n) {
 *     int count = 0;                       // аккумулятор: начальное значение - любое выражение
 *     for (int i = 0; i < n; i++) {        // ровно такой цикл: i < end, i++
 *         if (values.get(i) > 0f) {
 *             count++;                     // аккумулятор меняется только как acc = acc op e
 *         }
 *     }
 *     return count;                        // сразу после цикла
 * }
 * }</pre>
 *
 * <p>До цикла допустимы объявления и вычисления без записи в память. Операция над аккумулятором - одна на всё тело:
 * {@code +=}, {@code -=}, {@code ++}, {@code --}, {@code acc = Gpu.min(acc, e)}, {@code acc = Gpu.max(acc, e)},
 * {@code &=}, {@code |=}, {@code ^=}. Итерации должны быть независимы: тело не пишет в память (кроме своих
 * private-массивов), не меняет переменные, объявленные до цикла, и не выходит из цикла через {@code break} или
 * {@code return}. Нарушение шаблона - ошибка компиляции.</p>
 *
 * <p>Вызов метода из обычного Java-кода исполняет цикл на устройстве: каждый work-item проходит свою часть итераций,
 * группы складывают значения деревом в local-памяти, хост сворачивает суммы групп. Без устройства вызов исполняет
 * исходное тело на JVM. Порядок сложения на устройстве другой, поэтому сумма {@code float/double} может отличаться
 * от последовательной в младших битах; целые совпадают точно.</p>
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
public @interface Reduction {
}
