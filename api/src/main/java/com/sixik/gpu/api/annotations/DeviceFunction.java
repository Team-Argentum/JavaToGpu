package com.sixik.gpu.api.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Вспомогательная функция GPU-кода (обычная функция OpenCL C), вызываемая из {@link Kernel} и других
 * device-функций. Рекурсия запрещена. Функции, достижимые из ядра, попадают в модуль IR транзитивно
 * (REWORK_ARCHITECTURE.md, 5.6); аннотация фиксирует намерение и позволяет фронтенду проверять тело как GPU-код.
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
public @interface DeviceFunction {
}
