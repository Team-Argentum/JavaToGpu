package com.sixik.gpu.runtime;

import com.sixik.gpu.codegen.opencl.EmitOptions;
import com.sixik.gpu.ir.AddressSpace;
import com.sixik.gpu.ir.IrBuilder;
import com.sixik.gpu.ir.IrModule;
import com.sixik.gpu.ir.Op;
import com.sixik.gpu.ir.Result;
import com.sixik.gpu.ir.Types;
import com.sixik.gpu.ir.optimizer.OptimizeOptions;
import com.sixik.gpu.runtime.device.FakeDeviceApi;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Оптимизатор IR в составе {@link GpuContext}: модуль оптимизируется перед генерацией OpenCL C, индекс ядра переводится
 * через карту функций, а сигнатура (а значит, проверка аргументов) не меняется.
 */
class OptimizerIntegrationTest {

    private static int count(IrModule m, int op) {
        int c = 0;
        for (int n = 0; n < m.nodeCount(); n++) {
            if (m.op(n) == op) {
                c++;
            }
        }
        return c;
    }

    /** Неиспользуемая device-функция, затем ядро {@code out[0] = (2 + 3) * x[0]}. */
    private static IrModule module() {
        IrBuilder b = new IrBuilder();
        int arr = b.array(AddressSpace.GLOBAL, Types.I32);
        b.beginFunction(IrModule.DEVICE, Types.I32, Types.I32);
        b.endFunction(b.block(b.returnStmt(b.binary(Op.MUL, b.param(0), b.constI32(7)))));
        b.beginFunction(IrModule.KERNEL, Types.VOID, arr);
        int x = b.load(b.elemPtr(b.param(0), b.constI32(0)));
        int v = b.binary(Op.MUL, b.binary(Op.ADD, b.constI32(2), b.constI32(3)), x);
        b.endFunction(b.block(b.store(b.elemPtr(b.param(0), b.constI32(0)), v)));
        return b.build();
    }

    @Test
    void kernelIsOptimizedBeforeCodegen() {
        try (GpuContext ctx = GpuContext.builder().api(FakeDeviceApi.withGpu()).ignoreEnvironment().build()) {
            Result<CompiledKernel> r = ctx.compile(module());
            assertTrue(r.isOk(), r.diagnostics().toString());
            CompiledKernel k = r.value();
            assertEquals(1, k.module().functionCount(), "the unused device function is dropped");
            assertEquals(0, k.function(), "the kernel index follows the function map");
            assertEquals(0, count(k.module(), Op.ADD), "2 + 3 is folded");
            assertEquals(1, k.paramCount());
        }
    }

    @Test
    void optimizerCanBeTurnedOff() {
        try (GpuContext ctx = GpuContext.builder().api(FakeDeviceApi.withGpu()).ignoreEnvironment()
                .optimize(OptimizeOptions.NONE).build()) {
            CompiledKernel k = ctx.compile(module()).value();
            assertEquals(2, k.module().functionCount());
            assertEquals(1, count(k.module(), Op.ADD));
        }
    }

    @Test
    void debugEmitKeepsTheSourceUnoptimized() {
        try (GpuContext ctx = GpuContext.builder().api(FakeDeviceApi.withGpu()).ignoreEnvironment()
                .emitOptions(EmitOptions.DEBUG).build()) {
            CompiledKernel k = ctx.compile(module()).value();
            assertEquals(1, count(k.module(), Op.ADD));
        }
    }

    @Test
    void unusedSharedMemoryNoLongerExceedsTheDeviceLimit() {
        IrBuilder b = new IrBuilder();
        int arr = b.array(AddressSpace.GLOBAL, Types.I32);
        b.beginFunction(IrModule.KERNEL, Types.VOID, arr);
        int tile = b.declareLocal(b.array(AddressSpace.LOCAL, Types.I32));
        b.endFunction(b.block(b.localArrayDecl(tile, b.constI32(16 * 1024)),
                b.store(b.elemPtr(b.param(0), b.constI32(0)), b.constI32(1))));
        IrModule m = b.build();
        try (GpuContext off = GpuContext.builder().api(FakeDeviceApi.withGpu()).ignoreEnvironment()
                .optimize(OptimizeOptions.NONE).build()) {
            assertEquals("RT-D04", off.compile(m).diagnostics().get(0).code());
        }
        try (GpuContext on = GpuContext.builder().api(FakeDeviceApi.withGpu()).ignoreEnvironment().build()) {
            Result<CompiledKernel> r = on.compile(m);
            assertTrue(r.isOk(), "the 64 KiB tile is never read and is removed: " + r.diagnostics());
        }
    }

    @Test
    void overBudgetKernelProducesAWarning() {
        List<GpuEvent> events = new ArrayList<>();
        try (GpuContext ctx = GpuContext.builder().api(FakeDeviceApi.withGpu()).ignoreEnvironment()
                .listener(events::add).optimize(OptimizeOptions.DEFAULT.withRegisterBudget(1)).build()) {
            assertTrue(ctx.compile(module()).isOk());
        }
        assertTrue(events.stream().anyMatch(e -> e.kind() == GpuEvent.Kind.WARNING
                && e.detail().contains("register pressure")), events.toString());
    }

    @Test
    void optimizerSettingsAreCachedSeparately() {
        FakeDeviceApi fake = FakeDeviceApi.withGpu();
        IrModule m = module();
        try (GpuContext ctx = GpuContext.builder().api(fake).ignoreEnvironment().build()) {
            assertTrue(ctx.compile(m).isOk());
            assertTrue(ctx.compile(m).isOk());
            assertEquals(1, fake.builds(), "second compile is a cache hit");
        }
        assertFalse(OptimizeOptions.DEFAULT.equals(OptimizeOptions.NONE));
    }
}
