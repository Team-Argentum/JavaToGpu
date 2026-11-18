package com.sixik.gpu.runtime;

import com.sixik.gpu.ir.AddressSpace;
import com.sixik.gpu.ir.Fixtures;
import com.sixik.gpu.ir.IrBuilder;
import com.sixik.gpu.ir.IrModule;
import com.sixik.gpu.ir.Op;
import com.sixik.gpu.ir.Result;
import com.sixik.gpu.ir.Types;
import com.sixik.gpu.runtime.device.FakeDeviceApi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Шаг B6 (FRONTEND_ASM_RUNTIME.md, 18.2-S4): {@link GpuContext#compileProgram} собирает все ядра модуля одной
 * программой драйвера.
 */
class CompileProgramTest {

    /**
     * Общая функция {@code sq(x) = x * x} ({@code inline = NEVER}) и три ядра над {@code int[] a}:
     * {@code square: a[i] = sq(a[i])}, {@code plusOne: a[i] = a[i] + 1}, {@code fill: a[i] = sq(i)}.
     */
    static IrModule module() {
        IrBuilder b = new IrBuilder();
        int arr = b.array(AddressSpace.GLOBAL, Types.I32);
        int sq = b.beginFunction(IrModule.DEVICE, Types.I32, Types.I32);
        b.inlinePolicy(IrModule.INLINE_NEVER);
        b.nameFunction("sq");
        b.endFunction(b.block(b.returnStmt(b.binary(Op.MUL, b.param(0), b.param(0)))));
        String[] names = {"square", "plusOne", "fill"};
        for (int k = 0; k < 3; k++) {
            b.beginFunction(IrModule.KERNEL, Types.VOID, arr);
            b.nameFunction(names[k]);
            int id = b.declareLocal(Types.I32);
            int decl = b.localDecl(id, b.intrinsic(Fixtures.GET_GLOBAL_ID, b.constI32(0)));
            int v = switch (k) {
                case 0 -> b.call(sq, b.load(b.elemPtr(b.param(0), b.localGet(id))));
                case 1 -> b.binary(Op.ADD, b.load(b.elemPtr(b.param(0), b.localGet(id))), b.constI32(1));
                default -> b.call(sq, b.localGet(id));
            };
            b.endFunction(b.block(decl, b.store(b.elemPtr(b.param(0), b.localGet(id)), v)));
        }
        return b.build();
    }

    /** Ядро фейка: {@code a[i] = f(i, a[i])} по всей длине. */
    private static FakeDeviceApi.KernelBody body(java.util.function.IntBinaryOperator f) {
        return l -> {
            ByteBuffer a = l.buffer(0);
            for (int i = 0; i < l.intArg(1); i++) {
                a.putInt(4 * i, f.applyAsInt(i, a.getInt(4 * i)));
            }
        };
    }

    /** Критерий B4/B6: одна сборка драйвером, ядро на точку входа, поиск по имени метода и по индексу. */
    @Test
    void oneDriverBuildForAllKernels() {
        FakeDeviceApi fake = FakeDeviceApi.withGpu();
        IrModule m = module();
        try (GpuContext ctx = GpuContext.builder().api(fake).ignoreEnvironment().build()) {
            Result<CompiledProgram> r = ctx.compileProgram(m);
            assertTrue(r.isOk(), r.diagnostics().toString());
            CompiledProgram p = r.value();
            assertEquals(1, fake.builds());
            assertEquals(1, fake.sources().size());
            assertEquals(1, fake.livePrograms());
            assertEquals(3, fake.liveKernels());
            assertEquals(3, p.kernels().size());
            assertEquals(com.sixik.gpu.ir.IrBinary.kernelId(m), p.moduleId());
            assertSame(p.kernel(1), p.kernel("square"));
            assertSame(p.kernel(2), p.kernel("plusOne"));
            assertSame(p.kernel(3), p.kernel("fill"));
            assertSame(p.kernel("fill"), p.kernel(p.kernel("fill").artifact().entry()));
            assertNull(p.kernel(0), "sq is not a kernel");
            assertNull(p.kernel("sq"));
            for (CompiledKernel k : p.kernels()) {
                assertEquals(p.source(), k.artifact().source());
                assertTrue(p.source().contains("__kernel void " + k.artifact().entry() + "("));
            }

            fake.onKernel(p.kernel("square").artifact().entry(), body((i, a) -> a * a))
                    .onKernel(p.kernel("plusOne").artifact().entry(), body((i, a) -> a + 1))
                    .onKernel(p.kernel("fill").artifact().entry(), body((i, a) -> i * i));
            int[] a = {1, 2, 3, 4};
            assertTrue(ctx.launch(p.kernel("square"), LaunchConfig.of1D(4), (Object) a).isOk());
            assertTrue(ctx.launch(p.kernel("plusOne"), LaunchConfig.of1D(4), (Object) a).isOk());
            assertArrayEquals(new int[]{2, 5, 10, 17}, a);
            assertTrue(ctx.launch(p.kernel("fill"), LaunchConfig.of1D(4), (Object) a).isOk());
            assertArrayEquals(new int[]{0, 1, 4, 9}, a);

            assertSame(p, ctx.compileProgram(m).value(), "second request is a cache hit");
            assertEquals(1, fake.builds());
        }
        assertEquals(0, fake.livePrograms(), "the shared program is released with the context");
        assertEquals(0, fake.liveKernels());
    }

    /** Доступ параметров - по оптимизированному телу каждого ядра: {@code fill} только пишет. */
    @Test
    void accessIsPerKernel() {
        try (GpuContext ctx = GpuContext.builder().api(FakeDeviceApi.withGpu()).ignoreEnvironment().build()) {
            CompiledProgram p = ctx.compileProgram(module()).value();
            assertEquals(com.sixik.gpu.ir.Validator.ACCESS_READ | com.sixik.gpu.ir.Validator.ACCESS_WRITE,
                    p.kernel("square").access(0));
            assertEquals(com.sixik.gpu.ir.Validator.ACCESS_WRITE, p.kernel("fill").access(0));
        }
    }

    /** Второй контекст собирает программу из образа на диске: исходник драйверу не передаётся. */
    @Test
    void secondContextUsesTheDiskImage(@TempDir Path dir) {
        FakeDeviceApi first = FakeDeviceApi.withGpu();
        try (GpuContext ctx = GpuContext.builder().api(first).ignoreEnvironment().diskCache(dir).build()) {
            assertTrue(ctx.compileProgram(module()).isOk());
        }
        assertEquals(1, first.sources().size());
        List<GpuEvent> events = new ArrayList<>();
        FakeDeviceApi second = FakeDeviceApi.withGpu();
        try (GpuContext ctx = GpuContext.builder().api(second).ignoreEnvironment().diskCache(dir)
                .listener(events::add).build()) {
            Result<CompiledProgram> r = ctx.compileProgram(module());
            assertTrue(r.isOk(), r.diagnostics().toString());
            assertEquals(3, second.liveKernels());
        }
        assertEquals(0, second.sources().size(), "the source was not passed to the driver");
        assertTrue(events.stream().anyMatch(e -> e.kind() == GpuEvent.Kind.COMPILE
                && e.detail().startsWith("disk-hit; program of 3 kernels")), events.toString());
    }

    @Test
    void moduleWithoutKernelsIsAnError() {
        IrBuilder b = new IrBuilder();
        b.beginFunction(IrModule.DEVICE, Types.I32, Types.I32);
        b.endFunction(b.block(b.returnStmt(b.param(0))));
        try (GpuContext ctx = GpuContext.builder().api(FakeDeviceApi.withGpu()).ignoreEnvironment().build()) {
            Result<CompiledProgram> r = ctx.compileProgram(b.build());
            assertFalse(r.isOk());
            assertEquals("CG-A01", r.diagnostics().get(0).code());
        }
    }

    /** Ядро с {@code double} на устройстве без {@code cl_khr_fp64}: {@code RT-D02} для всей программы. */
    @Test
    void missingFeatureFailsTheWholeProgram() {
        IrBuilder b = new IrBuilder();
        int arr = b.array(AddressSpace.GLOBAL, Types.F64);
        b.beginFunction(IrModule.KERNEL, Types.VOID, arr);
        b.endFunction(b.block(b.store(b.elemPtr(b.param(0), b.constI32(0)), b.constF64(1.0))));
        FakeDeviceApi noFp64 = new FakeDeviceApi(FakeDeviceApi.Spec.gpu().withExtensions(""));
        try (GpuContext ctx = GpuContext.builder().api(noFp64).ignoreEnvironment().build()) {
            Result<CompiledProgram> r = ctx.compileProgram(b.build());
            assertFalse(r.isOk());
            assertEquals("RT-D02", r.diagnostics().get(0).code());
            assertNotNull(r.diagnostics().get(0).message());
            assertEquals(0, noFp64.builds());
        }
    }
}
