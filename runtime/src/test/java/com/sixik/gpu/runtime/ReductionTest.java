package com.sixik.gpu.runtime;

import com.sixik.gpu.api.types.GpuFloatArray;
import com.sixik.gpu.ir.IrModule;
import com.sixik.gpu.ir.Result;
import com.sixik.gpu.runtime.device.FakeDeviceApi;
import com.sixik.gpu.weaver.KernelCompiler;
import com.sixik.gpu.weaver.Weaver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Свёртки {@code @Reduction} сквозным путём: исходник компилируется javac с processor'ом, weaver подменяет тело, вызов
 * метода идёт через {@link JavaToGpu#reduce}. Устройство - {@link FakeDeviceApi}, тело ядра исполняет интерпретатор IR;
 * без устройства исполняется исходное тело на JVM.
 */
@SuppressWarnings("try") // области JavaToGpu.use открываются ради эффекта
class ReductionTest {

    private static final String SOURCE = """
            package demo;

            import com.sixik.gpu.api.Gpu;
            import com.sixik.gpu.api.annotations.Reduction;
            import com.sixik.gpu.api.annotations.Struct;
            import com.sixik.gpu.api.types.GpuFloatArray;
            import com.sixik.gpu.api.types.Int2;

            public class Sums {

                @Struct
                public static final class MinMax {
                    public float lo;
                    public float hi;
                    public MinMax() { }
                    public MinMax(float lo, float hi) { this.lo = lo; this.hi = hi; }
                }

                @Reduction
                public static MinMax range(float[] v) {
                    float lo = 1e30f;
                    float hi = -1e30f;
                    for (int i = 0; i < v.length; i++) {
                        lo = Gpu.min(lo, v[i]);
                        hi = Gpu.max(hi, v[i]);
                    }
                    return new MinMax(lo, hi);
                }

                @Reduction
                public static Int2 signs(float[] v, int n) {
                    int neg = 0;
                    int pos = 0;
                    for (int i = 0; i < n; i++) {
                        neg += v[i] < 0f ? 1 : 0;
                        pos += v[i] > 0f ? 1 : 0;
                    }
                    return new Int2(neg, pos);
                }

                @Reduction
                public static int positives(float[] v, int n) {
                    int count = 0;
                    for (int i = 0; i < n; i++) {
                        if (v[i] > 0f) {
                            count++;
                        }
                    }
                    return count;
                }

                @Reduction
                public static float peak(GpuFloatArray v) {
                    float best = -1e30f;
                    for (int i = 0; i < v.length(); i++) {
                        best = Gpu.max(best, v.get(i));
                    }
                    return best;
                }
            }
            """;

    @AfterEach
    void reset() {
        JavaToGpu.resetDefault();
    }

    private static Class<?> woven(Path dir, URLClassLoader[] holder) throws Exception {
        KernelCompiler.Compiled r = KernelCompiler.compile(dir, SOURCE);
        assertTrue(r.success(), r.diagnostics());
        Weaver.Report report = Weaver.weaveTree(dir);
        assertEquals(4, report.kernels(), report.problems().toString());
        holder[0] = new URLClassLoader(new URL[]{dir.toUri().toURL()}, ReductionTest.class.getClassLoader());
        return holder[0].loadClass("demo.Sums");
    }

    private static Object call(Class<?> c, String method, Object... args) throws Exception {
        for (Method m : c.getDeclaredMethods()) {
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

    private static float[] values(int n) {
        float[] a = new float[n];
        for (int i = 0; i < n; i++) {
            a[i] = ((i * 37) % 23) - 11.5f;
        }
        return a;
    }

    /** Регистрирует в фейке тело ядра свёртки: интерпретатор IR. */
    private static CompiledKernel install(FakeDeviceApi fake, GpuContext ctx, Class<?> owner, String method) {
        IrModule m = JavaToGpu.loadModule(owner, method);
        Result<CompiledKernel> k = ctx.compileReduction(m);
        assertTrue(k.isOk(), String.valueOf(k.diagnostics()));
        fake.onKernel(k.value().artifact().entry(), SplitLaunchTest.interpreting(k.value()));
        return k.value();
    }

    @Test
    void reductionRunsOnTheDeviceAndReturnsTheSequentialResult(@TempDir Path dir) throws Exception {
        URLClassLoader[] holder = new URLClassLoader[1];
        try {
            Class<?> sums = woven(dir, holder);
            FakeDeviceApi fake = FakeDeviceApi.withGpu();
            try (GpuContext ctx = GpuContext.builder().api(fake).ignoreEnvironment()
                    .fallback(GpuContext.Fallback.NEVER).build(); JavaToGpu.Scope s = JavaToGpu.use(ctx)) {
                CompiledKernel k = install(fake, ctx, sums, "positives");
                assertEquals(256, k.module().fReqWorkGroup(k.function(), 0));
                for (int n : new int[]{0, 1, 300, 2000}) {
                    float[] v = values(n);
                    int want = 0;
                    for (float x : v) {
                        want += x > 0f ? 1 : 0;
                    }
                    assertEquals(want, call(sums, "positives", v, n), "n=" + n);
                }
                long launches = fake.journal().stream().filter("enqueue"::equals).count();
                assertEquals(4, launches, "one launch per call: " + fake.journal());
            }
        } finally {
            holder[0].close();
        }
    }

    /** Поле {@code name} объекта результата (класс загружен своим загрузчиком - через отражение). */
    private static Object field(Object o, String name) throws ReflectiveOperationException {
        return o.getClass().getField(name).get(o);
    }

    @Test
    void severalAccumulatorsReturnAStructAndAVector(@TempDir Path dir) throws Exception {
        URLClassLoader[] holder = new URLClassLoader[1];
        try {
            Class<?> sums = woven(dir, holder);
            FakeDeviceApi fake = FakeDeviceApi.withGpu();
            try (GpuContext ctx = GpuContext.builder().api(fake).ignoreEnvironment()
                    .fallback(GpuContext.Fallback.NEVER).build(); JavaToGpu.Scope s = JavaToGpu.use(ctx)) {
                CompiledKernel range = install(fake, ctx, sums, "range");
                CompiledKernel signs = install(fake, ctx, sums, "signs");
                // оба ядра в release называются k0, а фейк ищет тело по имени: тело выбирает ядро по числу аргументов
                assertTrue(range.artifact().args().size() != signs.artifact().args().size());
                fake.onKernel(range.artifact().entry(), l -> SplitLaunchTest.interpreting(
                        l.argCount() == range.artifact().args().size() ? range : signs).run(l));
                assertEquals(1 + 2, range.module().fParamCount(range.function()), "v plus a partial per accumulator");
                for (int n : new int[]{1, 300, 2000}) {
                    float[] v = values(n);
                    float lo = 1e30f;
                    float hi = -1e30f;
                    int neg = 0;
                    int pos = 0;
                    for (float x : v) {
                        lo = Math.min(lo, x);
                        hi = Math.max(hi, x);
                        neg += x < 0f ? 1 : 0;
                        pos += x > 0f ? 1 : 0;
                    }
                    Object r = call(sums, "range", (Object) v);
                    assertEquals("demo.Sums$MinMax", r.getClass().getName());
                    assertEquals(lo, field(r, "lo"), "n=" + n);
                    assertEquals(hi, field(r, "hi"), "n=" + n);
                    Object g = call(sums, "signs", v, n);
                    assertEquals(neg, field(g, "x"), "n=" + n);
                    assertEquals(pos, field(g, "y"), "n=" + n);
                    assertTrue(call(sums, "range", (Object) v) != r, "every call returns a new object");
                }
            }
        } finally {
            holder[0].close();
        }
    }

    @Test
    void aStructReductionFallsBackToTheJvmBody(@TempDir Path dir) throws Exception {
        URLClassLoader[] holder = new URLClassLoader[1];
        try {
            Class<?> sums = woven(dir, holder);
            FakeDeviceApi fake = FakeDeviceApi.withGpu().rejectBuildWhen(src -> true, "no");
            try (GpuContext ctx = GpuContext.builder().api(fake).ignoreEnvironment()
                    .fallback(GpuContext.Fallback.ALLOWED).build(); JavaToGpu.Scope s = JavaToGpu.use(ctx)) {
                Object r = call(sums, "range", (Object) new float[]{3f, -2f, 7f});
                assertEquals(-2f, field(r, "lo"));
                assertEquals(7f, field(r, "hi"));
            }
        } finally {
            holder[0].close();
        }
    }

    @Test
    void residentArraysAndTheLengthBound(@TempDir Path dir) throws Exception {
        URLClassLoader[] holder = new URLClassLoader[1];
        try {
            Class<?> sums = woven(dir, holder);
            FakeDeviceApi fake = FakeDeviceApi.withGpu();
            try (GpuContext ctx = GpuContext.builder().api(fake).ignoreEnvironment()
                    .fallback(GpuContext.Fallback.NEVER).build(); JavaToGpu.Scope s = JavaToGpu.use(ctx)) {
                install(fake, ctx, sums, "peak");
                GpuFloatArray v = new GpuFloatArray(777);
                float want = -1e30f;
                for (int i = 0; i < v.length(); i++) {
                    v.set(i, (i * 13 % 101) - 50f);
                    want = Math.max(want, v.get(i));
                }
                assertEquals(want, call(sums, "peak", v));
                v.set(500, 1000f); // изменение на хосте загружается перед следующим вызовом
                assertEquals(1000f, call(sums, "peak", v));
            }
        } finally {
            holder[0].close();
        }
    }

    @Test
    void smallerWorkGroupOnDevicesWithALowerLimit(@TempDir Path dir) throws Exception {
        URLClassLoader[] holder = new URLClassLoader[1];
        try {
            Class<?> sums = woven(dir, holder);
            FakeDeviceApi.Spec gpu = FakeDeviceApi.Spec.gpu();
            FakeDeviceApi fake = new FakeDeviceApi(new FakeDeviceApi.Spec(gpu.platform(), gpu.name(), gpu.vendor(),
                    gpu.type(), gpu.memory(), gpu.extensions(), 48, gpu.maxConstant()));
            try (GpuContext ctx = GpuContext.builder().api(fake).ignoreEnvironment()
                    .fallback(GpuContext.Fallback.NEVER).build(); JavaToGpu.Scope s = JavaToGpu.use(ctx)) {
                CompiledKernel k = install(fake, ctx, sums, "positives");
                assertEquals(32, k.module().fReqWorkGroup(k.function(), 0), "largest power of two within 48");
                float[] v = values(1000);
                int want = 0;
                for (float x : v) {
                    want += x > 0f ? 1 : 0;
                }
                assertEquals(want, call(sums, "positives", v, 1000));
            }
        } finally {
            holder[0].close();
        }
    }

    @Test
    void withoutADeviceTheOriginalBodyRunsOnTheJvm(@TempDir Path dir) throws Exception {
        URLClassLoader[] holder = new URLClassLoader[1];
        try {
            Class<?> sums = woven(dir, holder);
            FakeDeviceApi fake = FakeDeviceApi.withGpu().rejectBuildWhen(src -> true, "no");
            List<GpuEvent> events = new ArrayList<>();
            try (GpuContext ctx = GpuContext.builder().api(fake).ignoreEnvironment().listener(events::add)
                    .fallback(GpuContext.Fallback.ALLOWED).build(); JavaToGpu.Scope s = JavaToGpu.use(ctx)) {
                float[] v = values(100);
                int want = 0;
                for (float x : v) {
                    want += x > 0f ? 1 : 0;
                }
                assertEquals(want, call(sums, "positives", v, 100));
                assertTrue(events.stream().anyMatch(e -> e.kind() == GpuEvent.Kind.FALLBACK), events.toString());
            }
        } finally {
            holder[0].close();
        }
    }
}
