package com.sixik.gpu.ir.optimizer;

import com.sixik.gpu.ir.Builtins;
import com.sixik.gpu.ir.IrModule;
import com.sixik.gpu.ir.Op;
import com.sixik.gpu.ir.Types;
import com.sixik.gpu.ir.interpreter.NdRange;
import org.junit.jupiter.api.Test;

import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Слияние {@code p = x*y; ... p + c} в {@code fma} через локали ({@link Contract}). Входы - небольшие целые числа в
 * {@code float}: и {@code x*y+c}, и {@code fma} точны, поэтому поведение сверяется интерпретатором побитово.
 */
class ContractTest {

    private static final int N = 64;
    private static final int FMA = Builtins.idOf("fma");

    private static Supplier<Object[]> smallInts() {
        return () -> {
            float[] a = new float[N];
            float[] b = new float[N];
            for (int k = 0; k < N; k++) {
                a[k] = k % 17 - 8;
                b[k] = k % 5 - 2;
            }
            return new Object[]{a, b, new float[N]};
        };
    }

    /** Локали: l0 = gid, l1 = x, l2 = y, l3 = p. */
    private static final int[] LOCALS = {Types.I32, Types.F32, Types.F32, Types.F32};

    private interface Use {
        int[] statements(com.sixik.gpu.ir.IrBuilder b, int[] l);
    }

    /** {@code i = gid; x = a[i]; y = b[i]; p = x * y;} затем {@code use}. */
    private static IrModule kernel(Use use) {
        return OptTest.kernel(new int[]{Types.F32, Types.F32, Types.F32}, LOCALS, (b, l) -> {
            int[] head = {
                    b.localDecl(l[0], OptTest.gid(b)),
                    b.localDecl(l[1], OptTest.loadAt(b, 0, b.localGet(l[0]))),
                    b.localDecl(l[2], OptTest.loadAt(b, 1, b.localGet(l[0]))),
                    b.localDecl(l[3], b.binary(Op.MUL, b.localGet(l[1]), b.localGet(l[2])))};
            int[] tail = use.statements(b, l);
            int[] all = java.util.Arrays.copyOf(head, head.length + tail.length);
            System.arraycopy(tail, 0, all, head.length, tail.length);
            return all;
        });
    }

    private static int fmas(IrModule m, int fp, OptimizeOptions o) {
        IrModule opt = OptTest.opt(OptTest.withFp(m, fp), o);
        OptTest.assertSameBehavior("contract", m, opt, NdRange.of1D(N), smallInts());
        return OptTest.countIntrinsic(opt, FMA);
    }

    private static int fmas(IrModule m, int fp) {
        return fmas(m, fp, OptimizeOptions.DEFAULT);
    }

    private static IrModule addThroughLocal() {
        // out[i] = p + 1.5
        return kernel((b, l) -> new int[]{
                OptTest.storeAt(b, 2, b.localGet(l[0]), b.binary(Op.ADD, b.localGet(l[3]), b.constF32(1.5f)))});
    }

    @Test
    void productInALocalIsContracted() {
        IrModule m = addThroughLocal();
        assertEquals(0, fmas(m, 0), "strict kernel");
        assertEquals(0, fmas(m, IrModule.FP_ALL & ~IrModule.FP_CONTRACT), "other flags do not allow it");
        IrModule o = OptTest.opt(OptTest.withFp(m, IrModule.FP_CONTRACT));
        assertEquals(1, OptTest.countIntrinsic(o, FMA));
        assertEquals(0, OptTest.count(o, Op.MUL), "p is read only by the sum, so the multiplication disappears");
    }

    @Test
    void noContractionWithoutHardwareFma() {
        IrModule m = addThroughLocal();
        assertEquals(0, fmas(m, IrModule.FP_CONTRACT, OptimizeOptions.DEFAULT.withHardwareFma(false, true)));
        assertEquals(1, fmas(m, IrModule.FP_CONTRACT, OptimizeOptions.DEFAULT.withHardwareFma(true, false)),
                "double support does not matter for float");
    }

    @Test
    void subtractionInBothPositions() {
        // out[i] = (p - 3) + (5 - p): обе разности сливаются (fma(x, y, -3) и fma(-x, y, 5))
        IrModule m = kernel((b, l) -> new int[]{
                OptTest.storeAt(b, 2, b.localGet(l[0]), b.binary(Op.MUL,
                        b.binary(Op.SUB, b.localGet(l[3]), b.constF32(3f)),
                        b.binary(Op.SUB, b.constF32(5f), b.localGet(l[3]))))});
        assertEquals(2, fmas(m, IrModule.FP_CONTRACT));
    }

    @Test
    void reassignedOperandStopsTheFact() {
        // x = x + 1 между p = x*y и p + 1.5: x*y уже не равно p
        IrModule m = kernel((b, l) -> new int[]{
                b.localSet(l[1], b.binary(Op.ADD, b.localGet(l[1]), b.constF32(1f))),
                OptTest.storeAt(b, 2, b.localGet(l[0]), b.binary(Op.ADD, b.localGet(l[3]), b.localGet(l[1])))});
        assertEquals(0, fmas(m, IrModule.FP_CONTRACT));
    }

    @Test
    void reassignedProductStopsTheFact() {
        // p = 2 - после этого p + 1.5 не произведение
        IrModule m = kernel((b, l) -> new int[]{
                b.localSet(l[3], b.constF32(2f)),
                OptTest.storeAt(b, 2, b.localGet(l[0]), b.binary(Op.ADD, b.localGet(l[3]), b.constF32(1.5f)))});
        assertEquals(0, fmas(m, IrModule.FP_CONTRACT));
    }

    @Test
    void factsSurviveABranchThatDoesNotTouchThem() {
        // if (i > 3) x2 = 1 else x2 = 2 - x, y и p не присваиваются, слияние после if возможно
        IrModule m = kernel((b, l) -> new int[]{
                b.ifStmt(b.binary(Op.GT, b.localGet(l[0]), b.constI32(3)),
                        b.block(OptTest.storeAt(b, 2, b.constI32(0), b.constF32(1f))),
                        b.block(OptTest.storeAt(b, 2, b.constI32(1), b.constF32(2f)))),
                OptTest.storeAt(b, 2, b.localGet(l[0]), b.binary(Op.ADD, b.localGet(l[3]), b.constF32(1.5f)))});
        assertEquals(1, fmas(m, IrModule.FP_CONTRACT));
    }

    @Test
    void aBranchThatAssignsAnOperandStopsTheFact() {
        IrModule m = kernel((b, l) -> new int[]{
                b.ifStmt(b.binary(Op.GT, b.localGet(l[0]), b.constI32(3)),
                        b.block(b.localSet(l[2], b.constF32(7f))), -1),
                // y читается после ветки, иначе её присваивание мёртвое и ветку удаляют целиком
                OptTest.storeAt(b, 2, b.localGet(l[0]), b.binary(Op.ADD, b.localGet(l[3]), b.localGet(l[2])))});
        assertEquals(0, fmas(m, IrModule.FP_CONTRACT));
    }

    @Test
    void productOfMemoryReadsIsSplitIntoTemporaries() {
        // p = a[i] * b[i]; a[i] = 100; out[i] = p + 1.5 - множители выносятся во временные локали до записи в a[i],
        // поэтому fma берёт прежние значения
        IrModule m = OptTest.kernel(new int[]{Types.F32, Types.F32, Types.F32}, new int[]{Types.I32, Types.F32},
                (b, l) -> new int[]{
                        b.localDecl(l[0], OptTest.gid(b)),
                        b.localDecl(l[1], b.binary(Op.MUL, OptTest.loadAt(b, 0, b.localGet(l[0])),
                                OptTest.loadAt(b, 1, b.localGet(l[0])))),
                        OptTest.storeAt(b, 0, b.localGet(l[0]), b.constF32(100f)),
                        OptTest.storeAt(b, 2, b.localGet(l[0]), b.binary(Op.ADD, b.localGet(l[1]), b.constF32(1.5f)))});
        assertEquals(1, fmas(m, IrModule.FP_CONTRACT));
        assertEquals(0, fmas(m, 0), "a strict kernel is not split or contracted");
    }

    @Test
    void productThatIsNeverSummedIsNotSplit() {
        // p = a[i] * b[i]; out[i] = p - не слагаемое, временные локали не нужны
        IrModule m = OptTest.kernel(new int[]{Types.F32, Types.F32, Types.F32}, new int[]{Types.I32, Types.F32},
                (b, l) -> new int[]{
                        b.localDecl(l[0], OptTest.gid(b)),
                        b.localDecl(l[1], b.binary(Op.MUL, OptTest.loadAt(b, 0, b.localGet(l[0])),
                                OptTest.loadAt(b, 1, b.localGet(l[0])))),
                        OptTest.storeAt(b, 2, b.localGet(l[0]), b.localGet(l[1]))});
        assertEquals(OptTest.opt(m).nodeCount(), OptTest.opt(OptTest.withFp(m, IrModule.FP_CONTRACT)).nodeCount());
    }

    @Test
    void accumulatorInALoop() {
        // s = 0; for (k = 0; k < 4; k++) { s = s + p; y = y + 1; p = x * y; } out[i] = s
        IrModule m = OptTest.kernel(new int[]{Types.F32, Types.F32, Types.F32},
                new int[]{Types.I32, Types.F32, Types.F32, Types.F32, Types.F32, Types.I32}, (b, l) -> new int[]{
                        b.localDecl(l[0], OptTest.gid(b)),
                        b.localDecl(l[1], OptTest.loadAt(b, 0, b.localGet(l[0]))),
                        b.localDecl(l[2], OptTest.loadAt(b, 1, b.localGet(l[0]))),
                        b.localDecl(l[4], b.constF32(0f)),
                        b.localDecl(l[5], b.constI32(0)),
                        b.loop(b.binary(Op.LT, b.localGet(l[5]), b.constI32(4)), b.block(
                                        b.localDecl(l[3], b.binary(Op.MUL, b.localGet(l[1]), b.localGet(l[2]))),
                                        b.localSet(l[4], b.binary(Op.ADD, b.localGet(l[4]), b.localGet(l[3]))),
                                        b.localSet(l[2], b.binary(Op.ADD, b.localGet(l[2]), b.constF32(1f)))),
                                b.localSet(l[5], b.binary(Op.ADD, b.localGet(l[5]), b.constI32(1)))),
                        OptTest.storeAt(b, 2, b.localGet(l[0]), b.localGet(l[4]))});
        assertEquals(1, fmas(m, IrModule.FP_CONTRACT), "s = fma(x, y, s) inside the loop");
    }
}
