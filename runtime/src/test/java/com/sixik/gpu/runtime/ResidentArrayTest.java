package com.sixik.gpu.runtime;

import com.sixik.gpu.api.types.GpuFloatArray;
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
 * {@code GpuFloatArray} в запуске ядра: данные остаются на устройстве между вызовами, на хост читаются лениво, изменения
 * хоста загружаются заново, закрытие контекста возвращает свежие данные на хост.
 */
class ResidentArrayTest {

    /** {@code out[g] = in[g] * k + b}: параметры (in, out, b, k); out перезаписывается целиком. */
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

    private static LaunchStats run(GpuContext ctx, CompiledKernel k, Object... args) {
        Result<LaunchStats> r = ctx.launchReusing(k, LaunchConfig.of1D(4), args);
        assertTrue(r.isOk(), r.diagnostics().toString());
        return r.value();
    }

    @Test
    void kernelChainKeepsDataOnTheDevice() {
        FakeDeviceApi fake = device();
        try (GpuContext ctx = open(fake)) {
            CompiledKernel k = ctx.compile(scaled()).value();
            GpuFloatArray in = GpuFloatArray.of(1, 2, 3, 4);
            GpuFloatArray mid = new GpuFloatArray(4);
            GpuFloatArray out = new GpuFloatArray(4);
            int writes = fake.writes();
            run(ctx, k, in, mid, 0, 2f);
            assertEquals(writes + 1, fake.writes(), "only `in` is uploaded: `mid` is overwritten entirely");
            int reads = fake.reads();
            run(ctx, k, mid, out, 1, 1f);
            assertEquals(writes + 1, fake.writes(), "`mid` is already on the device");
            assertEquals(reads, fake.reads(), "nothing is read back until the host asks");

            assertEquals(3f, out.get(0)); // 1 * 2 * 1 + 1
            assertEquals(reads + 1, fake.reads(), "one readback on the first host access");
            assertArrayEquals(new float[]{3, 5, 7, 9}, out.toArray());
            assertEquals(reads + 1, fake.reads(), "the host copy is fresh now");
            assertArrayEquals(new float[]{2, 4, 6, 8}, mid.toArray());
        }
    }

    @Test
    void chainDoesNotWaitBetweenKernelsUntilTheHostReads() {
        FakeDeviceApi fake = device();
        try (GpuContext ctx = open(fake)) {
            CompiledKernel k = ctx.compile(scaled()).value();
            GpuFloatArray a = GpuFloatArray.of(1, 2, 3, 4);
            GpuFloatArray b = new GpuFloatArray(4);
            int finishes = fake.finishes();
            for (int s = 0; s < 10; s++) {
                run(ctx, k, s % 2 == 0 ? a : b, s % 2 == 0 ? b : a, 1, 1f);
            }
            assertEquals(finishes, fake.finishes(), "no kernel in the chain waits for completion");
            int reads = fake.reads();
            assertArrayEquals(new float[]{11, 12, 13, 14}, a.toArray(), "the first host read waits for everything");
            assertEquals(reads + 1, fake.reads());
        }
    }

    @Test
    void residentOnlyCallsReuseOnePreparedLaunch() {
        FakeDeviceApi fake = device();
        try (GpuContext ctx = open(fake)) {
            CompiledKernel k = ctx.compile(scaled()).value();
            GpuFloatArray a = GpuFloatArray.of(1, 2, 3, 4);
            GpuFloatArray b = new GpuFloatArray(4);
            for (int s = 0; s < 5; s++) {
                run(ctx, k, a, b, s, 1f); // меняется только скаляр
            }
            assertEquals(1, ctx.reusedLaunches(), "one prepared launch serves all calls with the same arrays");
            assertArrayEquals(new float[]{5, 6, 7, 8}, b.toArray());
            run(ctx, k, b, a, 0, 2f); // другие массивы - переподстановка буферов в том же запуске
            assertArrayEquals(new float[]{10, 12, 14, 16}, a.toArray());
            assertEquals(1, ctx.reusedLaunches());
        }
    }

    @Test
    void deferredLaunchesAreBounded() {
        FakeDeviceApi fake = device();
        try (GpuContext ctx = open(fake)) {
            CompiledKernel k = ctx.compile(scaled()).value();
            GpuFloatArray a = GpuFloatArray.of(0, 0, 0, 0);
            GpuFloatArray b = new GpuFloatArray(4);
            int finishes = fake.finishes();
            for (int s = 0; s < GpuContext.MAX_DEFERRED_LAUNCHES + 1; s++) {
                run(ctx, k, s % 2 == 0 ? a : b, s % 2 == 0 ? b : a, 1, 1f);
            }
            assertEquals(finishes + 1, fake.finishes(), "after the limit one launch waits for the queue");
        }
    }

    @Test
    void plainArrayLaunchesStillWait() {
        FakeDeviceApi fake = device();
        try (GpuContext ctx = open(fake)) {
            CompiledKernel k = ctx.compile(scaled()).value();
            int finishes = fake.finishes();
            float[] out = new float[4];
            assertTrue(ctx.launch(k, LaunchConfig.of1D(4), new float[]{1, 2, 3, 4}, out, 0, 1f).isOk());
            assertEquals(finishes + 1, fake.finishes(), "a launch that reads back into float[] waits for the kernel");
            assertArrayEquals(new float[]{1, 2, 3, 4}, out);
        }
    }

    @Test
    void hostChangesAreUploadedBeforeTheNextLaunch() {
        FakeDeviceApi fake = device();
        try (GpuContext ctx = open(fake)) {
            CompiledKernel k = ctx.compile(scaled()).value();
            GpuFloatArray in = GpuFloatArray.of(1, 1, 1, 1);
            GpuFloatArray out = new GpuFloatArray(4);
            run(ctx, k, in, out, 0, 1f);
            int writes = fake.writes();
            run(ctx, k, in, out, 0, 1f);
            assertEquals(writes, fake.writes(), "unchanged input is not uploaded again");
            in.set(2, 10f);
            run(ctx, k, in, out, 0, 1f);
            assertEquals(writes + 1, fake.writes(), "a changed input is uploaded");
            assertArrayEquals(new float[]{1, 1, 10, 1}, out.toArray());
            in.fill(5f);
            run(ctx, k, in, out, 0, 1f);
            assertArrayEquals(new float[]{5, 5, 5, 5}, out.toArray());
        }
    }

    @Test
    void sameResultAsPlainArrays() {
        FakeDeviceApi fake = device();
        try (GpuContext ctx = open(fake)) {
            CompiledKernel k = ctx.compile(scaled()).value();
            float[] in = {0.5f, -2f, 7f, 1e9f};
            float[] out = new float[4];
            assertTrue(ctx.launch(k, LaunchConfig.of1D(4), in, out, 3, 1.5f).isOk());
            GpuFloatArray rin = GpuFloatArray.of(in);
            GpuFloatArray rout = new GpuFloatArray(4);
            assertTrue(ctx.launch(k, LaunchConfig.of1D(4), rin, rout, 3, 1.5f).isOk());
            assertArrayEquals(out, rout.toArray());
        }
    }

    @Test
    void closingTheContextBringsFreshDataHome() {
        FakeDeviceApi fake = device();
        GpuFloatArray out = new GpuFloatArray(4);
        try (GpuContext ctx = open(fake)) {
            CompiledKernel k = ctx.compile(scaled()).value();
            run(ctx, k, GpuFloatArray.of(1, 2, 3, 4), out, 0, 3f);
        }
        assertEquals(0, fake.liveBuffers());
        assertArrayEquals(new float[]{3, 6, 9, 12}, out.toArray(), "data written on the device survives closing");
    }

    @Test
    void arrayMovesBetweenContexts() {
        FakeDeviceApi fake1 = device();
        FakeDeviceApi fake2 = device();
        GpuFloatArray data = GpuFloatArray.of(1, 2, 3, 4);
        GpuFloatArray out = new GpuFloatArray(4);
        try (GpuContext a = open(fake1); GpuContext b = open(fake2)) {
            CompiledKernel ka = a.compile(scaled()).value();
            CompiledKernel kb = b.compile(scaled()).value();
            run(a, ka, data, out, 0, 2f); // out свежий в контексте a
            run(b, kb, out, data, 0, 1f); // out переходит в b: сначала читается из a
            assertArrayEquals(new float[]{2, 4, 6, 8}, data.toArray());
            assertArrayEquals(new float[]{2, 4, 6, 8}, out.toArray());
        }
    }

    @Test
    void preparedLaunchRejectsResidentArrays() {
        FakeDeviceApi fake = device();
        try (GpuContext ctx = open(fake)) {
            CompiledKernel k = ctx.compile(scaled()).value();
            Result<PreparedLauncher> r = ctx.prepare(k, GpuFloatArray.of(1, 2, 3, 4), new GpuFloatArray(4), 0, 1f);
            assertFalse(r.isOk());
            assertEquals("RT-A02", r.diagnostics().get(0).code());
        }
    }

    @Test
    void hostOnlyArrayWorksWithoutAnyDevice() {
        GpuFloatArray a = GpuFloatArray.of(1, 2, 3);
        a.set(1, 5f);
        a.copyFrom(new float[]{7, 8});
        assertArrayEquals(new float[]{7, 8, 3}, a.toArray());
        float[] dst = new float[2];
        a.copyTo(dst);
        assertArrayEquals(new float[]{7, 8}, dst);
        assertEquals(3, a.length());
    }
}
