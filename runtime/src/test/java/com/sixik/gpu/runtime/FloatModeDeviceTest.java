package com.sixik.gpu.runtime;

import com.sixik.gpu.weaver.KernelCompiler;
import com.sixik.gpu.weaver.Weaver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Флаг {@code fp=contract} на реальном устройстве, от Java-исходника до запуска: произведение, записанное в локаль, и
 * сложение с ним сливаются оптимизатором в явный {@code fma()}, и результат <b>побитово</b> равен {@link Math#fma}.
 * То же ядро без флага побитово равно Java-выражению {@code a * b + c} (драйвер не сливает сам: {@code FP_CONTRACT OFF}).
 * Без драйвера тест пропускается. Запуск: {@code ./gradlew :runtime:deviceTest}.
 */
@Tag("device")
class FloatModeDeviceTest {

    private static final String SOURCE = """
            package demo;

            import com.sixik.gpu.api.Gpu;
            import com.sixik.gpu.api.annotations.*;

            public class Fp {

                public static final String FAST = "fp=contract";

                @Kernel(compilationProperties = FAST)
                public static void fused(@Global float[] out, @Global float[] a, @Global float[] b, float c) {
                    int i = Gpu.getGlobalId(0);
                    float p = a[i] * b[i];
                    out[i] = p + c;
                }

                @Kernel
                public static void strict(@Global float[] out, @Global float[] a, @Global float[] b, float c) {
                    int i = Gpu.getGlobalId(0);
                    float p = a[i] * b[i];
                    out[i] = p + c;
                }
            }
            """;

    @AfterEach
    void reset() {
        JavaToGpu.resetDefault();
    }

    private static void call(Class<?> c, String method, Object... args) throws Exception {
        for (Method m : c.getDeclaredMethods()) {
            if (m.getName().equals(method)) {
                m.invoke(null, args);
                return;
            }
        }
        throw new IllegalArgumentException(method);
    }

    @Test
    void contractedKernelMatchesMathFmaBitForBit(@TempDir Path dir) throws Exception {
        JavaToGpu.configureDefault(b -> b.fallback(GpuContext.Fallback.NEVER));
        Assumptions.assumeTrue(JavaToGpu.defaultContext().isPresent(), "no OpenCL device");
        Assumptions.assumeTrue(JavaToGpu.defaultContext().get().device().hardwareFma(false),
                "the device has no hardware FMA: the optimizer does not contract there");

        KernelCompiler.Compiled r = KernelCompiler.compile(dir, SOURCE);
        assertTrue(r.success(), r.diagnostics());
        Weaver.weaveTree(dir);
        int n = 4096;
        float[] a = new float[n];
        float[] b = new float[n];
        Random rnd = new Random(7);
        for (int i = 0; i < n; i++) {
            a[i] = (rnd.nextFloat() - 0.5f) * 1000f;
            b[i] = (rnd.nextFloat() - 0.5f) * 1000f;
        }
        float c = 0.1f;
        float[] fmaRef = new float[n];
        float[] plainRef = new float[n];
        int differ = 0;
        for (int i = 0; i < n; i++) {
            fmaRef[i] = Math.fma(a[i], b[i], c);
            plainRef[i] = a[i] * b[i] + c;
            differ += Float.floatToRawIntBits(fmaRef[i]) != Float.floatToRawIntBits(plainRef[i]) ? 1 : 0;
        }
        assertTrue(differ > n / 10, "the inputs must tell fma and mul+add apart: " + differ);

        try (URLClassLoader loader = new URLClassLoader(new URL[]{dir.toUri().toURL()}, getClass().getClassLoader())) {
            Class<?> fp = loader.loadClass("demo.Fp");
            float[] fused = new float[n];
            float[] strict = new float[n];
            call(fp, "fused", fused, a, b, c);
            call(fp, "strict", strict, a, b, c);
            assertArrayEquals(fmaRef, fused, "fp=contract: one rounding, exactly Math.fma");
            assertArrayEquals(plainRef, strict, "strict kernel: two roundings, exactly the Java expression");
        }
    }
}
