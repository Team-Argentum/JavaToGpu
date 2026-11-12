package tour;

import com.sixik.gpu.runtime.JavaToGpu;
import com.sixik.gpu.test.GpuAssert;
import com.sixik.gpu.test.KernelSample;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tour.hello.Hello;
import tour.local.BlockSum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Как проверять свои ядра: {@code GpuAssert.sameResult} исполняет ядро на JVM, в интерпретаторе IR и на устройстве
 * (если оно есть) и сравнивает результаты между собой и с ожидаемым. Ветвь устройства без драйвера пропускается -
 * тест проходит на любой машине; {@code Report} показывает, что реально выполнилось.
 */
class TourKernelsTest {

    @AfterEach
    void close() {
        JavaToGpu.resetDefault();
    }

    @Test
    void saxpyAgreesEverywhere() {
        var sample = KernelSample.named("saxpy")
                .args(() -> new Object[]{new float[]{1, 2, 3}, new float[]{10, 10, 10}, 2f})
                .expected(null, new float[]{12, 14, 16}, null)
                .build();
        var report = GpuAssert.sameResult(Hello.class, "saxpy", sample);
        assertTrue(report.ranOn(KernelSample.Leg.JVM));
        assertTrue(report.ranOn(KernelSample.Leg.INTERPRETER));
    }

    /** Ядро с барьером на JVM не исполняется, но интерпретатор IR проверяет его без устройства. */
    @Test
    void blockSumOnTheInterpreter() {
        int n = BlockSum.GROUP * 4;
        int[] in = new int[n];
        int[] want = new int[4];
        for (int i = 0; i < n; i++) {
            in[i] = i % 13;
            want[i / BlockSum.GROUP] += in[i];
        }
        var sample = KernelSample.named("block sums")
                .args(() -> new Object[]{in.clone(), new int[4]})
                .expected(null, want)
                .without(KernelSample.Leg.DEVICE)
                .build();
        var report = GpuAssert.sameResult(BlockSum.class, "groupSums", sample);
        assertTrue(report.ranOn(KernelSample.Leg.INTERPRETER));
        assertEquals(0, report.ulps());
    }
}
