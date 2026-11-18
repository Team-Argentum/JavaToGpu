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
        return module(IrModule.OPT_INHERIT);
    }

    /** @param kernelOpt уровень оптимизации из свойств ядра */
    private static IrModule module(int kernelOpt) {
        IrBuilder b = new IrBuilder();
        int arr = b.array(AddressSpace.GLOBAL, Types.I32);
        b.beginFunction(IrModule.DEVICE, Types.I32, Types.I32);
        b.endFunction(b.block(b.returnStmt(b.binary(Op.MUL, b.param(0), b.constI32(7)))));
        b.beginFunction(IrModule.KERNEL, Types.VOID, arr);
        b.optLevel(kernelOpt);
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
    void kernelOptLevelOverridesTheContext() {
        try (GpuContext ctx = GpuContext.builder().api(FakeDeviceApi.withGpu()).ignoreEnvironment().build()) {
            CompiledKernel k = ctx.compile(module(0)).value();
            assertEquals(1, count(k.module(), Op.ADD), "opt=0 of the kernel turns the optimizer off");
        }
        try (GpuContext ctx = GpuContext.builder().api(FakeDeviceApi.withGpu()).ignoreEnvironment()
                .optimize(OptimizeOptions.NONE).build()) {
            CompiledKernel k = ctx.compile(module(2)).value();
            assertEquals(0, count(k.module(), Op.ADD), "opt=2 of the kernel works under a context without optimizer");
        }
    }

    /** Ядро с {@code fp=contract}: {@code a[0] = a[0] * a[1] + 1.5f}. */
    private static IrModule contractKernel() {
        IrBuilder b = new IrBuilder();
        int arr = b.array(AddressSpace.GLOBAL, Types.F32);
        b.beginFunction(IrModule.KERNEL, Types.VOID, arr);
        b.fpMode(IrModule.FP_CONTRACT);
        int x = b.load(b.elemPtr(b.param(0), b.constI32(0)));
        int y = b.load(b.elemPtr(b.param(0), b.constI32(1)));
        int v = b.binary(Op.ADD, b.binary(Op.MUL, x, y), b.constF32(1.5f));
        b.endFunction(b.block(b.store(b.elemPtr(b.param(0), b.constI32(0)), v)));
        return b.build();
    }

    private static int fmas(IrModule m) {
        int fma = com.sixik.gpu.ir.Builtins.idOf("fma");
        int c = 0;
        for (int n = 0; n < m.nodeCount(); n++) {
            if (m.op(n) == Op.INTRINSIC && m.a(n) == fma) {
                c++;
            }
        }
        return c;
    }

    @Test
    void contractionFollowsTheDeviceFma() {
        try (GpuContext ctx = GpuContext.builder().api(FakeDeviceApi.withGpu()).ignoreEnvironment().build()) {
            assertTrue(ctx.device().hardwareFma(false));
            CompiledKernel k = ctx.compile(contractKernel()).value();
            assertEquals(1, fmas(k.module()), "hardware FMA: a*b+c becomes fma");
            assertTrue(k.artifact().source().contains("fma("), k.artifact().source());
        }
        FakeDeviceApi noFma = new FakeDeviceApi(FakeDeviceApi.Spec.gpu().withoutFma());
        try (GpuContext ctx = GpuContext.builder().api(noFma).ignoreEnvironment().build()) {
            assertFalse(ctx.device().hardwareFma(false));
            assertFalse(ctx.device().hardwareFma(true));
            CompiledKernel k = ctx.compile(contractKernel()).value();
            assertEquals(0, fmas(k.module()), "a software fma() would be slower than mul + add");
        }
    }

    /** Строгое ядро {@code a[0] = a[0] / a[1]}. */
    private static IrModule divideKernel(int fp) {
        IrBuilder b = new IrBuilder();
        int arr = b.array(AddressSpace.GLOBAL, Types.F32);
        b.beginFunction(IrModule.KERNEL, Types.VOID, arr);
        b.fpMode(fp);
        int x = b.load(b.elemPtr(b.param(0), b.constI32(0)));
        int y = b.load(b.elemPtr(b.param(0), b.constI32(1)));
        b.endFunction(b.block(b.store(b.elemPtr(b.param(0), b.constI32(0)), b.binary(Op.DIV, x, y))));
        return b.build();
    }

    @Test
    void exactDivisionFollowsTheDevice() {
        FakeDeviceApi exact = new FakeDeviceApi(FakeDeviceApi.Spec.gpu().withExactDivideSqrt());
        try (GpuContext ctx = GpuContext.builder().api(exact).ignoreEnvironment().build()) {
            assertTrue(ctx.device().exactDivideSqrt());
            assertTrue(ctx.compile(divideKernel(0)).isOk());
            assertTrue(exact.buildOptions().get(0).contains("-cl-fp32-correctly-rounded-divide-sqrt"),
                    exact.buildOptions().toString());
            assertTrue(ctx.compile(divideKernel(IrModule.FP_RECIPROCAL)).isOk());
            assertFalse(exact.buildOptions().get(1).contains("correctly-rounded"), "fp=reciprocal allows 2.5 ULP");
        }
        FakeDeviceApi plain = FakeDeviceApi.withGpu();
        try (GpuContext ctx = GpuContext.builder().api(plain).ignoreEnvironment().build()) {
            assertFalse(ctx.device().exactDivideSqrt());
            assertTrue(ctx.compile(divideKernel(0)).isOk());
            assertFalse(plain.buildOptions().get(0).contains("correctly-rounded"), "the device does not support it");
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

    /**
     * Бюджет времени оптимизатора (шаг B7, FRONTEND_ASM_RUNTIME.md, S9-2): исчерпанный бюджет - не ошибка, ядро
     * собирается как есть, а слушатель получает предупреждение с фактическим уровнем.
     */
    @Test
    void exhaustedTimeBudgetCompilesTheKernelUnoptimized() {
        List<GpuEvent> events = new ArrayList<>();
        try (GpuContext ctx = GpuContext.builder().api(FakeDeviceApi.withGpu()).ignoreEnvironment()
                .listener(events::add).optimize(OptimizeOptions.DEFAULT.withTimeBudget(java.time.Duration.ZERO))
                .build()) {
            Result<CompiledKernel> r = ctx.compile(module());
            assertTrue(r.isOk(), r.diagnostics().toString());
            assertEquals(1, count(r.value().module(), Op.ADD), "2 + 3 is not folded at opt=0");
        }
        assertTrue(events.stream().anyMatch(e -> e.kind() == GpuEvent.Kind.WARNING
                && e.detail().contains("exceeded the time budget") && e.detail().endsWith("compiled at opt=0")),
                events.toString());
        assertFalse(events.stream().anyMatch(e -> e.detail() != null && e.detail().contains("pass rejected")),
                events.toString());
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
