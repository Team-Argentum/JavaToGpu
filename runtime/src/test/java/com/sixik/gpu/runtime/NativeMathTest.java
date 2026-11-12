package com.sixik.gpu.runtime;

import com.sixik.gpu.codegen.opencl.Target;
import com.sixik.gpu.ir.AddressSpace;
import com.sixik.gpu.ir.Builtins;
import com.sixik.gpu.ir.IrBuilder;
import com.sixik.gpu.ir.IrModule;
import com.sixik.gpu.ir.Types;
import com.sixik.gpu.runtime.device.FakeDeviceApi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Калибровка {@code native_*} ({@link NativeMath}) и её применение в {@link GpuContext}: функции, не прошедшие порог,
 * остаются точными; ядра без {@code fp=native} калибровку не запускают и {@code native_*} не получают.
 */
class NativeMathTest {

    /** Ошибки «устройства» в ядре калибровки фейка: добавки к sin, cos и множитель sqrt. */
    private static FakeDeviceApi device(float sinBias, float cosBias, float sqrtScale) {
        return FakeDeviceApi.withGpu().onKernel(NativeMath.KERNEL, l -> {
            ByteBuffer t = l.buffer(0);
            ByteBuffer s = l.buffer(1);
            ByteBuffer out = l.buffer(2);
            for (int i = 0; i < l.global(0); i++) {
                float x = t.getFloat(4 * i);
                out.putFloat(12 * i, (float) Math.sin(x) + sinBias);
                out.putFloat(12 * i + 4, (float) Math.cos(x) + cosBias);
                out.putFloat(12 * i + 8, (float) Math.sqrt(s.getFloat(4 * i)) * sqrtScale);
            }
        });
    }

    private static FakeDeviceApi exact() {
        return device(0f, 0f, 1f);
    }

    @Test
    void exactResultsAllowEverything() {
        float[] t = NativeMath.trigSamples();
        float[] s = NativeMath.sqrtSamples();
        float[] out = new float[3 * t.length];
        for (int i = 0; i < t.length; i++) {
            out[3 * i] = (float) Math.sin(t[i]);
            out[3 * i + 1] = (float) Math.cos(t[i]);
            out[3 * i + 2] = (float) Math.sqrt(s[i]);
        }
        NativeMath r = NativeMath.evaluate(t, s, out);
        assertEquals(Target.NATIVE_ALL, r.mask(), r.describe());
        assertTrue(r.sinError() < 1e-7 && r.cosError() < 1e-7 && r.sqrtError() < 1e-7, r.describe());

        out[5] = Float.NaN;
        assertEquals(Target.NATIVE_SIN | Target.NATIVE_COS, NativeMath.evaluate(t, s, out).mask(), "NaN rejects sqrt");
    }

    @Test
    void samplesCoverTheDocumentedRanges() {
        float[] t = NativeMath.trigSamples();
        assertEquals(-NativeMath.TRIG_RANGE, t[0], 1e-4);
        assertEquals(NativeMath.TRIG_RANGE, t[t.length - 1], 1e-4);
        float min = Float.MAX_VALUE;
        float max = 0;
        for (float v : NativeMath.sqrtSamples()) {
            assertTrue(v > 0);
            min = Math.min(min, v);
            max = Math.max(max, v);
        }
        assertEquals(0x1p-60f, min);
        assertTrue(max >= 0x1p60f);
    }

    @Test
    void eachFunctionIsJudgedSeparately() {
        try (GpuContext ctx = GpuContext.builder().api(device(1e-3f, 0f, 1f)).ignoreEnvironment().build()) {
            NativeMath r = ctx.nativeMath();
            assertEquals(Target.NATIVE_COS | Target.NATIVE_SQRT, r.mask(), "sin error 1e-3 > 2^-14: " + r.describe());
            assertEquals(1e-3, r.sinError(), 1e-5);
        }
        try (GpuContext ctx = GpuContext.builder().api(device(0f, 0f, 1.00001f)).ignoreEnvironment().build()) {
            assertEquals(Target.NATIVE_SIN | Target.NATIVE_COS, ctx.nativeMath().mask(), "sqrt relative error 1e-5");
        }
    }

    @Test
    void calibrationReleasesEverythingAndRunsOnce() {
        FakeDeviceApi fake = exact();
        try (GpuContext ctx = GpuContext.builder().api(fake).ignoreEnvironment().build()) {
            NativeMath first = ctx.nativeMath();
            assertEquals(Target.NATIVE_ALL, first.mask());
            assertSame(first, ctx.nativeMath());
            assertEquals(1, fake.enqueues());
            assertEquals(0, fake.liveBuffers());
            assertEquals(0, fake.liveKernels());
            assertEquals(0, fake.livePrograms());
        }
    }

    @Test
    void unknownDeviceBehaviourMeansExactFunctions() {
        // ядро калибровки фейка без поведения оставляет нули - ни одна функция не проходит
        try (GpuContext ctx = GpuContext.builder().api(FakeDeviceApi.withGpu()).ignoreEnvironment().build()) {
            assertEquals(0, ctx.nativeMath().mask());
        }
    }

    @Test
    void buildFailureIsAWarning() {
        List<GpuEvent> events = new ArrayList<>();
        FakeDeviceApi fake = exact().rejectBuildWhen(src -> src.contains(NativeMath.KERNEL), "no native here");
        try (GpuContext ctx = GpuContext.builder().api(fake).ignoreEnvironment().listener(events::add).build()) {
            assertSame(NativeMath.NONE, ctx.nativeMath());
        }
        assertTrue(events.stream().anyMatch(e -> e.kind() == GpuEvent.Kind.WARNING
                && e.detail().contains("calibration failed")), events.toString());
    }

    @Test
    void resultIsKeptInTheDiskCache(@TempDir Path dir) {
        try (GpuContext ctx = GpuContext.builder().api(device(1e-3f, 0f, 1f)).ignoreEnvironment().diskCache(dir)
                .build()) {
            assertEquals(Target.NATIVE_COS | Target.NATIVE_SQRT, ctx.nativeMath().mask());
        }
        FakeDeviceApi again = exact();
        try (GpuContext ctx = GpuContext.builder().api(again).ignoreEnvironment().diskCache(dir).build()) {
            assertEquals(Target.NATIVE_COS | Target.NATIVE_SQRT, ctx.nativeMath().mask(), "taken from the cache");
            assertEquals(0, again.enqueues(), "no second calibration");
        }
    }

    @Test
    void encodingRoundTrip() {
        NativeMath r = new NativeMath(Target.NATIVE_SIN, 1e-6, 0.5, Double.NaN);
        assertEquals(r, NativeMath.decode(r.encode()));
        assertNull(NativeMath.decode(null));
        assertNull(NativeMath.decode("0;7;0;0;0".getBytes()), "other version");
        assertNull(NativeMath.decode("1;x;0;0;0".getBytes()));
    }

    // ---- применение в контексте -------------------------------------------------------------------

    /** Ядро {@code a[0] = sin(a[0]) + sqrt(a[1])} с маской {@code fp}. */
    private static IrModule sinKernel(int fp) {
        IrBuilder b = new IrBuilder();
        int arr = b.array(AddressSpace.GLOBAL, Types.F32);
        b.beginFunction(IrModule.KERNEL, Types.VOID, arr);
        b.fpMode(fp);
        int x = b.load(b.elemPtr(b.param(0), b.constI32(0)));
        int y = b.load(b.elemPtr(b.param(0), b.constI32(1)));
        int v = b.binary(com.sixik.gpu.ir.Op.ADD, b.intrinsic(Builtins.idOf("sin"), x),
                b.intrinsic(Builtins.idOf("sqrt"), y));
        b.endFunction(b.block(b.store(b.elemPtr(b.param(0), b.constI32(0)), v)));
        return b.build();
    }

    @Test
    void nativeKernelGetsTheCalibratedFunctions() {
        FakeDeviceApi fake = device(1e-3f, 0f, 1f);
        try (GpuContext ctx = GpuContext.builder().api(fake).ignoreEnvironment().build()) {
            String src = ctx.compile(sinKernel(IrModule.FP_NATIVE)).value().artifact().source();
            assertTrue(src.contains("native_sqrt("), src);
            assertFalse(src.contains("native_sin("), "native_sin failed the calibration: " + src);
            assertTrue(src.contains("sin("), src);
        }
    }

    @Test
    void strictKernelDoesNotCalibrate() {
        FakeDeviceApi fake = exact();
        try (GpuContext ctx = GpuContext.builder().api(fake).ignoreEnvironment().build()) {
            String src = ctx.compile(sinKernel(IrModule.FP_ALL & ~IrModule.FP_NATIVE)).value().artifact().source();
            assertFalse(src.contains("native_"), src);
            assertEquals(0, fake.enqueues(), "no calibration for kernels without fp=native");
        }
    }

    @Test
    void nativeAndStrictVariantsAreCachedSeparately() {
        FakeDeviceApi fake = exact();
        try (GpuContext ctx = GpuContext.builder().api(fake).ignoreEnvironment().build()) {
            String a = ctx.compile(sinKernel(0)).value().artifact().source();
            String b = ctx.compile(sinKernel(IrModule.FP_NATIVE)).value().artifact().source();
            assertFalse(a.contains("native_"));
            assertTrue(b.contains("native_sin(") && b.contains("native_sqrt("), b);
        }
    }
}
