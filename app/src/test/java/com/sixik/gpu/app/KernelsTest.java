package com.sixik.gpu.app;

import com.sixik.gpu.runtime.JavaToGpu;
import com.sixik.gpu.runtime.device.FakeDeviceApi;
import com.sixik.gpu.test.GpuAssert;
import com.sixik.gpu.test.KernelSample;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static com.sixik.gpu.test.KernelSample.Leg.INTERPRETER;
import static com.sixik.gpu.test.KernelSample.Leg.JVM;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Проверяет сборку настоящего ядра, его JVM-fallback и соответствие IR исходному коду. */
class KernelsTest {

    /** Освобождает процессный контекст и настройки между тестами. */
    @AfterEach
    void resetRuntime() {
        JavaToGpu.resetDefault();
    }

    /**
     * Проверяет обычный вызов подменённого ядра без зависимости от драйвера и размера группы.
     *
     * @param size положительная длина массивов, включая единицу и нечётный размер
     */
    @ParameterizedTest
    @ValueSource(ints = {1, 17, 1024})
    void vectorAdd_noDevice_matchesExpected(int size) {
        JavaToGpu.configureDefault(builder -> builder.api(new FakeDeviceApi()).ignoreEnvironment());
        float[] left = new float[size];
        float[] right = new float[size];
        float[] expected = new float[size];
        for (int i = 0; i < size; i++) {
            left[i] = i - 8;
            right[i] = 0.5f;
            // math(a, 0.5) = (a > 0 ? a : 0) + 0.5 + CONST для этих конечных значений.
            expected[i] = (left[i] > 0 ? 2 * left[i] : left[i]) + 1f + Kernels.CONST;
        }
        float[] output = new float[size];
        Kernels.vectorAdd(left, right, output);
        // Эти целые и половины точно представимы в float: погрешность равна нулю.
        assertArrayEquals(expected, output, 0.0f);
    }

    /** Проверяет IR-ресурс и совпадение JVM с интерпретатором на границах IEEE 754. */
    @Test
    void vectorAdd_specialValues_agreesWithInterpreter() {
        KernelSample sample = KernelSample.named("float-boundaries")
                .args(() -> new Object[]{
                        new float[]{0f, -4f, Float.MIN_VALUE, Float.MAX_VALUE, Float.NaN, Float.POSITIVE_INFINITY},
                        new float[]{0f, 1.5f, Float.MIN_VALUE, Float.MAX_VALUE, 1f, Float.NEGATIVE_INFINITY},
                        new float[6]})
                .expected(null, null, new float[]{50f, 49f, 50f, Float.POSITIVE_INFINITY, Float.NaN, Float.NaN})
                .only(JVM, INTERPRETER)
                .build();
        GpuAssert.Report report = GpuAssert.sameResult(Kernels.class, "vectorAdd", sample);
        assertTrue(report.ranOn(JVM) && report.ranOn(INTERPRETER), report.toString());
    }

    /** Проверяет отказ до инициализации runtime при недопустимых параметрах командной строки. */
    @Test
    void main_invalidSize_rejectsArguments() {
        assertThrows(IllegalArgumentException.class, () -> Main.main(new String[]{"0"}));
        assertThrows(IllegalArgumentException.class, () -> Main.main(new String[]{"-1"}));
        assertThrows(IllegalArgumentException.class, () -> Main.main(new String[]{"abc"}));
        assertThrows(IllegalArgumentException.class, () -> Main.main(new String[]{"1", "2"}));
    }
}
