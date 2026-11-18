package com.sixik.gpu.ir.optimizer;

import com.sixik.gpu.ir.AddressSpace;
import com.sixik.gpu.ir.Fixtures;
import com.sixik.gpu.ir.IrBinary;
import com.sixik.gpu.ir.IrBuilder;
import com.sixik.gpu.ir.IrModule;
import com.sixik.gpu.ir.Op;
import com.sixik.gpu.ir.Result;
import com.sixik.gpu.ir.Types;
import com.sixik.gpu.ir.interpreter.Interpreter;
import com.sixik.gpu.ir.interpreter.NdRange;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Шаг B7 (FRONTEND_ASM_RUNTIME.md, S9-2): бюджет времени оптимизатора. Превысивший бюджет запуск повторяется на
 * уровне {@code 1}, затем модуль возвращается как есть; результат каждого уровня детерминирован. И масштаб: живость
 * вложенных циклов больше не экспоненциальна по глубине.
 */
class BudgetTest {

    private static final int WORK_ITEMS = 4;

    /**
     * {@code depth} вложенных циклов {@code for (j = 0; j < 1; j++)}, внутри - {@code a[i] = a[i] + 1}: у каждого
     * уровня свой счётчик, объявленный в блоке уровня.
     */
    static IrModule nestedLoops(int depth) {
        IrBuilder b = new IrBuilder();
        int arr = b.array(AddressSpace.GLOBAL, Types.F32);
        b.beginFunction(IrModule.KERNEL, Types.VOID, arr);
        int i = b.declareLocal(Types.I32);
        int[] j = new int[depth];
        for (int d = 0; d < depth; d++) {
            j[d] = b.declareLocal(Types.I32);
        }
        int di = b.localDecl(i, b.intrinsic(Fixtures.GET_GLOBAL_ID, b.constI32(0)));
        int body = b.block(b.store(b.elemPtr(b.param(0), b.localGet(i)),
                b.binary(Op.ADD, b.load(b.elemPtr(b.param(0), b.localGet(i))), b.constF32(1f))));
        for (int d = depth - 1; d >= 0; d--) {
            int decl = b.localDecl(j[d], b.constI32(0));
            int cond = b.binary(Op.LT, b.localGet(j[d]), b.constI32(1));
            int upd = b.localSet(j[d], b.binary(Op.ADD, b.localGet(j[d]), b.constI32(1)));
            body = b.block(decl, b.loop(cond, body, upd));
        }
        b.endFunction(b.block(di, body));
        return b.build();
    }

    private static float[] run(IrModule m) {
        float[] a = new float[WORK_ITEMS];
        Result<Interpreter.Execution> r = Interpreter.run(m, m.functionCount() - 1, NdRange.of1D(WORK_ITEMS),
                Interpreter.Options.DEFAULT, new Object[]{a});
        assertTrue(r.isOk(), r.diagnostics().toString());
        return a;
    }

    /** Нулевой бюджет: оба уровня прерваны, модуль - вход как есть, в отчёте - отметки понижения. */
    @Test
    void exhaustedBudgetReturnsTheModuleAsIs() {
        IrModule m = nestedLoops(4);
        OptimizeResult r = Optimizer.optimize(m, OptimizeOptions.DEFAULT.withTimeBudget(Duration.ZERO));
        assertEquals(0, r.level());
        assertSame(m, r.module());
        assertEquals(2, r.failures().size(), r.failures().toString());
        assertTrue(r.failures().get(0).startsWith("budget: opt=2 ") && r.failures().get(0).endsWith("retried at opt=1"),
                r.failures().toString());
        assertTrue(r.failures().get(1).startsWith("budget: opt=1 ") && r.failures().get(1).endsWith("retried at opt=0"),
                r.failures().toString());
    }

    /**
     * Уровень 2 не уложился, уровень 1 - уложился: часы «прыгают» после первого чтения, второй запуск получает новый
     * бюджет от нового «сейчас». Результат - ровно то, что дал бы уровень 1 без бюджета.
     */
    @Test
    void overBudgetFallsBackToLevelOne() {
        IrModule m = nestedLoops(4);
        AtomicLong reads = new AtomicLong();
        OptimizeResult r = Optimizer.optimize(m, OptimizeOptions.DEFAULT.withTimeBudget(Duration.ofSeconds(1)),
                () -> reads.getAndIncrement() == 0 ? 0L : 1_000_000_000_000L);
        assertEquals(1, r.level());
        assertEquals(1, r.failures().size(), r.failures().toString());
        IrModule one = Optimizer.optimize(m, OptimizeOptions.DEFAULT.withLevel(1).withTimeBudget(null)).module();
        assertArrayEquals(IrBinary.write(one), IrBinary.write(r.module()));
    }

    /** Без предела и в пределах бюджета результат один и тот же (бюджет не меняет IR, только выбор уровня). */
    @Test
    void budgetDoesNotChangeTheResult() {
        IrModule m = nestedLoops(6);
        OptimizeResult free = Optimizer.optimize(m, OptimizeOptions.DEFAULT.withTimeBudget(null));
        OptimizeResult bounded = Optimizer.optimize(m, OptimizeOptions.DEFAULT);
        assertEquals(2, free.level());
        assertEquals(2, bounded.level());
        assertTrue(bounded.failures().isEmpty(), bounded.failures().toString());
        assertArrayEquals(IrBinary.write(free.module()), IrBinary.write(bounded.module()));
    }

    @Test
    void negativeBudgetIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> OptimizeOptions.DEFAULT.withTimeBudget(Duration.ofMillis(-1)));
    }

    /**
     * Живость: прежде каждый цикл обходил тело минимум трижды, и глубина 64 стоила {@code 3^64} обходов. С мемо по
     * узлу цикла - секунды и меньше; результат равен неоптимизированному.
     */
    @Test
    void deeplyNestedLoopsAreOptimizedQuickly() {
        IrModule m = nestedLoops(256);
        OptimizeResult r = assertTimeoutPreemptively(Duration.ofSeconds(20),
                () -> Optimizer.optimize(m, OptimizeOptions.DEFAULT.withTimeBudget(null)));
        assertEquals(2, r.level());
        assertTrue(r.ok(), r.failures().toString());
        float[] ones = new float[WORK_ITEMS];
        java.util.Arrays.fill(ones, 1f);
        assertArrayEquals(ones, run(m));
        assertArrayEquals(ones, run(r.module()));
    }
}
