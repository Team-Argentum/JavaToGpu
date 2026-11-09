package com.sixik.gpu.test;

import com.sixik.gpu.runtime.GpuContext;
import com.sixik.gpu.runtime.JavaToGpu;
import com.sixik.gpu.runtime.device.FakeDeviceApi;
import com.sixik.gpu.test.KernelSample.Leg;
import com.sixik.gpu.weaver.KernelCompiler;
import com.sixik.gpu.weaver.Weaver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.opentest4j.AssertionFailedError;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.ByteBuffer;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code GpuAssert} на настоящем ядре, собранном и подменённом weaver-ом в самом тесте: выбор тела {@code <имя>$jvm},
 * обнаружение расхождений на фейковом устройстве, исключения JVM, опции образца. Реальное устройство не нужно.
 */
class GpuAssertTest {

    private static final String SOURCE = """
            package demo;

            import com.sixik.gpu.api.Gpu;
            import com.sixik.gpu.api.annotations.*;

            public class Kernels {

                @Kernel
                public static void scale(@Global float[] out, @Global float[] in, float k) {
                    int g = Gpu.getGlobalId(0);
                    out[g] = in[g] * k;
                }

                @Kernel
                public static void outOfBounds(@Global int[] out) {
                    out[Gpu.getGlobalId(0) + 1000] = 1;
                }

                @Kernel
                public static void divide(@Global float[] out, @Global float[] in) {
                    int g = Gpu.getGlobalId(0);
                    out[g] = 1.0f / in[g];
                }

                @Kernel
                public static void sine(@Global float[] out, @Global float[] in) {
                    int g = Gpu.getGlobalId(0);
                    out[g] = Gpu.sin(in[g]);
                }

                @Kernel
                public static void plain(@Global int[] out) {
                    out[Gpu.getGlobalId(0)] = 7;
                }

                @Kernel
                public static void preciseVariant(@Global double[] out, @Global double[] in) {
                    int g = Gpu.getGlobalId(0);
                    out[g] = Gpu.sqrt(in[g]);
                }

                @Kernel(required = "INT64_ATOMICS", fallback = "preciseVariant")
                public static void variantSine(@Global double[] out, @Global double[] in) {
                    int g = Gpu.getGlobalId(0);
                    out[g] = Gpu.sin(in[g]);
                }
            }
            """;

    private URLClassLoader loader;
    private Class<?> kernels;

    @BeforeEach
    void build(@TempDir Path dir) throws Exception {
        KernelCompiler.Compiled r = KernelCompiler.compile(dir, SOURCE);
        assertTrue(r.success(), r.diagnostics());
        assertEquals(7, Weaver.weaveTree(dir).kernels());
        loader = new URLClassLoader(new URL[]{dir.toUri().toURL()}, getClass().getClassLoader());
        kernels = loader.loadClass("demo.Kernels");
    }

    @AfterEach
    void close() throws Exception {
        loader.close();
        JavaToGpu.resetDefault();
    }

    private static float[] ramp(int n) {
        float[] a = new float[n];
        for (int i = 0; i < n; i++) {
            a[i] = i * 0.75f + 1f;
        }
        return a;
    }

    /** Фейковое устройство, исполняющее {@code scale} как положено либо с ошибкой на {@code delta}. */
    private static FakeDeviceApi scaleDevice(float delta, int[] launches) {
        return FakeDeviceApi.withGpu().onKernel("k0", l -> {
            launches[0]++;
            ByteBuffer out = l.buffer(0);
            ByteBuffer in = l.buffer(2); // за каждым массивом идёт его длина
            float k = l.floatArg(4);
            for (int i = 0; i < l.global(0); i++) {
                out.putFloat(4 * i, in.getFloat(4 * i) * k + (i == 3 ? delta : 0f));
            }
        });
    }

    private static GpuContext open(FakeDeviceApi api) {
        return GpuContext.builder().api(api).ignoreEnvironment().build();
    }

    // ---- ветви -------------------------------------------------------------------------------------

    @Test
    @SuppressWarnings("try") // область use() нужна ради побочного эффекта
    void jvmBodyOfAWovenClassIsUsedNotTheFacade() {
        int[] launches = {0};
        FakeDeviceApi api = scaleDevice(0f, launches);
        try (GpuContext ctx = open(api); JavaToGpu.Scope scope = JavaToGpu.use(ctx)) {
            // если бы GpuAssert вызвал подменённый scale, запуск ушёл бы в фасад и на фейковое устройство
            GpuAssert.Report r = GpuAssert.sameResult(kernels, "scale", KernelSample.named("jvm only")
                    .args(() -> new Object[]{new float[16], ramp(16), 2f}).without(Leg.DEVICE).build(), null);
            assertEquals(java.util.List.of(Leg.JVM, Leg.INTERPRETER), r.ran());
            assertEquals(0, launches[0], "body <name>$jvm ran on the JVM, not through the facade");
            assertEquals(0, api.enqueues());
        }
    }

    @Test
    void allThreeLegsAgreeOnAFakeDeviceThatComputesCorrectly() {
        int[] launches = {0};
        try (GpuContext ctx = open(scaleDevice(0f, launches))) {
            GpuAssert.Report r = GpuAssert.sameResult(kernels, "scale", KernelSample.named("ok")
                    .args(() -> new Object[]{new float[16], ramp(16), 2f}).build(), ctx);
            assertEquals(java.util.List.of(Leg.JVM, Leg.INTERPRETER, Leg.DEVICE), r.ran());
            assertTrue(r.skipped().isEmpty());
            assertFalse(r.jvmThrew());
            assertEquals(1, launches[0]);
            assertTrue(r.device() != null && !r.device().isBlank());
            assertEquals(0, r.ulps(), "multiplication is exact: the tolerance from the IR is zero");
        }
    }

    @Test
    void aMismatchOnTheDeviceIsReportedWithTheArgumentAndTheElement() {
        try (GpuContext ctx = open(scaleDevice(1f, new int[1]))) {
            AssertionFailedError e = assertThrows(AssertionFailedError.class, () -> GpuAssert.sameResult(kernels, "scale",
                    KernelSample.named("broken").args(() -> new Object[]{new float[16], ramp(16), 2f}).build(), ctx));
            assertTrue(e.getMessage().contains("Kernels.scale [broken]"), e.getMessage());
            assertTrue(e.getMessage().contains("argument 0"), e.getMessage());
            assertTrue(e.getMessage().contains("[3]"), e.getMessage());
            assertTrue(e.getMessage().contains("JVM versus DEVICE"), e.getMessage());
        }
    }

    @Test
    void ignoredArgumentsAreNotCompared() {
        try (GpuContext ctx = open(scaleDevice(1f, new int[1]))) {
            GpuAssert.Report r = GpuAssert.sameResult(kernels, "scale", KernelSample.named("ignored")
                    .args(() -> new Object[]{new float[16], ramp(16), 2f}).ignoring(0).build(), ctx);
            assertTrue(r.ranOn(Leg.DEVICE));
        }
    }

    @Test
    void ulpToleranceComesFromTheKernelSIr() {
        // sin: допуск builtin-а из таблицы 8.3; деление: DEVICE_DIVISION_ULPS; умножение: 0
        int sin = GpuAssert.autoUlps(com.sixik.gpu.runtime.JavaToGpu.loadModule(kernels, "sine"));
        int div = GpuAssert.autoUlps(com.sixik.gpu.runtime.JavaToGpu.loadModule(kernels, "divide"));
        int mul = GpuAssert.autoUlps(com.sixik.gpu.runtime.JavaToGpu.loadModule(kernels, "scale"));
        assertTrue(sin > 0, "sin: " + sin);
        assertEquals(GpuAssert.DEVICE_DIVISION_ULPS, div);
        assertEquals(0, mul);
    }

    @Test
    void deviceWithinTheToleranceIsAcceptedAndOutsideIsNot() {
        // фейковое «деление» отличается от точного на 1 ULP: допуск деления (3 ULP) это прощает
        FakeDeviceApi api = FakeDeviceApi.withGpu().onKernel("k0", l -> {
            ByteBuffer out = l.buffer(0);
            ByteBuffer in = l.buffer(2);
            for (int i = 0; i < l.global(0); i++) {
                float exact = 1.0f / in.getFloat(4 * i);
                out.putFloat(4 * i, Float.intBitsToFloat(Float.floatToRawIntBits(exact) + 1));
            }
        });
        try (GpuContext ctx = open(api)) {
            KernelSample s = KernelSample.named("div").args(() -> new Object[]{new float[16], ramp(16)}).build();
            GpuAssert.Report r = GpuAssert.sameResult(kernels, "divide", s, ctx);
            assertTrue(r.ranOn(Leg.DEVICE));
            assertEquals(GpuAssert.DEVICE_DIVISION_ULPS, r.ulps());
            // явный допуск образца строже: 0 ULP
            KernelSample strict = KernelSample.named("strict").args(() -> new Object[]{new float[16], ramp(16)}).ulps(0).build();
            assertThrows(AssertionFailedError.class, () -> GpuAssert.sameResult(kernels, "divide", strict, ctx));
        }
    }

    /** Выбранный double-sqrt требует 0 ULP, хотя исходное ядро использует sin с допуском 4 ULP. */
    @Test
    void selectedFallbackVariant_doesNotInheritTheMainKernelsTolerance() {
        FakeDeviceApi api = new FakeDeviceApi(FakeDeviceApi.Spec.gpu().withExtensions("cl_khr_fp64"))
                .onKernel("k0", launch -> launch.buffer(0).putDouble(0, Double.MIN_VALUE));
        try (GpuContext ctx = open(api)) {
            KernelSample sample = KernelSample.named("selected sqrt").args(() -> new Object[]{new double[1], new double[1]}).build();
            AssertionFailedError error = assertThrows(AssertionFailedError.class,
                    () -> GpuAssert.sameResult(kernels, "variantSine", sample, ctx));
            assertTrue(error.getMessage().contains("tolerance 0 ULP"), error.getMessage());
        }
    }

    // ---- исключения JVM ----------------------------------------------------------------------------

    @Test
    void whenTheJvmThrowsTheInterpreterMustRefuseAndTheDeviceIsNotLaunched() {
        int[] launches = {0};
        FakeDeviceApi api = FakeDeviceApi.withGpu().onKernel("k0", l -> launches[0]++);
        try (GpuContext ctx = open(api)) {
            GpuAssert.Report r = GpuAssert.sameResult(kernels, "outOfBounds",
                    KernelSample.named("oob").args(() -> new Object[]{new int[16]}).build(), ctx);
            assertTrue(r.jvmThrew());
            assertEquals(java.util.List.of(Leg.INTERPRETER), r.ran());
            assertTrue(r.skipped().get(Leg.DEVICE).contains("device behavior is undefined"));
            assertEquals(0, launches[0]);
        }
    }

    @Test
    void anInterpreterThatAcceptsWhatTheJvmRejectsIsAFailure() {
        // JVM-ветвь отдельно от интерпретатора: подменяем IR-ветвь образцом, где JVM падает, а интерпретатор нет, нельзя
        // без искусственного ядра, поэтому проверяем сообщение для исключённой ветви INTERPRETER
        AssertionFailedError e = assertThrows(AssertionFailedError.class, () -> GpuAssert.sameResult(kernels, "outOfBounds",
                KernelSample.named("no interpreter").args(() -> new Object[]{new int[16]}).without(Leg.INTERPRETER, Leg.DEVICE).build(), null));
        assertTrue(e.getMessage().contains("the INTERPRETER branch is excluded"), e.getMessage());
    }

    // ---- пропуск ветвей и ошибки использования -----------------------------------------------------

    @Test
    void withoutADeviceTheBranchIsSkippedAndReported() {
        GpuAssert.Report r = GpuAssert.sameResult(kernels, "plain", KernelSample.named("plain")
                .args(() -> new Object[]{new int[8]}).build(), null);
        assertEquals("no device", r.skipped().get(Leg.DEVICE));
        assertEquals(java.util.List.of(Leg.JVM, Leg.INTERPRETER), r.ran());
        assertEquals(null, r.device());
    }

    @Test
    void onlyJvmLegSkipsTheInterpreter() {
        GpuAssert.Report r = GpuAssert.sameResult(kernels, "plain", KernelSample.named("jvm")
                .args(() -> new Object[]{new int[8]}).only(Leg.JVM).build(), null);
        assertEquals(java.util.List.of(Leg.JVM), r.ran());
        assertEquals("excluded by the sample", r.skipped().get(Leg.INTERPRETER));
    }

    @Test
    void expectedValuesAreCheckedAgainstTheReference() {
        int[] seven = new int[8];
        java.util.Arrays.fill(seven, 7);
        GpuAssert.sameResult(kernels, "plain", KernelSample.named("expected ok")
                .args(() -> new Object[]{new int[8]}).expected((Object) seven).build(), null);
        int[] wrong = seven.clone();
        wrong[5] = 8;
        AssertionFailedError e = assertThrows(AssertionFailedError.class, () -> GpuAssert.sameResult(kernels, "plain",
                KernelSample.named("expected wrong").args(() -> new Object[]{new int[8]}).expected((Object) wrong).build(), null));
        assertTrue(e.getMessage().contains("[5]") && e.getMessage().contains("expected"), e.getMessage());
    }

    @Test
    void usageErrorsAreExplicit() {
        assertThrows(IllegalArgumentException.class, () -> GpuAssert.sameResult(kernels, "plain", new int[8], 1));
        AssertionFailedError e = assertThrows(AssertionFailedError.class,
                () -> GpuAssert.sameResult(String.class, "length", KernelSample.of("x", new int[1])));
        assertTrue(e.getMessage().contains("kernel IR not found"), e.getMessage());
    }
}
