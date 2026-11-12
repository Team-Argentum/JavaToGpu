package com.sixik.gpu.test;

import com.sixik.gpu.runtime.GpuContext;
import com.sixik.gpu.runtime.JavaToGpu;
import com.sixik.gpu.runtime.NativeMath;
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
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Допуски {@link GpuAssert} для ядер с флагами {@code fp}: устройство ядра {@code fp=contract} сверяется с
 * интерпретацией скомпилированного модуля (там {@code fma} явный), а не с JVM; {@code native_sin} добавляет абсолютный
 * допуск калибровки. Реальное устройство не нужно.
 */
class GpuAssertFpTest {

    private static final String SOURCE = """
            package demo;

            import com.sixik.gpu.api.Gpu;
            import com.sixik.gpu.api.annotations.*;

            public class Fp {

                @Kernel(compilationProperties = "fp=contract")
                public static void fused(@Global float[] out, @Global float[] a, @Global float[] b, float c) {
                    int g = Gpu.getGlobalId(0);
                    float p = a[g] * b[g];
                    out[g] = p + c;
                }

                @Kernel(compilationProperties = "fp=native")
                public static void fastSine(@Global float[] out, @Global float[] in) {
                    int g = Gpu.getGlobalId(0);
                    out[g] = Gpu.sin(in[g]);
                }
            }
            """;

    private static final int N = 256;
    private static final float C = 0.1f;

    private URLClassLoader loader;
    private Class<?> fp;

    @BeforeEach
    void build(@TempDir Path dir) throws Exception {
        KernelCompiler.Compiled r = KernelCompiler.compile(dir, SOURCE);
        assertTrue(r.success(), r.diagnostics());
        Weaver.weaveTree(dir);
        loader = new URLClassLoader(new URL[]{dir.toUri().toURL()}, getClass().getClassLoader());
        fp = loader.loadClass("demo.Fp");
    }

    @AfterEach
    void close() throws Exception {
        loader.close();
        JavaToGpu.resetDefault();
    }

    private static float[] random(int seed) {
        Random r = new Random(seed);
        float[] a = new float[N];
        for (int i = 0; i < N; i++) {
            a[i] = (r.nextFloat() - 0.5f) * 1000f;
        }
        return a;
    }

    private static KernelSample fusedSample() {
        return KernelSample.named("fma").args(() -> new Object[]{new float[N], random(1), random(2), C}).build();
    }

    /** Фейк ядра {@code fused}: с {@code fma == true} - одно округление, иначе два (как JVM). */
    private static FakeDeviceApi fusedDevice(FakeDeviceApi.Spec spec, boolean fma) {
        return new FakeDeviceApi(spec).onKernel("k0", l -> {
            ByteBuffer out = l.buffer(0);
            ByteBuffer a = l.buffer(2); // за каждым массивом идёт его длина
            ByteBuffer b = l.buffer(4);
            float c = l.floatArg(6);
            for (int i = 0; i < l.global(0); i++) {
                float x = a.getFloat(4 * i);
                float y = b.getFloat(4 * i);
                out.putFloat(4 * i, fma ? Math.fma(x, y, c) : x * y + c);
            }
        });
    }

    private static GpuContext open(FakeDeviceApi api) {
        return GpuContext.builder().api(api).ignoreEnvironment().build();
    }

    @Test
    void theInputsTellFmaAndMulAddApart() {
        float[] a = random(1);
        float[] b = random(2);
        int differ = 0;
        for (int i = 0; i < N; i++) {
            differ += Math.fma(a[i], b[i], C) != a[i] * b[i] + C ? 1 : 0;
        }
        assertTrue(differ > N / 10, "differ: " + differ);
    }

    @Test
    void contractedKernelIsComparedWithTheCompiledModule() {
        try (GpuContext ctx = open(fusedDevice(FakeDeviceApi.Spec.gpu(), true))) {
            GpuAssert.Report r = GpuAssert.sameResult(fp, "fused", fusedSample(), ctx);
            assertTrue(r.ranOn(Leg.DEVICE));
            assertTrue(r.compiledReference(), "the device result differs from the JVM by design");
            assertEquals(0, r.ulps(), "fma is correctly rounded: exact against the compiled module");
            assertEquals(0, r.absolute());
        }
    }

    @Test
    void aDeviceThatDoesNotFuseIsCaught() {
        try (GpuContext ctx = open(fusedDevice(FakeDeviceApi.Spec.gpu(), false))) {
            AssertionFailedError e = assertThrows(AssertionFailedError.class,
                    () -> GpuAssert.sameResult(fp, "fused", fusedSample(), ctx));
            assertTrue(e.getMessage().contains("COMPILED versus DEVICE"), e.getMessage());
        }
    }

    @Test
    void withoutHardwareFmaTheKernelStaysPlain() {
        // оптимизатор не сливает: скомпилированный модуль - mul + add, как JVM
        try (GpuContext ctx = open(fusedDevice(FakeDeviceApi.Spec.gpu().withoutFma(), false))) {
            GpuAssert.Report r = GpuAssert.sameResult(fp, "fused", fusedSample(), ctx);
            assertTrue(r.ranOn(Leg.DEVICE));
        }
    }

    // ---- native ---------------------------------------------------------------------------------

    /** Фейк: калибровка проходит, {@code native_sin} ядра ошибается на {@code error}. */
    private static FakeDeviceApi sineDevice(float error) {
        return FakeDeviceApi.withGpu().onKernel(NativeMath.KERNEL, l -> {
            ByteBuffer t = l.buffer(0);
            ByteBuffer s = l.buffer(1);
            ByteBuffer out = l.buffer(2);
            for (int i = 0; i < l.global(0); i++) {
                float x = t.getFloat(4 * i);
                out.putFloat(12 * i, (float) Math.sin(x));
                out.putFloat(12 * i + 4, (float) Math.cos(x));
                out.putFloat(12 * i + 8, (float) Math.sqrt(s.getFloat(4 * i)));
            }
        }).onKernel("k0", l -> {
            ByteBuffer out = l.buffer(0);
            ByteBuffer in = l.buffer(2);
            for (int i = 0; i < l.global(0); i++) {
                out.putFloat(4 * i, (float) StrictMath.sin(in.getFloat(4 * i)) + error);
            }
        });
    }

    private static KernelSample sineSample() {
        float[] x = new float[N];
        for (int i = 0; i < N; i++) {
            x[i] = (i - N / 2) * 0.1f;
        }
        return KernelSample.named("sin").args(() -> new Object[]{new float[N], x.clone()}).build();
    }

    @Test
    void nativeSineGetsTheCalibrationTolerance() {
        try (GpuContext ctx = open(sineDevice(1e-5f))) {
            GpuAssert.Report r = GpuAssert.sameResult(fp, "fastSine", sineSample(), ctx);
            assertEquals(NativeMath.TRIG_MAX_ABS, r.absolute());
            assertFalse(r.compiledReference(), "native alone does not rewrite the IR: the JVM stays the reference");
        }
        try (GpuContext ctx = open(sineDevice(1e-3f))) {
            AssertionFailedError e = assertThrows(AssertionFailedError.class,
                    () -> GpuAssert.sameResult(fp, "fastSine", sineSample(), ctx));
            assertTrue(e.getMessage().contains("or absolute"), e.getMessage());
        }
    }

    @Test
    void withoutCalibrationNativeIsNotUsedAndNoToleranceIsAdded() {
        // фейк без ядра калибровки: native_sin не проходит, ядро точное
        FakeDeviceApi api = FakeDeviceApi.withGpu().onKernel("k0", l -> {
            ByteBuffer out = l.buffer(0);
            ByteBuffer in = l.buffer(2);
            for (int i = 0; i < l.global(0); i++) {
                out.putFloat(4 * i, (float) StrictMath.sin(in.getFloat(4 * i)));
            }
        });
        try (GpuContext ctx = open(api)) {
            GpuAssert.Report r = GpuAssert.sameResult(fp, "fastSine", sineSample(), ctx);
            assertEquals(0, r.absolute());
        }
    }
}
