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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link JavaToGpu#warmUp}: фоновая сборка ядер и свёрток класса и то, что сборка драйвером больше не держит замок
 * контекста - запуск уже собранного ядра не ждёт идущей сборки, а закрытие контекста её дожидается. Сквозной путь:
 * javac с processor'ом, weaver, вызовы через фасад; устройство - {@link FakeDeviceApi} (сборка может длиться заданное
 * время, тела ядер ничего не делают).
 */
@SuppressWarnings("try") // области JavaToGpu.use открываются ради эффекта
class WarmUpTest {

    private static final String SOURCE = """
            package demo;

            import com.sixik.gpu.api.Gpu;
            import com.sixik.gpu.api.annotations.Kernel;
            import com.sixik.gpu.api.annotations.Reduction;
            import com.sixik.gpu.api.types.GpuIntArray;

            public class W {

                @Kernel
                public static void fill(GpuIntArray out, int v) {
                    out.set(Gpu.getGlobalId(0), v);
                }

                @Kernel
                public static void twice(GpuIntArray a) {
                    int i = Gpu.getGlobalId(0);
                    a.set(i, a.get(i) * 2);
                }

                @Reduction
                public static int sum(GpuIntArray v) {
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
    private Class<?> w;
    private FakeDeviceApi fake;
    private GpuContext ctx;

    @BeforeEach
    void open() throws Exception {
        KernelCompiler.Compiled r = KernelCompiler.compile(dir, SOURCE);
        assertTrue(r.success(), r.diagnostics());
        Weaver.Report report = Weaver.weaveTree(dir);
        assertEquals(3, report.kernels(), report.problems().toString());
        loader = new URLClassLoader(new URL[]{dir.toUri().toURL()}, WarmUpTest.class.getClassLoader());
        w = loader.loadClass("demo.W");
        fake = FakeDeviceApi.withGpu().onAnyKernel(l -> { });
        ctx = GpuContext.builder().api(fake).ignoreEnvironment().noDiskCache().fusion(false)
                .fallback(GpuContext.Fallback.NEVER).build();
    }

    @AfterEach
    void close() throws Exception {
        ctx.close();
        JavaToGpu.resetDefault();
        loader.close();
    }

    private Object call(String method, Object... args) throws Exception {
        for (Method m : w.getDeclaredMethods()) {
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

    @Test
    void warmUpBuildsEveryKernelAndReductionSoTheFirstCallHitsTheCache() throws Exception {
        JavaToGpu.WarmUp done;
        try (JavaToGpu.Scope s = JavaToGpu.use(ctx)) {
            done = JavaToGpu.warmUp(w).get(10, TimeUnit.SECONDS);
        }
        assertEquals(3, done.compiled(), done.problems().toString());
        assertTrue(done.problems().isEmpty());
        int builds = fake.builds();
        try (JavaToGpu.Scope s = JavaToGpu.use(ctx)) {
            GpuIntArray a = new GpuIntArray(64);
            call("fill", a, 3);
            call("twice", a);
            call("sum", a);
        }
        assertEquals(builds, fake.builds(), "the first calls compile nothing");
    }

    @Test
    void aLaunchDoesNotWaitForABuildInProgress() throws Exception {
        GpuIntArray a = new GpuIntArray(64);
        try (JavaToGpu.Scope s = JavaToGpu.use(ctx)) {
            call("fill", a, 1); // собрано заранее
            fake.buildDelay(800);
            CompletableFuture<JavaToGpu.WarmUp> warm = JavaToGpu.warmUp(w); // twice и sum - по 800 мс
            Thread.sleep(100); // прогрев уже внутри сборки драйвером
            long t = System.nanoTime();
            call("fill", a, 2);
            long ms = (System.nanoTime() - t) / 1_000_000;
            assertTrue(ms < 400, "a launch waited " + ms + " ms for an unrelated build");
            JavaToGpu.WarmUp done = warm.get(10, TimeUnit.SECONDS);
            assertEquals(3, done.compiled(), done.problems().toString());
        }
    }

    @Test
    void closingTheContextWaitsForTheBuildInProgress() throws Exception {
        fake.buildDelay(300);
        CompletableFuture<JavaToGpu.WarmUp> warm;
        try (JavaToGpu.Scope s = JavaToGpu.use(ctx)) {
            warm = JavaToGpu.warmUp(w);
        }
        Thread.sleep(100); // первая сборка идёт
        ctx.close();
        JavaToGpu.WarmUp done = warm.get(10, TimeUnit.SECONDS);
        assertTrue(done.compiled() <= 1, "at most the build in progress finished: " + done);
        assertTrue(done.problems().stream().anyMatch(d -> d.code().equals("RT-X02")), done.problems().toString());
        assertEquals(0, fake.livePrograms(), "no program outlives the context");
        assertEquals(0, fake.liveKernels());
    }
}
