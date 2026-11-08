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
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Критерий M10 на реальном устройстве: ядра с {@code @Local}, {@code Gpu.barrier()} и атомиками запускаются обычным
 * вызовом Java-метода (путь целиком: javac + processor &rarr; weaver &rarr; {@link JavaToGpu} &rarr; GPU) и дают
 * результат, посчитанный на хосте. Размер запуска по умолчанию - длина первого массива, размер группы берётся из
 * {@code @WorkGroup}. Без драйвера тест пропускается. Запуск: {@code ./gradlew :runtime:deviceTest}.
 */
@Tag("device")
class GroupDeviceTest {

    private static final String SOURCE = """
            package demo;

            import com.sixik.gpu.api.Gpu;
            import com.sixik.gpu.api.annotations.*;
            import com.sixik.gpu.api.types.*;

            public class Groups {

                @DeviceFunction
                static void fold(@Local int[] tile, int lid, int s) {
                    if (lid < s) {
                        tile[lid] += tile[lid + s];
                    }
                    Gpu.barrier();
                }

                @Kernel
                @WorkGroup(x = 64)
                public static void reduceSum(@Global int[] in, @Global int[] partial) {
                    @Local int[] tile = new int[64];
                    int lid = Gpu.getLocalId(0);
                    tile[lid] = in[Gpu.getGlobalId(0)];
                    Gpu.barrier();
                    for (int s = 32; s > 0; s >>= 1) {
                        fold(tile, lid, s);
                    }
                    if (lid == 0) {
                        partial[Gpu.getGroupId(0)] = tile[0];
                    }
                }

                @Kernel
                @WorkGroup(x = 32)
                public static void histogram(@Global int[] data, @Global int[] bins) {
                    @Local int[] local = new int[16];
                    int lid = Gpu.getLocalId(0);
                    if (lid < 16) {
                        local[lid] = 0;
                    }
                    Gpu.barrier();
                    Gpu.atomicAdd(LocalIntPtr.of(local, data[Gpu.getGlobalId(0)] & 15), 1);
                    Gpu.barrier();
                    if (lid < 16) {
                        Gpu.atomicAdd(GlobalIntPtr.of(bins, lid), local[lid]);
                    }
                }

                @Kernel
                @WorkGroup(x = 64)
                public static void hog(@Global int[] a) {
                    @Local int[] huge = new int[1 << 20];
                    huge[Gpu.getLocalId(0)] = 1;
                    Gpu.barrier();
                    a[Gpu.getGlobalId(0)] = huge[0];
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

    private Class<?> load(Path dir, URLClassLoader[] holder) throws Exception {
        KernelCompiler.Compiled r = KernelCompiler.compile(dir, SOURCE);
        assertTrue(r.success(), r.diagnostics());
        assertEquals(3, Weaver.weaveTree(dir).kernels());
        holder[0] = new URLClassLoader(new URL[]{dir.toUri().toURL()}, getClass().getClassLoader());
        return holder[0].loadClass("demo.Groups");
    }

    @Test
    void reductionAndHistogramRunOnTheDeviceByAnOrdinaryCall(@TempDir Path dir) throws Exception {
        List<GpuEvent> events = new ArrayList<>();
        JavaToGpu.configureDefault(b -> b.listener(events::add).fallback(GpuContext.Fallback.NEVER));
        Assumptions.assumeTrue(JavaToGpu.defaultContext().isPresent(), "нет OpenCL-устройства");
        URLClassLoader[] holder = new URLClassLoader[1];
        try {
            Class<?> groups = load(dir, holder);

            int groupsCount = 40;
            int[] in = new int[groupsCount * 64];
            Random rnd = new Random(11);
            for (int i = 0; i < in.length; i++) {
                in[i] = rnd.nextInt();
            }
            int[] expected = new int[groupsCount];
            for (int i = 0; i < in.length; i++) {
                expected[i / 64] += in[i];
            }
            int[] partial = new int[groupsCount];
            call(groups, "reduceSum", in, partial);
            assertArrayEquals(expected, partial, "сумма каждой группы из 64: local-массив + 6 барьеров");

            int[] data = new int[32 * 100];
            for (int i = 0; i < data.length; i++) {
                data[i] = rnd.nextInt(100000);
            }
            int[] bins = new int[16];
            int[] expectedBins = new int[16];
            for (int v : data) {
                expectedBins[v & 15]++;
            }
            call(groups, "histogram", data, bins);
            assertArrayEquals(expectedBins, bins, "атомики в local, затем слияние в global");
        } finally {
            if (holder[0] != null) {
                holder[0].close();
            }
        }
        assertTrue(events.stream().anyMatch(e -> e.kind() == GpuEvent.Kind.KERNEL), "ядра выполнены на устройстве");
        assertFalse(events.stream().anyMatch(e -> e.kind() == GpuEvent.Kind.FALLBACK));
    }

    @Test
    void tooMuchLocalMemoryIsRejectedWithAClearError(@TempDir Path dir) throws Exception {
        JavaToGpu.configureDefault(b -> b.fallback(GpuContext.Fallback.NEVER));
        Assumptions.assumeTrue(JavaToGpu.defaultContext().isPresent(), "нет OpenCL-устройства");
        Assumptions.assumeTrue(JavaToGpu.defaultContext().get().device().localMemory() < 4L << 20, "нужен обозримый предел local-памяти");
        URLClassLoader[] holder = new URLClassLoader[1];
        try {
            Class<?> groups = load(dir, holder);
            GpuException e = assertThrows(GpuException.class, () -> call(groups, "hog", new int[64]));
            assertTrue(e.getMessage().contains("RT-D04"), e.getMessage());
            assertTrue(e.getMessage().contains("CL_DEVICE_LOCAL_MEM_SIZE"), e.getMessage());
        } finally {
            if (holder[0] != null) {
                holder[0].close();
            }
        }
    }
}
