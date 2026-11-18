package com.sixik.gpu.gradle;

import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;

/**
 * Настройки плагина, блок {@code gpu { ... }} в {@code build.gradle}.
 *
 * <pre>{@code
 * gpu {
 *     version = '0.2.0'          // версия библиотек (по умолчанию - версия самого плагина)
 *     group = 'io.github.deussixik'
 *     autoDependencies = true    // подключать библиотеки и annotation processor
 *     weave = true               // подменять тела @Kernel-методов после компиляции
 *     strictBarriers = false     // true: barrier под расходящимся управлением - ошибка компиляции (GPU0131), а не предупреждение
 *     frontend = 'java'          // 'java' - annotation processor; 'asm' - IR из class-файлов после компиляции
 *     backend = 'ffm'            // драйвер: 'ffm' (Java 25), 'lwjgl' (Java 17, LWJGL приложения), 'none' - добавит сборка
 * }
 * }</pre>
 */
public abstract class JavaToGpuExtension {

    /** @return версия библиотек, которые плагин подключает как зависимости */
    public abstract Property<String> getVersion();

    /** @return группа координат библиотек */
    public abstract Property<String> getGroup();

    /** @return {@code true} - плагин сам добавляет зависимости и annotation processor; {@code false} - это делает сборка */
    public abstract Property<Boolean> getAutoDependencies();

    /** @return {@code true} - после компиляции тела {@code @Kernel}-методов подменяются (weave-шаг) */
    public abstract Property<Boolean> getWeave();

    /**
     * @return {@code true} - процессор получает {@code -Ajavatogpu.strictBarriers=true}: {@code barrier()} под управлением,
     *         зависящим от {@code getGlobalId/getLocalId}, становится ошибкой {@code GPU0131}
     */
    public abstract Property<Boolean> getStrictBarriers();

    /**
     * @return фронтенд: {@code 'java'} (по умолчанию) - annotation processor {@code frontend-java} при компиляции;
     *         {@code 'asm'} - {@code frontend-asm} читает готовые class-файлы в {@code doLast} задачи компиляции
     *         (device-функции из jar без исходников, байткод не только javac; REWORK_ARCHITECTURE.md, раздел 17)
     */
    public abstract Property<String> getFrontend();

    /**
     * @return бэкенд драйвера OpenCL, который подключается вместе с {@code runtime}: {@code 'ffm'} (по умолчанию) -
     *         {@code runtime-opencl-ffm} (FFM, Java 25); {@code 'lwjgl'} - {@code runtime-opencl-lwjgl} (Java 17, ядро LWJGL
     *         и его нативы даёт приложение, например Minecraft); {@code 'none'} - без бэкенда (его добавляет сборка)
     */
    public abstract Property<String> getBackend();

    /** @return дополнительные аргументы JVM для задач {@code test} и {@code run} (помимо {@code --enable-native-access}) */
    public abstract ListProperty<String> getExtraJvmArgs();
}
