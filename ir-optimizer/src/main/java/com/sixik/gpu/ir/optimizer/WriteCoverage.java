package com.sixik.gpu.ir.optimizer;

import com.sixik.gpu.ir.Builtins;
import com.sixik.gpu.ir.IrModule;
import com.sixik.gpu.ir.Op;

import java.util.BitSet;

/**
 * Анализ полного покрытия записью: какие параметры-массивы ядро гарантированно перезаписывает целиком.
 *
 * <p>Нужен рантайму, чтобы не загружать на устройство массив, который ядро только пишет. Загрузка такого массива нужна
 * лишь затем, чтобы элементы, которые ядро не тронуло, сохранили значения хоста. Если каждый элемент перезаписывается,
 * загрузка - пустая трата шины: для выходного массива на миллионы элементов это половина времени запуска.</p>
 *
 * <p><b>Распознаваемый шаблон.</b> Стейтмент верхнего уровня тела {@code a[getGlobalId(0)] = v} (индекс - сам builtin
 * либо локаль, объявленная на верхнем уровне как {@code int i = getGlobalId(0)} и больше нигде не изменяемая), перед
 * которым на верхнем уровне нет стейтментов с {@code return}: такой стейтмент выполняется каждым work-item'ом. Тогда при
 * запуске с {@code globalSize(0) == a.length} каждый элемент массива записан хотя бы одним work-item'ом (другие оси
 * запуска лишь повторяют запись). Анализ консервативен: всё, что не подходит под шаблон, считается непокрытым.</p>
 *
 * <p>Пользоваться результатом можно только вместе с двумя проверками рантайма: параметр ядром не читается
 * ({@code Validator.ACCESS_READ}) и размер запуска по оси 0 равен длине массива.</p>
 */
public final class WriteCoverage {

    private static final int GET_GLOBAL_ID = Builtins.idOf("getGlobalId");

    private WriteCoverage() {
    }

    /**
     * @param m      корректный модуль
     * @param kernel индекс ядра
     * @return по параметру: {@code true}, если ядро при {@code globalSize(0) == length} записывает каждый его элемент
     */
    public static boolean[] fullyWrittenParams(IrModule m, int kernel) {
        boolean[] out = new boolean[m.fParamCount(kernel)];
        Codec.ModuleTree mt;
        try {
            mt = Codec.lift(m);
        } catch (UnsupportedOperationException e) {
            return out;
        }
        FnTree fn = mt.fns[kernel];
        if (fn.kind != IrModule.KERNEL) {
            return out;
        }
        // локали-копии getGlobalId(0): единственная запись - объявление верхнего уровня, адрес не берётся
        int[] writes = new int[fn.localCount];
        countWrites(fn.body, writes);
        boolean[] tracked = Locals.tracked(fn, mt.source);
        BitSet gid = new BitSet();
        for (Tree s : fn.body.kids) {
            if (s.op == Op.LOCAL_DECL && writes[s.imm] == 1 && tracked[s.imm] && isGlobalId0(s.kids[0], gid)) {
                gid.set(s.imm);
            }
        }
        for (Tree s : fn.body.kids) {
            if (s.op == Op.STORE && s.kids[0].op == Op.ELEM_PTR) {
                Tree ptr = s.kids[0];
                if (ptr.kids[0].op == Op.PARAM && isGlobalId0(ptr.kids[1], gid)) {
                    out[ptr.kids[0].imm] = true;
                }
            }
            if (Costs.contains(s, Op.RETURN)) {
                // после возможного выхода следующие стейтменты выполняются не каждым work-item'ом
                break;
            }
        }
        return out;
    }

    private static boolean isGlobalId0(Tree e, BitSet gidLocals) {
        if (e.op == Op.LOCAL_GET) {
            return gidLocals.get(e.imm);
        }
        return e.op == Op.INTRINSIC && e.imm == GET_GLOBAL_ID && e.kids.length == 1
                && e.kids[0].op == Op.CONST_INT && e.kids[0].k == 0;
    }

    private static void countWrites(Tree t, int[] writes) {
        if (t.op == Op.LOCAL_DECL || t.op == Op.LOCAL_SET) {
            writes[t.imm]++;
        }
        for (Tree c : t.kids) {
            countWrites(c, writes);
        }
    }
}
