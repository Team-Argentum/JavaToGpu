package com.sixik.gpu.ir.optimizer;

import com.sixik.gpu.ir.IrModule;
import com.sixik.gpu.ir.Op;
import com.sixik.gpu.ir.Types;

import java.util.BitSet;

/**
 * Вспомогательные анализы использования локалей функции.
 *
 * <p>«Отслеживаемая» локаль - обычная переменная, значением которой можно оперировать как значением SSA:
 * не массив и не локаль с взятым адресом ({@code LOCAL_ADDR}: через указатель её могут менять незаметно для
 * анализа). Остальные локали все проходы считают «чёрным ящиком».</p>
 */
final class Locals {

    private Locals() {
    }

    /**
     * @param fn  функция
     * @param src модуль с таблицей типов
     * @return признак «отслеживаемая» по каждой локали
     */
    static boolean[] tracked(FnTree fn, IrModule src) {
        boolean[] t = new boolean[fn.localCount];
        for (int l = 0; l < t.length; l++) {
            t[l] = Types.kind(src, fn.locals[l]) != Types.KIND_ARRAY;
        }
        markUntracked(fn.body, t);
        return t;
    }

    private static void markUntracked(Tree n, boolean[] t) {
        if (n.op == Op.LOCAL_ADDR || n.op == Op.PRIVATE_ARRAY_DECL || n.op == Op.LOCAL_ARRAY_DECL) {
            t[n.imm] = false;
        }
        for (Tree c : n.kids) {
            markUntracked(c, t);
        }
    }

    /**
     * Собирает локали, в которые идёт запись (объявление с инициализацией либо присваивание) где-либо в поддереве.
     *
     * @param n   поддерево
     * @param out множество-приёмник
     */
    static void assigned(Tree n, BitSet out) {
        if (n.op == Op.LOCAL_DECL || n.op == Op.LOCAL_SET) {
            out.set(n.imm);
        }
        for (Tree c : n.kids) {
            assigned(c, out);
        }
    }

    /**
     * Собирает переменные, читаемые поддеревом: локали как {@code 0..L-1}, параметры как {@code paramBase + index}.
     *
     * @param n         поддерево
     * @param out       множество-приёмник
     * @param paramBase номер, с которого нумеруются параметры
     */
    static void used(Tree n, BitSet out, int paramBase) {
        if (n.op == Op.LOCAL_GET) {
            out.set(n.imm);
        } else if (n.op == Op.PARAM) {
            out.set(paramBase + n.imm);
        }
        for (Tree c : n.kids) {
            used(c, out, paramBase);
        }
    }

    /**
     * Читается ли локаль где-либо в поддереве ({@code LOCAL_GET} либо {@code LOCAL_ADDR}).
     *
     * @param n     поддерево
     * @param local локаль
     * @return {@code true}, если читается
     */
    static boolean isRead(Tree n, int local) {
        if ((n.op == Op.LOCAL_GET || n.op == Op.LOCAL_ADDR) && n.imm == local) {
            return true;
        }
        for (Tree c : n.kids) {
            if (isRead(c, local)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Число чтений каждой локали в дереве.
     *
     * @param n      поддерево
     * @param counts приёмник (длина не меньше числа локалей)
     */
    static void readCounts(Tree n, int[] counts) {
        if (n.op == Op.LOCAL_GET || n.op == Op.LOCAL_ADDR) {
            counts[n.imm]++;
        }
        for (Tree c : n.kids) {
            readCounts(c, counts);
        }
    }
}
