package com.sixik.gpu.runtime;

import com.sixik.gpu.codegen.opencl.Target;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code fp=native} на реальном устройстве: калибровка {@code native_*}, сгенерированный текст и точность результата.
 * Ядро с флагом получает {@code native_*} ровно для функций, прошедших калибровку, и его ошибка не выходит за порог
 * калибровки; то же ядро без флага остаётся точным. Без драйвера тест пропускается. Запуск:
 * {@code ./gradlew :runtime:deviceTest}.
 */
@Tag("device")
class NativeMathDeviceTest {

    private static final String SOURCE = """
            package demo;

            import com.sixik.gpu.api.Gpu;
            import com.sixik.gpu.api.annotations.*;

            public class Nm {

                @Kernel(compilationProperties = "fp=native")
                public static void fast(@Global float[] out, @Global float[] x) {
                    int i = Gpu.getGlobalId(0);
                    out[i] = Gpu.sin(x[i]) + Gpu.cos(x[i]) + Gpu.sqrt(x[i] < 0 ? -x[i] : x[i]);
                }

                @Kernel
                public static void exact(@Global float[] out, @Global float[] x) {
                    int i = Gpu.getGlobalId(0);
                    out[i] = Gpu.sin(x[i]) + Gpu.cos(x[i]) + Gpu.sqrt(x[i] < 0 ? -x[i] : x[i]);
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
    void nativeKernelUsesTheCalibratedFunctions(@TempDir Path dir) throws Exception {
        Path dump = dir.resolve("dump");
        JavaToGpu.configureDefault(b -> b.fallback(GpuContext.Fallback.NEVER).dumpSources(dump));
        Assumptions.assumeTrue(JavaToGpu.defaultContext().isPresent(), "no OpenCL device");
        NativeMath nm = JavaToGpu.defaultContext().get().nativeMath();
        System.out.println(JavaToGpu.defaultContext().get().device().describe() + ": " + nm.describe());

        KernelCompiler.Compiled r = KernelCompiler.compile(dir, SOURCE);
        assertTrue(r.success(), r.diagnostics());
        Weaver.weaveTree(dir);
        int n = 4096;
        float[] x = new float[n];
        for (int i = 0; i < n; i++) {
            x[i] = (float) ((i - n / 2) * (NativeMath.TRIG_RANGE / (n / 2)));
        }
        float[] fast = new float[n];
        float[] exact = new float[n];
        try (URLClassLoader loader = new URLClassLoader(new URL[]{dir.toUri().toURL()}, getClass().getClassLoader())) {
            Class<?> c = loader.loadClass("demo.Nm");
            call(c, "fast", fast, x);
            call(c, "exact", exact, x);
        }

        String all;
        try (Stream<Path> files = Files.list(dump)) {
            all = files.map(p -> {
                try {
                    return Files.readString(p);
                } catch (java.io.IOException e) {
                    throw new java.io.UncheckedIOException(e);
                }
            }).reduce("", String::concat);
        }
        int used = (all.contains("native_sin(") ? Target.NATIVE_SIN : 0) | (all.contains("native_cos(") ? Target.NATIVE_COS : 0)
                | (all.contains("native_sqrt(") ? Target.NATIVE_SQRT : 0);
        assertEquals(nm.mask(), used, "exactly the calibrated functions are emitted as native_*");

        // допуск: пороги калибровки для разрешённых функций плюс ошибка точных (4 ulp sin/cos, 3 ulp sqrt) и сумм
        double tol = 2 * NativeMath.TRIG_MAX_ABS + 1e-4;
        double worst = 0;
        for (int i = 0; i < n; i++) {
            double ref = Math.sin(x[i]) + Math.cos(x[i]) + Math.sqrt(Math.abs(x[i]));
            worst = Math.max(worst, Math.abs(fast[i] - ref));
            assertEquals(ref, exact[i], 1e-5, "exact kernel at " + x[i]);
        }
        assertTrue(worst <= tol, "fp=native error " + worst + " > " + tol);
    }
}
