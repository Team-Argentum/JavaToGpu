package com.sixik.gpu.ir.optimizer;

import com.sixik.gpu.ir.AddressSpace;
import com.sixik.gpu.ir.IrBuilder;
import com.sixik.gpu.ir.IrModule;
import com.sixik.gpu.ir.Op;
import com.sixik.gpu.ir.Result;
import com.sixik.gpu.ir.Types;
import com.sixik.gpu.ir.interpreter.Interpreter;
import com.sixik.gpu.ir.interpreter.NdRange;
import com.sixik.gpu.ir.interpreter.samples.SampleArgs;
import com.sixik.gpu.ir.interpreter.samples.SampleKernels;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link WriteCoverage}: распознавание полного покрытия записью и его корректность - для помеченного массива результат
 * ядра не зависит от начального содержимого массива.
 */
class WriteCoverageTest {

    private static boolean[] cover(IrModule m) {
        return WriteCoverage.fullyWrittenParams(m, OptTest.kernelIndex(m));
    }

    @Test
    void directAndLocalGlobalIdIndexAreCovered() {
        // out0[getGlobalId(0)] = 1; int i = getGlobalId(0); out1[i] = in[i]
        IrModule m = OptTest.kernel(new int[]{Types.I32, Types.I32, Types.I32}, new int[]{Types.I32}, (b, l) -> new int[]{
                OptTest.storeAt(b, 0, OptTest.gid(b), b.constI32(1)),
                b.localDecl(l[0], OptTest.gid(b)),
                OptTest.storeAt(b, 1, b.localGet(l[0]), OptTest.loadAt(b, 2, b.localGet(l[0])))});
        assertArrayEquals(new boolean[]{true, true, false}, cover(m));
    }

    @Test
    void conditionalOrShiftedOrLateWritesAreNotCovered() {
        IrModule m = OptTest.kernel(new int[]{Types.I32, Types.I32, Types.I32, Types.I32}, new int[]{Types.I32},
                (b, l) -> new int[]{
                        b.localDecl(l[0], OptTest.gid(b)),
                        // условная запись
                        b.ifStmt(b.binary(Op.GT, b.localGet(l[0]), b.constI32(3)),
                                b.block(OptTest.storeAt(b, 0, b.localGet(l[0]), b.constI32(1))), -1),
                        // сдвинутый индекс
                        OptTest.storeAt(b, 1, b.binary(Op.ADD, b.localGet(l[0]), b.constI32(1)), b.constI32(1)),
                        // после возможного return
                        b.ifStmt(b.binary(Op.EQ, b.localGet(l[0]), b.constI32(0)), b.block(b.returnStmt(-1)), -1),
                        OptTest.storeAt(b, 2, b.localGet(l[0]), b.constI32(1)),
                        OptTest.storeAt(b, 3, b.localGet(l[0]), b.constI32(1))});
        assertArrayEquals(new boolean[]{false, false, false, false}, cover(m));
    }

    @Test
    void reassignedIndexLocalIsNotTrusted() {
        IrModule m = OptTest.kernel(new int[]{Types.I32}, new int[]{Types.I32}, (b, l) -> new int[]{
                b.localDecl(l[0], OptTest.gid(b)),
                b.localSet(l[0], b.binary(Op.MUL, b.localGet(l[0]), b.constI32(2))),
                OptTest.storeAt(b, 0, b.localGet(l[0]), b.constI32(1))});
        assertFalse(cover(m)[0]);
    }

    /**
     * Корректность на образцах: если массив помечен покрытым и ядро его не читает, то при {@code global == length}
     * результат ядра одинаков для нулевого и мусорного начального содержимого.
     */
    @Test
    void coveredArraysOfSamplesDoNotDependOnTheirInitialContent() {
        int checked = 0;
        for (SampleKernels.Kernel k : SampleKernels.all()) {
            IrModule m = k.module();
            int f = m.functionCount() - 1;
            // группы и тяжёлые образцы (private-массив в 1 МиБ на work-item) интерпретатору на 1024 work-item'ах дороги
            if (m.fReqWorkGroup(f, 0) > 0 || SampleArgs.notComparableOnDevice(k.label())) {
                continue;
            }
            boolean[] cov = WriteCoverage.fullyWrittenParams(m, f);
            int[] access = com.sixik.gpu.ir.Validator.validate(m).functions()[f].paramAccess();
            for (int p = 0; p < cov.length; p++) {
                if (!cov[p] || (access[p] & com.sixik.gpu.ir.Validator.ACCESS_READ) != 0) {
                    continue;
                }
                Object[] a = SampleArgs.random(k, 99).get();
                Object[] b = SampleArgs.random(k, 99).get();
                int len = java.lang.reflect.Array.getLength(a[p]);
                fillGarbage(b[p]);
                NdRange range = NdRange.of1D(len);
                Result<Interpreter.Execution> ra = Interpreter.run(m, range, a);
                Result<Interpreter.Execution> rb = Interpreter.run(m, range, b);
                assertEquals(ra.isOk(), rb.isOk(), k.label());
                if (ra.isOk()) {
                    for (int i = 0; i < a.length; i++) {
                        OptTest.compare(k.label() + " covered param " + p, i, a[i], b[i]);
                    }
                    checked++;
                }
            }
        }
        assertTrue(checked > 0, "no sample has a covered array");
    }

    private static void fillGarbage(Object array) {
        if (array instanceof int[] x) {
            Arrays.fill(x, 0x5A5A5A5A);
        } else if (array instanceof long[] x) {
            Arrays.fill(x, 0x5A5A5A5A5A5A5A5AL);
        } else if (array instanceof float[] x) {
            Arrays.fill(x, Float.NaN);
        } else if (array instanceof double[] x) {
            Arrays.fill(x, -12345.5);
        }
    }

    // ---- 2D/3D-индексы ---------------------------------------------------------------------------

    private static final int GET_GLOBAL_SIZE = com.sixik.gpu.ir.Builtins.idOf("getGlobalSize");

    private static int gid(IrBuilder b, int axis) {
        return b.intrinsic(OptTest.GET_GLOBAL_ID, b.constI32(axis));
    }

    private static int gsz(IrBuilder b, int axis) {
        return b.intrinsic(GET_GLOBAL_SIZE, b.constI32(axis));
    }

    /**
     * Ядро {@code (out: i32[], w: int, h: int)}: локали {@code l0..l3} типа int, тело строит {@code body}. Параметр 0 -
     * массив, 1 и 2 - скаляры.
     */
    private static IrModule gridKernel(OptTest.Body body) {
        IrBuilder b = new IrBuilder();
        b.beginFunction(IrModule.KERNEL, Types.VOID,
                new int[]{b.array(AddressSpace.GLOBAL, Types.I32), Types.I32, Types.I32});
        int[] l = new int[4];
        for (int i = 0; i < l.length; i++) {
            l[i] = b.declareLocal(Types.I32);
        }
        b.endFunction(b.block(body.statements(b, l)));
        return b.build();
    }

    private static WriteCoverage.Coverage gridCoverage(OptTest.Body body) {
        IrModule m = gridKernel(body);
        return WriteCoverage.coverage(m, OptTest.kernelIndex(m))[0];
    }

    private static boolean covers(WriteCoverage.Coverage c, long length, long gx, long gy, long gz, long w, long h) {
        return c.covers(length, new long[]{gx, gy, gz}, p -> p == 1 ? w : p == 2 ? h : Long.MIN_VALUE);
    }

    @Test
    void rowMajor2dIndexWithAWidthParameterIsCovered() {
        // int x = gid0; int y = gid1; int i = y * w + x; out[i] = x
        WriteCoverage.Coverage c = gridCoverage((b, l) -> new int[]{
                b.localDecl(l[0], gid(b, 0)),
                b.localDecl(l[1], gid(b, 1)),
                b.localDecl(l[2], b.binary(Op.ADD, b.binary(Op.MUL, b.localGet(l[1]), b.param(1)), b.localGet(l[0]))),
                OptTest.storeAt(b, 0, b.localGet(l[2]), b.localGet(l[0]))});
        assertEquals(2, c.axes());
        assertTrue(covers(c, 64 * 48, 64, 48, 1, 64, 0), "w == global(0), length == w * global(1)");
        assertFalse(covers(c, 64 * 48, 64, 48, 1, 65, 0), "the width must equal global(0)");
        assertFalse(covers(c, 64 * 48 + 1, 64, 48, 1, 64, 0), "the array is longer than the grid");
        assertTrue(covers(c, 64 * 47, 64, 47, 2, 64, 0), "a third axis only repeats the writes");
        assertFalse(c.covers(64 * 48, new long[]{64, 48, 1}, p -> Long.MIN_VALUE), "an unknown width is not trusted");
    }

    @Test
    void globalSizeStridesAndAnyTermOrderAreCovered() {
        // out[gsz0 * gid1 + gid0]
        WriteCoverage.Coverage c2 = gridCoverage((b, l) -> new int[]{
                OptTest.storeAt(b, 0, b.binary(Op.ADD, b.binary(Op.MUL, gsz(b, 0), gid(b, 1)), gid(b, 0)),
                        b.constI32(1))});
        assertTrue(covers(c2, 30 * 7, 30, 7, 1, 0, 0));
        assertFalse(covers(c2, 30 * 7, 30, 6, 1, 0, 0));
        // 3D: out[(gid2 * h + gid1) * w + gid0]
        WriteCoverage.Coverage c3 = gridCoverage((b, l) -> new int[]{
                OptTest.storeAt(b, 0, b.binary(Op.ADD,
                        b.binary(Op.MUL, b.binary(Op.ADD, b.binary(Op.MUL, gid(b, 2), b.param(2)), gid(b, 1)),
                                b.param(1)), gid(b, 0)), b.constI32(1))});
        assertEquals(3, c3.axes());
        assertTrue(covers(c3, 8L * 5 * 3, 8, 5, 3, 8, 5));
        assertFalse(covers(c3, 8L * 5 * 3, 8, 5, 3, 8, 4), "the height must equal global(1)");
        // 3D через gsz: out[gid0 + gid1 * gsz0 + gid2 * gsz0 * gsz1]
        WriteCoverage.Coverage c4 = gridCoverage((b, l) -> new int[]{
                OptTest.storeAt(b, 0, b.binary(Op.ADD, b.binary(Op.ADD, gid(b, 0), b.binary(Op.MUL, gid(b, 1), gsz(b, 0))),
                        b.binary(Op.MUL, gid(b, 2), b.binary(Op.MUL, gsz(b, 0), gsz(b, 1)))), b.constI32(1))});
        assertTrue(covers(c4, 4L * 6 * 9, 4, 6, 9, 0, 0));
    }

    @Test
    void nonBijectiveOrUnknownIndexFormsAreNotCovered() {
        // строка вместо столбца на оси 0: out[x * w + y]
        assertNull(gridCoverage((b, l) -> new int[]{
                OptTest.storeAt(b, 0, b.binary(Op.ADD, b.binary(Op.MUL, gid(b, 0), b.param(1)), gid(b, 1)),
                        b.constI32(1))}));
        // пропуск оси: out[gid0 + gid2 * w]
        assertNull(gridCoverage((b, l) -> new int[]{
                OptTest.storeAt(b, 0, b.binary(Op.ADD, gid(b, 0), b.binary(Op.MUL, gid(b, 2), b.param(1))),
                        b.constI32(1))}));
        // одна ось дважды: out[gid0 + gid0]
        assertNull(gridCoverage((b, l) -> new int[]{
                OptTest.storeAt(b, 0, b.binary(Op.ADD, gid(b, 0), gid(b, 0)), b.constI32(1))}));
        // смещение: out[gid1 * w + gid0 + 1]
        assertNull(gridCoverage((b, l) -> new int[]{
                OptTest.storeAt(b, 0, b.binary(Op.ADD, b.binary(Op.ADD, b.binary(Op.MUL, gid(b, 1), b.param(1)),
                        gid(b, 0)), b.constI32(1)), b.constI32(1))}));
        // шаг из локали, которую переприсваивают
        assertNull(gridCoverage((b, l) -> new int[]{
                b.localDecl(l[0], b.param(1)),
                b.localSet(l[0], b.binary(Op.ADD, b.localGet(l[0]), b.constI32(1))),
                OptTest.storeAt(b, 0, b.binary(Op.ADD, b.binary(Op.MUL, gid(b, 1), b.localGet(l[0])), gid(b, 0)),
                        b.constI32(1))}));
        // шаг из данных: out[gid1 * out[0] + gid0]
        assertNull(gridCoverage((b, l) -> new int[]{
                OptTest.storeAt(b, 0, b.binary(Op.ADD, b.binary(Op.MUL, gid(b, 1), OptTest.loadAt(b, 0, b.constI32(0))),
                        gid(b, 0)), b.constI32(1))}));
    }

    @Test
    void zeroOrOverflowingSizesAreRejected() {
        WriteCoverage.Coverage c = gridCoverage((b, l) -> new int[]{
                OptTest.storeAt(b, 0, b.binary(Op.ADD, b.binary(Op.MUL, gid(b, 1), b.param(1)), gid(b, 0)),
                        b.constI32(1))});
        assertFalse(covers(c, 0, 0, 5, 1, 0, 0));
        assertFalse(covers(c, 16, 4, 4, 1, -4, 0));
        assertFalse(covers(c, Long.MAX_VALUE, Long.MAX_VALUE / 2, 3, 1, Long.MAX_VALUE / 2, 0));
    }

    /**
     * Корректность 2D-покрытия на интерпретаторе: при запуске, который {@code covers} одобряет, результат не зависит от
     * начального содержимого массива; при неподходящей ширине массив действительно записан не весь.
     */
    @Test
    void covered2dArrayDoesNotDependOnItsInitialContent() {
        IrModule m = gridKernel((b, l) -> new int[]{
                b.localDecl(l[0], gid(b, 0)),
                b.localDecl(l[1], gid(b, 1)),
                OptTest.storeAt(b, 0, b.binary(Op.ADD, b.binary(Op.MUL, b.localGet(l[1]), b.param(1)), b.localGet(l[0])),
                        b.binary(Op.ADD, b.localGet(l[0]), b.binary(Op.MUL, b.localGet(l[1]), b.constI32(100))))});
        WriteCoverage.Coverage c = WriteCoverage.coverage(m, OptTest.kernelIndex(m))[0];
        int w = 12;
        int h = 5;
        assertTrue(covers(c, w * h, w, h, 1, w, 0));
        int[] clean = new int[w * h];
        int[] dirty = new int[w * h];
        Arrays.fill(dirty, 0x5A5A5A5A);
        NdRange range = NdRange.of2D(w, h);
        assertTrue(Interpreter.run(m, range, new Object[]{clean, w, 0}).isOk());
        assertTrue(Interpreter.run(m, range, new Object[]{dirty, w, 0}).isOk());
        assertArrayEquals(clean, dirty);

        // запуск уже массива: covers отказывает, и действительно хвост массива не тронут
        assertFalse(covers(c, w * h, w - 1, h, 1, w, 0));
        int[] partial = new int[w * h];
        Arrays.fill(partial, 7);
        assertTrue(Interpreter.run(m, NdRange.of2D(w - 1, h), new Object[]{partial, w, 0}).isOk());
        assertEquals(7, partial[w - 1], "x = w - 1 is never written");
    }

    @Test
    void randomProgramsNeverClaimCoverageWrongly() {
        // фаззинг-ядра пишут по gid*SLOTS+k - покрытия нет; анализ обязан это видеть
        for (long seed = 0; seed < 200; seed++) {
            IrModule m = RandomPrograms.generate(seed);
            boolean[] cov = cover(m);
            for (boolean c : cov) {
                assertFalse(c, "seed " + seed);
            }
        }
    }
}
