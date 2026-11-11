package com.sixik.gpu.ir.optimizer;

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
