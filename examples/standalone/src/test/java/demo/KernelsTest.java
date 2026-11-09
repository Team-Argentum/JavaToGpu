package demo;

import com.sixik.gpu.runtime.JavaToGpu;
import com.sixik.gpu.test.GpuAssert;
import com.sixik.gpu.test.KernelSample;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Проверяет опубликованный test-support, IR-ресурсы и weaving в main и test source sets. */
class KernelsTest {

    /** Освобождает контекст, если GpuAssert открыл устройство. */
    @AfterEach
    void close() {
        JavaToGpu.resetDefault();
    }

    /** Сверяет JVM и интерпретатор с независимо посчитанным результатом без устройства. */
    @Test
    void scale_matchesExpectedOnJvmAndInterpreter() throws NoSuchMethodException {
        assertNotNull(Kernels.class.getDeclaredMethod("scale$jvm", float[].class, float[].class, float.class));
        var sample = KernelSample.named("standalone").args(() -> new Object[]{new float[]{1, 2, 3, 4}, new float[4], 2f})
                .without(KernelSample.Leg.DEVICE).expected(null, new float[]{3, 5, 7, 9}, null).build();
        var report = GpuAssert.sameResult(Kernels.class, "scale", sample, null);
        assertTrue(report.ranOn(KernelSample.Leg.JVM));
        assertTrue(report.ranOn(KernelSample.Leg.INTERPRETER));
        assertEquals(0, report.ulps());
    }

    /** Подтверждает работу processor и weaver для ядра из test source set. */
    @Test
    void testSources_areProcessedAndWoven() throws NoSuchMethodException {
        assertNotNull(TestKernels.class.getDeclaredMethod("fill$jvm", int[].class));
        var sample = KernelSample.named("test kernel").args(() -> new Object[]{new int[4]})
                .without(KernelSample.Leg.DEVICE).expected((Object) new int[]{7, 7, 7, 7}).build();
        GpuAssert.sameResult(TestKernels.class, "fill", sample, null);
    }
}
