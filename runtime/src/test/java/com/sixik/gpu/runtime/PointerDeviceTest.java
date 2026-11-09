package com.sixik.gpu.runtime;

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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Критерий M9 на реальном устройстве: ядро с ссылкой {@code FloatPtr}, явными указателями и таблицей
 * {@code @Global(constant = true)} запускается обычным вызовом, совпадает с JVM-эталоном, а запись через указатель в
 * исходный массив возвращается на хост. Слишком большая константная таблица отвергается понятной ошибкой до запуска.
 * Без драйвера тест пропускается. Запуск: {@code ./gradlew :runtime:deviceTest}.
 */
@Tag("device")
class PointerDeviceTest {

    private static final String SOURCE = """
            package demo;

            import com.sixik.gpu.api.Gpu;
            import com.sixik.gpu.api.annotations.*;
            import com.sixik.gpu.api.types.*;

            public class Ptrs {

                @DeviceFunction
                static void addTo(FloatPtr acc, float v) {
                    acc.value += v;
                }

                @DeviceFunction
                static float sum(ConstantFloatPtr t, int n) {
                    float s = 0f;
                    for (int i = 0; i < n; i++) {
                        s += t.get(i);
                    }
                    return s;
                }

                @DeviceFunction
                static void scale(GlobalFloatPtr p, int n, float k) {
                    for (int i = 0; i < n; i++) {
                        p.set(i, p.get(i) * k);
                    }
                }

                @Kernel
                public static void run(@Global float[] out, @Global float[] data, @Global(constant = true) float[] table) {
                    int g = Gpu.getGlobalId(0);
                    FloatPtr acc = new FloatPtr(data[g * 4]);
                    addTo(acc, 2f);
                    addTo(acc, table[0]);
                    GlobalFloatPtr row = GlobalFloatPtr.of(data, g * 4);
                    scale(row.plus(1), 2, 3f);
                    out[g] = acc.value + sum(ConstantFloatPtr.of(table, 1), 3) + row.get(2);
                }

                @Kernel
                public static void big(@Global(constant = true) float[] table, @Global float[] out) {
                    out[Gpu.getGlobalId(0)] = table[Gpu.getGlobalId(0)];
                }
            }
            """;

    @AfterEach
    void reset() {
        JavaToGpu.resetDefault();
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

    @Test
    void pointersAndConstantTableRunOnTheDevice(@TempDir Path dir) throws Exception {
        List<GpuEvent> events = new ArrayList<>();
        JavaToGpu.configureDefault(b -> b.listener(events::add).fallback(GpuContext.Fallback.NEVER));
        Assumptions.assumeTrue(JavaToGpu.defaultContext().isPresent(), "no OpenCL device");

        KernelCompiler.Compiled r = KernelCompiler.compile(dir, SOURCE);
        assertTrue(r.success(), r.diagnostics());
        assertEquals(2, Weaver.weaveTree(dir).kernels());
        try (URLClassLoader loader = new URLClassLoader(new URL[]{dir.toUri().toURL()}, getClass().getClassLoader())) {
            Class<?> ptrs = loader.loadClass("demo.Ptrs");
            int n = 64;
            float[] data = new float[n * 4];
            for (int i = 0; i < data.length; i++) {
                data[i] = i * 0.5f - 17;
            }
            float[] table = {1.5f, -2f, 4.25f, 8f, 100f};
            float[] expectedData = data.clone();
            float[] expectedOut = new float[n];
            for (int g = 0; g < n; g++) {
                float acc = expectedData[g * 4];
                acc += 2f;
                acc += table[0];
                expectedData[g * 4 + 1] *= 3f;
                expectedData[g * 4 + 2] *= 3f;
                float s = 0f;
                for (int i = 0; i < 3; i++) {
                    s += table[1 + i];
                }
                expectedOut[g] = acc + s + expectedData[g * 4 + 2];
            }
            // размер запуска по умолчанию - длина первого массива (out): ровно n work-item'ов, без записи за границы
            float[] out = new float[n];
            call(ptrs, "run", out, data, table);

            assertArrayEquals(expectedOut, out, "the kernel result matched the JVM reference exactly (+ and * in float are exact)");
            assertArrayEquals(expectedData, data,
                    "the write through the pointer into the source array was returned to the host (access mode via a local pointer)");
            assertEquals(1.5f, table[0], "the constant table did not change");
        }
        assertTrue(events.stream().anyMatch(e -> e.kind() == GpuEvent.Kind.KERNEL), "the kernel ran on the device");
        assertFalse(events.stream().anyMatch(e -> e.kind() == GpuEvent.Kind.FALLBACK));
    }

    @Test
    void constantTableLargerThanTheDeviceLimitIsRejectedWithAClearError(@TempDir Path dir) throws Exception {
        JavaToGpu.configureDefault(b -> b.fallback(GpuContext.Fallback.NEVER));
        Assumptions.assumeTrue(JavaToGpu.defaultContext().isPresent(), "no OpenCL device");
        long limit = JavaToGpu.defaultContext().get().device().maxConstantBuffer();
        Assumptions.assumeTrue(limit > 0 && limit < (1L << 28), "a reasonable __constant limit is needed");

        KernelCompiler.Compiled r = KernelCompiler.compile(dir, SOURCE);
        assertTrue(r.success(), r.diagnostics());
        Weaver.weaveTree(dir);
        try (URLClassLoader loader = new URLClassLoader(new URL[]{dir.toUri().toURL()}, getClass().getClassLoader())) {
            Class<?> ptrs = loader.loadClass("demo.Ptrs");
            int elements = (int) (limit / 4) + 16;
            GpuException e = assertThrows(GpuException.class,
                    () -> call(ptrs, "big", new float[elements], new float[elements]));
            assertTrue(e.getMessage().contains("RT-A04"), e.getMessage());
            assertTrue(e.getMessage().contains("CL_DEVICE_MAX_CONSTANT_BUFFER_SIZE"), e.getMessage());
        }
    }
}
