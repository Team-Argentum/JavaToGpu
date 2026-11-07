package com.sixik.gpu.ir;

/**
 * Единая схема того, какие колонки узла являются <b>операндами-узлами</b> (REWORK_ARCHITECTURE.md, 5.10).
 *
 * <p>Остальные значения колонок - непосредственные (индекс локали, параметра, функции, builtin, поля,
 * компоненты вектора, смещение списка, флаги). Схема нужна валидатору, интерпретатору, кодгену и дамперу,
 * чтобы никто не дублировал знание о раскладке колонок. Доступ индексный и не создаёт объектов.</p>
 *
 * <p>Порядок операндов - порядок хранения; он не задаёт порядок вычисления (выражения не имеют
 * эффектов, см. правила {@code IR-E01/E02}).</p>
 */
public final class Operands {

    private Operands() {
    }

    /**
     * Число операндов-узлов у узла {@code n}. Отсутствующие необязательные операнды ({@code -1}) не считаются.
     *
     * @param m модуль
     * @param n id узла
     * @return число операндов
     */
    public static int count(IrModule m, int n) {
        switch (m.op(n)) {
            case Op.NEG, Op.NOT, Op.CONVERT, Op.BITCAST, Op.LOAD, Op.ARRAY_LEN, Op.SPLAT, Op.EXTRACT,
                 Op.FIELD_GET, Op.FIELD_PTR, Op.EXPR_STMT, Op.SHUFFLE:
                return 1;
            case Op.LOCAL_DECL, Op.LOCAL_SET, Op.PRIVATE_ARRAY_DECL, Op.LOCAL_ARRAY_DECL:
                // b = значение/длина; -1 означает «нет» (нарушение IR-V02 ловит валидатор)
                return m.b(n) >= 0 ? 1 : 0;
            case Op.ADD, Op.SUB, Op.MUL, Op.DIV, Op.REM, Op.AND, Op.OR, Op.XOR, Op.SHL, Op.SHR, Op.USHR,
                 Op.EQ, Op.NE, Op.LT, Op.LE, Op.GT, Op.GE, Op.ELEM_PTR, Op.STORE, Op.INSERT, Op.FIELD_SET,
                 Op.DO_WHILE:
                return 2;
            case Op.SELECT:
                return 3;
            case Op.IF, Op.LOOP:
                return m.c(n) >= 0 ? 3 : 2;
            case Op.RETURN:
                return m.a(n) >= 0 ? 1 : 0;
            case Op.BUILD, Op.STRUCT_BUILD, Op.BLOCK:
                return m.b(n);
            case Op.CALL, Op.INTRINSIC:
                return m.c(n);
            case Op.SWITCH:
                return 1 + m.c(n);
            default:
                return 0;
        }
    }

    /**
     * {@code k}-й операнд-узел узла {@code n}.
     *
     * @param m модуль
     * @param n id узла
     * @param k номер операнда, {@code 0 <= k < count(m, n)}
     * @return id узла-операнда
     */
    public static int get(IrModule m, int n, int k) {
        switch (m.op(n)) {
            case Op.NEG, Op.NOT, Op.CONVERT, Op.BITCAST, Op.LOAD, Op.ARRAY_LEN, Op.SPLAT, Op.EXTRACT,
                 Op.FIELD_GET, Op.FIELD_PTR, Op.EXPR_STMT, Op.SHUFFLE:
                return m.a(n);
            case Op.LOCAL_DECL, Op.LOCAL_SET, Op.PRIVATE_ARRAY_DECL, Op.LOCAL_ARRAY_DECL:
                return m.b(n);
            case Op.ADD, Op.SUB, Op.MUL, Op.DIV, Op.REM, Op.AND, Op.OR, Op.XOR, Op.SHL, Op.SHR, Op.USHR,
                 Op.EQ, Op.NE, Op.LT, Op.LE, Op.GT, Op.GE, Op.ELEM_PTR, Op.STORE:
                return k == 0 ? m.a(n) : m.b(n);
            case Op.INSERT, Op.FIELD_SET:
                return k == 0 ? m.a(n) : m.c(n);
            case Op.DO_WHILE:
                return k == 0 ? m.a(n) : m.b(n);
            case Op.SELECT, Op.IF, Op.LOOP:
                return k == 0 ? m.a(n) : (k == 1 ? m.b(n) : m.c(n));
            case Op.RETURN:
                return m.a(n);
            case Op.BUILD, Op.STRUCT_BUILD, Op.BLOCK:
                return m.list(m.a(n) + k);
            case Op.CALL, Op.INTRINSIC:
                return m.list(m.b(n) + k);
            case Op.SWITCH:
                return k == 0 ? m.a(n) : m.list(m.b(n) + 3 * (k - 1) + 2);
            default:
                throw new IllegalArgumentException("op " + Op.nameOf(m.op(n)) + " has no operands");
        }
    }

    /**
     * Должен ли {@code k}-й операнд узла быть <b>стейтментом</b> (иначе - выражением).
     *
     * <p>Стейтменты-дети есть только у {@code BLOCK, IF (ветки), LOOP (тело и update), DO_WHILE (тело),
     * SWITCH (тела case)}; условия, ключи и значения всегда выражения.</p>
     *
     * @param m модуль
     * @param n id узла
     * @param k номер операнда
     * @return {@code true}, если операнд обязан быть стейтментом
     */
    public static boolean operandIsStatement(IrModule m, int n, int k) {
        return switch (m.op(n)) {
            case Op.BLOCK -> true;
            case Op.IF, Op.LOOP -> k >= 1;
            case Op.DO_WHILE -> k == 0;
            case Op.SWITCH -> k >= 1;
            default -> false;
        };
    }

    /**
     * Является ли {@code k}-й операнд «значением» стейтмента, в позиции которого разрешён эффектный вызов
     * (правило {@code IR-E01}): {@code EXPR_STMT.a, LOCAL_DECL.b, LOCAL_SET.b, STORE.b, RETURN.a}.
     *
     * @param m модуль
     * @param n id узла-родителя
     * @param k номер операнда
     * @return {@code true}, если в этой позиции допустим эффектный узел
     */
    public static boolean allowsEffectfulOperand(IrModule m, int n, int k) {
        return switch (m.op(n)) {
            case Op.EXPR_STMT, Op.RETURN, Op.LOCAL_DECL, Op.LOCAL_SET -> true;
            case Op.STORE -> k == 1;
            default -> false;
        };
    }
}
