package com.sixik.gpu.runtime;

import com.sixik.gpu.api.types.Float4;
import com.sixik.gpu.runtime.device.FakeDeviceApi;
import com.sixik.gpu.weaver.KernelCompiler;
import com.sixik.gpu.weaver.KernelIndex;
import com.sixik.gpu.weaver.Weaver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Хост-фасад {@link JavaToGpu} с векторами и {@code @Struct}: массив структур и вектор по значению проходят сборку
 * (javac + processor + weaver), обычный вызов метода уходит на устройство, а при отказе драйвера те же значения
 * получает JVM-тело. Устройство - {@link FakeDeviceApi}.
 */
@SuppressWarnings("try")
class JavaToGpuAggregateTest {

    private static final String SOURCE = """
            package demo;

            import com.sixik.gpu.api.Gpu;
            import com.sixik.gpu.api.annotations.Global;
            import com.sixik.gpu.api.annotations.Kernel;
            import com.sixik.gpu.api.annotations.Struct;
            import com.sixik.gpu.api.types.Float4;

            public class Bodies {

                @Struct
                public static final class P {
                    public float x;
                    public Float4 v;

                    public P() {
                        this.v = new Float4(0, 0, 0, 0);
                    }

                    public P(float x, Float4 v) {
                        this.x = x;
                        this.v = v;
                    }

                    public P copy() {
                        return new P(x, v);
                    }
                }

                @Kernel
                public static void shift(@Global P[] ps, Float4 d) {
                    int g = Gpu.getGlobalId(0);
                    P p = ps[g].copy();
                    p.x = p.x + d.x;
                    p.v = p.v.add(d);
                    ps[g] = p.copy();
                }

                @Kernel
                public static void lengthSquared(@Global Float4[] in, @Global float[] out) {
                    int g = Gpu.getGlobalId(0);
                    Float4 a = in[g];
                    out[g] = a.x * a.x + a.y * a.y + a.z * a.z + a.w * a.w;
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
        assertEquals(2, report.kernels(), report.problems().toString());
        holder[0] = new URLClassLoader(new URL[]{dir.toUri().toURL()}, JavaToGpuAggregateTest.class.getClassLoader());
        return holder[0].loadClass("demo.Bodies");
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

    private static GpuContext context(FakeDeviceApi fake, List<GpuEvent> events) {
        return GpuContext.builder().api(fake).ignoreEnvironment().listener(events::add)
                .fallback(GpuContext.Fallback.ALLOWED).build();
    }

    /** Создаёт массив частиц {@code demo.Bodies$P} по значениям {@code x} и {@code v.y}. */
    private static Object[] particles(Class<?> bodies, float... xs) throws Exception {
        Class<?> p = bodies.getClassLoader().loadClass("demo.Bodies$P");
        Object[] arr = (Object[]) Array.newInstance(p, xs.length);
        for (int i = 0; i < xs.length; i++) {
            arr[i] = p.getConstructor(float.class, Float4.class).newInstance(xs[i], new Float4(0, xs[i] * 10, 0, 1));
        }
        return arr;
    }

    private static float x(Object particle) throws Exception {
        return particle.getClass().getField("x").getFloat(particle);
    }

    private static Float4 v(Object particle) throws Exception {
        return (Float4) particle.getClass().getField("v").get(particle);
    }

    @Test
    void indexDescribesObjectParameters(@TempDir Path dir) throws Exception {
        URLClassLoader[] holder = new URLClassLoader[1];
        try {
            woven(dir, holder);
            KernelIndex idx = KernelIndex.parse(Files.readString(dir.resolve(KernelIndex.PATH), StandardCharsets.UTF_8));
            assertEquals(2, idx.size());
            assertFalse(idx.kernelId("demo.Bodies", "shift", "([Ldemo/Bodies$P;Lcom/sixik/gpu/api/types/Float4;)V").isEmpty());
            assertFalse(idx.kernelId("demo.Bodies", "lengthSquared", "([Lcom/sixik/gpu/api/types/Float4;[F)V").isEmpty());
        } finally {
            if (holder[0] != null) {
                holder[0].close();
            }
        }
    }

    @Test
    void structArrayAndVectorParameterRunOnTheDevice(@TempDir Path dir) throws Exception {
        URLClassLoader[] holder = new URLClassLoader[1];
        // P: float x @0, float4 v @16, размер 32
        FakeDeviceApi fake = FakeDeviceApi.withGpu().onKernel("k0", l -> {
            java.nio.ByteBuffer d = l.bytesArg(2);
            for (int i = 0; i < l.intArg(1); i++) {
                int at = i * 32;
                l.buffer(0).putFloat(at, l.buffer(0).getFloat(at) + d.getFloat(0));
                for (int c = 0; c < 4; c++) {
                    l.buffer(0).putFloat(at + 16 + 4 * c, l.buffer(0).getFloat(at + 16 + 4 * c) + d.getFloat(4 * c));
                }
            }
        });
        List<GpuEvent> events = new ArrayList<>();
        try (GpuContext ctx = context(fake, events); var scope = JavaToGpu.use(ctx)) {
            Class<?> bodies = woven(dir, holder);
            Object[] ps = particles(bodies, 1, 2, 3);
            Object first = ps[0];
            call(bodies, "shift", ps, new Float4(0.5f, 1, 2, 3));
            assertEquals(1, fake.enqueues());
            assertSame(first, ps[0], "struct обновляется на месте");
            assertEquals(1.5f, x(ps[0]));
            assertEquals(3.5f, x(ps[2]));
            assertEquals(new Float4(0.5f, 11, 2, 4), v(ps[0]));
            assertEquals(new Float4(0.5f, 31, 2, 4), v(ps[2]));
            assertFalse(events.stream().anyMatch(e -> e.kind() == GpuEvent.Kind.FALLBACK));
        } finally {
            if (holder[0] != null) {
                holder[0].close();
            }
        }
    }

    @Test
    void vectorArrayIsReadOnlyForTheCaller(@TempDir Path dir) throws Exception {
        URLClassLoader[] holder = new URLClassLoader[1];
        FakeDeviceApi fake = FakeDeviceApi.withGpu().onKernel("k0", l -> {
            for (int i = 0; i < l.intArg(1); i++) {
                float s = 0;
                for (int c = 0; c < 4; c++) {
                    float f = l.buffer(0).getFloat(16 * i + 4 * c);
                    s += f * f;
                }
                l.buffer(2).putFloat(4 * i, s);
            }
        });
        try (GpuContext ctx = context(fake, new ArrayList<>()); var scope = JavaToGpu.use(ctx)) {
            Class<?> bodies = woven(dir, holder);
            Float4[] in = {new Float4(1, 2, 3, 4), new Float4(0, 0, 2, 0)};
            Float4 before = in[0];
            float[] out = new float[2];
            call(bodies, "lengthSquared", in, out);
            assertEquals(30f, out[0]);
            assertEquals(4f, out[1]);
            assertSame(before, in[0]);
        } finally {
            if (holder[0] != null) {
                holder[0].close();
            }
        }
    }

    @Test
    void jvmFallbackGivesTheSameValuesAsTheDevice(@TempDir Path dir) throws Exception {
        URLClassLoader[] holder = new URLClassLoader[1];
        FakeDeviceApi fake = FakeDeviceApi.withGpu().rejectBuildWhen(s -> true, "<kernel>:1:1: error: no");
        List<GpuEvent> events = new ArrayList<>();
        try (GpuContext ctx = context(fake, events); var scope = JavaToGpu.use(ctx)) {
            Class<?> bodies = woven(dir, holder);
            Object[] ps = particles(bodies, 1, 2, 3);
            call(bodies, "shift", ps, new Float4(0.5f, 1, 2, 3));
            assertEquals(0, fake.enqueues());
            assertEquals(1.5f, x(ps[0]));
            assertEquals(3.5f, x(ps[2]));
            assertEquals(new Float4(0.5f, 11, 2, 4), v(ps[0]));
            assertEquals(new Float4(0.5f, 31, 2, 4), v(ps[2]));
            assertTrue(events.stream().anyMatch(e -> e.kind() == GpuEvent.Kind.FALLBACK));

            float[] out = new float[2];
            call(bodies, "lengthSquared", new Float4[]{new Float4(1, 2, 3, 4), new Float4(0, 0, 2, 0)}, out);
            assertEquals(30f, out[0]);
            assertEquals(4f, out[1]);
        } finally {
            if (holder[0] != null) {
                holder[0].close();
            }
        }
    }
}
