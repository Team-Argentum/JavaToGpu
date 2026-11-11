package com.sixik.gpu.runtime;

import com.sixik.gpu.ir.AddressSpace;
import com.sixik.gpu.ir.IrBuilder;
import com.sixik.gpu.ir.IrModule;
import com.sixik.gpu.ir.Op;
import com.sixik.gpu.ir.Result;
import com.sixik.gpu.ir.Types;
import com.sixik.gpu.runtime.device.FakeDeviceApi;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Кэш подготовленных запусков обычных вызовов ({@link GpuContext#launchReusing}): результат тот же, что у
 * {@link GpuContext#launch}, а буферы устройства переиспользуются.
 */
class LaunchReuseTest {

    /** {@code out[g] = in[g] * k + b}: параметры (in, out, b, k). */
    private static IrModule scaled() {
        IrBuilder b = new IrBuilder();
        int a = b.array(AddressSpace.GLOBAL, Types.F32);
        b.beginFunction(IrModule.KERNEL, Types.VOID, a, a, Types.I32, Types.F32);
        int g = b.declareLocal(Types.I32);
        b.endFunction(b.block(
                b.localDecl(g, b.intrinsic(0, b.constI32(0))),
                b.store(b.elemPtr(b.param(1), b.localGet(g)),
                        b.binary(Op.ADD, b.binary(Op.MUL, b.load(b.elemPtr(b.param(0), b.localGet(g))), b.param(3)),
                                b.convert(Types.F32, b.param(2))))));
        return b.build();
    }

    private static FakeDeviceApi device() {
        return FakeDeviceApi.withGpu().onKernel("k0", l -> {
            ByteBuffer in = l.buffer(0);
            ByteBuffer out = l.buffer(2);
            int n = l.intArg(1);
            for (int i = 0; i < n; i++) {
                out.putFloat(4 * i, in.getFloat(4 * i) * l.floatArg(5) + l.intArg(4));
            }
        });
    }

    private static GpuContext open(FakeDeviceApi fake) {
        return GpuContext.builder().api(fake).ignoreEnvironment().build();
    }

    private static void run(GpuContext ctx, CompiledKernel k, Object... args) {
        Result<LaunchStats> r = ctx.launchReusing(k, LaunchConfig.of1D(((float[]) args[0]).length), args);
        assertTrue(r.isOk(), r.diagnostics().toString());
    }

    @Test
    void repeatedCallsReuseBuffersAndSeeEveryChange() {
        FakeDeviceApi fake = device();
        try (GpuContext ctx = open(fake)) {
            CompiledKernel k = ctx.compile(scaled()).value();
            float[] in = {1, 2, 3, 4};
            float[] out = new float[4];
            run(ctx, k, in, out, 0, 1f);
            assertArrayEquals(new float[]{1, 2, 3, 4}, out);
            int buffers = fake.liveBuffers();

            in[2] = 30; // изменение входа между вызовами обязано дойти до устройства
            run(ctx, k, in, out, 5, 2f); // и новые скаляры
            assertArrayEquals(new float[]{7, 9, 65, 13}, out);
            run(ctx, k, in, out, 5, 2f);
            assertArrayEquals(new float[]{7, 9, 65, 13}, out);

            assertEquals(1, ctx.reusedLaunches());
            assertEquals(buffers, fake.liveBuffers(), "no new device buffers for the same arrays");
        }
        assertEquals(0, fake.liveBuffers());
    }

    @Test
    void resultMatchesThePlainLaunch() {
        FakeDeviceApi fake = device();
        try (GpuContext ctx = open(fake)) {
            CompiledKernel k = ctx.compile(scaled()).value();
            float[] in = new float[100];
            for (int i = 0; i < in.length; i++) {
                in[i] = i * 0.25f - 7;
            }
            float[] a = new float[100];
            float[] b = new float[100];
            for (int round = 0; round < 3; round++) {
                in[round] += 100;
                assertTrue(ctx.launch(k, LaunchConfig.of1D(100), in, a, round, 1.5f).isOk());
                run(ctx, k, in, b, round, 1.5f);
                assertArrayEquals(a, b, "round " + round);
            }
        }
    }

    @Test
    void differentArraysGetTheirOwnEntriesUpToTheLimit() {
        FakeDeviceApi fake = device();
        try (GpuContext ctx = open(fake)) {
            CompiledKernel k = ctx.compile(scaled()).value();
            for (int i = 0; i < GpuContext.REUSE_MAX_ENTRIES + 5; i++) {
                float[] in = {i};
                float[] out = new float[1];
                run(ctx, k, in, out, 0, 1f);
                assertEquals(i, out[0]);
            }
            assertEquals(GpuContext.REUSE_MAX_ENTRIES, ctx.reusedLaunches());
            assertEquals(2 * GpuContext.REUSE_MAX_ENTRIES, fake.liveBuffers(), "evicted launches free their buffers");
            ctx.trimLaunchCache();
            assertEquals(0, ctx.reusedLaunches());
            assertEquals(0, fake.liveBuffers());
        }
    }

    @Test
    void pingPongBetweenTwoArraySetsStaysCached() {
        FakeDeviceApi fake = device();
        try (GpuContext ctx = open(fake)) {
            CompiledKernel k = ctx.compile(scaled()).value();
            float[] x = {1, 1};
            float[] y = {0, 0};
            for (int i = 0; i < 6; i++) {
                if (i % 2 == 0) {
                    run(ctx, k, x, y, 1, 1f);
                } else {
                    run(ctx, k, y, x, 1, 1f);
                }
            }
            assertArrayEquals(new float[]{7, 7}, x);
            assertEquals(2, ctx.reusedLaunches());
        }
    }

    @Test
    void errorsAreTheSameAsForThePlainLaunch() {
        FakeDeviceApi fake = device();
        try (GpuContext ctx = open(fake)) {
            CompiledKernel k = ctx.compile(scaled()).value();
            Result<LaunchStats> plain = ctx.launch(k, LaunchConfig.of1D(4), new float[4], new float[4], 0);
            Result<LaunchStats> reused = ctx.launchReusing(k, LaunchConfig.of1D(4),
                    new Object[]{new float[4], new float[4], 0});
            assertFalse(plain.isOk());
            assertFalse(reused.isOk());
            assertEquals(plain.diagnostics().get(0).code(), reused.diagnostics().get(0).code());
            assertEquals(0, ctx.reusedLaunches());
        }
    }

    @Test
    void wrongScalarOnACachedEntryIsReported() {
        FakeDeviceApi fake = device();
        try (GpuContext ctx = open(fake)) {
            CompiledKernel k = ctx.compile(scaled()).value();
            float[] in = new float[2];
            float[] out = new float[2];
            run(ctx, k, in, out, 0, 1f);
            Result<LaunchStats> r = ctx.launchReusing(k, LaunchConfig.of1D(2), new Object[]{in, out, "x", 1f});
            assertFalse(r.isOk());
            assertEquals("RT-A02", r.diagnostics().get(0).code());
        }
    }
}
