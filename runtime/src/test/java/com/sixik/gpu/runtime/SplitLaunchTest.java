package com.sixik.gpu.runtime;

import com.sixik.gpu.api.types.GpuFloatArray;
import com.sixik.gpu.codegen.opencl.KernelArg;
import com.sixik.gpu.ir.AddressSpace;
import com.sixik.gpu.ir.Builtins;
import com.sixik.gpu.ir.IrBuilder;
import com.sixik.gpu.ir.IrModule;
import com.sixik.gpu.ir.Op;
import com.sixik.gpu.ir.Result;
import com.sixik.gpu.ir.Types;
import com.sixik.gpu.ir.interpreter.Interpreter;
import com.sixik.gpu.ir.interpreter.NdRange;
import com.sixik.gpu.runtime.device.FakeDeviceApi;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Исполнение ядра по стадиям {@code KernelSplitter}: рантайм собирает стадии, выделяет промежуточные буферы и запускает
 * стадии подряд; результат совпадает с исполнением целого ядра. Тела ядер фейкового устройства исполняют их IR
 * интерпретатором, поэтому стадии считают по-настоящему.
 */
class SplitLaunchTest {

    /**
     * Ядро с долгоживущими значениями: {@code c0..c(k-1)} читаются в начале и нужны только в конце, между ними две
     * тяжёлые фазы по {@code h} значений. Параметры {@code (in0, in1, out)}; work-item читает {@code stride} элементов
     * каждого входа (при {@code k <= stride} все {@code c} читаются из разных адресов и живы одновременно).
     */
    static IrModule phased(int k, int h) {
        return phased(k, h, 64);
    }

    static IrModule phased(int k, int h, int stride) {
        IrBuilder b = new IrBuilder();
        int arr = b.array(AddressSpace.GLOBAL, Types.F32);
        b.beginFunction(IrModule.KERNEL, Types.VOID, arr, arr, arr);
        List<Integer> st = new ArrayList<>();
        int g = b.declareLocal(Types.I32);
        st.add(b.localDecl(g, b.intrinsic(Builtins.idOf("getGlobalId"), b.constI32(0))));
        int[] c = new int[k];
        for (int i = 0; i < k; i++) {
            c[i] = b.declareLocal(Types.F32);
            st.add(b.localDecl(c[i], b.load(b.elemPtr(b.param(0), index(b, g, i % stride, stride)))));
        }
        int[] phaseSum = new int[2];
        for (int ph = 0; ph < 2; ph++) {
            int[] a = new int[h];
            for (int i = 0; i < h; i++) {
                a[i] = b.declareLocal(Types.F32);
                st.add(b.localDecl(a[i], b.intrinsic(Builtins.idOf("sin"),
                        b.load(b.elemPtr(b.param(1), index(b, g, (ph * h + i) % stride, stride))))));
            }
            int sum = b.declareLocal(Types.F32);
            int acc = b.localGet(a[0]);
            for (int i = 1; i < h; i++) {
                acc = b.binary(Op.ADD, acc, b.binary(Op.MUL, b.localGet(a[i]), b.localGet(a[h - 1 - i])));
            }
            st.add(b.localDecl(sum, acc));
            phaseSum[ph] = sum;
        }
        // сбалансированное дерево: глубина выражения ~log2(k) (компилятор OpenCL C ограничивает вложенность скобок)
        List<Integer> level = new ArrayList<>();
        level.add(b.binary(Op.ADD, b.localGet(phaseSum[0]), b.localGet(phaseSum[1])));
        for (int i = 0; i < k; i++) {
            level.add(b.localGet(c[i]));
        }
        while (level.size() > 1) {
            List<Integer> next = new ArrayList<>();
            for (int i = 0; i + 1 < level.size(); i += 2) {
                next.add(b.binary(Op.ADD, b.binary(Op.MUL, level.get(i), b.constF32(0.5f)), level.get(i + 1)));
            }
            if (level.size() % 2 == 1) {
                next.add(level.get(level.size() - 1));
            }
            level = next;
        }
        int total = level.get(0);
        st.add(b.store(b.elemPtr(b.param(2), b.localGet(g)), total));
        b.endFunction(b.block(st.stream().mapToInt(Integer::intValue).toArray()));
        return b.build();
    }

    private static int index(IrBuilder b, int g, int i, int stride) {
        return b.binary(Op.ADD, b.binary(Op.MUL, b.localGet(g), b.constI32(stride)), b.constI32(i));
    }

    /** Входы на {@code threads} work-item'ов. */
    static float[][] inputs(int threads) {
        return inputs(threads, 64);
    }

    static float[][] inputs(int threads, int stride) {
        float[] in0 = new float[threads * stride];
        float[] in1 = new float[threads * stride];
        for (int i = 0; i < in0.length; i++) {
            in0[i] = (i * 7 % 13) * 0.25f - 1f;
            in1[i] = (i * 5 % 17) * 0.1f;
        }
        return new float[][]{in0, in1};
    }

    /** Эталон: интерпретатор над исходным модулем. */
    static float[] expected(IrModule m, int threads) {
        float[][] in = inputs(threads);
        float[] out = new float[threads];
        Result<Interpreter.Execution> r = Interpreter.run(m, NdRange.of1D(threads), in[0], in[1], out);
        assertTrue(r.isOk(), r.diagnostics().toString());
        return out;
    }

    /** Тело ядра фейка: исполняет функцию {@code k} интерпретатором над буферами запуска (массивы - {@code float}/{@code int}). */
    private static FakeDeviceApi.KernelBody interpreting(CompiledKernel k) {
        return l -> {
            IrModule m = k.module();
            int f = k.function();
            Object[] args = new Object[m.fParamCount(f)];
            List<KernelArg> list = k.artifact().args();
            int[] argOf = new int[args.length];
            for (int i = 0; i < list.size(); i++) {
                KernelArg ka = list.get(i);
                if (ka.kind() == KernelArg.ARRAY_PTR) {
                    argOf[ka.param()] = i;
                    ByteBuffer buf = l.buffer(i);
                    int elem = Types.elem(m, m.fParamType(f, ka.param()));
                    if (elem == Types.F32) {
                        float[] a = new float[buf.capacity() / 4];
                        buf.asFloatBuffer().get(a);
                        args[ka.param()] = a;
                    } else {
                        int[] a = new int[buf.capacity() / 4];
                        buf.asIntBuffer().get(a);
                        args[ka.param()] = a;
                    }
                } else if (ka.kind() == KernelArg.SCALAR) {
                    args[ka.param()] = ka.type() == Types.F32 ? (Object) l.floatArg(i) : (Object) l.intArg(i);
                }
            }
            Result<Interpreter.Execution> r = Interpreter.run(m, f, NdRange.of1D((int) l.global(0)),
                    Interpreter.Options.DEFAULT, args);
            assertTrue(r.isOk(), r.diagnostics().toString());
            for (int p = 0; p < args.length; p++) {
                if (args[p] instanceof float[] a) {
                    l.buffer(argOf[p]).asFloatBuffer().put(a);
                } else if (args[p] instanceof int[] a) {
                    l.buffer(argOf[p]).asIntBuffer().put(a);
                }
            }
        };
    }

    /**
     * Регистрирует тела ядра и его стадий в фейке. Стадии - функции другого модуля, и их имена могут совпасть с именем
     * целого ядра ({@code k0}); фейк ищет тело по имени, поэтому тело выбирает ядро по числу аргументов (у стадий есть
     * ещё промежуточные буферы).
     */
    private static void install(FakeDeviceApi fake, CompiledKernel k) {
        java.util.Map<String, List<CompiledKernel>> byName = new java.util.HashMap<>();
        byName.computeIfAbsent(k.artifact().entry(), x -> new ArrayList<>()).add(k);
        if (k.split != null) {
            for (CompiledKernel s : k.split.stages()) {
                byName.computeIfAbsent(s.artifact().entry(), x -> new ArrayList<>()).add(s);
            }
        }
        byName.forEach((name, kernels) -> fake.onKernel(name, l -> {
            for (CompiledKernel c : kernels) {
                if (c.artifact().args().size() == l.argCount()) {
                    interpreting(c).run(l);
                    return;
                }
            }
            throw new AssertionError("no kernel " + name + " with " + l.argCount() + " arguments");
        }));
    }

    private static GpuContext open(FakeDeviceApi fake, KernelSplitMode mode) {
        return GpuContext.builder().api(fake).ignoreEnvironment().splitKernels(mode).build();
    }

    @Test
    void splitKernelGivesTheSameResultAsTheWholeOne() {
        IrModule m = phased(20, 12);
        int threads = 16;
        float[] want = expected(m, threads);
        for (KernelSplitMode mode : new KernelSplitMode[]{KernelSplitMode.OFF, KernelSplitMode.ALWAYS}) {
            FakeDeviceApi fake = FakeDeviceApi.withGpu();
            try (GpuContext ctx = open(fake, mode)) {
                CompiledKernel k = ctx.compile(m).value();
                install(fake, k);
                if (mode == KernelSplitMode.ALWAYS) {
                    assertTrue(k.stageCount() >= 2, "the kernel is split: " + k.stageCount());
                } else {
                    assertEquals(1, k.stageCount());
                }
                float[][] in = inputs(threads);
                float[] out = new float[threads];
                int enqueues = fake.enqueues();
                LaunchStats st = ctx.launch(k, LaunchConfig.of1D(threads), in[0], in[1], out).value();
                assertEquals(enqueues + k.stageCount(), fake.enqueues(), "one launch per stage");
                assertTrue(st.kernelNanos() >= 0);
                assertArrayEquals(want, out, "mode " + mode);
            }
        }
    }

    @Test
    void autoSplitsOnlyKernelsOverTheBudgetWhenTheDriverGivesNoStats() {
        FakeDeviceApi fake = FakeDeviceApi.withGpu();
        try (GpuContext ctx = open(fake, KernelSplitMode.AUTO)) {
            CompiledKernel light = ctx.compile(phased(4, 4)).value();
            assertEquals(1, light.stageCount(), "estimate " + light.estimatedRegisters());
            CompiledKernel heavy = ctx.compile(phased(60, 24)).value();
            assertTrue(heavy.estimatedRegisters() > ctx.optimizeOptions().registerBudget());
            assertTrue(heavy.stageCount() >= 2, "estimate " + heavy.estimatedRegisters());
        }
    }

    @Test
    void preparedAndReusedLaunchesRunTheStages() {
        IrModule m = phased(20, 12);
        int threads = 8;
        float[] want = expected(m, threads);
        FakeDeviceApi fake = FakeDeviceApi.withGpu();
        try (GpuContext ctx = open(fake, KernelSplitMode.ALWAYS)) {
            CompiledKernel k = ctx.compile(m).value();
            install(fake, k);
            float[][] in = inputs(threads);
            float[] out = new float[threads];
            try (PreparedLauncher p = ctx.prepare(k, in[0], in[1], out).value()) {
                for (int i = 0; i < 3; i++) {
                    java.util.Arrays.fill(out, 0f);
                    p.run(LaunchConfig.of1D(threads));
                    assertArrayEquals(want, out, "prepared launch " + i);
                }
            }
            GpuFloatArray a = GpuFloatArray.of(in[0]);
            GpuFloatArray b = GpuFloatArray.of(in[1]);
            GpuFloatArray r = new GpuFloatArray(threads);
            int finishes = fake.finishes();
            for (int i = 0; i < 3; i++) {
                assertTrue(ctx.launchReusing(k, LaunchConfig.of1D(threads), new Object[]{a, b, r}).isOk());
            }
            assertEquals(finishes, fake.finishes(), "stages of a GpuArray launch do not wait either");
            assertArrayEquals(want, r.toArray());
        }
    }

    @Test
    void autoMeasuresBothVariantsThenDecides() {
        IrModule m = phased(60, 24);
        int threads = 8;
        float[] want = expected(m, threads);
        FakeDeviceApi fake = FakeDeviceApi.withGpu();
        List<String> decisions = new ArrayList<>();
        GpuContext.Builder builder = GpuContext.builder().api(fake).ignoreEnvironment().splitKernels(KernelSplitMode.AUTO)
                .listener(e -> {
                    if (e.kind() == GpuEvent.Kind.COMPILE && e.detail().contains("by measurement")) {
                        decisions.add(e.detail());
                    }
                });
        try (GpuContext ctx = builder.build()) {
            CompiledKernel k = ctx.compile(m).value();
            install(fake, k);
            assertTrue(k.stageCount() >= 2);
            assertTrue(!k.splitDecided(), "AUTO decides by measurement, not at compile time");
            float[][] in = inputs(threads);
            for (int i = 0; i < GpuContext.SPLIT_TRIAL_LAUNCHES; i++) {
                float[] out = new float[threads];
                assertTrue(ctx.launch(k, LaunchConfig.of1D(threads), in[0], in[1], out).isOk());
                assertArrayEquals(want, out, "trial launch " + i);
            }
            assertTrue(k.splitDecided());
            assertEquals(1, decisions.size(), decisions.toString());
            float[] out = new float[threads];
            assertTrue(ctx.launch(k, LaunchConfig.of1D(threads), in[0], in[1], out).isOk());
            assertArrayEquals(want, out, "after the decision");
        }
    }

    @Test
    void scratchBuffersAreReusedAndReleasedWithTheContext() {
        IrModule m = phased(20, 12);
        FakeDeviceApi fake = FakeDeviceApi.withGpu();
        try (GpuContext ctx = open(fake, KernelSplitMode.ALWAYS)) {
            CompiledKernel k = ctx.compile(m).value();
            install(fake, k);
            float[][] in = inputs(32);
            float[] out = new float[32];
            assertTrue(ctx.launch(k, LaunchConfig.of1D(32), in[0], in[1], out).isOk());
            int live = fake.liveBuffers();
            assertTrue(ctx.launch(k, LaunchConfig.of1D(16), in[0], in[1], out).isOk());
            assertEquals(live, fake.liveBuffers(), "a smaller launch reuses the scratch buffers");
        }
        assertEquals(0, fake.liveBuffers());
    }
}
