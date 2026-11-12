package com.sixik.gpu.runtime;

import com.sixik.gpu.api.types.GpuIntArray;
import com.sixik.gpu.runtime.device.FakeDeviceApi;
import com.sixik.gpu.weaver.KernelCompiler;
import com.sixik.gpu.weaver.Weaver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Отложенные ядра-производители и слияние «ядро → свёртка» ({@link LazyProducers}) сквозным путём: javac с processor'ом,
 * weaver, вызовы через фасад. Устройство - {@link FakeDeviceApi}, тела ядер исполняет интерпретатор IR; число запусков
 * считается по журналу фейка. Результаты обязаны совпадать с немедленным исполнением.
 */
@SuppressWarnings("try") // области JavaToGpu.use открываются ради эффекта
class LazyProducersTest {

    private static final String SOURCE = """
            package demo;

            import com.sixik.gpu.api.Gpu;
            import com.sixik.gpu.api.annotations.Kernel;
            import com.sixik.gpu.api.annotations.Reduction;
            import com.sixik.gpu.api.types.GpuIntArray;

            public class Lazy {

                @Kernel
                public static void produce(GpuIntArray out, GpuIntArray base, int seed) {
                    int i = Gpu.getGlobalId(0);
                    out.set(i, base.get(i % base.length()) * 3 + seed + i);
                }

                @Kernel
                public static void bump(GpuIntArray a) {
                    int i = Gpu.getGlobalId(0);
                    a.set(i, a.get(i) + 1);
                }

                @Kernel
                public static void scale(GpuIntArray out, GpuIntArray in, int f) {
                    int i = Gpu.getGlobalId(0);
                    out.set(i, in.get(i) * f);
                }

                @Kernel
                public static void accumulate(GpuIntArray acc, GpuIntArray v) {
                    int i = Gpu.getGlobalId(0);
                    acc.set(i, acc.get(i) + v.get(i));
                }

                @Kernel
                public static void shift(GpuIntArray out, GpuIntArray in) {
                    int i = Gpu.getGlobalId(0);
                    out.set(i, in.get(i == 0 ? 0 : i - 1));
                }

                @Reduction
                public static int total(GpuIntArray v) {
                    int s = 0;
                    for (int i = 0; i < v.length(); i++) {
                        s += v.get(i);
                    }
                    return s;
                }
            }
            """;

    @TempDir
    Path dir;
    private URLClassLoader loader;
    private Class<?> lazy;
    private FakeDeviceApi fake;
    private GpuContext ctx;
    private JavaToGpu.Scope scope;

    @BeforeEach
    void open() throws Exception {
        KernelCompiler.Compiled r = KernelCompiler.compile(dir, SOURCE);
        assertTrue(r.success(), r.diagnostics());
        Weaver.Report report = Weaver.weaveTree(dir);
        assertEquals(6, report.kernels(), report.problems().toString());
        loader = new URLClassLoader(new URL[]{dir.toUri().toURL()}, LazyProducersTest.class.getClassLoader());
        lazy = loader.loadClass("demo.Lazy");
        open(true);
    }

    private void open(boolean fusion) {
        fake = FakeDeviceApi.withGpu();
        GpuContext c = GpuContext.builder().api(fake).ignoreEnvironment().fusion(fusion)
                .fallback(GpuContext.Fallback.NEVER).build();
        ctx = c;
        fake.onAnyKernel(l -> SplitLaunchTest.interpreting(c.compiledByHandle(l.kernel())).run(l));
        scope = JavaToGpu.use(ctx);
    }

    @AfterEach
    void close() throws Exception {
        if (scope != null) {
            scope.close();
        }
        if (ctx != null) {
            ctx.close();
        }
        JavaToGpu.resetDefault();
        loader.close();
    }

    private Object call(String method, Object... args) throws Exception {
        for (Method m : lazy.getDeclaredMethods()) {
            if (m.getName().equals(method) && m.getParameterCount() == args.length) {
                try {
                    return m.invoke(null, args);
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

    private long launches() {
        return fake.enqueues();
    }

    private static GpuIntArray base(int n) {
        GpuIntArray b = new GpuIntArray(n);
        for (int i = 0; i < n; i++) {
            b.set(i, (i * 37) % 11 - 5);
        }
        return b;
    }

    /** Значения производителя при заданных данных {@code base} (снятых до изменений). */
    private static int[] expected(int[] base, int seed, int n) {
        int[] out = new int[n];
        for (int i = 0; i < n; i++) {
            out[i] = base[i % base.length] * 3 + seed + i;
        }
        return out;
    }

    private static int sum(int[] a) {
        int s = 0;
        for (int x : a) {
            s += x;
        }
        return s;
    }

    @Test
    void reductionOfADeferredOutputRunsFusedWithoutTheProducer() throws Exception {
        GpuIntArray base = base(5);
        GpuIntArray out = new GpuIntArray(1000);
        int[] want = expected(base.toArray(), 7, 1000);
        call("produce", out, base, 7);
        assertEquals(0, launches(), "the producer is deferred");
        assertEquals(1, ctx.lazy.pending());
        assertEquals(sum(want), call("total", out));
        assertEquals(1, launches(), "one fused reduction kernel, the producer did not run");
        assertEquals(sum(want), call("total", out));
        assertEquals(2, launches(), "the output stays deferred across reductions");
        assertEquals(want[3], out.get(3), "reading the output runs the producer");
        assertEquals(3, launches());
        assertEquals(0, ctx.lazy.pending());
        assertEquals(sum(want), call("total", out), "a materialized output is reduced as usual");
    }

    @Test
    void smallInputsAreSnapshotted() throws Exception {
        GpuIntArray base = base(5);
        int[] old = base.toArray();
        GpuIntArray out = new GpuIntArray(300);
        call("produce", out, base, 1);
        base.set(0, 1000); // следующая порция: старый запуск не исполняется, он видит свой снимок
        base.set(1, 2000);
        assertEquals(0, launches());
        assertEquals(sum(expected(old, 1, 300)), call("total", out));
        assertEquals(expected(old, 1, 300)[0], out.get(0));
        call("produce", out, base, 1);
        assertEquals(sum(expected(base.toArray(), 1, 300)), call("total", out), "a new launch sees the new input");
    }

    @Test
    void largeInputsAreWatchedAndAHostWriteRunsTheProducerFirst() throws Exception {
        GpuIntArray base = base(20_000); // 80 КБ - больше снимка
        int[] old = base.toArray();
        GpuIntArray out = new GpuIntArray(20_000);
        call("produce", out, base, 2);
        assertEquals(0, launches());
        base.set(0, 1000);
        assertEquals(1, launches(), "the producer runs before the host changes its input");
        assertEquals(expected(old, 2, 20_000)[0], out.get(0));
        assertEquals(sum(expected(old, 2, 20_000)), call("total", out));
    }

    @Test
    void aKernelWritingAnInputOrReadingTheOutputRunsTheProducerFirst() throws Exception {
        GpuIntArray base = base(20_000);
        int[] old = base.toArray();
        GpuIntArray out = new GpuIntArray(20_000);
        call("produce", out, base, 0);
        call("bump", base); // пишет вход: производитель исполняется раньше
        assertEquals(2, launches());
        assertEquals(expected(old, 0, 20_000)[9], out.get(9));
        assertEquals(old[9] + 1, base.get(9));

        GpuIntArray out2 = new GpuIntArray(100);
        GpuIntArray small = base(3);
        int[] want = expected(small.toArray(), 4, 100);
        call("produce", out2, small, 4);
        call("bump", out2); // получает выход: производитель исполняется раньше
        assertEquals(want[50] + 1, out2.get(50));
    }

    @Test
    void anOverwrittenOutputDropsTheOldLaunch() throws Exception {
        GpuIntArray base = base(5);
        GpuIntArray out = new GpuIntArray(64);
        call("produce", out, base, 1);
        call("produce", out, base, 2);
        assertEquals(0, launches(), "the first launch is never needed");
        assertEquals(1, ctx.lazy.pending());
        assertEquals(expected(base.toArray(), 2, 64)[10], out.get(10));
        assertEquals(1, launches());
        call("produce", out, base, 3);
        out.fill(-1); // хост перезаписал всё: запуск отбрасывается
        assertEquals(1, launches());
        assertEquals(-1, out.get(10));
        assertEquals(0, ctx.lazy.pending());
    }

    @Test
    void closingTheContextRunsDeferredLaunches() throws Exception {
        GpuIntArray base = base(5);
        GpuIntArray out = new GpuIntArray(64);
        call("produce", out, base, 5);
        scope.close();
        scope = null;
        ctx.close();
        assertEquals(1, launches());
        assertEquals(expected(base.toArray(), 5, 64)[20], out.get(20), "the data is on the host after close");
    }

    @Test
    void aChainOfProducersFusesIntoTheReduction() throws Exception {
        GpuIntArray base = base(5);
        GpuIntArray out1 = new GpuIntArray(500);
        GpuIntArray out2 = new GpuIntArray(500);
        int[] first = expected(base.toArray(), 7, 500);
        call("produce", out1, base, 7);
        call("scale", out2, out1, 3);
        assertEquals(0, launches(), "both producers are deferred; scale got produce inserted");
        assertEquals(2, ctx.lazy.pending());
        assertEquals(3 * sum(first), call("total", out2));
        assertEquals(1, launches(), "one kernel for produce + scale + total");
        assertEquals(3 * first[42], out2.get(42), "scale runs with produce inside, without out1");
        assertEquals(2, launches());
        assertEquals(1, ctx.lazy.pending(), "out1 is still deferred");
        assertEquals(first[42], out1.get(42));
        assertEquals(3, launches());
    }

    @Test
    void anElementWiseConsumerKernelRunsWithTheProducerInside() throws Exception {
        GpuIntArray base = base(5);
        GpuIntArray out = new GpuIntArray(200);
        GpuIntArray acc = new GpuIntArray(200);
        acc.fill(10);
        int[] want = expected(base.toArray(), 1, 200);
        call("produce", out, base, 1);
        call("accumulate", acc, out); // читает и пишет acc - не производитель, исполняется сразу
        assertEquals(1, launches(), "only the fused accumulate ran");
        assertEquals(1, ctx.lazy.pending(), "the producer output is still deferred");
        assertEquals(10 + want[77], acc.get(77));
        assertEquals(want[77], out.get(77));
        assertEquals(2, launches());
    }

    @Test
    void aNeighbourReadIsNotFusedAndRunsTheProducerFirst() throws Exception {
        GpuIntArray base = base(5);
        GpuIntArray out1 = new GpuIntArray(100);
        GpuIntArray out2 = new GpuIntArray(100);
        int[] want = expected(base.toArray(), 2, 100);
        call("produce", out1, base, 2);
        call("shift", out2, out1); // читает соседа: не сливается, но сам откладывается и наблюдает out1
        assertEquals(0, launches());
        assertEquals(want[29], out2.get(30));
        assertEquals(2, launches(), "produce, then shift");
        assertEquals(want[0], out2.get(0));
    }

    @Test
    void fusionCanBeTurnedOff() throws Exception {
        scope.close();
        ctx.close();
        open(false);
        GpuIntArray base = base(5);
        GpuIntArray out = new GpuIntArray(64);
        call("produce", out, base, 5);
        assertEquals(1, launches(), "without fusion the kernel runs at once");
        assertEquals(sum(expected(base.toArray(), 5, 64)), call("total", out));
    }
}
