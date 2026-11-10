package com.sixik.gpu.ir.optimizer;

import com.sixik.gpu.ir.IrModule;
import com.sixik.gpu.ir.Op;
import com.sixik.gpu.ir.Result;
import com.sixik.gpu.ir.Types;
import com.sixik.gpu.ir.Validator;
import com.sixik.gpu.ir.interpreter.Interpreter;
import com.sixik.gpu.ir.interpreter.NdRange;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Оценка давления на регистры и разбиение ядра на стадии.
 */
class PressureAndSplitTest {

    /**
     * Ядро с «долгоживущими» значениями: {@code c0..c(k-1)} загружаются в начале и нужны только в конце, между ними две
     * тяжёлые фазы с {@code h} живыми значениями каждая. Целиком ядро держит {@code k + h} значений, а в стадии
     * достаточно {@code h} (или {@code k}).
     */
    private static IrModule phasedKernel(int k, int h) {
        com.sixik.gpu.ir.IrBuilder b = new com.sixik.gpu.ir.IrBuilder();
        int arr = b.array(com.sixik.gpu.ir.AddressSpace.GLOBAL, Types.F32);
        b.beginFunction(IrModule.KERNEL, Types.VOID, arr, arr, arr);
        List<Integer> st = new ArrayList<>();
        int g = b.declareLocal(Types.I32);
        st.add(b.localDecl(g, OptTest.gid(b)));
        int[] c = new int[k];
        for (int i = 0; i < k; i++) {
            c[i] = b.declareLocal(Types.F32);
            int idx = b.binary(Op.ADD, b.binary(Op.MUL, b.localGet(g), b.constI32(64)), b.constI32(i));
            st.add(b.localDecl(c[i], OptTest.loadAt(b, 0, idx)));
        }
        int[] phaseSum = new int[2];
        for (int ph = 0; ph < 2; ph++) {
            int[] a = new int[h];
            for (int i = 0; i < h; i++) {
                a[i] = b.declareLocal(Types.F32);
                int idx = b.binary(Op.ADD, b.binary(Op.MUL, b.localGet(g), b.constI32(64)), b.constI32(32 + ph * 16 + i));
                // значения зависят от входа через sin, поэтому не сворачиваются
                st.add(b.localDecl(a[i], b.intrinsic(OptTest.SIN, OptTest.loadAt(b, 1, idx))));
            }
            int sum = b.declareLocal(Types.F32);
            int acc = b.localGet(a[0]);
            for (int i = 1; i < h; i++) {
                acc = b.binary(Op.ADD, acc, b.localGet(a[i]));
            }
            st.add(b.localDecl(sum, acc));
            phaseSum[ph] = sum;
        }
        int total = b.localGet(phaseSum[0]);
        total = b.binary(Op.ADD, total, b.localGet(phaseSum[1]));
        for (int i = 0; i < k; i++) {
            total = b.binary(Op.ADD, total, b.localGet(c[i]));
        }
        st.add(OptTest.storeAt(b, 2, b.localGet(g), total));
        b.endFunction(b.block(st.stream().mapToInt(Integer::intValue).toArray()));
        return b.build();
    }

    private static Object[] data(int threads) {
        float[] in0 = new float[threads * 64];
        float[] in1 = new float[threads * 64];
        for (int i = 0; i < in0.length; i++) {
            in0[i] = (i * 7 % 13) * 0.25f - 1f;
            in1[i] = (i * 5 % 17) * 0.1f;
        }
        return new Object[]{in0, in1, new float[threads]};
    }

    @Test
    void pressureGrowsWithLiveValues() {
        int small = RegisterPressure.analyze(phasedKernel(2, 2)).functions()[0].registers();
        int big = RegisterPressure.analyze(phasedKernel(20, 12)).functions()[0].registers();
        assertTrue(big > small + 10, "registers: " + small + " vs " + big);
        RegisterPressure.FunctionPressure fp = RegisterPressure.analyze(phasedKernel(20, 12)).functions()[0];
        assertTrue(fp.registers() >= 20 + 12, "all long-lived values and one phase are alive: " + fp);
        assertEquals(fp.registers(), fp.peakLiveWords() + fp.peakTempWords());
    }

    @Test
    void doubleAndVectorValuesWeighMore() {
        IrModule f32 = OptTest.kernel(new int[]{Types.F32}, new int[]{Types.F32, Types.F32},
                (b, l) -> new int[]{b.localDecl(l[0], b.constF32(1f)), b.localDecl(l[1], b.constF32(2f)),
                        OptTest.storeAt(b, 0, b.constI32(0), b.binary(Op.ADD,
                                b.binary(Op.MUL, b.localGet(l[0]), OptTest.loadAt(b, 0, b.constI32(1))),
                                b.localGet(l[1])))});
        IrModule f64 = OptTest.kernel(new int[]{Types.F64}, new int[]{Types.F64, Types.F64},
                (b, l) -> new int[]{b.localDecl(l[0], b.constF64(1d)), b.localDecl(l[1], b.constF64(2d)),
                        OptTest.storeAt(b, 0, b.constI32(0), b.binary(Op.ADD,
                                b.binary(Op.MUL, b.localGet(l[0]), OptTest.loadAt(b, 0, b.constI32(1))),
                                b.localGet(l[1])))});
        int r32 = RegisterPressure.analyze(f32).functions()[0].registers();
        int r64 = RegisterPressure.analyze(f64).functions()[0].registers();
        assertTrue(r64 > r32, r32 + " vs " + r64);
    }

    @Test
    void optimizerReportsKernelsOverBudget() {
        IrModule m = phasedKernel(20, 12);
        OptimizeResult r = Optimizer.optimize(m, OptimizeOptions.DEFAULT.withRegisterBudget(24));
        assertTrue(r.ok());
        assertEquals(List.of(0), r.overBudget());
        assertTrue(Optimizer.optimize(m, OptimizeOptions.DEFAULT.withRegisterBudget(512)).overBudget().isEmpty());
    }

    @Test
    void kernelThatFitsIsNotSplit() {
        IrModule m = phasedKernel(2, 2);
        KernelSplitter.SplitResult r = KernelSplitter.split(m, 0, OptimizeOptions.DEFAULT);
        assertFalse(r.isSplit());
        assertTrue(r.reason().contains("fits"), r.reason());
    }

    @Test
    void heavyKernelSplitsIntoStagesWithSameResults() {
        IrModule m = OptTest.opt(phasedKernel(20, 12));
        int original = RegisterPressure.analyze(m).functions()[0].registers();
        OptimizeOptions budget = OptimizeOptions.DEFAULT.withRegisterBudget(original - 6);

        KernelSplitter.SplitResult r = KernelSplitter.split(m, 0, budget);
        assertTrue(r.isSplit(), r.reason());
        KernelSplitter.KernelSplit s = r.split();
        assertTrue(s.stages().length >= 2);
        assertFalse(Validator.validate(r.module()).hasErrors());
        for (int regs : s.stageRegisters()) {
            assertTrue(regs < s.originalRegisters(), "stage " + regs + " vs original " + s.originalRegisters());
        }

        // исполнение стадий подряд с общими scratch-буферами = исполнение исходного ядра
        int threads = 8;
        Object[] expected = data(threads);
        Result<Interpreter.Execution> ref = Interpreter.run(m, NdRange.of1D(threads), expected);
        assertTrue(ref.isOk(), ref.diagnostics().toString());

        Object[] args = new Object[3 + s.scratch().size()];
        Object[] base = data(threads);
        System.arraycopy(base, 0, args, 0, 3);
        for (KernelSplitter.Scratch sc : s.scratch()) {
            args[sc.paramIndex()] = OptTest.newArray(sc.elementType(), threads * sc.elementsPerWorkItem());
        }
        for (int stage : s.stages()) {
            Result<Interpreter.Execution> run = Interpreter.run(r.module(), stage, NdRange.of1D(threads),
                    Interpreter.Options.DEFAULT, args);
            assertTrue(run.isOk(), run.diagnostics().toString());
        }
        OptTest.compare("split", 2, expected[2], args[2]);
    }

    @Test
    void kernelWithEarlyReturnInTheMiddleIsNotCutAfterIt() {
        IrModule m = OptTest.kernel(new int[]{Types.F32}, new int[]{Types.F32},
                (b, l) -> new int[]{
                        b.localDecl(l[0], OptTest.loadAt(b, 0, b.constI32(0))),
                        b.ifStmt(b.binary(Op.GT, b.localGet(l[0]), b.constF32(0f)), b.block(b.returnStmt(-1)), -1),
                        OptTest.storeAt(b, 0, b.constI32(1), b.localGet(l[0])),
                        OptTest.storeAt(b, 0, b.constI32(2), b.localGet(l[0]))});
        KernelSplitter.SplitResult r = KernelSplitter.split(m, 0, OptimizeOptions.DEFAULT.withRegisterBudget(1));
        // допустимы только разрезы до if; результат либо не разбит, либо корректен
        if (r.isSplit()) {
            assertFalse(Validator.validate(r.module()).hasErrors());
        }
    }
}
