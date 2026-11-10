package com.sixik.gpu.ir.optimizer;

import com.sixik.gpu.ir.Op;

import java.util.Arrays;

/**
 * Неизменяемый узел рабочего дерева оптимизатора.
 *
 * <p>Плоская арена {@code IrModule} неудобна для переписывания (id узлов привязаны к порядку создания), поэтому
 * на время оптимизации каждая функция поднимается в дерево неизменяемых узлов, а затем целиком собирается обратно
 * через {@code IrBuilder} ({@link Codec}). Проходы - чистые функции {@code Tree -> Tree}; неизменившиеся
 * поддеревья возвращаются теми же объектами, поэтому «изменилось ли что-то» проверяется сравнением ссылок.</p>
 *
 * <p>Раскладка полей по опкодам (соответствует схеме колонок {@code a, b, c} из {@code Operands}):</p>
 * <ul>
 *   <li>{@code k} - сырые биты константы ({@code CONST_*}; для 32-битных типов знаково расширенные биты);</li>
 *   <li>{@code imm} - непосредственное значение: индекс локали, параметра, функции, builtin, поля, компоненты,
 *       флаги барьера, id spec-константы;</li>
 *   <li>{@code kids} - операнды-узлы (для {@code IF/LOOP} последний необязательный операнд просто отсутствует;
 *       для {@code SWITCH} - ключ и затем тела case);</li>
 *   <li>{@code ints} - индексы компонент {@code SHUFFLE} либо пары {@code (isDefault, value)} для {@code SWITCH}.</li>
 * </ul>
 */
final class Tree {

    /** Пустой список операндов. */
    static final Tree[] NONE = new Tree[0];
    /** Пустой список непосредственных значений. */
    static final int[] NO_INTS = new int[0];

    /** Опкод ({@link Op}). */
    final int op;
    /** Id типа результата (у стейтментов {@code VOID}). */
    final int type;
    /** Непосредственное значение (см. описание класса). */
    final int imm;
    /** Сырые биты константы. */
    final long k;
    /** Операнды-узлы. */
    final Tree[] kids;
    /** Дополнительные целые данные узла. */
    final int[] ints;
    /** Строка исходника либо 0. */
    final int line;
    /** Столбец исходника либо 0. */
    final int col;

    Tree(int op, int type, int imm, long k, Tree[] kids, int[] ints, int line, int col) {
        this.op = op;
        this.type = type;
        this.imm = imm;
        this.k = k;
        this.kids = kids;
        this.ints = ints;
        this.line = line;
        this.col = col;
    }

    /**
     * @param op   опкод
     * @param type тип результата
     * @param imm  непосредственное значение
     * @param k    биты константы
     * @param kids операнды
     * @param ints дополнительные данные
     * @return новый узел без позиции
     */
    static Tree make(int op, int type, int imm, long k, Tree[] kids, int[] ints) {
        return new Tree(op, type, imm, k, kids, ints, 0, 0);
    }

    /**
     * @param op   опкод
     * @param type тип результата
     * @param kids операнды
     * @return узел без непосредственных значений
     */
    static Tree of(int op, int type, Tree... kids) {
        return new Tree(op, type, 0, 0, kids, NO_INTS, 0, 0);
    }

    /**
     * @param op   опкод
     * @param type тип результата
     * @param imm  непосредственное значение
     * @param kids операнды
     * @return узел с одним непосредственным значением
     */
    static Tree ofImm(int op, int type, int imm, Tree... kids) {
        return new Tree(op, type, imm, 0, kids, NO_INTS, 0, 0);
    }

    /**
     * @param op   {@code CONST_INT}, {@code CONST_FLOAT} или {@code CONST_BOOL}
     * @param type тип
     * @param bits сырые биты (канонические для типа, см. {@link Eval#canon})
     * @return константа
     */
    static Tree constant(int op, int type, long bits) {
        return new Tree(op, type, 0, bits, NONE, NO_INTS, 0, 0);
    }

    /**
     * @param t    узел-образец (его позиция сохраняется)
     * @param type тип константы
     * @param bits биты
     * @return константа, унаследовавшая позицию образца
     */
    static Tree constLike(Tree t, int type, long bits) {
        int op = type == com.sixik.gpu.ir.Types.BOOL ? Op.CONST_BOOL
                : com.sixik.gpu.ir.Types.isFloat(type) ? Op.CONST_FLOAT : Op.CONST_INT;
        return new Tree(op, type, 0, bits, NONE, NO_INTS, t.line, t.col);
    }

    /** @return {@code true} для {@code CONST_INT / CONST_FLOAT / CONST_BOOL} */
    boolean isConst() {
        return op == Op.CONST_INT || op == Op.CONST_FLOAT || op == Op.CONST_BOOL;
    }

    /** @return {@code true}, если узел - стейтмент */
    boolean isStmt() {
        return Op.isStatement(op);
    }

    /** @return число операндов */
    int n() {
        return kids.length;
    }

    /**
     * @param i номер операнда
     * @return операнд
     */
    Tree kid(int i) {
        return kids[i];
    }

    /**
     * Копия узла с другими операндами. Если операнды те же объекты, возвращается сам узел.
     *
     * @param newKids новые операнды (длина может отличаться, например при удалении ветки {@code else})
     * @return узел с теми же полями, но новыми операндами
     */
    Tree withKids(Tree[] newKids) {
        if (Arrays.equals(kids, newKids)) {
            return this;
        }
        return new Tree(op, type, imm, k, newKids, ints, line, col);
    }

    /**
     * Копия узла с одним заменённым операндом.
     *
     * @param i   номер операнда
     * @param kid новый операнд
     * @return узел либо он же, если операнд тот же объект
     */
    Tree withKid(int i, Tree kid) {
        if (kids[i] == kid) {
            return this;
        }
        Tree[] nk = kids.clone();
        nk[i] = kid;
        return new Tree(op, type, imm, k, nk, ints, line, col);
    }

    /**
     * Копия узла с другим непосредственным значением.
     *
     * @param newImm новое значение
     * @return новый узел
     */
    Tree withImm(int newImm) {
        return new Tree(op, type, newImm, k, kids, ints, line, col);
    }

    /**
     * Копия с другим опкодом и операндами (тип и позиция сохраняются).
     *
     * @param newOp   новый опкод
     * @param newKids новые операнды
     * @return новый узел
     */
    Tree retarget(int newOp, Tree... newKids) {
        return new Tree(newOp, type, imm, k, newKids, ints, line, col);
    }

    /**
     * Структурное равенство двух поддеревьев (позиции игнорируются).
     *
     * @param a первое дерево
     * @param b второе дерево
     * @return {@code true}, если деревья совпадают по опкоду, типу, полям и операндам
     */
    static boolean same(Tree a, Tree b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        if (a.op != b.op || a.type != b.type || a.imm != b.imm || a.k != b.k || a.kids.length != b.kids.length
                || !Arrays.equals(a.ints, b.ints)) {
            return false;
        }
        for (int i = 0; i < a.kids.length; i++) {
            if (!same(a.kids[i], b.kids[i])) {
                return false;
            }
        }
        return true;
    }

    /**
     * Число узлов в поддереве (для эвристик «не раздувать код»).
     *
     * @return размер поддерева
     */
    int size() {
        int s = 1;
        for (Tree c : kids) {
            s += c.size();
        }
        return s;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        print(sb);
        return sb.toString();
    }

    private void print(StringBuilder sb) {
        sb.append(Op.nameOf(op));
        if (isConst()) {
            sb.append(':').append(k);
        } else if (imm != 0 || op == Op.PARAM || op == Op.LOCAL_GET) {
            sb.append('#').append(imm);
        }
        if (kids.length > 0) {
            sb.append('(');
            for (int i = 0; i < kids.length; i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                kids[i].print(sb);
            }
            sb.append(')');
        }
    }
}
