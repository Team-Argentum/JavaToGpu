package com.sixik.gpu.runtime;

import com.sixik.gpu.ir.Fixtures;
import com.sixik.gpu.ir.IrBinary;
import com.sixik.gpu.ir.IrModule;
import com.sixik.gpu.runtime.device.FakeDeviceApi;
import com.sixik.gpu.weaver.KernelCompiler;
import com.sixik.gpu.weaver.KernelIndex;
import com.sixik.gpu.weaver.Weaver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Хост-фасад {@link JavaToGpu} сквозным путём без железа: исходник компилируется javac с processor'ом
 * {@code frontend-java}, класс подменяется weaver'ом, загружается, и ядро вызывается обычным вызовом Java-метода.
 * Устройство - {@link FakeDeviceApi}.
 */
@SuppressWarnings("try") // области JavaToGpu.use/launch открываются ради эффекта, переменная не нужна
class JavaToGpuTest {

    private static final String SOURCE = """
            package demo;

            import com.sixik.gpu.api.Gpu;
            import com.sixik.gpu.api.annotations.Global;
            import com.sixik.gpu.api.annotations.Kernel;

            public class Demo {

                @Kernel
                public static void scale(@Global float[] in, @Global float[] out, float k) {
                    int g = Gpu.getGlobalId(0);
                    out[g] = in[g] * k;
                }

                @Kernel
                public static void ids(@Global int[] out) {
                    int x = Gpu.getGlobalId(0);
                    int y = Gpu.getGlobalId(1);
                    out[y * Gpu.getGlobalSize(0) + x] = x * 100 + y * 10 + Gpu.getLocalId(0);
                }

                @Kernel
                public static void noArrays(int a) {
                    int g = Gpu.getGlobalId(0);
                }

                @Kernel
                public static void boom(@Global int[] out) {
                    out[Gpu.getGlobalId(0) + 1000] = 1;
                }
            }
            """;

    @AfterEach
    void reset() {
        JavaToGpu.resetDefault();
    }

    // ---- сборка образца -------------------------------------------------------------------------

    /** Компилирует, подменяет и загружает {@code demo.Demo}. */
    private static Class<?> woven(Path dir, URLClassLoader[] holder) throws Exception {
        KernelCompiler.Compiled r = KernelCompiler.compile(dir, SOURCE);
        assertTrue(r.success(), r.diagnostics());
        Weaver.Report report = Weaver.weaveTree(dir);
        assertEquals(4, report.kernels(), report.problems().toString());
        holder[0] = new URLClassLoader(new URL[]{dir.toUri().toURL()}, JavaToGpuTest.class.getClassLoader());
        return holder[0].loadClass("demo.Demo");
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

    private static GpuContext context(FakeDeviceApi fake, List<GpuEvent> events, GpuContext.Fallback fallback) {
        return GpuContext.builder().api(fake).ignoreEnvironment().listener(events::add).fallback(fallback).build();
    }

    private static FakeDeviceApi scaleDevice(List<long[]> locals, List<Long> globals) {
        return FakeDeviceApi.withGpu().onKernel("k0", l -> {
            locals.add(l.local());
            globals.add(l.global(0));
            for (int i = 0; i < l.intArg(1); i++) {
                l.buffer(2).putFloat(4 * i, l.buffer(0).getFloat(4 * i) * l.floatArg(4));
            }
        });
    }

    // ---- сборка даёт то, что нужно фасаду -------------------------------------------------------

    @Test
    void buildProducesWovenClassAndIrResources(@TempDir Path dir) throws Exception {
        URLClassLoader[] holder = new URLClassLoader[1];
        try {
            Class<?> demo = woven(dir, holder);
            assertNotNull(demo.getDeclaredMethod("scale", float[].class, float[].class, float.class));
            assertNotNull(demo.getDeclaredMethod("scale$jvm", float[].class, float[].class, float.class));
            KernelIndex idx = KernelIndex.parse(Files.readString(dir.resolve(KernelIndex.PATH), StandardCharsets.UTF_8));
            assertEquals(4, idx.size());
            String id = idx.kernelId("demo.Demo", "scale", "([F[FF)V");
            assertTrue(Files.isRegularFile(dir.resolve("META-INF/gpu/ir/" + id + ".gir")));
        } finally {
            holder[0].close();
        }
    }

    // ---- подготовленный запуск ------------------------------------------------------------------

    @Test
    void facadePreparesRepeatedLaunchesOfAKernelByName(@TempDir Path dir) throws Exception {
        URLClassLoader[] holder = new URLClassLoader[1];
        FakeDeviceApi fake = scaleDevice(new ArrayList<>(), new ArrayList<>());
        try (GpuContext ctx = context(fake, new ArrayList<>(), GpuContext.Fallback.ALLOWED); var scope = JavaToGpu.use(ctx)) {
            Class<?> demo = woven(dir, holder);
            float[] in = {1, 2, 3};
            float[] out = new float[3];
            try (PreparedLauncher p = JavaToGpu.prepare(demo, "scale", in, out, 2f)) {
                p.run(LaunchConfig.of1D(3));
                assertArrayEquals(new float[]{2, 4, 6}, out);
                p.set(2, 10f).run(LaunchConfig.of1D(3));
                assertArrayEquals(new float[]{10, 20, 30}, out);
            }
            assertEquals(2, fake.enqueues());
            assertEquals(0, fake.liveBuffers(), "the launch is closed - buffers released");

            GpuException missing = assertThrows(GpuException.class, () -> JavaToGpu.prepare(demo, "nope", in, out, 2f));
            assertEquals("RT-F02", missing.diagnostics().get(0).code());
            GpuException wrong = assertThrows(GpuException.class, () -> JavaToGpu.prepare(demo, "scale", in));
            assertEquals("RT-A01", wrong.diagnostics().get(0).code());
        } finally {
            holder[0].close();
        }
    }

    @Test
    void facadePrepareWithoutDeviceFailsInsteadOfFallingBack(@TempDir Path dir) throws Exception {
        URLClassLoader[] holder = new URLClassLoader[1];
        JavaToGpu.configureDefault(b -> b.api(new FakeDeviceApi()).ignoreEnvironment());
        try {
            Class<?> demo = woven(dir, holder);
            GpuException e = assertThrows(GpuException.class, () -> JavaToGpu.prepare(demo, "scale", new float[1], new float[1], 1f));
            assertEquals("RT-D01", e.diagnostics().get(0).code());
        } finally {
            holder[0].close();
        }
    }

    // ---- устройство -----------------------------------------------------------------------------

    @Test
    void ordinaryCallRunsOnTheDevice(@TempDir Path dir) throws Exception {
        URLClassLoader[] holder = new URLClassLoader[1];
        List<long[]> locals = new ArrayList<>();
        List<Long> globals = new ArrayList<>();
        FakeDeviceApi fake = scaleDevice(locals, globals);
        List<GpuEvent> events = new ArrayList<>();
        try (GpuContext ctx = context(fake, events, GpuContext.Fallback.ALLOWED); var scope = JavaToGpu.use(ctx)) {
            Class<?> demo = woven(dir, holder);
            float[] in = {1, 2, 3, 4, 5};
            float[] out = new float[5];
            call(demo, "scale", in, out, 2f);
            assertArrayEquals(new float[]{2, 4, 6, 8, 10}, out);
            assertEquals(1, fake.enqueues());
            assertEquals(List.of(5L), globals, "the default launch size is the length of the first array");
            assertEquals(null, locals.get(0));
            assertFalse(events.stream().anyMatch(e -> e.kind() == GpuEvent.Kind.FALLBACK));
        } finally {
            holder[0].close();
        }
    }

    @Test
    void launchScopeOverridesTheDefaultSize(@TempDir Path dir) throws Exception {
        URLClassLoader[] holder = new URLClassLoader[1];
        List<long[]> locals = new ArrayList<>();
        List<Long> globals = new ArrayList<>();
        FakeDeviceApi fake = scaleDevice(locals, globals);
        try (GpuContext ctx = context(fake, new ArrayList<>(), GpuContext.Fallback.ALLOWED); var s = JavaToGpu.use(ctx)) {
            Class<?> demo = woven(dir, holder);
            float[] in = new float[8];
            float[] out = new float[8];
            try (var l = JavaToGpu.launch(LaunchConfig.of1D(8, 4))) {
                call(demo, "scale", in, out, 1f);
            }
            call(demo, "scale", in, out, 1f);
            assertArrayEquals(new long[]{4}, locals.get(0));
            assertEquals(null, locals.get(1), "after the scope closes, sizes are back to default");
        } finally {
            holder[0].close();
        }
    }

    @Test
    void scopesNestAndAreThreadLocal() throws Exception {
        FakeDeviceApi a = FakeDeviceApi.withGpu();
        FakeDeviceApi b = FakeDeviceApi.withGpu();
        try (GpuContext one = context(a, new ArrayList<>(), GpuContext.Fallback.ALLOWED);
             GpuContext two = context(b, new ArrayList<>(), GpuContext.Fallback.ALLOWED)) {
            JavaToGpu.Scope outer = JavaToGpu.use(one);
            JavaToGpu.Scope inner = JavaToGpu.use(two);
            assertThrows(IllegalStateException.class, outer::close, "closing out of opening order");
            inner.close();
            inner.close(); // повторное закрытие безопасно
            outer.close();
            Throwable[] other = new Throwable[1];
            Thread t = new Thread(() -> {
                try {
                    JavaToGpu.Scope s = JavaToGpu.use(one);
                    s.close();
                } catch (Throwable e) {
                    other[0] = e;
                }
            });
            t.start();
            t.join();
            assertEquals(null, other[0]);
        }
        assertThrows(IllegalArgumentException.class, () -> JavaToGpu.use(null));
        assertThrows(IllegalArgumentException.class, () -> JavaToGpu.launch(null));
    }

    @Test
    void twoDimensionalLaunchOnTheDevice(@TempDir Path dir) throws Exception {
        URLClassLoader[] holder = new URLClassLoader[1];
        List<long[]> locals = new ArrayList<>();
        FakeDeviceApi fake = FakeDeviceApi.withGpu().onKernel("k0", l -> {
            assertEquals(2, l.dim());
            assertEquals(4, l.global(0));
            assertEquals(2, l.global(1));
            locals.add(l.local());
        });
        try (GpuContext ctx = context(fake, new ArrayList<>(), GpuContext.Fallback.ALLOWED); var s = JavaToGpu.use(ctx);
             var l = JavaToGpu.launch(LaunchConfig.of2D(4, 2, 2, 1))) {
            call(woven(dir, holder), "ids", new int[8]);
            assertArrayEquals(new long[]{2, 1}, locals.get(0));
        } finally {
            holder[0].close();
        }
    }

    // ---- JVM-fallback ---------------------------------------------------------------------------

    @Test
    void kernelRunsOnTheJvmWhenTheDriverRejectsIt(@TempDir Path dir) throws Exception {
        URLClassLoader[] holder = new URLClassLoader[1];
        FakeDeviceApi fake = FakeDeviceApi.withGpu().rejectBuildWhen(s -> true, "<kernel>:3:1: error: no");
        List<GpuEvent> events = new ArrayList<>();
        try (GpuContext ctx = context(fake, events, GpuContext.Fallback.ALLOWED); var s = JavaToGpu.use(ctx)) {
            Class<?> demo = woven(dir, holder);
            float[] in = {1, 2, 3};
            float[] out = new float[3];
            call(demo, "scale", in, out, 10f);
            call(demo, "scale", in, out, 100f);
            assertArrayEquals(new float[]{100, 200, 300}, out, "ran on the JVM");
            assertEquals(0, fake.enqueues());
            List<GpuEvent> fallbacks = events.stream().filter(e -> e.kind() == GpuEvent.Kind.FALLBACK).toList();
            assertEquals(1, fallbacks.size(), "one warning per kernel");
            assertTrue(fallbacks.get(0).detail().contains("RT-C01"), fallbacks.get(0).detail());
            assertTrue(fallbacks.get(0).detail().contains("line 3:1: no"), "the warning contains the parsed driver build log: " + fallbacks.get(0).detail());
        } finally {
            holder[0].close();
        }
    }

    @Test
    void jvmFallbackIsASequentialLoopOverTheNdRange(@TempDir Path dir) throws Exception {
        URLClassLoader[] holder = new URLClassLoader[1];
        FakeDeviceApi fake = FakeDeviceApi.withGpu().rejectBuildWhen(s -> true, "boom");
        try (GpuContext ctx = context(fake, new ArrayList<>(), GpuContext.Fallback.ALLOWED); var s = JavaToGpu.use(ctx);
             var l = JavaToGpu.launch(LaunchConfig.of2D(4, 2, 2, 1))) {
            int[] out = new int[8];
            call(woven(dir, holder), "ids", out);
            // x*100 + y*10 + localId(0), local = 2x1
            assertArrayEquals(new int[]{0, 101, 200, 301, 10, 111, 210, 311}, out);
        } finally {
            holder[0].close();
        }
    }

    @Test
    void strictContextRefusesToFallBack(@TempDir Path dir) throws Exception {
        URLClassLoader[] holder = new URLClassLoader[1];
        FakeDeviceApi fake = FakeDeviceApi.withGpu().rejectBuildWhen(s -> true, "<kernel>:3:1: error: no");
        try (GpuContext ctx = context(fake, new ArrayList<>(), GpuContext.Fallback.NEVER); var s = JavaToGpu.use(ctx)) {
            Class<?> demo = woven(dir, holder);
            GpuException e = assertThrows(GpuException.class, () -> call(demo, "scale", new float[2], new float[2], 1f));
            assertEquals("RT-C01", e.diagnostics().get(0).code());
        } finally {
            holder[0].close();
        }
    }

    @Test
    void userErrorsAreNotMaskedByFallback(@TempDir Path dir) throws Exception {
        URLClassLoader[] holder = new URLClassLoader[1];
        FakeDeviceApi fake = scaleDevice(new ArrayList<>(), new ArrayList<>());
        try (GpuContext ctx = context(fake, new ArrayList<>(), GpuContext.Fallback.ALLOWED); var s = JavaToGpu.use(ctx);
             var l = JavaToGpu.launch(LaunchConfig.of1D(10, 4))) {
            Class<?> demo = woven(dir, holder);
            GpuException e = assertThrows(GpuException.class, () -> call(demo, "scale", new float[10], new float[10], 1f));
            assertEquals("RT-L01", e.diagnostics().get(0).code(), "the size is not divisible by the group");
        } finally {
            holder[0].close();
        }
    }

    @Test
    void exceptionsOfTheKernelBodyPropagateFromTheJvm(@TempDir Path dir) throws Exception {
        URLClassLoader[] holder = new URLClassLoader[1];
        FakeDeviceApi fake = FakeDeviceApi.withGpu().rejectBuildWhen(s -> true, "x");
        try (GpuContext ctx = context(fake, new ArrayList<>(), GpuContext.Fallback.ALLOWED); var s = JavaToGpu.use(ctx)) {
            Class<?> demo = woven(dir, holder);
            assertThrows(ArrayIndexOutOfBoundsException.class, () -> call(demo, "boom", new int[4]));
        } finally {
            holder[0].close();
        }
    }

    // ---- контекст по умолчанию ------------------------------------------------------------------

    @Test
    void defaultContextIsCreatedLazilyAndClosedOnReset(@TempDir Path dir) throws Exception {
        URLClassLoader[] holder = new URLClassLoader[1];
        FakeDeviceApi fake = scaleDevice(new ArrayList<>(), new ArrayList<>());
        List<GpuEvent> events = new ArrayList<>();
        JavaToGpu.configureDefault(b -> b.api(fake).ignoreEnvironment().listener(events::add));
        try {
            assertEquals(0, fake.liveContexts(), "there is no context before the first access");
            float[] out = new float[3];
            call(woven(dir, holder), "scale", new float[]{1, 2, 3}, out, 2f);
            assertArrayEquals(new float[]{2, 4, 6}, out);
            assertEquals(1, fake.liveContexts());
            assertTrue(JavaToGpu.defaultContext().isPresent());
            assertEquals(GpuEvent.Kind.DEVICE_SELECTED, events.get(0).kind());
            assertThrows(IllegalStateException.class, () -> JavaToGpu.configureDefault(b -> {
            }), "it cannot be configured after creation");
        } finally {
            holder[0].close();
        }
        JavaToGpu.resetDefault();
        assertEquals(0, fake.liveContexts());
    }

    @Test
    void noDeviceMeansJvmAndNoRetries(@TempDir Path dir) throws Exception {
        URLClassLoader[] holder = new URLClassLoader[1];
        FakeDeviceApi nothing = new FakeDeviceApi();
        JavaToGpu.configureDefault(b -> b.api(nothing).ignoreEnvironment());
        try {
            Class<?> demo = woven(dir, holder);
            float[] out = new float[3];
            call(demo, "scale", new float[]{1, 2, 3}, out, 3f);
            assertArrayEquals(new float[]{3, 6, 9}, out, "no device - the kernel ran on the JVM");
            int callsAfterFirst = nothing.journal().size();
            call(demo, "scale", new float[]{1, 2, 3}, out, 4f);
            assertArrayEquals(new float[]{4, 8, 12}, out);
            assertEquals(callsAfterFirst, nothing.journal().size(), "no repeated attempts to find a device");
            assertEquals(Optional.empty(), JavaToGpu.defaultContext());
        } finally {
            holder[0].close();
        }
    }

    // ---- размер запуска и ошибки ---------------------------------------------------------------

    @Test
    void sizeWithoutArraysMustBeGiven(@TempDir Path dir) throws Exception {
        URLClassLoader[] holder = new URLClassLoader[1];
        FakeDeviceApi nothing = new FakeDeviceApi();
        JavaToGpu.configureDefault(b -> b.api(nothing).ignoreEnvironment());
        try {
            Class<?> demo = woven(dir, holder);
            GpuException e = assertThrows(GpuException.class, () -> call(demo, "noArrays", 5));
            assertEquals("RT-L02", e.diagnostics().get(0).code());
            try (var l = JavaToGpu.launch(LaunchConfig.of1D(4))) {
                call(demo, "noArrays", 5); // с явным размером выполняется (на JVM: устройства нет)
            }
        } finally {
            holder[0].close();
        }
    }

    @Test
    void missingIrResourceIsReported(@TempDir Path dir) throws Exception {
        URLClassLoader[] holder = new URLClassLoader[1];
        JavaToGpu.configureDefault(b -> b.api(new FakeDeviceApi()).ignoreEnvironment());
        try {
            Class<?> demo = woven(dir, holder);
            KernelIndex idx = KernelIndex.parse(Files.readString(dir.resolve(KernelIndex.PATH)));
            String id = idx.kernelId("demo.Demo", "scale", "([F[FF)V");
            Files.delete(dir.resolve("META-INF/gpu/ir/" + id + ".gir"));
            GpuException e = assertThrows(GpuException.class, () -> call(demo, "scale", new float[1], new float[1], 1f));
            assertEquals("RT-F02", e.diagnostics().get(0).code());
            assertTrue(e.getMessage().contains("frontend-java"));
        } finally {
            holder[0].close();
        }
    }

    @Test
    void classWithoutJvmBodyIsReported(@TempDir Path dir) throws Exception {
        KernelCompiler.Compiled r = KernelCompiler.compile(dir, SOURCE);
        assertTrue(r.success(), r.diagnostics());
        KernelIndex idx = KernelIndex.parse(Files.readString(dir.resolve(KernelIndex.PATH)));
        String id = idx.kernelId("demo.Demo", "ids", "([I)V");
        try (URLClassLoader loader = new URLClassLoader(new URL[]{dir.toUri().toURL()}, getClass().getClassLoader())) {
            Class<?> demo = loader.loadClass("demo.Demo");
            JavaToGpu.configureDefault(b -> b.api(new FakeDeviceApi()).ignoreEnvironment());
            GpuException e = assertThrows(GpuException.class,
                    () -> JavaToGpu.invoke(demo, "ids", id, new Object[]{new int[2]}));
            assertEquals("RT-F03", e.diagnostics().get(0).code());
        }
    }

    @Test
    void kernelsWithBarriersNeverFallBack(@TempDir Path dir) throws Exception {
        // настоящее Java-ядро с barrier и @Local: устройства нет, а на JVM такое ядро не выполняется (RT-F01)
        KernelCompiler.Compiled r = KernelCompiler.compile(dir, """
                package demo;
                import com.sixik.gpu.api.Gpu;
                import com.sixik.gpu.api.annotations.*;
                public class Sync {
                    @Kernel @WorkGroup(x = 4)
                    public static void run(@Global int[] a) {
                        @Local int[] t = new int[4];
                        t[Gpu.getLocalId(0)] = 1;
                        Gpu.barrier();
                        a[Gpu.getGlobalId(0)] = t[0];
                    }
                }
                """);
        assertTrue(r.success(), r.diagnostics());
        assertEquals(1, Weaver.weaveTree(dir).kernels());
        JavaToGpu.configureDefault(b -> b.api(new FakeDeviceApi()).ignoreEnvironment());
        try (URLClassLoader loader = new URLClassLoader(new URL[]{dir.toUri().toURL()}, getClass().getClassLoader())) {
            Class<?> sync = loader.loadClass("demo.Sync");
            GpuException e = assertThrows(GpuException.class, () -> call(sync, "run", new int[4]));
            assertEquals("RT-F01", e.diagnostics().get(0).code());
        }
    }
}
