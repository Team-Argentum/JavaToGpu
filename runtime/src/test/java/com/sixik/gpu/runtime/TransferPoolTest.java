package com.sixik.gpu.runtime;

import com.sixik.gpu.ir.AddressSpace;
import com.sixik.gpu.ir.IrBuilder;
import com.sixik.gpu.ir.IrModule;
import com.sixik.gpu.ir.Op;
import com.sixik.gpu.ir.Types;
import com.sixik.gpu.runtime.device.FakeDeviceApi;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Общий закреплённый буфер передач контекста: один на контекст, растёт, переиспользуется, освобождается при закрытии. */
class TransferPoolTest {

    /** {@code out[g] = in[g] * k + b}. */
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

    @Test
    void plainLaunchesShareOneGrowingPinnedBuffer() {
        FakeDeviceApi fake = device();
        try (GpuContext ctx = GpuContext.builder().api(fake).ignoreEnvironment().build()) {
            CompiledKernel k = ctx.compile(scaled()).value();
            for (int n : new int[]{300_000, 600_000, 450_000}) { // 1.2, 2.4, 1.8 МБ
                float[] in = new float[n];
                for (int i = 0; i < n; i++) {
                    in[i] = i;
                }
                float[] out = new float[n];
                assertTrue(ctx.launch(k, LaunchConfig.of1D(n), in, out, 1, 2f).isOk());
                assertEquals(in[n - 1] * 2f + 1, out[n - 1]);
                assertEquals(in[n / 3] * 2f + 1, out[n / 3]);
                assertEquals(1, fake.livePinned(), "one pinned staging buffer per context");
            }
        }
        assertEquals(0, fake.livePinned(), "closing the context releases the pinned buffer");
    }

    @Test
    void gpuBufferTransfersUseThePoolToo() {
        FakeDeviceApi fake = device();
        try (GpuContext ctx = GpuContext.builder().api(fake).ignoreEnvironment().build()) {
            int[] data = new int[1 << 19]; // 2 МиБ
            for (int i = 0; i < data.length; i++) {
                data[i] = i * 3;
            }
            try (GpuBuffer b = ctx.allocate(Types.I32, data.length)) {
                b.write(data);
                int[] back = new int[data.length];
                b.read(back);
                assertArrayEquals(data, back);
                assertEquals(1, fake.livePinned());
            }
        }
        assertEquals(0, fake.livePinned());
    }

    @Test
    void smallTransfersDoNotPin() {
        FakeDeviceApi fake = device();
        try (GpuContext ctx = GpuContext.builder().api(fake).ignoreEnvironment().build()) {
            CompiledKernel k = ctx.compile(scaled()).value();
            assertTrue(ctx.launch(k, LaunchConfig.of1D(16), new float[16], new float[16], 0, 1f).isOk());
            assertEquals(0, fake.livePinned());
        }
    }
}
