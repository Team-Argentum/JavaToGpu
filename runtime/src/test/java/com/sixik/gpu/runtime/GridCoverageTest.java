package com.sixik.gpu.runtime;

import com.sixik.gpu.ir.AddressSpace;
import com.sixik.gpu.ir.Builtins;
import com.sixik.gpu.ir.IrBuilder;
import com.sixik.gpu.ir.IrModule;
import com.sixik.gpu.ir.Op;
import com.sixik.gpu.ir.Types;
import com.sixik.gpu.runtime.device.FakeDeviceApi;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Полное покрытие записью для 2D-индекса {@code out[y * w + x]}: рантайм не загружает выходной массив, только если запуск
 * согласован с шагом ({@code global(0) == w}) и длиной ({@code w * global(1) == out.length}).
 */
class GridCoverageTest {

    /** {@code kernel grid(int[] out, int w) { int x = gid0; int y = gid1; out[y * w + x] = x + 100 * y; }}. */
    static IrModule grid() {
        IrBuilder b = new IrBuilder();
        int gid = Builtins.idOf("getGlobalId");
        b.beginFunction(IrModule.KERNEL, Types.VOID, new int[]{b.array(AddressSpace.GLOBAL, Types.I32), Types.I32});
        int x = b.declareLocal(Types.I32);
        int y = b.declareLocal(Types.I32);
        b.endFunction(b.block(
                b.localDecl(x, b.intrinsic(gid, b.constI32(0))),
                b.localDecl(y, b.intrinsic(gid, b.constI32(1))),
                b.store(b.elemPtr(b.param(0), b.binary(Op.ADD, b.binary(Op.MUL, b.localGet(y), b.param(1)), b.localGet(x))),
                        b.binary(Op.ADD, b.localGet(x), b.binary(Op.MUL, b.localGet(y), b.constI32(100))))));
        return b.build();
    }

    private static GpuContext open(FakeDeviceApi fake) {
        return GpuContext.builder().api(fake).ignoreEnvironment().build();
    }

    @Test
    void matching2dLaunchDoesNotUploadTheOutput() {
        FakeDeviceApi fake = FakeDeviceApi.withGpu();
        try (GpuContext ctx = open(fake)) {
            CompiledKernel k = ctx.compile(grid()).value();
            fake.onKernel(k.artifact().entry(), l -> { });
            assertTrue(k.fullyWritten(0));
            int w = 16;
            int h = 4;
            LaunchStats s = ctx.launch(k, LaunchConfig.of2D(w, h), new int[w * h], w).value();
            assertEquals(0, s.bytesUp(), "out[y * w + x] covers the array when global == (w, length / w)");
            s = ctx.launch(k, LaunchConfig.of2D(w - 1, h), new int[w * h], w).value();
            assertEquals(w * h * 4L, s.bytesUp(), "a narrower launch leaves a column unwritten");
            s = ctx.launch(k, LaunchConfig.of2D(w, h), new int[w * h + 1], w).value();
            assertEquals((w * h + 1) * 4L, s.bytesUp(), "a longer array keeps its tail");
            s = ctx.launch(k, LaunchConfig.of2D(w, h), new int[w * h], w * 2).value();
            assertEquals(w * h * 4L, s.bytesUp(), "a wider stride skips elements");
        }
    }

    @Test
    void preparedLaunchRechecksCoverageWhenTheWidthChanges() {
        FakeDeviceApi fake = FakeDeviceApi.withGpu();
        try (GpuContext ctx = open(fake)) {
            CompiledKernel k = ctx.compile(grid()).value();
            fake.onKernel(k.artifact().entry(), l -> { });
            int w = 8;
            int h = 8;
            try (PreparedLauncher p = ctx.prepare(k, new Object[]{new int[w * h], w}).value()) {
                assertEquals(0, p.run(LaunchConfig.of2D(w, h)).bytesUp());
                p.set(1, w / 2);
                assertEquals(w * h * 4L, p.run(LaunchConfig.of2D(w, h)).bytesUp(),
                        "with w != global(0) the array is not covered any more");
                assertEquals(0, p.run(LaunchConfig.of2D(w / 2, h * 2)).bytesUp());
            }
        }
    }
}
