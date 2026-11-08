package com.sixik.gpu.test;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Тест GPU-ядра: обычный {@link Test} JUnit 5, в котором работает {@link GpuAssert#sameResult} (REWORK_ARCHITECTURE.md,
 * 9.6). Данные описываются кодом (генераторы, seed) или JSON ({@link KernelSample#fromJson}); никаких ресурсов-фикстур и
 * метаданных в IR у теста нет.
 *
 * <p>Тест выполняется на любой машине: ветви JVM и интерпретатор не требуют железа, ветвь устройства без драйвера
 * пропускается, и {@link GpuAssert.Report} это показывает. Метод может принять параметр
 * {@link com.sixik.gpu.runtime.GpuContext} - контекст по умолчанию; если устройства нет, тест пропускается.
 * {@link #requireDevice()} пропускает тест целиком, когда устройства нет; такие тесты помечают тегом
 * {@code @Tag("device")}, чтобы обычная сборка их не запускала.</p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Test
@ExtendWith(GpuTestExtension.class)
public @interface GPUTest {

    /**
     * @return {@code true} - пропустить тест, если на машине нет OpenCL-устройства
     */
    boolean requireDevice() default false;
}
