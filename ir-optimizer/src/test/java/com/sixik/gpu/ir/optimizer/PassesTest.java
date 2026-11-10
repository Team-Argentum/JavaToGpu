package com.sixik.gpu.ir.optimizer;

import com.sixik.gpu.ir.AddressSpace;
import com.sixik.gpu.ir.IrBuilder;
import com.sixik.gpu.ir.IrModule;
import com.sixik.gpu.ir.Op;
import com.sixik.gpu.ir.Types;
import com.sixik.gpu.ir.interpreter.NdRange;
import org.junit.jupiter.api.Test;

import java.util.Random;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Структурные тесты проходов: что именно оптимизатор убрал, плюс сверка поведения с исходным модулем.
 */
class PassesTest {

    private static final int N = 16;

    private static Supplier<Object[]> ints(int arrays) {
        return () -> {
            Random r = new Random(7);
            Object[] a = new Object[arrays];
            for (int i = 0; i < arrays; i++) {
                int[] x = new int[N];
                for (int k = 0; k < N; k++) {
                    x[k] = r.nextInt();
                }
                a[i] = x;
            }
            return a;
        };
    }

    private static Supplier<Object[]> floats(int arrays) {
        return () -> {
            Random r = new Random(11);
            Object[] a = new Object[arrays];
            for (int i = 0; i < arrays; i++) {
                float[] x = new float[N];
                for (int k = 0; k < N; k++) {
                    x[k] = (r.nextFloat() - 0.3f) * 40f;
                }
                a[i] = x;
            }
            return a;
        };
    }

    // ---- алгебра -------------------------------------------------------------------------------

    @Test
    void integerIdentitiesDisappear() {
        IrModule m = OptTest.kernel(new int[]{Types.I32, Types.I32}, new int[]{Types.I32}, (b, l) -> {
            int g = l[0];
            int x = OptTest.loadAt(b, 0, b.localGet(g));
            // (((x + 0) * 1) - 0 | 0) ^ 0, затем x & -1
            int v = b.binary(Op.ADD, x, b.constI32(0));
            v = b.binary(Op.MUL, v, b.constI32(1));
            v = b.binary(Op.SUB, v, b.constI32(0));
            v = b.binary(Op.OR, v, b.constI32(0));
            v = b.binary(Op.XOR, v, b.constI32(0));
            v = b.binary(Op.AND, v, b.constI32(-1));
            return new int[]{b.localDecl(g, OptTest.gid(b)), OptTest.storeAt(b, 1, b.localGet(g), v)};
        });
        IrModule o = OptTest.opt(m);
        for (int op : new int[]{Op.ADD, Op.MUL, Op.SUB, Op.OR, Op.XOR, Op.AND}) {
            assertEquals(0, OptTest.count(o, op), Op.nameOf(op));
        }
        OptTest.assertSameBehavior("identities", m, o, NdRange.of1D(N), ints(2));
    }

    @Test
    void strengthReductionUsesShiftsAndMasks() {
        IrModule m = OptTest.kernel(new int[]{Types.U32, Types.U32, Types.U32, Types.I32, Types.I32}, new int[]{Types.I32},
                (b, l) -> {
                    int g = l[0];
                    int x = OptTest.loadAt(b, 0, b.localGet(g));
                    return new int[]{
                            b.localDecl(g, OptTest.gid(b)),
                            OptTest.storeAt(b, 1, b.localGet(g), b.binary(Op.DIV, x, b.constU32(8))),
                            OptTest.storeAt(b, 2, b.localGet(g), b.binary(Op.REM, OptTest.loadAt(b, 0, b.localGet(g)),
                                    b.constU32(16))),
                            OptTest.storeAt(b, 3, b.localGet(g),
                                    b.binary(Op.MUL, OptTest.loadAt(b, 3, b.localGet(g)), b.constI32(32))),
                    };
                });
        IrModule o = OptTest.opt(m);
        assertEquals(0, OptTest.count(o, Op.DIV));
        assertEquals(0, OptTest.count(o, Op.REM));
        assertEquals(0, OptTest.count(o, Op.MUL));
        assertTrue(OptTest.count(o, Op.SHR) >= 1);
        assertTrue(OptTest.count(o, Op.SHL) >= 1);
        assertTrue(OptTest.count(o, Op.AND) >= 1);
        OptTest.assertSameBehavior("strength", m, o, NdRange.of1D(N), ints(5));
    }

    @Test
    void signedDivisionByPowerOfTwoIsNotReplacedByShift() {
        // x / 4 для знакового округляет к нулю, а x >> 2 - к минус бесконечности: заменять нельзя
        IrModule m = OptTest.kernel(new int[]{Types.I32, Types.I32}, new int[]{Types.I32}, (b, l) -> new int[]{
                b.localDecl(l[0], OptTest.gid(b)),
                OptTest.storeAt(b, 1, b.localGet(l[0]),
                        b.binary(Op.DIV, OptTest.loadAt(b, 0, b.localGet(l[0])), b.constI32(4)))});
        IrModule o = OptTest.opt(m);
        assertEquals(1, OptTest.count(o, Op.DIV));
        OptTest.assertSameBehavior("sdiv", m, o, NdRange.of1D(N), ints(2));
    }

    private IrModule floatDivision(float divisor) {
        return OptTest.kernel(new int[]{Types.F32, Types.F32}, new int[]{Types.I32}, (b, l) -> new int[]{
                b.localDecl(l[0], OptTest.gid(b)),
                OptTest.storeAt(b, 1, b.localGet(l[0]),
                        b.binary(Op.DIV, OptTest.loadAt(b, 0, b.localGet(l[0])), b.constF32(divisor)))});
    }

    @Test
    void floatDivisionByPowerOfTwoBecomesExactMultiplication() {
        IrModule m = floatDivision(4.0f);
        IrModule o = OptTest.opt(m);
        assertEquals(0, OptTest.count(o, Op.DIV));
        assertEquals(1, OptTest.count(o, Op.MUL));
        OptTest.assertSameBehavior("fdiv4", m, o, NdRange.of1D(N), floats(2));
    }

    @Test
    void floatDivisionByArbitraryConstantNeedsFastMath() {
        IrModule m = floatDivision(3.0f);
        assertEquals(1, OptTest.count(OptTest.opt(m), Op.DIV), "exact semantics keep the division");
        IrModule fast = OptTest.opt(m, OptimizeOptions.DEFAULT.withFastMath(true));
        assertEquals(0, OptTest.count(fast, Op.DIV));
        assertEquals(1, OptTest.count(fast, Op.MUL));
    }

    @Test
    void multiplyAddBecomesFmaOnlyWithFastMath() {
        IrModule m = OptTest.kernel(new int[]{Types.F32, Types.F32}, new int[]{Types.I32}, (b, l) -> {
            int x = OptTest.loadAt(b, 0, b.localGet(l[0]));
            int y = OptTest.loadAt(b, 0, b.localGet(l[0]));
            int fma = b.binary(Op.ADD, b.binary(Op.MUL, x, y), b.constF32(1.5f));
            return new int[]{b.localDecl(l[0], OptTest.gid(b)), OptTest.storeAt(b, 1, b.localGet(l[0]), fma)};
        });
        assertEquals(0, OptTest.countIntrinsic(OptTest.opt(m), Builtins_FMA));
        assertEquals(1, OptTest.countIntrinsic(OptTest.opt(m, OptimizeOptions.DEFAULT.withFastMath(true)),
                Builtins_FMA));
    }

    private static final int Builtins_FMA = com.sixik.gpu.ir.Builtins.idOf("fma");

    @Test
    void floatIdentitiesKeepNegativeZeroSemantics() {
        // x + 0.0 нельзя заменять на x (-0.0 + 0.0 = +0.0), а x + (-0.0) можно
        IrModule m = OptTest.kernel(new int[]{Types.F32, Types.F32, Types.F32}, new int[]{Types.I32}, (b, l) -> new int[]{
                b.localDecl(l[0], OptTest.gid(b)),
                OptTest.storeAt(b, 1, b.localGet(l[0]),
                        b.binary(Op.ADD, OptTest.loadAt(b, 0, b.localGet(l[0])), b.constF32(0.0f))),
                OptTest.storeAt(b, 2, b.localGet(l[0]),
                        b.binary(Op.ADD, OptTest.loadAt(b, 0, b.localGet(l[0])), b.constF32(-0.0f)))});
        IrModule o = OptTest.opt(m);
        assertEquals(1, OptTest.count(o, Op.ADD), "only x + 0.0 must survive");
        OptTest.assertSameBehavior("negzero", m, o, NdRange.of1D(4), () -> new Object[]{
                new float[]{-0.0f, 0.0f, 1f, -1f}, new float[4], new float[4]});
    }

    // ---- мёртвый код ---------------------------------------------------------------------------

    @Test
    void deadCodeIsRemoved() {
        IrModule m = OptTest.kernel(new int[]{Types.I32}, new int[]{Types.I32, Types.I32}, (b, l) -> new int[]{
                // неиспользуемая локаль с чистой инициализацией
                b.localDecl(l[0], b.binary(Op.ADD, OptTest.gid(b), b.constI32(5))),
                // if (false) {...}
                b.ifStmt(b.constBool(false), b.block(OptTest.storeAt(b, 0, b.constI32(5), b.constI32(9))), -1),
                // while (false) {...}
                b.loop(b.constBool(false), b.block(OptTest.storeAt(b, 0, b.constI32(6), b.constI32(9))), -1),
                OptTest.storeAt(b, 0, b.constI32(0), b.constI32(1)),
                b.returnStmt(-1),
                // недостижимый код
                OptTest.storeAt(b, 0, b.constI32(1), b.constI32(2))});
        IrModule o = OptTest.opt(m);
        assertEquals(1, OptTest.count(o, Op.STORE));
        assertEquals(0, OptTest.count(o, Op.IF));
        assertEquals(0, OptTest.count(o, Op.LOOP));
        assertEquals(0, o.fLocalCount(0), "the unused local must disappear");
        OptTest.assertSameBehavior("dead", m, o, NdRange.of1D(1), ints(1));
    }

    @Test
    void unusedDeviceFunctionsAreDropped() {
        IrBuilder b = new IrBuilder();
        int arr = b.array(AddressSpace.GLOBAL, Types.I32);
        b.beginFunction(IrModule.DEVICE, Types.I32, Types.I32);
        b.endFunction(b.block(b.returnStmt(b.binary(Op.ADD, b.param(0), b.constI32(1)))));
        b.beginFunction(IrModule.DEVICE, Types.I32, Types.I32);
        b.endFunction(b.block(b.returnStmt(b.binary(Op.MUL, b.param(0), b.constI32(3)))));
        b.beginFunction(IrModule.KERNEL, Types.VOID, arr);
        // вызывается только вторая функция
        b.endFunction(b.block(b.store(b.elemPtr(b.param(0), b.constI32(0)), b.call(1, b.constI32(4)))));
        IrModule m = b.build();
        assertEquals(3, m.functionCount());
        IrModule o = OptTest.opt(m, OptimizeOptions.DEFAULT.withLevel(1));
        assertEquals(2, o.functionCount());
        OptTest.assertSameBehavior("fn", m, o, NdRange.of1D(1), () -> new Object[]{new int[1]});
    }

    // ---- локали --------------------------------------------------------------------------------

    @Test
    void constantsPropagateThroughLocals() {
        IrModule m = OptTest.kernel(new int[]{Types.I32}, new int[]{Types.I32, Types.I32}, (b, l) -> new int[]{
                b.localDecl(l[0], b.constI32(5)),
                b.localDecl(l[1], b.binary(Op.MUL, b.localGet(l[0]), b.constI32(3))),
                OptTest.storeAt(b, 0, b.constI32(0), b.binary(Op.ADD, b.localGet(l[1]), b.localGet(l[0])))});
        IrModule o = OptTest.opt(m);
        assertEquals(0, OptTest.count(o, Op.LOCAL_GET));
        assertEquals(0, o.fLocalCount(0));
        OptTest.assertSameBehavior("prop", m, o, NdRange.of1D(1), () -> new Object[]{new int[1]});
        int store = -1;
        for (int n = 0; n < o.nodeCount(); n++) {
            if (o.op(n) == Op.STORE) {
                store = n;
            }
        }
        assertEquals(Op.CONST_INT, o.op(o.b(store)));
        assertEquals(20, o.a(o.b(store)));
    }

    @Test
    void overwrittenStoreIsEliminated() {
        IrModule m = OptTest.kernel(new int[]{Types.I32, Types.I32}, new int[]{Types.I32, Types.I32}, (b, l) -> new int[]{
                b.localDecl(l[0], OptTest.gid(b)),
                b.localDecl(l[1], OptTest.loadAt(b, 0, b.localGet(l[0]))),   // мёртвая запись
                b.localSet(l[1], b.constI32(7)),
                OptTest.storeAt(b, 1, b.localGet(l[0]), b.localGet(l[1]))});
        IrModule o = OptTest.opt(m);
        assertEquals(0, OptTest.count(o, Op.LOAD), "the dead load must go away");
        OptTest.assertSameBehavior("dse", m, o, NdRange.of1D(N), ints(2));
    }

    @Test
    void loopCarriedLocalsAreNotPropagated() {
        IrModule m = OptTest.kernel(new int[]{Types.I32}, new int[]{Types.I32, Types.I32}, (b, l) -> new int[]{
                b.localDecl(l[0], b.constI32(0)),
                b.localDecl(l[1], b.constI32(0)),
                b.loop(b.binary(Op.LT, b.localGet(l[0]), b.constI32(4)),
                        b.block(b.localSet(l[1], b.binary(Op.ADD, b.localGet(l[1]), b.localGet(l[0])))),
                        b.localSet(l[0], b.binary(Op.ADD, b.localGet(l[0]), b.constI32(1)))),
                OptTest.storeAt(b, 0, b.constI32(0), b.localGet(l[1]))});
        IrModule o = OptTest.opt(m);
        assertEquals(1, OptTest.count(o, Op.LOOP));
        OptTest.assertSameBehavior("loop", m, o, NdRange.of1D(1), () -> new Object[]{new int[1]});
        // 0+1+2+3
        int[] out = new int[1];
        com.sixik.gpu.ir.interpreter.Interpreter.run(o, NdRange.of1D(1), (Object) out);
        assertEquals(6, out[0]);
    }

    @Test
    void branchJoinKeepsOnlyAgreeingFacts() {
        IrModule m = OptTest.kernel(new int[]{Types.I32, Types.I32}, new int[]{Types.I32, Types.I32}, (b, l) -> new int[]{
                b.localDecl(l[0], OptTest.gid(b)),
                b.localDecl(l[1], b.constI32(1)),
                b.ifStmt(b.binary(Op.GT, OptTest.loadAt(b, 0, b.localGet(l[0])), b.constI32(0)),
                        b.block(b.localSet(l[1], b.constI32(2))), -1),
                OptTest.storeAt(b, 1, b.localGet(l[0]), b.localGet(l[1]))});
        IrModule o = OptTest.opt(m);
        OptTest.assertSameBehavior("join", m, o, NdRange.of1D(N), ints(2));
    }

    @Test
    void copiesOfLocalsFromAnEndedScopeAreNotPropagated() {
        // x = 0; { int t = in[g]; x = t; } out[g] = x;  - после блока t не видна, x -> t подставлять нельзя
        // (регрессия, найденная RandomProgramTest)
        IrModule m = OptTest.kernel(new int[]{Types.I32, Types.I32}, new int[]{Types.I32, Types.I32, Types.I32},
                (b, l) -> new int[]{
                        b.localDecl(l[0], OptTest.gid(b)),
                        b.localDecl(l[1], b.constI32(0)),
                        b.block(b.localDecl(l[2], OptTest.loadAt(b, 0, b.localGet(l[0]))),
                                b.localSet(l[1], b.localGet(l[2]))),
                        OptTest.storeAt(b, 1, b.localGet(l[0]), b.localGet(l[1]))});
        IrModule o = OptTest.opt(m);
        OptTest.assertSameBehavior("scope", m, o, NdRange.of1D(N), ints(2));
    }

    // ---- CSE и LICM ----------------------------------------------------------------------------

    @Test
    void commonSubexpressionsShareOneTemporary() {
        IrModule m = OptTest.kernel(new int[]{Types.F32, Types.F32}, new int[]{Types.I32}, (b, l) -> {
            int s1 = b.intrinsic(OptTest.SIN, OptTest.loadAt(b, 0, b.localGet(l[0])));
            int s2 = b.intrinsic(OptTest.SIN, OptTest.loadAt(b, 0, b.localGet(l[0])));
            int v = b.binary(Op.ADD, b.binary(Op.MUL, s1, b.constF32(2f)), b.binary(Op.MUL, s2, b.constF32(3f)));
            return new int[]{b.localDecl(l[0], OptTest.gid(b)), OptTest.storeAt(b, 1, b.localGet(l[0]), v)};
        });
        IrModule o = OptTest.opt(m);
        assertEquals(1, OptTest.countIntrinsic(o, OptTest.SIN));
        assertEquals(1, OptTest.count(o, Op.LOAD));
        OptTest.assertSameBehavior("cse", m, o, NdRange.of1D(N), floats(2));
    }

    @Test
    void cseDoesNotCrossAStoreToMemory() {
        IrModule m = OptTest.kernel(new int[]{Types.F32, Types.F32}, new int[]{Types.F32}, (b, l) -> {
            int heavy = b.intrinsic(OptTest.SIN, OptTest.loadAt(b, 0, b.constI32(0)));
            return new int[]{
                    b.localDecl(l[0], heavy),
                    OptTest.storeAt(b, 0, b.constI32(0), b.constF32(5f)),
                    OptTest.storeAt(b, 1, b.constI32(0),
                            b.binary(Op.ADD, b.localGet(l[0]),
                                    b.intrinsic(OptTest.SIN, OptTest.loadAt(b, 0, b.constI32(0)))))};
        });
        IrModule o = OptTest.opt(m);
        assertEquals(2, OptTest.countIntrinsic(o, OptTest.SIN), "the second sin sees a different memory state");
        OptTest.assertSameBehavior("cse-store", m, o, NdRange.of1D(1), floats(2));
    }

    private static IrModule loopWithInvariant(int builtin) {
        return OptTest.kernel(new int[]{Types.F32, Types.F32}, new int[]{Types.I32, Types.F32, Types.F32, Types.I32},
                (b, l) -> {
                    int g = l[0];
                    int x = l[1];
                    int acc = l[2];
                    int i = l[3];
                    int term = b.binary(Op.MUL, b.intrinsic(builtin, b.localGet(x)), b.convert(Types.F32, b.localGet(i)));
                    return new int[]{
                            b.localDecl(g, OptTest.gid(b)),
                            b.localDecl(x, OptTest.loadAt(b, 0, b.localGet(g))),
                            b.localDecl(acc, b.constF32(0f)),
                            b.localDecl(i, b.constI32(0)),
                            b.loop(b.binary(Op.LT, b.localGet(i), b.constI32(8)),
                                    b.block(b.localSet(acc, b.binary(Op.ADD, b.localGet(acc), term))),
                                    b.localSet(i, b.binary(Op.ADD, b.localGet(i), b.constI32(1)))),
                            OptTest.storeAt(b, 1, b.localGet(g), b.localGet(acc))};
                });
    }

    @Test
    void loopInvariantsAreHoisted() {
        IrModule m = loopWithInvariant(OptTest.SQRT);
        assertTrue(OptTest.intrinsicInsideLoop(m, OptTest.SQRT));
        IrModule o = OptTest.opt(m);
        assertFalse(OptTest.intrinsicInsideLoop(o, OptTest.SQRT), "sqrt(x) is invariant and must leave the loop");
        assertEquals(1, OptTest.countIntrinsic(o, OptTest.SQRT));
        OptTest.assertSameBehavior("licm", m, o, NdRange.of1D(N), () -> new Object[]{
                new float[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16}, new float[N]});
    }

    @Test
    void registerBudgetBlocksHoistingUnlessTheValueIsExpensive() {
        IrModule cheap = loopWithInvariant(OptTest.SQRT);
        IrModule heavy = loopWithInvariant(OptTest.SIN);
        // бюджет в 1 регистр: запаса нет
        OptimizeOptions tight = OptimizeOptions.DEFAULT.withRegisterBudget(1);
        assertTrue(OptTest.intrinsicInsideLoop(OptTest.opt(cheap, tight), OptTest.SQRT));
        assertTrue(OptTest.intrinsicInsideLoop(OptTest.opt(heavy, tight), OptTest.SIN));
        // уровень 3 разрешает дорогое (sin) даже без запаса, но не среднее (sqrt)
        OptimizeOptions aggressive = tight.withLevel(3);
        assertFalse(OptTest.intrinsicInsideLoop(OptTest.opt(heavy, aggressive), OptTest.SIN));
        assertTrue(OptTest.intrinsicInsideLoop(OptTest.opt(cheap, aggressive), OptTest.SQRT));
    }

    @Test
    void loadsAreNotHoistedFromConditionallyExecutedCode() {
        // for (i = 0; i < 4; i++) if (i < n) acc += in[idx]; - in[idx] инвариантно, но читать его до цикла нельзя:
        // исходная программа читает только при i < n
        IrModule m = OptTest.kernel(new int[]{Types.I32, Types.I32}, new int[]{Types.I32, Types.I32, Types.I32},
                (b, l) -> {
                    int idx = b.binary(Op.ADD, b.binary(Op.MUL, b.localGet(l[0]), b.constI32(1000)), b.constI32(3));
                    int guarded = b.ifStmt(b.binary(Op.LT, b.localGet(l[1]), b.constI32(0)),
                            b.block(b.localSet(l[2], b.binary(Op.ADD, b.localGet(l[2]), OptTest.loadAt(b, 0, idx)))), -1);
                    return new int[]{
                            b.localDecl(l[0], OptTest.gid(b)),
                            b.localDecl(l[1], b.constI32(0)),
                            b.localDecl(l[2], b.constI32(0)),
                            b.loop(b.binary(Op.LT, b.localGet(l[1]), b.constI32(4)),
                                    b.block(guarded, b.localSet(l[1], b.binary(Op.ADD, b.localGet(l[1]), b.constI32(1)))),
                                    -1),
                            OptTest.storeAt(b, 1, b.localGet(l[0]), b.localGet(l[2]))};
                });
        IrModule o = OptTest.opt(m);
        boolean loadInLoop = false;
        for (int n = 0; n < o.nodeCount(); n++) {
            if (o.op(n) == Op.LOOP && OptTest.inside(o, n, Op.LOAD)) {
                loadInLoop = true;
            }
        }
        assertTrue(loadInLoop, "the guarded load must stay inside the loop");
        // и исполнение не должно упасть выходом за границы: ветка не выполняется никогда
        OptTest.assertSameBehavior("guarded", m, o, NdRange.of1D(4), () -> new Object[]{new int[4], new int[4]});
    }

    // ---- общие свойства ------------------------------------------------------------------------

    @Test
    void levelZeroReturnsTheSameModule() {
        IrModule m = loopWithInvariant(OptTest.SQRT);
        assertSame(m, Optimizer.optimize(m, OptimizeOptions.NONE).module());
    }

    @Test
    void optimizationIsDeterministicAndIdempotent() {
        IrModule m = loopWithInvariant(OptTest.SQRT);
        IrModule a = OptTest.opt(m);
        IrModule b = OptTest.opt(m);
        assertEquals(com.sixik.gpu.ir.IrBinary.hex(com.sixik.gpu.ir.IrBinary.hash(a)),
                com.sixik.gpu.ir.IrBinary.hex(com.sixik.gpu.ir.IrBinary.hash(b)));
        OptimizeResult again = Optimizer.optimize(a, OptimizeOptions.DEFAULT);
        assertTrue(again.applied().isEmpty(), () -> "second run still changed: " + again.applied());
    }
}
