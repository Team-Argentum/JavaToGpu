package com.sixik.gpu.api.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Заглушка {@link DeviceFunction}, чью реализацию даёт сгенерированный во время работы код (FRONTEND_ASM_RUNTIME.md,
 * 18.2-S2): библиотека в jar вызывает функцию, которой при сборке jar ещё нет. Тело заглушки фронтенд не читает;
 * {@code AsmJit} заменяет вызов вызовом цели из таблицы {@code Links} (дескрипторы совпадают). Вызов без связи и вызов
 * из {@code frontend-java}, который не связывает, - {@code GPU0309}.
 *
 * <pre>{@code
 * @DeviceFunction @Extern
 * public static double erosion(int bx, int by, int bz) {
 *     throw new IllegalStateException("linked at runtime");
 * }
 * }</pre>
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
public @interface Extern {
}
