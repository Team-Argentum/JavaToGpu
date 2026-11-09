package com.sixik.gpu.runtime;

import com.sixik.gpu.ir.Types;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Критерий M11 на реальном устройстве: подготовленный запуск {@code @Kernel}-метода по имени, повторные запуски с
 * изменяемыми данными и скаляром, static-таблица в {@link GpuBuffer} и итерации «пинг-понг» без передач через хост.
 * Результаты сверяются с расчётом на хосте. Без драйвера тест пропускается. Запуск: {@code ./gradlew :runtime:deviceTest}.
 */
@Tag("device")
class PreparedDeviceTest {

    private static final String SOURCE = """
            package demo;

            import com.sixik.gpu.api.Gpu;
            import com.sixik.gpu.api.annotations.*;

            public class Steps {

                /** out[g] = in[g] * k + table[g % 4]; table - static-аргумент. */
                @Kernel
                public static void affine(@Global int[] out, @Global int[] in, @Global(constant = true) int[] table, int k) {
                    int g = Gpu.getGlobalId(0);
                    out[g] = in[g] * k + table[g % 4];
                }

                /** Один шаг «пинг-понга»: out[g] = in[g] + in[(g + 1) % n] (с оборотом int). */
                @Kernel
                public static void smooth(@Global int[] out, @Global int[] in) {
                    int g = Gpu.getGlobalId(0);
                    out[g] = in[g] + in[(g + 1) % in.length];
                }
            }
            """;

    @AfterEach
    void reset() {
        JavaToGpu.resetDefault();
    }

    @Test
    void repeatedLaunchesWithChangingHostDataAndScalar(@TempDir Path dir) throws Exception {
        List<GpuEvent> events = new ArrayList<>();
        JavaToGpu.configureDefault(b -> b.listener(events::add).fallback(GpuContext.Fallback.NEVER));
        Assumptions.assumeTrue(JavaToGpu.defaultContext().isPresent(), "no OpenCL device");
        KernelCompiler.Compiled r = KernelCompiler.compile(dir, SOURCE);
        assertTrue(r.success(), r.diagnostics());
        assertEquals(2, Weaver.weaveTree(dir).kernels());
        try (URLClassLoader loader = new URLClassLoader(new URL[]{dir.toUri().toURL()}, getClass().getClassLoader())) {
            Class<?> steps = loader.loadClass("demo.Steps");
            int n = 4096;
            int[] table = {7, -3, 100, 5};
            int[] in = new int[n];
            int[] out = new int[n];
            Random rnd = new Random(2);
            try (PreparedLauncher p = JavaToGpu.prepare(steps, "affine", out, in, table, 3)) {
                for (int round = 0; round < 20; round++) {
                    for (int i = 0; i < n; i++) {
                        in[i] = rnd.nextInt(1000);
                    }
                    int k = round - 5;
                    p.set(3, k).run(LaunchConfig.of1D(n));
                    for (int i = 0; i < n; i++) {
                        assertEquals(in[i] * k + table[i % 4], out[i], "round " + round + " i=" + i);
                    }
                }
            }
        }
        assertEquals(20, events.stream().filter(e -> e.kind() == GpuEvent.Kind.KERNEL).count());
        assertFalse(events.stream().anyMatch(e -> e.kind() == GpuEvent.Kind.FALLBACK));
    }

    @Test
    void staticBufferAndPingPongIterationsStayOnTheDevice(@TempDir Path dir) throws Exception {
        List<GpuEvent> events = new ArrayList<>();
        JavaToGpu.configureDefault(b -> b.listener(events::add).fallback(GpuContext.Fallback.NEVER));
        GpuContext ctx = JavaToGpu.defaultContext().orElse(null);
        Assumptions.assumeTrue(ctx != null, "no OpenCL device");
        KernelCompiler.Compiled r = KernelCompiler.compile(dir, SOURCE);
        assertTrue(r.success(), r.diagnostics());
        Weaver.weaveTree(dir);
        try (URLClassLoader loader = new URLClassLoader(new URL[]{dir.toUri().toURL()}, getClass().getClassLoader())) {
            Class<?> steps = loader.loadClass("demo.Steps");
            int n = 1000;
            int[] start = new int[n];
            Random rnd = new Random(8);
            for (int i = 0; i < n; i++) {
                start[i] = rnd.nextInt();
            }
            int iterations = 25;
            int[] expected = start.clone();
            for (int it = 0; it < iterations; it++) {
                int[] next = new int[n];
                for (int i = 0; i < n; i++) {
                    next[i] = expected[i] + expected[(i + 1) % n];
                }
                expected = next;
            }

            try (GpuBuffer a = ctx.upload(start); GpuBuffer b = ctx.allocate(Types.I32, n);
                 PreparedLauncher p = JavaToGpu.prepare(steps, "smooth", b, a)) {
                long transferred = 0;
                GpuBuffer src = a;
                GpuBuffer dst = b;
                for (int it = 0; it < iterations; it++) {
                    p.set(0, dst).set(1, src);
                    LaunchStats s = p.run(LaunchConfig.of1D(n));
                    transferred += s.bytesUp() + s.bytesDown();
                    GpuBuffer t = src;
                    src = dst;
                    dst = t;
                }
                assertEquals(0, transferred, "data does not travel through the host between iterations");
                int[] result = new int[n];
                src.read(result);
                assertArrayEquals(expected, result);
            }
        }
    }
}
