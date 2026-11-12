package com.sixik.gpu.test;

import com.sixik.gpu.runtime.GpuContext;
import com.sixik.gpu.runtime.JavaToGpu;
import com.sixik.gpu.test.KernelSample.Leg;
import com.sixik.gpu.weaver.KernelCompiler;
import com.sixik.gpu.weaver.Weaver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link GpuAssert} на реальном устройстве для ядер с {@code fp=contract} и {@code fp=native}: «быстрые» ядра проходят
 * со своими допусками без явного {@code ulps}, строгое деление на устройстве с
 * {@code CL_FP_CORRECTLY_ROUNDED_DIVIDE_SQRT} - побитово как JVM. Без драйвера тест пропускается. Запуск:
 * {@code ./gradlew :test-support:deviceTest}.
 */
@Tag("device")
class GpuAssertFpDeviceTest {

    private static final String SOURCE = """
            package demo;

            import com.sixik.gpu.api.Gpu;
            import com.sixik.gpu.api.annotations.*;

            public class FpDev {

                @Kernel(compilationProperties = "fp=contract")
                public static void fused(@Global float[] out, @Global float[] a, @Global float[] b, float c) {
                    int g = Gpu.getGlobalId(0);
                    float p = a[g] * b[g];
                    out[g] = p + c;
                }

                @Kernel(compilationProperties = "fp=native,contract")
                public static void wave(@Global float[] out, @Global float[] x) {
                    int g = Gpu.getGlobalId(0);
                    float v = x[g];
                    out[g] = Gpu.sin(v) * Gpu.cos(v) + Gpu.sqrt(v * v + 1.0f);
                }

                @Kernel
                public static void divide(@Global float[] out, @Global float[] a, @Global float[] b) {
                    int g = Gpu.getGlobalId(0);
                    out[g] = a[g] / b[g] + Gpu.sqrt(a[g] < 0 ? -a[g] : a[g]);
                }

                @Kernel(compilationProperties = "fp=finite,nsz,reciprocal")
                public static void clampNorm(@Global float[] out, @Global float[] a, @Global float[] b) {
                    int g = Gpu.getGlobalId(0);
                    float x = a[g];
                    float y = b[g];
                    float hi = Gpu.max(x, y);
                    float lo = Gpu.min(x, y);
                    out[g] = (hi - lo) * (1.0f / Gpu.sqrt(x * x + y * y + 1.0f));
                }
            }
            """;

    @AfterEach
    void reset() {
        JavaToGpu.resetDefault();
    }

    private static float[] random(int n, int seed, float scale) {
        Random r = new Random(seed);
        float[] a = new float[n];
        for (int i = 0; i < n; i++) {
            a[i] = (r.nextFloat() - 0.5f) * scale;
        }
        return a;
    }

    @Test
    void fastKernelsPassWithTheirTolerances(@TempDir Path dir) throws Exception {
        Assumptions.assumeTrue(JavaToGpu.defaultContext().isPresent(), "no OpenCL device");
        GpuContext ctx = JavaToGpu.defaultContext().get();
        KernelCompiler.Compiled r = KernelCompiler.compile(dir, SOURCE);
        assertTrue(r.success(), r.diagnostics());
        Weaver.weaveTree(dir);
        int n = 4096;
        try (URLClassLoader loader = new URLClassLoader(new URL[]{dir.toUri().toURL()}, getClass().getClassLoader())) {
            Class<?> c = loader.loadClass("demo.FpDev");
            GpuAssert.Report fused = GpuAssert.sameResult(c, "fused", KernelSample.named("fma")
                    .args(() -> new Object[]{new float[n], random(n, 1, 1000f), random(n, 2, 1000f), 0.1f}).build(), ctx);
            System.out.println(fused);
            assertTrue(fused.ranOn(Leg.DEVICE));
            assertTrue(fused.compiledReference() || !ctx.device().hardwareFma(false));

            GpuAssert.Report wave = GpuAssert.sameResult(c, "wave", KernelSample.named("wave")
                    .args(() -> new Object[]{new float[n], random(n, 3, 60f)}).build(), ctx);
            System.out.println(wave + " " + ctx.nativeMath().describe());
            assertTrue(wave.ranOn(Leg.DEVICE));

            GpuAssert.Report div = GpuAssert.sameResult(c, "divide", KernelSample.named("div")
                    .args(() -> new Object[]{new float[n], random(n, 4, 1e6f), random(n, 5, 1e-3f)}).build(), ctx);
            System.out.println(div);
            if (ctx.device().exactDivideSqrt()) {
                assertEquals(0, div.ulps(), "a strict kernel divides exactly like the JVM");
            }

            String src = ctx.compile(JavaToGpu.loadModule(c, "clampNorm")).value().artifact().source();
            assertTrue(src.contains("fmax(") && src.contains("fmin(") && src.contains("rsqrt("), src);
            GpuAssert.Report norm = GpuAssert.sameResult(c, "clampNorm", KernelSample.named("norm")
                    .args(() -> new Object[]{new float[n], random(n, 6, 100f), random(n, 7, 100f)}).build(), ctx);
            System.out.println(norm);
            assertTrue(norm.compiledReference());
        }
    }
}
