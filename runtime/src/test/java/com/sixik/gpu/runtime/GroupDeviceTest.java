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

                @DeviceFunction
                static int push(@Private int[] stack, int top, int v) {
                    stack[top] = v;
                    return top + 1;
                }

                @DeviceFunction
                static int weighted(@Private int[] stack, int top) {
                    int s = 0;
                    for (int i = 0; i < top; i++) {
                        s += stack[i] * (i + 1);
                    }
                    return s + stack.length;
                }

                @Kernel
                public static void scratch(@Global int[] out, @Global int[] in) {
                    int gid = Gpu.getGlobalId(0);
                    int[] stack = new int[6];
                    int top = 0;
                    for (int k = 0; k < 4; k++) {
                        top = push(stack, top, in[gid] + k);
                    }
                    out[gid] = weighted(stack, top) + stack[3];
                }

                @DeviceFunction
                static int sumUnsigned(GlobalBytePtr p, int n) {
                    int s = 0;
                    for (int i = 0; i < n; i++) {
                        s += p.get(i) & 0xFF;
                    }
                    return s;
                }

                @DeviceFunction
                static void bump(LocalBytePtr t, int i) {
                    t.set(i, (byte) (t.get(i) + 1));
                }

                @DeviceFunction
                static void negate(ShortPtr r) {
                    r.value = (short) -r.value;
                }

                @Kernel
                @WorkGroup(x = 16)
                public static void bytes(@Global int[] sums, @Global byte[] data, @Global short[] sh, @Global int[] tiles,
                                         @Global int[] negs) {
                    @Local byte[] tile = new byte[16];
                    int gid = Gpu.getGlobalId(0);
                    int lid = Gpu.getLocalId(0);
                    tile[lid] = data[gid * 4];
                    Gpu.barrier();
                    bump(LocalBytePtr.of(tile, 0), lid);
                    GlobalShortPtr s = GlobalShortPtr.of(sh, gid);
                    ShortPtr r = new ShortPtr(s.get(0));
                    negate(r);
                    s.set(0, r.value);
                    sums[gid] = sumUnsigned(GlobalBytePtr.of(data, gid * 4), 4);
                    tiles[gid] = tile[lid];
                    negs[gid] = r.value;
                }

                @DeviceFunction
                static Float4 twice(@Global float[] f, int i) {
                    Float4 v = Gpu.vload4(f, i);
                    return new Float4(v.x * 2f, v.y * 2f, v.z * 2f, v.w * 2f);
                }

                @Kernel
                @WorkGroup(x = 16)
                public static void vec(@Global int[] oi, @Global float[] of, @Global float[] f, @Global double[] od,
                                       @Global double[] d) {
                    @Local int[] tile = new int[32];
                    int i = Gpu.getGlobalId(0);
                    int lid = Gpu.getLocalId(0);
                    Gpu.vstore4(of, i, twice(f, i));
                    Double4 u = Gpu.vload4(d, i);
                    Gpu.vstore4(od, i, new Double4(u.x + u.y, u.y * u.z, u.z - u.w, u.w));
                    int[] scratch = new int[4];
                    Gpu.vstore2(scratch, 1, new Int2(i, lid));
                    Gpu.vstore2(tile, lid, Gpu.vload2(scratch, 1));
                    Gpu.barrier();
                    Int2 t = Gpu.vload2(tile, (lid + 1) & 15);
                    oi[i] = t.x * 1000 + t.y;
                }

                @Kernel
                public static void claim(@Global int[] keys, @Global int[] owner, @Global int[] winners, @Global long[] total) {
                    int gid = Gpu.getGlobalId(0);
                    if (Gpu.atomicCmpxchg(GlobalIntPtr.of(owner, keys[gid] & 63), 0, gid + 1) == 0) {
                        Gpu.atomicAdd(GlobalIntPtr.of(winners, 0), 1);
                    }
                    long old = total[0];
                    while (true) {
                        long seen = Gpu.atomicCmpxchg(GlobalLongPtr.of(total, 0), old, old + keys[gid]);
                        if (seen == old) {
                            break;
                        }
                        old = seen;
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
        assertEquals(7, Weaver.weaveTree(dir).kernels());
        holder[0] = new URLClassLoader(new URL[]{dir.toUri().toURL()}, getClass().getClassLoader());
        return holder[0].loadClass("demo.Groups");
    }

    @Test
    void reductionAndHistogramRunOnTheDeviceByAnOrdinaryCall(@TempDir Path dir) throws Exception {
        List<GpuEvent> events = new ArrayList<>();
        JavaToGpu.configureDefault(b -> b.listener(events::add).fallback(GpuContext.Fallback.NEVER));
        Assumptions.assumeTrue(JavaToGpu.defaultContext().isPresent(), "no OpenCL device");
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
            assertArrayEquals(expected, partial, "sum of each group of 64: local array + 6 barriers");

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
            assertArrayEquals(expectedBins, bins, "atomics in local, then merge into global");

            int[] keys = new int[1024];
            long sum = 0;
            boolean[] used = new boolean[64];
            for (int i = 0; i < keys.length; i++) {
                keys[i] = rnd.nextInt(40);
                sum += keys[i];
                used[keys[i] & 63] = true;
            }
            int[] owner = new int[64];
            int[] winners = new int[1];
            long[] total = new long[1];
            call(groups, "claim", keys, owner, winners, total);
            int claimed = 0;
            for (int k = 0; k < 64; k++) {
                if (used[k]) {
                    claimed++;
                    assertEquals(k, keys[owner[k] - 1] & 63, "the owner of slot " + k + " has this key");
                } else {
                    assertEquals(0, owner[k]);
                }
            }
            assertEquals(claimed, winners[0], "exactly one compare-exchange wins each slot");
            assertEquals(sum, total[0], "64-bit compare-exchange loop adds every key");

            int[] src = new int[256];
            int[] expectedScratch = new int[src.length];
            for (int i = 0; i < src.length; i++) {
                src[i] = rnd.nextInt(1000) - 500;
                int w = 0;
                for (int k = 0; k < 4; k++) {
                    w += (src[i] + k) * (k + 1);
                }
                expectedScratch[i] = w + 6 + src[i] + 3;
            }
            int[] scratchOut = new int[src.length];
            call(groups, "scratch", scratchOut, src);
            assertArrayEquals(expectedScratch, scratchOut, "@Private array passed to device functions by reference");

            int items = 64;
            byte[] bytes = new byte[items * 4];
            rnd.nextBytes(bytes);
            short[] shorts = new short[items];
            for (int i = 0; i < items; i++) {
                shorts[i] = (short) (rnd.nextInt(65536) - 32768);
            }
            short[] shortsBefore = shorts.clone();
            int[] sums = new int[items];
            int[] tiles = new int[items];
            int[] negs = new int[items];
            call(groups, "bytes", sums, bytes, shorts, tiles, negs);
            for (int i = 0; i < items; i++) {
                int s = 0;
                for (int k = 0; k < 4; k++) {
                    s += bytes[i * 4 + k] & 0xFF;
                }
                assertEquals(s, sums[i], "GlobalBytePtr: unsigned sum of item " + i);
                assertEquals((byte) (bytes[i * 4] + 1), tiles[i], "LocalBytePtr: signed byte with wrap-around, item " + i);
                assertEquals((short) -shortsBefore[i], negs[i], "ShortPtr reference, item " + i);
                assertEquals((short) -shortsBefore[i], shorts[i], "GlobalShortPtr store, item " + i);
            }

            int vn = 128;
            float[] f = new float[vn * 4];
            double[] d = new double[vn * 4];
            for (int i = 0; i < f.length; i++) {
                f[i] = rnd.nextFloat() * 100f - 50f;
                d[i] = rnd.nextDouble() * 100 - 50;
            }
            int[] oi = new int[vn];
            float[] of = new float[vn * 4];
            double[] od = new double[vn * 4];
            call(groups, "vec", oi, of, f, od, d);
            for (int i = 0; i < vn; i++) {
                for (int c = 0; c < 4; c++) {
                    assertEquals(f[4 * i + c] * 2f, of[4 * i + c], "vload4/vstore4 float, item " + i);
                }
                double x = d[4 * i], y = d[4 * i + 1], z = d[4 * i + 2], w = d[4 * i + 3];
                assertArrayEquals(new double[]{x + y, y * z, z - w, w}, java.util.Arrays.copyOfRange(od, 4 * i, 4 * i + 4),
                        "vload4/vstore4 double, item " + i);
                int lid = i % 16;
                int j = (lid + 1) & 15;
                assertEquals((i - lid + j) * 1000 + j, oi[i], "vload2/vstore2 over private and local arrays, item " + i);
            }
        } finally {
            if (holder[0] != null) {
                holder[0].close();
            }
        }
        assertTrue(events.stream().anyMatch(e -> e.kind() == GpuEvent.Kind.KERNEL), "the kernels ran on the device");
        assertFalse(events.stream().anyMatch(e -> e.kind() == GpuEvent.Kind.FALLBACK));
    }

    @Test
    void tooMuchLocalMemoryIsRejectedWithAClearError(@TempDir Path dir) throws Exception {
        JavaToGpu.configureDefault(b -> b.fallback(GpuContext.Fallback.NEVER));
        Assumptions.assumeTrue(JavaToGpu.defaultContext().isPresent(), "no OpenCL device");
        Assumptions.assumeTrue(JavaToGpu.defaultContext().get().device().localMemory() < 4L << 20, "a reasonable local-memory limit is needed");
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
