package com.sixik.gpu.frontend.asm;

/**
 * Узел дерева выражений фронтенда (REWORK_ARCHITECTURE.md, 17.7): неизменяемая запись с видом Java, которую можно
 * дублировать ({@code dup}) и выбрасывать ({@code pop}). Узлы IR создаёт только эмиттер, по готовому дереву, - так
 * в арене не остаётся недостижимых узлов ({@code IR-S03}) и открытых узлов с двумя родителями ({@code IR-E02}).
 *
 * <p>Приведение бывает явным (часть выражения) и <b>неявным</b> ({@link #implicit}): числовое повышение операнда,
 * приведение значения к типу локали, элемента, параметра или результата. {@code frontend-java} создаёт неявное
 * приведение после всех соседних операндов, поэтому эмиттер делает так же (порядок узлов задаёт {@code kernelId}).
 * В байткоде оба вида выглядят одинаково ({@code i2f}); неявным считается расширяющее приведение там, где его
 * требуют правила Java, - см. {@code MethodLowerer}.</p>
 */
final class Ex {

    static final int CONST = 0;
    static final int PARAM = 1;
    static final int LOCAL_GET = 2;
    static final int BINARY = 3;
    static final int NEG = 4;
    static final int NOT = 5;
    static final int CONVERT = 6;
    /** {@code load(elem_ptr(a, i))}; у {@code boolean[]} - ещё {@code convert(BOOL)}. */
    static final int LOAD = 7;
    static final int ARRAY_LEN = 8;
    static final int INTRINSIC = 9;
    /** Метка {@code newarray} до записи в локаль (в IR не попадает: становится объявлением массива). */
    static final int NEW_ARRAY = 10;

    final int op;
    /** Вид Java результата ({@link Kinds}). */
    final int kind;
    /** Для массива - вид элемента, иначе -1. */
    final int elemKind;
    /** Для массива - адресное пространство, иначе -1. */
    final int space;
    /** Значение может измениться последующими эффектами (читает изменяемую локаль или память). */
    final boolean open;
    /** Константа: значение целого/{@code boolean} либо биты {@code float}/{@code double}. */
    final long bits;
    /** Индекс параметра, опкод IR ({@code Op}), id builtin'а или длина массива. */
    final int aux;
    /** Переменная для {@link #LOCAL_GET}. */
    final Var var;
    /** Операнды. */
    final Ex[] kids;
    /** Для {@link #CONVERT}: неявное приведение. */
    final boolean implicit;

    private Ex(int op, int kind, int elemKind, int space, boolean open, long bits, int aux, Var var, Ex[] kids,
               boolean implicit) {
        this.op = op;
        this.kind = kind;
        this.elemKind = elemKind;
        this.space = space;
        this.open = open;
        this.bits = bits;
        this.aux = aux;
        this.var = var;
        this.kids = kids;
        this.implicit = implicit;
    }

    private static final Ex[] NONE = new Ex[0];

    static Ex constant(int kind, long bits) {
        return new Ex(CONST, kind, -1, -1, false, bits, 0, null, NONE, false);
    }

    static Ex param(int index, int kind, int elemKind, int space) {
        return new Ex(PARAM, kind, elemKind, space, false, 0, index, null, NONE, false);
    }

    static Ex localGet(Var v) {
        // массив-ссылку переназначить нельзя (IR-V04), временная локаль пишется один раз: такие чтения не «открыты»
        boolean open = !v.temp() && v.kind != Kinds.K_ARRAY;
        return new Ex(LOCAL_GET, v.kind, v.elemKind, v.space, open, 0, 0, v, NONE, false);
    }

    static Ex binary(int irOp, int kind, Ex l, Ex r) {
        return new Ex(BINARY, kind, -1, -1, l.open || r.open, 0, irOp, null, new Ex[]{l, r}, false);
    }

    static Ex unary(int op, int kind, Ex x) {
        return new Ex(op, kind, -1, -1, x.open, 0, 0, null, new Ex[]{x}, false);
    }

    static Ex convert(int kind, Ex x, boolean implicit) {
        return new Ex(CONVERT, kind, -1, -1, x.open, 0, 0, null, new Ex[]{x}, implicit);
    }

    static Ex load(Ex array, Ex index) {
        return new Ex(LOAD, array.elemKind, -1, -1, true, 0, 0, null, new Ex[]{array, index}, false);
    }

    static Ex arrayLen(Ex array) {
        return new Ex(ARRAY_LEN, Kinds.K_INT, -1, -1, false, 0, 0, null, new Ex[]{array}, false);
    }

    static Ex intrinsic(int id, int kind, boolean open, Ex[] args) {
        return new Ex(INTRINSIC, kind, -1, -1, open, 0, id, null, args, false);
    }

    static Ex newArray(int elemKind, int length) {
        return new Ex(NEW_ARRAY, Kinds.K_ARRAY, elemKind, -1, false, 0, length, null, NONE, false);
    }

    /** @return то же приведение с другим признаком «неявное» */
    Ex withImplicit(boolean value) {
        return new Ex(op, kind, elemKind, space, open, bits, aux, var, kids, value);
    }

    /** @return {@code true}, если в дереве есть чтение переменной {@code v} */
    boolean reads(Var v) {
        if (op == LOCAL_GET && var == v) {
            return true;
        }
        for (Ex k : kids) {
            if (k.reads(v)) {
                return true;
            }
        }
        return false;
    }

    /** @return {@code true} для константы {@code int} с этим значением */
    boolean isIntConstant(long value) {
        return op == CONST && (kind == Kinds.K_INT || kind == Kinds.K_LONG) && bits == value;
    }
}
