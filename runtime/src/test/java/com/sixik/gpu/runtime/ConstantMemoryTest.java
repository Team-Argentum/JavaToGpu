package com.sixik.gpu.runtime;

import com.sixik.gpu.ir.AddressSpace;
import com.sixik.gpu.ir.IrBuilder;
import com.sixik.gpu.ir.IrModule;
import com.sixik.gpu.ir.Result;
import com.sixik.gpu.ir.Types;
import com.sixik.gpu.runtime.device.FakeDeviceApi;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Массивы {@code @Global(constant = true)} (M9): ограничения {@code __constant} проверяются до загрузки данных
 * ({@code RT-A04}), а обычный запуск проходит и читает константный массив. Устройство - {@link FakeDeviceApi}.
 */
class ConstantMemoryTest {

    /** Ядро {@code out[g] = table[g] * 2}: константная таблица и обычный выходной массив. */
    private static IrModule tableKernel() {
        IrBuilder b = new IrBuilder();
        int table = b.array(AddressSpace.CONSTANT, Types.F32);
        int out = b.array(AddressSpace.GLOBAL, Types.F32);
        b.beginFunction(IrModule.KERNEL, Types.VOID, table, out);
        b.nameFunction("scaleTable");
        int g = b.declareLocal(Types.I32);
        b.endFunction(b.block(
                b.localDecl(g, b.intrinsic(0, b.constI32(0))),
                b.store(b.elemPtr(b.param(1), b.localGet(g)),
                        b.binary(com.sixik.gpu.ir.Op.MUL, b.load(b.elemPtr(b.param(0), b.localGet(g))), b.constF32(2)))));
        return b.build();
    }

    /** Ядро с {@code n} константными массивами, которые оно читает. */
    private static IrModule manyConstants(int n) {
        IrBuilder b = new IrBuilder();
        int[] params = new int[n + 1];
        for (int i = 0; i < n; i++) {
            params[i] = b.array(AddressSpace.CONSTANT, Types.I32);
        }
        params[n] = b.array(AddressSpace.GLOBAL, Types.I32);
        b.beginFunction(IrModule.KERNEL, Types.VOID, params);
        int sum = b.constI32(0);
        for (int i = 0; i < n; i++) {
            sum = b.binary(com.sixik.gpu.ir.Op.ADD, sum, b.load(b.elemPtr(b.param(i), b.constI32(0))));
        }
        b.endFunction(b.block(b.store(b.elemPtr(b.param(n), b.constI32(0)), sum)));
        return b.build();
    }

    private static GpuContext open(FakeDeviceApi fake) {
        return GpuContext.builder().api(fake).ignoreEnvironment().build();
    }

    @Test
    void constantTableWithinTheLimitIsLaunchedAndNotReadBack() {
        FakeDeviceApi fake = FakeDeviceApi.withGpu().onKernel("k0", l -> {
            ByteBuffer table = l.buffer(0);
            ByteBuffer out = l.buffer(2);
            for (int i = 0; i < 4; i++) {
                out.putFloat(4 * i, table.getFloat(4 * i) * 2);
            }
        });
        try (GpuContext ctx = open(fake)) {
            float[] table = {1, 2, 3, 4};
            float[] out = new float[4];
            LaunchStats stats = ctx.run(tableKernel(), LaunchConfig.of1D(4), table, out);
            assertEquals(4 * 4L, stats.bytesDown(), "only the written out is returned: the constant table is not read back");
            assertEquals(2f, out[0]);
            assertEquals(8f, out[3]);
            assertEquals(1f, table[0]);
        }
    }

    @Test
    void tableLargerThanTheConstantBufferLimitIsRejectedBeforeUpload() {
        FakeDeviceApi fake = new FakeDeviceApi(FakeDeviceApi.Spec.gpu().withMaxConstant(64)).onKernel("k0", l -> { });
        try (GpuContext ctx = open(fake)) {
            Result<CompiledKernel> k = ctx.compile(tableKernel());
            assertTrue(k.isOk(), k.diagnostics().toString());
            Result<LaunchStats> r = ctx.launch(k.value(), LaunchConfig.of1D(32), new float[32], new float[32]);
            assertFalse(r.isOk());
            assertEquals("RT-A04", r.diagnostics().get(0).code());
            assertTrue(r.diagnostics().get(0).message().contains("128 bytes"), r.diagnostics().toString());
            assertTrue(r.diagnostics().get(0).message().contains("64"), r.diagnostics().toString());
            assertEquals(0, fake.enqueues(), "the kernel was not launched");
            assertEquals(0, fake.liveBuffers(), "and no buffers were created for the table");
            // ровно по границе - допустимо
            assertTrue(ctx.launch(k.value(), LaunchConfig.of1D(16), new float[16], new float[16]).isOk());
        }
    }

    @Test
    void deviceBufferInConstantParameterIsCheckedToo() {
        FakeDeviceApi fake = new FakeDeviceApi(FakeDeviceApi.Spec.gpu().withMaxConstant(64)).onKernel("k0", l -> { });
        try (GpuContext ctx = open(fake)) {
            CompiledKernel k = ctx.compile(tableKernel()).value();
            try (GpuBuffer big = ctx.allocate(Types.F32, 100); GpuBuffer out = ctx.allocate(Types.F32, 100)) {
                Result<LaunchStats> r = ctx.launch(k, LaunchConfig.of1D(100), big, out);
                assertEquals("RT-A04", r.diagnostics().get(0).code());
            }
        }
    }

    @Test
    void tooManyConstantArgumentsAreRejected() {
        try (GpuContext ctx = open(FakeDeviceApi.withGpu().onKernel("k0", l -> { }))) {
            int n = 9; // фейк разрешает 8, как минимум по спецификации OpenCL
            Result<CompiledKernel> k = ctx.compile(manyConstants(n));
            assertTrue(k.isOk(), k.diagnostics().toString());
            Object[] args = new Object[n + 1];
            for (int i = 0; i < n; i++) {
                args[i] = new int[]{i};
            }
            args[n] = new int[1];
            Result<LaunchStats> r = ctx.launch(k.value(), LaunchConfig.of1D(1), args);
            assertEquals("RT-A04", r.diagnostics().get(0).code());
            assertTrue(r.diagnostics().get(0).message().contains("9"), r.diagnostics().toString());

            Result<CompiledKernel> ok = ctx.compile(manyConstants(8));
            Object[] eight = new Object[9];
            for (int i = 0; i < 8; i++) {
                eight[i] = new int[]{i};
            }
            eight[8] = new int[1];
            assertTrue(ctx.launch(ok.value(), LaunchConfig.of1D(1), eight).isOk());
        }
    }

    @Test
    void deviceInfoCarriesTheConstantLimits() {
        try (GpuContext ctx = open(new FakeDeviceApi(FakeDeviceApi.Spec.gpu().withMaxConstant(1234)))) {
            assertEquals(1234, ctx.device().maxConstantBuffer());
            assertEquals(8, ctx.device().maxConstantArgs());
        }
    }
}
