package com.sixik.gpu.ir.optimizer;

import com.sixik.gpu.ir.Op;
import com.sixik.gpu.ir.Types;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.UnaryOperator;

/**
 * Удаление мёртвого кода: записи в никогда не читаемые локали, неиспользуемые массивы и недостижимые device-функции.
 *
 * <p>Остальное «мёртвое» ({@code if (false)}, код после {@code return}, пустые блоки, чистые {@code expr_stmt})
 * убирает {@link Simplify}, а записи, перезатираемые до чтения, - {@link Liveness#eliminateDeadStores}.</p>
 */
final class DeadCode {

    private DeadCode() {
    }

    /**
     * Применяет {@code f} к дочерним стейтментам узла.
     *
     * @param s узел-стейтмент
     * @param f преобразование
     * @return узел с обновлёнными детьми (тот же, если ничего не изменилось)
     */
    static Tree mapStmtKids(Tree s, UnaryOperator<Tree> f) {
        int from;
        int to = s.kids.length;
        switch (s.op) {
            case Op.BLOCK -> from = 0;
            case Op.IF, Op.LOOP, Op.SWITCH -> from = 1;
            case Op.DO_WHILE -> {
                from = 0;
                to = 1;
            }
            default -> {
                return s;
            }
        }
        Tree[] nk = null;
        for (int i = from; i < to; i++) {
            Tree r = f.apply(s.kids[i]);
            if (r != s.kids[i]) {
                if (nk == null) {
                    nk = s.kids.clone();
                }
                nk[i] = r;
            }
        }
        return nk == null ? s : s.withKids(nk);
    }

    /**
     * Удаляет все записи и объявления локалей, которые нигде не читаются.
     *
     * @param fn функция
     * @param fx анализ эффектов
     * @return новое тело
     */
    static Tree removeUnreadLocals(FnTree fn, Effects fx) {
        int[] reads = new int[fn.localCount];
        Locals.readCounts(fn.body, reads);
        return strip(fn.body, reads, fx);
    }

    private static Tree strip(Tree s, int[] reads, Effects fx) {
        switch (s.op) {
            case Op.LOCAL_DECL, Op.LOCAL_SET -> {
                if (reads[s.imm] == 0) {
                    Tree v = s.kids[0];
                    return fx.isRemovable(v) ? Simplify.EMPTY : Tree.of(Op.EXPR_STMT, Types.VOID, v);
                }
                return s;
            }
            case Op.PRIVATE_ARRAY_DECL, Op.LOCAL_ARRAY_DECL -> {
                return reads[s.imm] == 0 ? Simplify.EMPTY : s;
            }
            default -> {
                return mapStmtKids(s, k -> strip(k, reads, fx));
            }
        }
    }

    /**
     * Помечает недостижимые device-функции мёртвыми (корни - все kernel и fallback-функции живых).
     *
     * @param mt модуль
     * @return {@code true}, если состав живых функций изменился
     */
    static boolean markDeadFunctions(Codec.ModuleTree mt) {
        int n = mt.fns.length;
        boolean anyKernel = false;
        for (FnTree f : mt.fns) {
            anyKernel |= f.kind == com.sixik.gpu.ir.IrModule.KERNEL;
        }
        if (!anyKernel) {
            return false;
        }
        boolean[] live = new boolean[n];
        Deque<Integer> work = new ArrayDeque<>();
        for (int f = 0; f < n; f++) {
            if (mt.fns[f].kind == com.sixik.gpu.ir.IrModule.KERNEL) {
                live[f] = true;
                work.add(f);
            }
        }
        while (!work.isEmpty()) {
            int f = work.poll();
            FnTree fn = mt.fns[f];
            if (fn.fallback >= 0 && !live[fn.fallback]) {
                live[fn.fallback] = true;
                work.add(fn.fallback);
            }
            Codec.collectCallees(fn.body, callee -> {
                if (!live[callee]) {
                    live[callee] = true;
                    work.add(callee);
                }
            });
        }
        boolean changed = false;
        for (int f = 0; f < n; f++) {
            if (mt.fns[f].alive != live[f]) {
                mt.fns[f].alive = live[f];
                changed = true;
            }
        }
        return changed;
    }
}
