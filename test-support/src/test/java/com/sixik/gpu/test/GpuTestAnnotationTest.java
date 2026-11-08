package com.sixik.gpu.test;

import com.sixik.gpu.ir.interpreter.samples.IndexKernels;
import com.sixik.gpu.runtime.GpuContext;
import org.junit.jupiter.api.Tag;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code @GPUTest} как пользователь пишет его в своём проекте: обычный JUnit-тест, который сверяет JVM, интерпретатор
 * и (если есть драйвер) устройство.
 */
class GpuTestAnnotationTest {

    private static float[] ramp() {
        float[] a = new float[64];
        for (int i = 0; i < a.length; i++) {
            a[i] = i * 0.1f;
        }
        return a;
    }

    /** Устройство необязательно: без драйвера ветвь DEVICE пропускается, тест проходит по JVM и интерпретатору. */
    @GPUTest
    void sineAgreesOnEveryAvailableLeg() {
        // sine(input, output): размер запуска - длина первого массива
        GpuAssert.Report r = GpuAssert.sameResult(IndexKernels.class, "sine", ramp(), new float[64]);
        assertTrue(r.ranOn(KernelSample.Leg.JVM) && r.ranOn(KernelSample.Leg.INTERPRETER), r.toString());
    }

    /** Устройство обязательно: без драйвера тест пропускается (assumption), а не проходит молча. */
    @GPUTest(requireDevice = true)
    @Tag("device")
    void sineRunsOnTheDevice(GpuContext ctx) {
        assertNotNull(ctx);
        GpuAssert.Report r = GpuAssert.sameResult(IndexKernels.class, "sine",
                KernelSample.of("sine", ramp(), new float[64]), ctx);
        assertTrue(r.ranOn(KernelSample.Leg.DEVICE), r.toString());
    }
}
