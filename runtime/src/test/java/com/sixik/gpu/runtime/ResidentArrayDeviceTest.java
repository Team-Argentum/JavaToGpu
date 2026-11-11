package com.sixik.gpu.runtime;

import com.sixik.gpu.api.types.GpuFloatArray;
import com.sixik.gpu.weaver.KernelCompiler;
import com.sixik.gpu.weaver.Weaver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code GpuFloatArray} настоящим путём пользователя: исходник &rarr; процессор &rarr; weaver &rarr; обычный вызов метода &rarr;
 * GPU. Цепочка из многих шагов, передающих массивы друг другу, сравнивается с той же цепочкой на {@code float[]}:
 * результат обязан совпасть побитово, а передач по шине - почти не быть. Печатает время обеих цепочек.
 */
@Tag("device")
class ResidentArrayDeviceTest {

    private static final String SOURCE = """
            package demo;

            import com.sixik.gpu.api.Gpu;
            import com.sixik.gpu.api.annotations.Global;
            import com.sixik.gpu.api.annotations.Kernel;
            import com.sixik.gpu.api.types.GpuFloatArray;

            public class Chain {

                @Kernel
                public static void step(GpuFloatArray a, GpuFloatArray b, float k) {
                    int i = Gpu.getGlobalId(0);
                    b.set(i, a.get(i) * k + 1f);
                }

                @Kernel
                public static void stepPlain(@Global float[] a, @Global float[] b, float k) {
                    int i = Gpu.getGlobalId(0);
                    b[i] = a[i] * k + 1f;
                }
            }
            """;

    private static final int N = 4 << 20; // 16 МБ на массив
    private static final int STEPS = 20;

    @AfterEach
    void reset() {
        JavaToGpu.resetDefault();
    }

    @Test
    void kernelChainStaysOnTheDevice(@TempDir Path dir) throws Exception {
        List<GpuEvent> events = new ArrayList<>();
        JavaToGpu.configureDefault(b -> b.listener(events::add).fallback(GpuContext.Fallback.NEVER));
        Assumptions.assumeTrue(JavaToGpu.defaultContext().isPresent(), "no OpenCL device");

        KernelCompiler.Compiled r = KernelCompiler.compile(dir, SOURCE);
        assertTrue(r.success(), r.diagnostics());
        assertEquals(2, Weaver.weaveTree(dir).kernels());
        try (URLClassLoader loader = new URLClassLoader(new URL[]{dir.toUri().toURL()}, getClass().getClassLoader())) {
            Class<?> chain = loader.loadClass("demo.Chain");
            float[] init = new float[N];
            for (int i = 0; i < N; i++) {
                init[i] = (i % 1000) * 0.001f;
            }

            // прогрев: компиляция обоих ядер
            GpuFloatArray wa = GpuFloatArray.of(init);
            GpuFloatArray wb = new GpuFloatArray(N);
            call(chain, "step", wa, wb, 0.5f);
            call(chain, "stepPlain", init.clone(), new float[N], 0.5f);

            // цепочка на float[]
            float[] pa = init.clone();
            float[] pb = new float[N];
            long t0 = System.nanoTime();
            for (int s = 0; s < STEPS; s++) {
                if (s % 2 == 0) {
                    call(chain, "stepPlain", pa, pb, 0.5f);
                } else {
                    call(chain, "stepPlain", pb, pa, 0.5f);
                }
            }
            long plainNanos = System.nanoTime() - t0;

            // та же цепочка на GpuFloatArray
            GpuFloatArray ra = GpuFloatArray.of(init);
            GpuFloatArray rb = new GpuFloatArray(N);
            int eventsBefore = events.size();
            t0 = System.nanoTime();
            for (int s = 0; s < STEPS; s++) {
                if (s % 2 == 0) {
                    call(chain, "step", ra, rb, 0.5f);
                } else {
                    call(chain, "step", rb, ra, 0.5f);
                }
            }
            float[] result = ra.toArray(); // единственное чтение на хост
            long residentNanos = System.nanoTime() - t0;

            assertArrayEquals(pa, result, "the resident chain must compute exactly what the float[] chain computes");
            long up = 0;
            long down = 0;
            for (GpuEvent e : events.subList(eventsBefore, events.size())) {
                if (e.kind() == GpuEvent.Kind.UPLOAD) {
                    up += e.bytes();
                } else if (e.kind() == GpuEvent.Kind.READBACK) {
                    down += e.bytes();
                }
            }
            assertEquals(4L * N, up, "only the initial data is uploaded once");
            assertEquals(4L * N, down, "only the final result is read once");
            System.out.printf(Locale.ROOT, "%d steps over %d MB: float[] %.1f ms, GpuFloatArray %.1f ms%n", STEPS,
                    4 * N / 1_000_000, plainNanos / 1e6, residentNanos / 1e6);
        }
        assertFalse(events.stream().anyMatch(e -> e.kind() == GpuEvent.Kind.FALLBACK));
    }

    private static void call(Class<?> c, String method, Object... args) throws Exception {
        for (Method m : c.getDeclaredMethods()) {
            if (m.getName().equals(method) && m.getParameterCount() == args.length) {
                try {
                    m.invoke(null, args);
                    return;
                } catch (InvocationTargetException e) {
                    if (e.getCause() instanceof RuntimeException re) {
                        throw re;
                    }
                    throw e;
                }
            }
        }
        throw new IllegalArgumentException(method);
    }
}
