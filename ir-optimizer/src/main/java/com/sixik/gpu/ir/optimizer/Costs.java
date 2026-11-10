package com.sixik.gpu.ir.optimizer;

import com.sixik.gpu.ir.Builtins;
import com.sixik.gpu.ir.IrModule;
import com.sixik.gpu.ir.Op;
import com.sixik.gpu.ir.Types;

/**
 * Грубая модель стоимости выражений и общий бюджет регистров для проходов, которые заводят временные локали.
 *
 * <p>Стоимость - условные такты: сложение, сравнение, выбор, преобразование - 1; умножение - 1 (64-бит - 3);
 * деление и остаток - 8 для целых, 4 для вещественных; {@code sin/cos} - 10; {@code sqrt} - 6; чтение памяти - 6;
 * вызов - 10 плюс аргументы. Листья (константы, параметры, чтение локали) бесплатны. Цифры не претендуют на точность -
 * их задача отличить «стоит держать в регистре» от «проще пересчитать».</p>
 */
final class Costs {

    /** Минимальная стоимость выражения, ради которой заводится временная локаль. */
    static final int MIN_COST = 3;
    /** Стоимость, начиная с которой выражение «дорогое» (оправдывает регистр даже при нехватке запаса). */
    static final int HIGH_COST = 8;

    private static final int SIN = Builtins.idOf("sin");
    private static final int COS = Builtins.idOf("cos");
    private static final int SQRT = Builtins.idOf("sqrt");

    private Costs() {
    }

    /** Запас регистров, разделяемый проходами одной функции. */
    static final class Budget {
        /** Сколько слов ещё можно добавить к давлению. */
        int headroom;
        /** Разрешить ли дорогие вычисления при нулевом запасе (уровень 3). */
        final boolean bypassForExpensive;

        Budget(int headroom, boolean bypassForExpensive) {
            this.headroom = Math.max(0, headroom);
            this.bypassForExpensive = bypassForExpensive;
        }

        /**
         * Пробует занять запас под временное значение.
         *
         * @param words размер значения в словах
         * @param cost  стоимость вычисления, которое сохраняется
         * @return {@code true}, если временную локаль заводить можно (запас уменьшен)
         */
        boolean tryTake(int words, int cost) {
            if (words <= headroom) {
                headroom -= words;
                return true;
            }
            if (bypassForExpensive && cost >= HIGH_COST) {
                headroom = 0;
                return true;
            }
            return false;
        }
    }

    /**
     * @param t выражение
     * @return оценка стоимости вычисления
     */
    static int cost(Tree t) {
        int c = own(t);
        for (Tree k : t.kids) {
            if (!k.isStmt()) {
                c += cost(k);
            }
        }
        return c;
    }

    private static int own(Tree t) {
        switch (t.op) {
            case Op.CONST_INT, Op.CONST_FLOAT, Op.CONST_BOOL, Op.SPEC_CONST, Op.PARAM, Op.LOCAL_GET, Op.LOCAL_ADDR -> {
                return 0;
            }
            case Op.MUL -> {
                return Types.bits(t.type) == 64 ? 3 : 1;
            }
            case Op.DIV, Op.REM -> {
                int base = Types.isFloat(t.type) ? 4 : 8;
                return Types.bits(t.type) == 64 ? base * 2 : base;
            }
            case Op.LOAD -> {
                return 6;
            }
            case Op.CALL -> {
                return 10;
            }
            case Op.INTRINSIC -> {
                if (t.imm == SIN || t.imm == COS) {
                    return 10;
                }
                if (t.imm == SQRT) {
                    return 6;
                }
                return Builtins.has(t.imm, Builtins.UNIFORM) && Builtins.argCount(t.imm) <= 1 ? 1 : 2;
            }
            case Op.BUILD, Op.STRUCT_BUILD -> {
                return t.kids.length / 2 + 1;
            }
            default -> {
                return 1;
            }
        }
    }

    /**
     * Годится ли тип для хранения во временной локали.
     *
     * @param m модуль
     * @param t тип
     * @return {@code true} для скаляров, векторов и struct (но не указателей, массивов и {@code VOID})
     */
    static boolean storable(IrModule m, int t) {
        if (t == Types.VOID) {
            return false;
        }
        int k = Types.kind(m, t);
        return k == Types.KIND_SCALAR || k == Types.KIND_VEC || k == Types.KIND_STRUCT;
    }

    /**
     * @param t поддерево
     * @param op опкод
     * @return {@code true}, если в поддереве есть узел с таким опкодом
     */
    static boolean contains(Tree t, int op) {
        if (t.op == op) {
            return true;
        }
        for (Tree c : t.kids) {
            if (contains(c, op)) {
                return true;
            }
        }
        return false;
    }
}
