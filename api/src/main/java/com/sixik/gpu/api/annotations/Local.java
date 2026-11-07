package com.sixik.gpu.api.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Локальная память work-group (OpenCL {@code __local}): общая для всех work-item одной группы и живёт, пока
 * выполняется группа. Парна {@link Global} (глобальная память) и {@link Constant} (константы).
 *
 * <p>Два места применения:</p>
 * <ul>
 *   <li><b>Локальная переменная ядра</b> - массив фиксированного размера, например
 *   {@code @Local float[] tile = new float[256];}. Размер - константа (или специализационная константа);
 *   объявление допустимо только в {@link Kernel}, на верхнем уровне тела (правило IR-M02), в IR это
 *   {@code local_array_decl}. Инициализатор задаёт только тип и размер: содержимое после объявления не определено,
 *   его заполняют work-item'ы с синхронизацией через {@code barrier}.</li>
 *   <li><b>Параметр {@link Kernel}</b> - {@code __local}-буфер; размер задаётся при запуске (правило IR-M04,
 *   REWORK_ARCHITECTURE.md, 8.2).</li>
 * </ul>
 *
 * <p>Ядро с локальной памятью использует синхронизацию группы ({@code uses_group_sync}) и не выполняется как
 * JVM-fallback: оно требует устройства (REWORK_ARCHITECTURE.md, 3.5); для проверки без железа служит
 * IR-интерпретатор. Аннотация на локальной переменной читается фронтендом из исходника - javac не записывает такие
 * аннотации в class-файл.</p>
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.LOCAL_VARIABLE, ElementType.PARAMETER})
public @interface Local {
}
