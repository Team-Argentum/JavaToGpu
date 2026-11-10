package com.sixik.gpu.ir.optimizer;

import com.sixik.gpu.ir.Builtins;
import com.sixik.gpu.ir.Op;

/**
 * Анализ побочных эффектов: какие поддеревья можно безопасно удалить, дублировать, переупорядочить или вынести из цикла.
 *
 * <p>Эффектом считаются: запись по указателю ({@code STORE}), барьер, builtin с флагом {@code EFFECT}
 * (атомики) и вызов функции, у которой есть эффект. Чтением памяти - {@code LOAD}, builtin с флагом {@code READS}
 * и вызов функции, читающей память. Свойства функций вычисляются неподвижной точкой по графу вызовов.</p>
 */
final class Effects {

    private final boolean[] funcEffects;
    private final boolean[] funcReads;

    private Effects(boolean[] funcEffects, boolean[] funcReads) {
        this.funcEffects = funcEffects;
        this.funcReads = funcReads;
    }

    /**
     * Строит анализ для всех функций модуля.
     *
     * @param mt модуль
     * @return анализ
     */
    static Effects analyze(Codec.ModuleTree mt) {
        int n = mt.fns.length;
        Effects e = new Effects(new boolean[n], new boolean[n]);
        boolean changed = true;
        while (changed) {
            changed = false;
            for (int f = 0; f < n; f++) {
                boolean eff = e.funcEffects[f];
                boolean rd = e.funcReads[f];
                if (!eff && e.hasEffect(mt.fns[f].body)) {
                    e.funcEffects[f] = true;
                    changed = true;
                }
                if (!rd && e.readsMemory(mt.fns[f].body)) {
                    e.funcReads[f] = true;
                    changed = true;
                }
            }
        }
        return e;
    }

    /**
     * @param f индекс функции
     * @return {@code true}, если вызов функции может иметь побочный эффект
     */
    boolean functionHasEffect(int f) {
        return funcEffects[f];
    }

    /**
     * @param t поддерево
     * @return {@code true}, если в поддереве есть узел с побочным эффектом
     */
    boolean hasEffect(Tree t) {
        switch (t.op) {
            case Op.STORE, Op.BARRIER -> {
                return true;
            }
            case Op.INTRINSIC -> {
                if (Builtins.has(t.imm, Builtins.EFFECT)) {
                    return true;
                }
            }
            case Op.CALL -> {
                if (funcEffects[t.imm]) {
                    return true;
                }
            }
            default -> {
            }
        }
        for (Tree c : t.kids) {
            if (hasEffect(c)) {
                return true;
            }
        }
        return false;
    }

    /**
     * @param t поддерево
     * @return {@code true}, если в поддереве есть чтение памяти (load, builtin с {@code READS}, чтение из вызова)
     */
    boolean readsMemory(Tree t) {
        switch (t.op) {
            case Op.LOAD -> {
                return true;
            }
            case Op.INTRINSIC -> {
                if (Builtins.has(t.imm, Builtins.READS)) {
                    return true;
                }
            }
            case Op.CALL -> {
                if (funcReads[t.imm] || funcEffects[t.imm]) {
                    return true;
                }
            }
            default -> {
            }
        }
        for (Tree c : t.kids) {
            if (readsMemory(c)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Можно ли удалить вычисление, если результат не нужен: нет эффектов. (Чтение памяти и деление не мешают:
     * удаление не добавляет наблюдаемого поведения.)
     *
     * @param t выражение
     * @return {@code true}, если выражение можно выбросить
     */
    boolean isRemovable(Tree t) {
        return !hasEffect(t);
    }

    /**
     * @param t поддерево
     * @return {@code true}, если в поддереве есть барьер
     */
    static boolean containsBarrier(Tree t) {
        if (t.op == Op.BARRIER) {
            return true;
        }
        for (Tree c : t.kids) {
            if (containsBarrier(c)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Может ли выражение «упасть» или вернуть неопределённое: целое деление или остаток на не-константу
     * (или на константу ноль). Такие узлы нельзя спекулятивно выполнять там, где исходная программа их не выполняла.
     *
     * @param t выражение
     * @return {@code true}, если вычисление может завершиться ошибкой
     */
    static boolean mayTrap(Tree t) {
        if ((t.op == Op.DIV || t.op == Op.REM) && com.sixik.gpu.ir.Types.isInteger(t.type)) {
            Tree d = t.kids[1];
            if (!(d.op == Op.CONST_INT && d.k != 0)) {
                return true;
            }
        }
        for (Tree c : t.kids) {
            if (mayTrap(c)) {
                return true;
            }
        }
        return false;
    }
}
