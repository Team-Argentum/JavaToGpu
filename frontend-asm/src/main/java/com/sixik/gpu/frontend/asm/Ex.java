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
    /** {@code select(kids[0], kids[1], kids[2])} - тернарный оператор. */
    static final int SELECT = 11;
    /** {@code kids[0] && kids[1]} = {@code select(c, d, false)}. */
    static final int LAND = 12;
    /** {@code kids[0] || kids[1]} = {@code select(c, true, d)}. */
    static final int LOR = 13;
    /**
     * Результат {@code lcmp}, {@code fcmpl/fcmpg}, {@code dcmpl/dcmpg} (живёт до ближайшего условного перехода);
     * {@code aux} - вариант: {@link Conds#CMP_EXACT}, {@link Conds#CMP_NAN_LESS}, {@link Conds#CMP_NAN_GREATER}.
     */
    static final int CMP3 = 14;
    /**
     * Вызов {@code @DeviceFunction}: {@link #target} - ключ функции ({@code владелец.имя дескриптор}), {@link #aux} -
     * индекс инструкции {@code invokestatic}, {@link #bits} - индекс первой инструкции вычисления аргументов (по ним
     * восстанавливается порядок вызовов исходника, 17.11).
     */
    static final int CALL = 15;
    /** Поле struct: {@code kids[0]} - значение struct, {@code aux} - номер поля. */
    static final int FIELD_GET = 16;
    /** Компонента вектора: {@code aux} - номер. */
    static final int EXTRACT = 17;
    /** Вектор или struct из компонент/полей ({@link #cls} - тип). */
    static final int BUILD = 18;
    /** Вектор из одного скаляра ({@code splat}). */
    static final int SPLAT = 19;
    /** Вектор с заменённой компонентой {@code aux} ({@code withX}). */
    static final int INSERT = 20;
    /** Перестановка компонент ({@link #lanes}). */
    static final int SHUFFLE = 21;
    /** Чтение через указатель ({@code p.value}, {@code p.get(i)}). */
    static final int LOAD_PTR = 22;
    /** Указатель на элемент массива или указателя ({@code of}, {@code plus}); {@code aux = 1} - вызов API указателя. */
    static final int ELEM_PTR = 23;
    /** Адрес локали-ссылки {@code FloatPtr}. */
    static final int LOCAL_ADDR = 24;
    /** Нулевой агрегат (объявление без инициализатора). */
    static final int ZERO_AGG = 25;
    /** Ещё не инициализированный объект ({@code new T} до {@code <init>}). */
    static final int NEW_OBJ = 26;
    /** Начальное значение ссылки {@code new FloatPtr(v)}: {@code kids[0]} - значение ячейки. */
    static final int NEW_PTR = 27;

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
    /** Для {@link #CALL}: ключ вызываемой функции, иначе {@code null}. */
    final String target;
    /** Внутреннее имя агрегата (у массива - элемента; у {@code NEW_OBJ} - создаваемого класса), иначе {@code null}. */
    String cls;
    /** Номера компонент {@code SHUFFLE}. */
    int[] lanes;
    /** Свежее значение struct ({@code copy()}, результат вызова, сохранённый во временную локаль): правило 17.8. */
    boolean fresh;

    private Ex(int op, int kind, int elemKind, int space, boolean open, long bits, int aux, Var var, Ex[] kids,
               boolean implicit) {
        this(op, kind, elemKind, space, open, bits, aux, var, kids, implicit, null);
    }

    private Ex(int op, int kind, int elemKind, int space, boolean open, long bits, int aux, Var var, Ex[] kids,
               boolean implicit, String target) {
        this.target = target;
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
        Ex e = new Ex(LOCAL_GET, v.kind, v.elemKind, v.space, open, 0, 0, v, NONE, false);
        e.cls = v.cls;
        return e;
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
        Ex e = new Ex(LOAD, array.elemKind, -1, -1, true, 0, 0, null, new Ex[]{array, index}, false);
        e.cls = array.cls;
        return e;
    }

    static Ex arrayLen(Ex array) {
        return new Ex(ARRAY_LEN, Kinds.K_INT, -1, -1, false, 0, 0, null, new Ex[]{array}, false);
    }

    static Ex intrinsic(int id, int kind, boolean open, Ex[] args) {
        return new Ex(INTRINSIC, kind, -1, -1, open, 0, id, null, args, false);
    }

    /**
     * @param target ключ функции
     * @param kind   вид результата
     * @param at     индекс инструкции вызова
     * @param start  индекс первой инструкции аргументов
     * @param args   аргументы (с неявными приведениями к типам параметров)
     * @return вызов; результат «открыт», как у {@code frontend-java}: функция может читать память
     */
    static Ex call(String target, int kind, int at, int start, Ex[] args) {
        return new Ex(CALL, kind, -1, -1, true, start, at, null, args, false, target);
    }

    /** Агрегатная операция {@code op} над {@code kids}; открыта, если открыт любой операнд. */
    static Ex agg(int op, int kind, String cls, int aux, Ex... kids) {
        boolean open = false;
        for (Ex k : kids) {
            open |= k.open;
        }
        Ex e = new Ex(op, kind, -1, -1, open, 0, aux, null, kids, false);
        e.cls = cls;
        return e;
    }

    /** Чтение через указатель {@code ptr}. */
    static Ex loadPtr(Ex ptr) {
        return new Ex(LOAD_PTR, ptr.elemKind, -1, -1, true, 0, 0, null, new Ex[]{ptr}, false);
    }

    /** Указатель на элемент {@code index} массива или указателя {@code base} с элементом {@code elemKind}. */
    static Ex elemPtr(Ex base, Ex index, int elemKind, int space, boolean api) {
        return new Ex(ELEM_PTR, Kinds.K_PTR, elemKind, space, base.open || index.open, 0, api ? 1 : 0, null,
                new Ex[]{base, index}, false);
    }

    /** Адрес локали-ссылки. */
    static Ex localAddr(Var v) {
        return new Ex(LOCAL_ADDR, Kinds.K_PTR, v.elemKind, v.space, false, 0, 0, v, NONE, false);
    }

    /** Нулевое значение агрегата {@code cls}. */
    static Ex zeroAgg(String cls) {
        Ex e = new Ex(ZERO_AGG, Kinds.K_AGG, -1, -1, false, 0, 0, null, NONE, false);
        e.cls = cls;
        return e;
    }

    /** Неинициализированный объект класса {@code cls}. */
    static Ex newObject(String cls) {
        Ex e = new Ex(NEW_OBJ, Kinds.K_OTHER, -1, -1, false, 0, 0, null, NONE, false);
        e.cls = cls;
        return e;
    }

    /** Ссылка {@code new FloatPtr(init)}. */
    static Ex newPtr(Ex init, int elemKind) {
        return new Ex(NEW_PTR, Kinds.K_PTR, elemKind, com.sixik.gpu.ir.AddressSpace.PRIVATE, init.open, 0, 0, null,
                new Ex[]{init}, false);
    }

    /** @return то же выражение с именем агрегата {@code c} */
    Ex typed(String c) {
        this.cls = c;
        return this;
    }

    static Ex newArray(int elemKind, int length) {
        return new Ex(NEW_ARRAY, Kinds.K_ARRAY, elemKind, -1, false, 0, length, null, NONE, false);
    }

    /** @return то же приведение с другим признаком «неявное» */
    static Ex select(int kind, Ex c, Ex a, Ex b) {
        Ex e = new Ex(SELECT, kind, a.elemKind, a.space, c.open || a.open || b.open, 0, 0, null, new Ex[]{c, a, b}, false);
        e.cls = a.cls;
        return e;
    }

    static Ex logical(int op, Ex c, Ex d) {
        return new Ex(op, Kinds.K_BOOLEAN, -1, -1, c.open || d.open, 0, 0, null, new Ex[]{c, d}, false);
    }

    static Ex cmp3(int variant, Ex l, Ex r) {
        return new Ex(CMP3, Kinds.K_INT, -1, -1, l.open || r.open, 0, variant, null, new Ex[]{l, r}, false);
    }

    /** @return копия с другими операндами */
    Ex withKids(Ex[] k) {
        Ex e = new Ex(op, kind, elemKind, space, open, bits, aux, var, k, implicit, target);
        e.cls = cls;
        e.lanes = lanes;
        e.fresh = fresh;
        return e;
    }

    Ex withImplicit(boolean value) {
        Ex e = new Ex(op, kind, elemKind, space, open, bits, aux, var, kids, value, target);
        e.cls = cls;
        e.lanes = lanes;
        e.fresh = fresh;
        return e;
    }

    /** @return то же значение, помеченное свежим */
    Ex asFresh() {
        Ex e = withKids(kids);
        e.fresh = true;
        return e;
    }

    /**
     * Свежее значение struct (как {@code isFresh} в frontend-java): {@code new S(...)}, {@code copy()}, вызов
     * device-функции или {@code ?:} из таких. Копия переменной, параметра или элемента на JVM была бы общей ссылкой.
     */
    boolean isFresh() {
        return fresh || op == BUILD || op == NEW_OBJ || op == CALL || op == SELECT && kids[1].isFresh() && kids[2].isFresh();
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
