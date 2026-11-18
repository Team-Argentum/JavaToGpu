package com.sixik.gpu.ir.optimizer;

import com.sixik.gpu.ir.AddressSpace;
import com.sixik.gpu.ir.Fixtures;
import com.sixik.gpu.ir.IrBuilder;
import com.sixik.gpu.ir.IrModule;
import com.sixik.gpu.ir.Op;
import com.sixik.gpu.ir.Operands;
import com.sixik.gpu.ir.Result;
import com.sixik.gpu.ir.Types;
import com.sixik.gpu.ir.interpreter.Interpreter;
import com.sixik.gpu.ir.interpreter.NdRange;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Шаг B5 (FRONTEND_ASM_RUNTIME.md, 18.2-S6, п. 3): оптимизатор не спекулирует - CSE и LICM не вводят вычисление на
 * путь, где его не было. Вызов чистой функции с инвариантным аргументом под условием в цикле остаётся под условием на
 * всех уровнях: по счётчику вызовов интерпретатора и по форме IR. Для GA2 ленивость таких ветвей - главная экономия.
 */
class NonSpeculationTest {

    private static final int WORK_ITEMS = 16;
    private static final int TRIPS = 8;

    /**
     * {@code f(x) = x*x + 1} (чистая) и ядро {@code k(float[] a, float c)}:
     * {@code for (j = 0; j < 8; j++) if (a[i] > 100) a[i] = a[i] + f(c);} - {@code f(c)} инвариантен в цикле, но
     * исполняется только при истинном условии.
     */
    static IrModule guardedInvariantCall() {
        IrBuilder b = new IrBuilder();
        int arr = b.array(AddressSpace.GLOBAL, Types.F32);
        int f = b.beginFunction(IrModule.DEVICE, Types.F32, Types.F32);
        b.endFunction(b.block(b.returnStmt(b.binary(Op.ADD, b.binary(Op.MUL, b.param(0), b.param(0)),
                b.constF32(1f)))));
        b.beginFunction(IrModule.KERNEL, Types.VOID, arr, Types.F32);
        int i = b.declareLocal(Types.I32);
        int j = b.declareLocal(Types.I32);
        int di = b.localDecl(i, b.intrinsic(Fixtures.GET_GLOBAL_ID, b.constI32(0)));
        int dj = b.localDecl(j, b.constI32(0));
        int cond = b.binary(Op.LT, b.localGet(j), b.constI32(TRIPS));
        int test = b.binary(Op.GT, b.load(b.elemPtr(b.param(0), b.localGet(i))), b.constF32(100f));
        int sum = b.binary(Op.ADD, b.load(b.elemPtr(b.param(0), b.localGet(i))), b.call(f, b.param(1)));
        int st = b.store(b.elemPtr(b.param(0), b.localGet(i)), sum);
        int body = b.block(b.ifStmt(test, b.block(st), -1));
        int upd = b.localSet(j, b.binary(Op.ADD, b.localGet(j), b.constI32(1)));
        b.endFunction(b.block(di, dj, b.loop(cond, body, upd)));
        return b.build();
    }

    /**
     * CSE: два одинаковых вызова в ветках {@code select} одного выражения -
     * {@code a[i] = (a[i] > 100 ? f(c) : 0) + (a[i+16] > 100 ? f(c) : 1)} - не объединяются в общую локаль,
     * вычисляемую до обоих {@code select}.
     */
    static IrModule callsInSelectBranches() {
        IrBuilder b = new IrBuilder();
        int arr = b.array(AddressSpace.GLOBAL, Types.F32);
        int f = b.beginFunction(IrModule.DEVICE, Types.F32, Types.F32);
        b.endFunction(b.block(b.returnStmt(b.binary(Op.ADD, b.binary(Op.MUL, b.param(0), b.param(0)),
                b.constF32(1f)))));
        b.beginFunction(IrModule.KERNEL, Types.VOID, arr, Types.F32);
        int i = b.declareLocal(Types.I32);
        int di = b.localDecl(i, b.intrinsic(Fixtures.GET_GLOBAL_ID, b.constI32(0)));
        int[] v = new int[2];
        for (int k = 0; k < 2; k++) {
            int idx = k == 0 ? b.localGet(i) : b.binary(Op.ADD, b.localGet(i), b.constI32(WORK_ITEMS));
            int test = b.binary(Op.GT, b.load(b.elemPtr(b.param(0), idx)), b.constF32(100f));
            v[k] = b.select(test, b.call(f, b.param(1)), b.constF32(k));
        }
        int sum = b.binary(Op.ADD, v[0], v[1]);
        int st = b.store(b.elemPtr(b.param(0), b.localGet(i)), sum);
        b.endFunction(b.block(di, st));
        return b.build();
    }

    @Test
    void callsInSelectBranchesAreNotMerged() {
        IrModule m = callsInSelectBranches();
        for (int level = 0; level <= 3; level++) {
            IrModule opt = level == 0 ? m : OptTest.opt(m, OptimizeOptions.DEFAULT.withLevel(level));
            float[] a = new float[2 * WORK_ITEMS];
            Result<Interpreter.Execution> r = Interpreter.run(opt, opt.functionCount() - 1, NdRange.of1D(WORK_ITEMS),
                    Interpreter.Options.DEFAULT, new Object[]{a, 3f});
            assertTrue(r.isOk(), r.diagnostics().toString());
            assertEquals(0, r.value().calls(), "opt=" + level + ": a call from a select branch was evaluated");
        }
    }

    private static long calls(IrModule m, float[] a) {
        Result<Interpreter.Execution> r = Interpreter.run(m, m.functionCount() - 1, NdRange.of1D(WORK_ITEMS),
                Interpreter.Options.DEFAULT, new Object[]{a, 3f});
        assertTrue(r.isOk(), r.diagnostics().toString());
        return r.value().calls();
    }

    /** Все узлы {@code call} функции {@code f} лежат в ветке «истина» какого-нибудь {@code if}. */
    private static boolean everyCallIsGuarded(IrModule m, int f) {
        boolean[] guarded = new boolean[m.nodeCount()];
        boolean[] inF = new boolean[m.nodeCount()];
        mark(m, m.fBody(f), inF);
        for (int n = 0; n < m.nodeCount(); n++) {
            if (inF[n] && m.op(n) == Op.IF) {
                mark(m, m.b(n), guarded);
            }
        }
        for (int n = 0; n < m.nodeCount(); n++) {
            if (inF[n] && m.op(n) == Op.CALL && !guarded[n]) {
                return false;
            }
        }
        return true;
    }

    private static void mark(IrModule m, int root, boolean[] out) {
        int[] stack = new int[Math.max(16, m.nodeCount())];
        int sp = 0;
        stack[sp++] = root;
        while (sp > 0) {
            int n = stack[--sp];
            if (out[n]) {
                continue;
            }
            out[n] = true;
            for (int k = 0; k < Operands.count(m, n); k++) {
                stack[sp++] = Operands.get(m, n, k);
            }
        }
    }

    /** Условие ложно у всех: ни одного вызова ни на одном уровне; вызов остаётся под {@code if}. */
    @Test
    void callFromABranchIsNotHoisted() {
        IrModule m = guardedInvariantCall();
        for (int level = 0; level <= 3; level++) {
            IrModule opt = level == 0 ? m : OptTest.opt(m, OptimizeOptions.DEFAULT.withLevel(level));
            float[] a = new float[WORK_ITEMS];
            assertEquals(0, calls(opt, a), "opt=" + level + ": the call is executed on a path where it was not");
            assertArrayEquals(new float[WORK_ITEMS], a);
            assertTrue(everyCallIsGuarded(opt, opt.functionCount() - 1), "opt=" + level + ": a call left its if");
        }
    }

    /**
     * Условие истинно: вызовов не больше, чем у исходного модуля ({@code 8} на work-item), результат тот же. Уровни
     * выше 0 могут вызывать реже (инвариант внутри ветки), но не чаще.
     */
    @Test
    void takenBranchCallsAtMostAsOftenAsTheOriginal() {
        IrModule m = guardedInvariantCall();
        float[] expected = new float[WORK_ITEMS];
        Arrays.fill(expected, 200f);
        assertEquals((long) WORK_ITEMS * TRIPS, calls(m, expected));
        for (int level = 1; level <= 3; level++) {
            IrModule opt = OptTest.opt(m, OptimizeOptions.DEFAULT.withLevel(level));
            float[] a = new float[WORK_ITEMS];
            Arrays.fill(a, 200f);
            long c = calls(opt, a);
            assertTrue(c <= (long) WORK_ITEMS * TRIPS, "opt=" + level + ": " + c + " calls");
            assertArrayEquals(expected, a, "opt=" + level);
        }
    }
}
