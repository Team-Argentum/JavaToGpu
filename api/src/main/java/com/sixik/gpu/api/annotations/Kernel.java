package com.sixik.gpu.api.annotations;

import com.sixik.gpu.api.enums.InlinePolicy;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Точка входа GPU-кода (OpenCL {@code __kernel}). Метод должен быть {@code static void}; нарушение подмножества
 * языка - ошибка компиляции с кодом {@code GPU####} (REWORK_ARCHITECTURE.md, 3.3).
 *
 * <p>Элементы - директивы ядра; они превращаются в атрибуты функции IR. Вызов метода из обычного Java-кода
 * запускает его на устройстве (тело подменяет weaver), а исходное тело сохраняется как JVM-реализация.</p>
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
public @interface Kernel {

    /**
     * Политика встраивания функции.
     *
     * @return политика встраивания; по умолчанию {@link InlinePolicy#DEFAULT}
     */
    InlinePolicy inline() default InlinePolicy.DEFAULT;

    /**
     * Уровень оптимизации функции.
     *
     * @return уровень оптимизации {@code 0..3}; по умолчанию 0 (оптимизатора в v1 нет)
     */
    int optLevel() default 0;

    /**
     * Сокращение для {@code @WorkGroup(x = workgroup)}: одномерный обязательный размер work-group.
     * Совместное использование с {@link WorkGroup} - ошибка.
     *
     * @return размер work-group по оси X либо 0, если не задан
     */
    int workgroup() default 0;

    /**
     * Возможности устройства, без которых ядро не может работать.
     *
     * @return имена требуемых возможностей устройства ({@code FP64}, {@code INT64_ATOMICS}, ...); если устройство
     * их не поддерживает, берётся вариант {@link #fallback()}
     */
    String[] required() default {};

    /**
     * Запасной вариант реализации.
     *
     * @return имя другого метода с той же сигнатурой - вариант реализации, который используется, когда
     * {@link #required()} не выполнено; пустая строка - варианта нет
     */
    String fallback() default "";
}
