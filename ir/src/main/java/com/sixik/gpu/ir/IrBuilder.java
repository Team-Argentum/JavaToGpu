package com.sixik.gpu.ir;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Сборщик {@link IrModule}: единственный способ получить модуль в коде (фронтенд, тесты, golden).
 *
 * <p>Id узлов выдаются строго в порядке создания, поэтому операнды всегда имеют меньший id, чем узел
 * (инвариант {@code IR-S02} выполняется по построению). Порядок создания узлов задаёт вызывающий и должен
 * быть детерминированным: одинаковая последовательность вызовов даёт побайтово одинаковый модуль.</p>
 *
 * <p>Типизированные методы сами вычисляют тип результата по типам операндов. Это ошибки программиста, а не
 * пользователя (пользовательские ошибки ловит валидатор и диагностики фронтенда), поэтому при явном
 * несоответствии типов методы бросают {@link IllegalArgumentException}. Для построения заведомо
 * некорректных модулей (негативные тесты валидатора) есть «сырой» {@link #node(int, int, int, int, int)}.</p>
 *
 * <p>Класс не потокобезопасен. Внутренние {@link HashMap} используются только для поиска при интернировании
 * строк и не влияют на порядок вывода.</p>
 */
public final class IrBuilder {

    private final IntList[] types = new IntList[IrModule.T_COLS];
    private final List<String> strings = new ArrayList<>();
    private final Map<String, Integer> stringIds = new HashMap<>();
    private long[] consts = new long[8];
    private int constCount;
    private final IntList[] nodes = new IntList[IrModule.N_COLS];
    private final IntList lists = new IntList(64);
    private final IntList[] funcs = new IntList[IrModule.F_COLS];

    // состояние текущей функции
    private int cur = -1;
    private int[] curParams = new int[0];
    private final IntList curLocals = new IntList();
    private final IntList curRequired = new IntList();

    // отладочная информация
    private final List<String> dbgStrings = new ArrayList<>();
    private final Map<String, Integer> dbgStringIds = new HashMap<>();
    private final IntList dbgFuncName = new IntList();
    private final List<int[]> dbgParamNames = new ArrayList<>();
    private final List<IntList> dbgLocalNames = new ArrayList<>();
    private final Map<Integer, int[]> dbgStructNames = new HashMap<>();
    private final IntList dbgLine = new IntList();
    private final IntList dbgCol = new IntList();

    /** Создаёт пустой builder с 16 предустановленными типами. */
    public IrBuilder() {
        int[][] preset = IrModule.presetTypeColumns(Types.FIRST_DYNAMIC);
        for (int c = 0; c < IrModule.T_COLS; c++) {
            types[c] = new IntList(32);
            for (int t = 0; t < Types.FIRST_DYNAMIC; t++) {
                types[c].add(preset[c][t]);
            }
        }
        for (int c = 0; c < IrModule.N_COLS; c++) {
            nodes[c] = new IntList(64);
        }
        for (int c = 0; c < IrModule.F_COLS; c++) {
            funcs[c] = new IntList(8);
        }
    }

    // =========================================================================================
    // Типы
    // =========================================================================================

    private int findOrAddType(int kind, int a, int b) {
        for (int t = Types.FIRST_DYNAMIC; t < types[0].size(); t++) {
            if (types[IrModule.T_KIND].get(t) == kind && types[IrModule.T_A].get(t) == a
                    && types[IrModule.T_B].get(t) == b) {
                return t;
            }
        }
        return addType(kind, a, b, 0, 0);
    }

    private int addType(int kind, int a, int b, int c, int d) {
        int id = types[IrModule.T_KIND].add(kind);
        types[IrModule.T_A].add(a);
        types[IrModule.T_B].add(b);
        types[IrModule.T_C].add(c);
        types[IrModule.T_D].add(d);
        return id;
    }

    /**
     * Вектор {@code (scalar, lanes)}; повторный вызов с теми же аргументами возвращает тот же id.
     *
     * @param scalar id скалярного типа
     * @param lanes  число компонент ({@code 2,3,4,8,16})
     * @return id типа
     */
    public int vec(int scalar, int lanes) {
        if (!Types.isScalar(scalar) || scalar == Types.BOOL) {
            throw new IllegalArgumentException("vec: invalid scalar " + scalar);
        }
        if (!Types.isValidLanes(lanes)) {
            throw new IllegalArgumentException("vec: invalid lane count " + lanes);
        }
        return findOrAddType(Types.KIND_VEC, scalar, lanes);
    }

    /**
     * Указатель; повторный вызов с теми же аргументами возвращает тот же id.
     *
     * @param space   address space
     * @param pointee id типа, на который указывает указатель
     * @return id типа
     */
    public int ptr(int space, int pointee) {
        return findOrAddType(Types.KIND_PTR, space, pointee);
    }

    /**
     * Массив с неявной длиной; повторный вызов с теми же аргументами возвращает тот же id.
     *
     * @param space address space
     * @param elem  id типа элемента
     * @return id типа
     */
    public int array(int space, int elem) {
        return findOrAddType(Types.KIND_ARRAY, space, elem);
    }

    /**
     * Структура. Структуры номинальны: каждый вызов создаёт новый тип, даже при одинаковых полях.
     *
     * @param fieldTypes id типов полей по порядку
     * @param align      явное выравнивание в байтах (степень двойки) либо 0 для естественного
     * @return id типа
     */
    public int struct(int[] fieldTypes, int align) {
        int off = lists.size();
        for (int ft : fieldTypes) {
            lists.add(ft);
        }
        return addType(Types.KIND_STRUCT, 0, off, fieldTypes.length, align);
    }

    /**
     * Создаёт строку таблицы типов без проверок; нужен только негативным тестам валидатора.
     *
     * @param kind вид типа
     * @param a    колонка a
     * @param b    колонка b
     * @param c    колонка c
     * @param d    колонка d
     * @return id типа
     */
    int rawType(int kind, int a, int b, int c, int d) {
        return addType(kind, a, b, c, d);
    }

    /**
     * Добавляет значения в пул списков и возвращает смещение; нужен только негативным тестам валидатора.
     *
     * @param values значения
     * @return смещение первого значения в пуле
     */
    int rawList(int... values) {
        return list(values);
    }

    // =========================================================================================
    // Строки и константы
    // =========================================================================================

    /**
     * Интернирует семантическую строку (имя требуемой возможности и т.п.).
     *
     * @param s строка
     * @return id строки
     */
    public int string(String s) {
        Integer id = stringIds.get(s);
        if (id != null) {
            return id;
        }
        int n = strings.size();
        strings.add(s);
        stringIds.put(s, n);
        return n;
    }

    private int addConst(long bits) {
        if (constCount == consts.length) {
            consts = Arrays.copyOf(consts, constCount * 2);
        }
        consts[constCount] = bits;
        return constCount++;
    }

    // =========================================================================================
    // Узлы
    // =========================================================================================

    /**
     * Создаёт узел без каких-либо проверок. Нужен типизированным методам и негативным тестам валидатора.
     *
     * @param op   опкод
     * @param type id типа результата
     * @param a    колонка a
     * @param b    колонка b
     * @param c    колонка c
     * @return id нового узла
     */
    public int node(int op, int type, int a, int b, int c) {
        int id = nodes[IrModule.N_OP].add(op);
        nodes[IrModule.N_TYPE].add(type);
        nodes[IrModule.N_A].add(a);
        nodes[IrModule.N_B].add(b);
        nodes[IrModule.N_C].add(c);
        return id;
    }

    /**
     * @param n id узла
     * @return id типа результата узла
     */
    public int typeOf(int n) {
        return nodes[IrModule.N_TYPE].get(n);
    }

    private int opOf(int n) {
        return nodes[IrModule.N_OP].get(n);
    }

    private int kindOf(int type) {
        return types[IrModule.T_KIND].get(type);
    }

    private int typeA(int type) {
        return types[IrModule.T_A].get(type);
    }

    private int typeB(int type) {
        return types[IrModule.T_B].get(type);
    }

    private int list(int[] values) {
        int off = lists.size();
        for (int v : values) {
            lists.add(v);
        }
        return off;
    }

    // ---- константы ------------------------------------------------------------------------------

    /**
     * Целая константа произвольного целого типа.
     *
     * @param type  целый тип ({@code I8..U64}) либо BOOL не допускается
     * @param value значение (для типов до 32 бит берутся младшие 32 бита)
     * @return id узла
     */
    public int constInt(int type, long value) {
        if (!Types.isInteger(type)) {
            throw new IllegalArgumentException("constInt: not an integer type " + type);
        }
        if (Types.bits(type) == 64) {
            return node(Op.CONST_INT, type, addConst(value), 0, 0);
        }
        return node(Op.CONST_INT, type, (int) value, 0, 0);
    }

    /** @param v значение @return константа {@code I32} */
    public int constI32(int v) {
        return constInt(Types.I32, v);
    }

    /** @param v значение @return константа {@code U32} (биты) */
    public int constU32(int v) {
        return constInt(Types.U32, v);
    }

    /** @param v значение @return константа {@code I64} */
    public int constI64(long v) {
        return constInt(Types.I64, v);
    }

    /** @param v значение @return константа {@code U64} (биты) */
    public int constU64(long v) {
        return constInt(Types.U64, v);
    }

    /** @param v значение @return константа {@code F32}; биты хранятся в колонке a */
    public int constF32(float v) {
        return node(Op.CONST_FLOAT, Types.F32, Float.floatToRawIntBits(v), 0, 0);
    }

    /** @param v значение @return константа {@code F64}; биты - в таблице констант */
    public int constF64(double v) {
        return node(Op.CONST_FLOAT, Types.F64, addConst(Double.doubleToRawLongBits(v)), 0, 0);
    }

    /** @param v значение @return булева константа */
    public int constBool(boolean v) {
        return node(Op.CONST_BOOL, Types.BOOL, v ? 1 : 0, 0, 0);
    }

    /**
     * Специализационная константа (зарезервировано под AutoTune).
     *
     * @param id   номер константы
     * @param type целый тип
     * @return id узла
     */
    public int specConst(int id, int type) {
        return node(Op.SPEC_CONST, type, id, 0, 0);
    }

    // ---- параметры и локали ---------------------------------------------------------------------

    /**
     * Чтение параметра текущей функции.
     *
     * @param index индекс параметра
     * @return id узла
     */
    public int param(int index) {
        requireFunction();
        if (index < 0 || index >= curParams.length) {
            throw new IllegalArgumentException("param: index " + index + " outside 0.." + (curParams.length - 1));
        }
        return node(Op.PARAM, curParams[index], index, 0, 0);
    }

    /**
     * Объявляет локаль текущей функции (только слот; объявление в коде - узлы {@code LOCAL_DECL} и др.).
     *
     * @param type id типа локали
     * @return индекс локали
     */
    public int declareLocal(int type) {
        requireFunction();
        return curLocals.add(type);
    }

    /** @param local индекс локали @return чтение локали */
    public int localGet(int local) {
        return node(Op.LOCAL_GET, curLocals.get(local), local, 0, 0);
    }

    /** @param local индекс локали @return адрес локали; тип {@code Ptr<private, T>} */
    public int localAddr(int local) {
        return node(Op.LOCAL_ADDR, ptr(AddressSpace.PRIVATE, curLocals.get(local)), local, 0, 0);
    }

    /**
     * @param local индекс локали
     * @param init  узел начального значения
     * @return стейтмент объявления
     */
    public int localDecl(int local, int init) {
        return node(Op.LOCAL_DECL, Types.VOID, local, init, 0);
    }

    /**
     * @param local индекс локали
     * @param value узел нового значения
     * @return стейтмент присваивания
     */
    public int localSet(int local, int value) {
        return node(Op.LOCAL_SET, Types.VOID, local, value, 0);
    }

    /**
     * @param local индекс локали типа {@code Array(PRIVATE, elem)}
     * @param len   узел длины (константа)
     * @return стейтмент объявления private-массива
     */
    public int privateArrayDecl(int local, int len) {
        return node(Op.PRIVATE_ARRAY_DECL, Types.VOID, local, len, 0);
    }

    /**
     * @param local индекс локали типа {@code Array(LOCAL, elem)}
     * @param len   узел длины (константа)
     * @return стейтмент объявления local-массива
     */
    public int localArrayDecl(int local, int len) {
        return node(Op.LOCAL_ARRAY_DECL, Types.VOID, local, len, 0);
    }

    // ---- арифметика -----------------------------------------------------------------------------

    /**
     * Бинарная операция: арифметика, побитовые, сдвиги (тип результата = тип {@code a}) либо
     * сравнение (результат {@code BOOL}).
     *
     * @param op опкод
     * @param a  левый операнд
     * @param b  правый операнд
     * @return id узла
     */
    public int binary(int op, int a, int b) {
        if (Op.isCompare(op)) {
            return node(op, Types.BOOL, a, b, 0);
        }
        if (Op.isArithBinary(op) || Op.isBitwiseBinary(op) || Op.isShift(op)) {
            return node(op, typeOf(a), a, b, 0);
        }
        throw new IllegalArgumentException("binary: opcode " + Op.nameOf(op) + " is not binary");
    }

    /** @param a операнд @return {@code -a} */
    public int neg(int a) {
        return node(Op.NEG, typeOf(a), a, 0, 0);
    }

    /** @param a операнд @return побитовое/логическое НЕ */
    public int not(int a) {
        return node(Op.NOT, typeOf(a), a, 0, 0);
    }

    /**
     * Ленивый выбор.
     *
     * @param cond условие ({@code BOOL})
     * @param then значение при истине
     * @param otherwise значение при лжи
     * @return id узла
     */
    public int select(int cond, int then, int otherwise) {
        return node(Op.SELECT, typeOf(then), cond, then, otherwise);
    }

    /**
     * @param toType целевой тип
     * @param x      значение
     * @return явное преобразование
     */
    public int convert(int toType, int x) {
        return node(Op.CONVERT, toType, x, 0, 0);
    }

    /**
     * @param toType целевой тип
     * @param x      значение
     * @return переинтерпретация битов
     */
    public int bitcast(int toType, int x) {
        return node(Op.BITCAST, toType, x, 0, 0);
    }

    // ---- память ---------------------------------------------------------------------------------

    /**
     * Адрес элемента массива или указателя.
     *
     * @param base  массив или указатель
     * @param index индекс ({@code I32})
     * @return указатель на элемент
     */
    public int elemPtr(int base, int index) {
        int t = typeOf(base);
        int k = kindOf(t);
        if (k != Types.KIND_ARRAY && k != Types.KIND_PTR) {
            throw new IllegalArgumentException("elemPtr: operand n" + base + " is neither an array nor a pointer");
        }
        return node(Op.ELEM_PTR, ptr(typeA(t), typeB(t)), base, index, 0);
    }

    /**
     * Адрес поля структуры.
     *
     * @param ptr   указатель на struct
     * @param field индекс поля
     * @return указатель на поле
     */
    public int fieldPtr(int ptr, int field) {
        int t = typeOf(ptr);
        if (kindOf(t) != Types.KIND_PTR || kindOf(typeB(t)) != Types.KIND_STRUCT) {
            throw new IllegalArgumentException("fieldPtr: operand n" + ptr + " is not a pointer to a struct");
        }
        int fieldType = lists.get(types[IrModule.T_B].get(typeB(t)) + field);
        return node(Op.FIELD_PTR, ptr(typeA(t), fieldType), ptr, field, 0);
    }

    /** @param ptr указатель @return значение по указателю */
    public int load(int ptr) {
        int t = typeOf(ptr);
        if (kindOf(t) != Types.KIND_PTR) {
            throw new IllegalArgumentException("load: operand n" + ptr + " is not a pointer");
        }
        return node(Op.LOAD, typeB(t), ptr, 0, 0);
    }

    /**
     * @param ptr   указатель
     * @param value значение
     * @return стейтмент записи
     */
    public int store(int ptr, int value) {
        return node(Op.STORE, Types.VOID, ptr, value, 0);
    }

    /** @param array массив @return длина массива ({@code I32}) */
    public int arrayLen(int array) {
        return node(Op.ARRAY_LEN, Types.I32, array, 0, 0);
    }

    // ---- векторы --------------------------------------------------------------------------------

    /**
     * @param vecType тип вектора
     * @param scalar  скаляр
     * @return вектор из одинаковых компонент
     */
    public int splat(int vecType, int scalar) {
        return node(Op.SPLAT, vecType, scalar, 0, 0);
    }

    /**
     * @param vec  вектор
     * @param lane индекс компоненты
     * @return компонента
     */
    public int extract(int vec, int lane) {
        return node(Op.EXTRACT, typeA(typeOf(vec)), vec, lane, 0);
    }

    /**
     * @param vec    вектор
     * @param lane   индекс компоненты
     * @param scalar новое значение компоненты
     * @return новый вектор
     */
    public int insert(int vec, int lane, int scalar) {
        return node(Op.INSERT, typeOf(vec), vec, lane, scalar);
    }

    /**
     * @param vec   вектор
     * @param lanes индексы компонент результата
     * @return вектор длины {@code lanes.length}
     */
    public int shuffle(int vec, int... lanes) {
        int result = vec(typeA(typeOf(vec)), lanes.length);
        return node(Op.SHUFFLE, result, vec, list(lanes), lanes.length);
    }

    /**
     * @param vecType тип вектора
     * @param scalars компоненты
     * @return вектор
     */
    public int build(int vecType, int... scalars) {
        return node(Op.BUILD, vecType, list(scalars), scalars.length, 0);
    }

    // ---- struct ---------------------------------------------------------------------------------

    /**
     * @param structType тип struct
     * @param fields     значения полей по порядку
     * @return значение struct
     */
    public int structBuild(int structType, int... fields) {
        return node(Op.STRUCT_BUILD, structType, list(fields), fields.length, 0);
    }

    /**
     * @param value значение struct
     * @param field индекс поля
     * @return значение поля
     */
    public int fieldGet(int value, int field) {
        int st = typeOf(value);
        return node(Op.FIELD_GET, lists.get(types[IrModule.T_B].get(st) + field), value, field, 0);
    }

    /**
     * @param value    значение struct
     * @param field    индекс поля
     * @param newValue новое значение поля
     * @return новое значение struct
     */
    public int fieldSet(int value, int field, int newValue) {
        return node(Op.FIELD_SET, typeOf(value), value, field, newValue);
    }

    // ---- вызовы ---------------------------------------------------------------------------------

    /**
     * Вызов ранее объявленной функции модуля.
     *
     * @param func индекс функции (уже созданной через {@link #beginFunction})
     * @param args аргументы
     * @return значение вызова
     */
    public int call(int func, int... args) {
        return node(Op.CALL, funcs[IrModule.F_RET].get(func), func, list(args), args.length);
    }

    /**
     * Вызов builtin; тип результата вычисляется по сигнатуре из таблицы builtins.
     *
     * @param builtinId id builtin
     * @param args      аргументы
     * @return значение вызова
     */
    public int intrinsic(int builtinId, int... args) {
        int[] argTypes = new int[args.length];
        for (int i = 0; i < args.length; i++) {
            argTypes[i] = typeOf(args[i]);
        }
        int ret = Builtins.resultType(snapshotTypes(), builtinId, argTypes, args.length);
        if (ret < 0) {
            throw new IllegalArgumentException("intrinsic: arguments do not fit builtin "
                    + (Builtins.isValid(builtinId) ? Builtins.name(builtinId) : "#" + builtinId));
        }
        return node(Op.INTRINSIC, ret, builtinId, list(args), args.length);
    }

    /** Лёгкий модуль-«слепок» с текущей таблицей типов; нужен только для разбора типов в Builtins. */
    private IrModule snapshotTypes() {
        int[][] t = new int[IrModule.T_COLS][];
        for (int c = 0; c < IrModule.T_COLS; c++) {
            t[c] = types[c].toArray();
        }
        return new IrModule(new IrModule.Parts(Builtins.VERSION, t, new String[0], new long[0],
                new int[IrModule.N_COLS][0], new int[0], new int[IrModule.F_COLS][0], DebugInfo.EMPTY));
    }

    // ---- стейтменты и control flow --------------------------------------------------------------

    /** @param expr выражение @return вычисление ради эффекта */
    public int exprStmt(int expr) {
        return node(Op.EXPR_STMT, Types.VOID, expr, 0, 0);
    }

    /** @param flags флаги барьера (бит 0 - local, бит 1 - global) @return барьер */
    public int barrier(int flags) {
        return node(Op.BARRIER, Types.VOID, flags, 0, 0);
    }

    /** @param statements стейтменты по порядку @return блок */
    public int block(int... statements) {
        return node(Op.BLOCK, Types.VOID, list(statements), statements.length, 0);
    }

    /**
     * @param cond      условие
     * @param then      ветка при истине
     * @param otherwise ветка при лжи либо {@code -1}
     * @return условный оператор
     */
    public int ifStmt(int cond, int then, int otherwise) {
        return node(Op.IF, Types.VOID, cond, then, otherwise);
    }

    /**
     * @param cond   условие
     * @param body   тело
     * @param update стейтмент перехода либо {@code -1}
     * @return цикл с предусловием
     */
    public int loop(int cond, int body, int update) {
        return node(Op.LOOP, Types.VOID, cond, body, update);
    }

    /**
     * @param body тело
     * @param cond условие
     * @return цикл с постусловием
     */
    public int doWhile(int body, int cond) {
        return node(Op.DO_WHILE, Types.VOID, body, cond, 0);
    }

    /**
     * Switch как в C (с проваливанием).
     *
     * @param key        ключ (целое)
     * @param isDefault  для каждого case: {@code true} для ветки default
     * @param values     для каждого case: значение (игнорируется для default)
     * @param bodies     для каждого case: стейтмент
     * @return switch
     */
    public int switchStmt(int key, boolean[] isDefault, int[] values, int[] bodies) {
        int n = bodies.length;
        int[] triples = new int[3 * n];
        for (int i = 0; i < n; i++) {
            triples[3 * i] = isDefault[i] ? 1 : 0;
            triples[3 * i + 1] = values[i];
            triples[3 * i + 2] = bodies[i];
        }
        return node(Op.SWITCH, Types.VOID, key, list(triples), n);
    }

    /** @return {@code break} */
    public int breakStmt() {
        return node(Op.BREAK, Types.VOID, 0, 0, 0);
    }

    /** @return {@code continue} */
    public int continueStmt() {
        return node(Op.CONTINUE, Types.VOID, 0, 0, 0);
    }

    /** @param value значение либо {@code -1} @return возврат */
    public int returnStmt(int value) {
        return node(Op.RETURN, Types.VOID, value, 0, 0);
    }

    // =========================================================================================
    // Функции
    // =========================================================================================

    /**
     * Начинает функцию. До {@link #endFunction(int)} узлы относятся к ней (параметры и локали).
     *
     * @param kind       {@link IrModule#KERNEL} или {@link IrModule#DEVICE}
     * @param retType    тип результата
     * @param paramTypes типы параметров
     * @return индекс функции
     */
    public int beginFunction(int kind, int retType, int... paramTypes) {
        if (cur >= 0) {
            throw new IllegalStateException("the previous function is not finished");
        }
        cur = funcs[IrModule.F_KIND].size();
        curParams = paramTypes.clone();
        curLocals.clear();
        curRequired.clear();
        int paramsOff = list(paramTypes);
        for (int c = 0; c < IrModule.F_COLS; c++) {
            funcs[c].add(0);
        }
        funcs[IrModule.F_KIND].set(cur, kind);
        funcs[IrModule.F_RET].set(cur, retType);
        funcs[IrModule.F_PARAMS_OFF].set(cur, paramsOff);
        funcs[IrModule.F_PARAM_COUNT].set(cur, paramTypes.length);
        funcs[IrModule.F_BODY].set(cur, -1);
        funcs[IrModule.F_FALLBACK].set(cur, -1);
        dbgFuncName.add(-1);
        dbgParamNames.add(null);
        dbgLocalNames.add(null);
        return cur;
    }

    private void requireFunction() {
        if (cur < 0) {
            throw new IllegalStateException("no current function: call beginFunction");
        }
    }

    /**
     * Обязательный размер work-group ({@code reqd_work_group_size}).
     *
     * @param x размер по оси X
     * @param y размер по оси Y
     * @param z размер по оси Z
     */
    public void requireWorkGroup(int x, int y, int z) {
        requireFunction();
        funcs[IrModule.F_WG_X].set(cur, x);
        funcs[IrModule.F_WG_Y].set(cur, y);
        funcs[IrModule.F_WG_Z].set(cur, z);
    }

    /** @param policy {@code IrModule.INLINE_*} */
    public void inlinePolicy(int policy) {
        requireFunction();
        funcs[IrModule.F_INLINE].set(cur, policy);
    }

    /** @param level уровень оптимизации 0..3 */
    public void optLevel(int level) {
        requireFunction();
        funcs[IrModule.F_OPT].set(cur, level);
    }

    /** @param names имена требуемых возможностей (директива {@code required}) */
    public void required(String... names) {
        requireFunction();
        for (String n : names) {
            curRequired.add(string(n));
        }
    }

    /** @param func индекс функции-варианта, используемой при невыполненных {@code required} */
    public void fallback(int func) {
        requireFunction();
        funcs[IrModule.F_FALLBACK].set(cur, func);
    }

    /**
     * Завершает текущую функцию.
     *
     * @param body корневой узел тела (BLOCK)
     */
    public void endFunction(int body) {
        requireFunction();
        funcs[IrModule.F_BODY].set(cur, body);
        int localsOff = lists.size();
        for (int i = 0; i < curLocals.size(); i++) {
            lists.add(curLocals.get(i));
        }
        funcs[IrModule.F_LOCALS_OFF].set(cur, localsOff);
        funcs[IrModule.F_LOCAL_COUNT].set(cur, curLocals.size());
        int reqOff = lists.size();
        for (int i = 0; i < curRequired.size(); i++) {
            lists.add(curRequired.get(i));
        }
        funcs[IrModule.F_REQUIRED_OFF].set(cur, reqOff);
        funcs[IrModule.F_REQUIRED_COUNT].set(cur, curRequired.size());
        cur = -1;
    }

    // =========================================================================================
    // Отладочная информация (не входит в хэш)
    // =========================================================================================

    private int dbgString(String s) {
        Integer id = dbgStringIds.get(s);
        if (id != null) {
            return id;
        }
        int n = dbgStrings.size();
        dbgStrings.add(s);
        dbgStringIds.put(s, n);
        return n;
    }

    /** @param name имя текущей функции */
    public void nameFunction(String name) {
        requireFunction();
        dbgFuncName.set(cur, dbgString(name));
    }

    /**
     * @param names имена параметров текущей функции по порядку
     */
    public void nameParams(String... names) {
        requireFunction();
        int[] ids = new int[names.length];
        for (int i = 0; i < names.length; i++) {
            ids[i] = dbgString(names[i]);
        }
        dbgParamNames.set(cur, ids);
    }

    /**
     * @param local индекс локали
     * @param name  имя локали
     */
    public void nameLocal(int local, String name) {
        requireFunction();
        IntList names = dbgLocalNames.get(cur);
        if (names == null) {
            names = new IntList();
            dbgLocalNames.set(cur, names);
        }
        while (names.size() <= local) {
            names.add(-1);
        }
        names.set(local, dbgString(name));
    }

    /**
     * @param structType id struct-типа
     * @param name       имя struct
     * @param fields     имена полей
     */
    public void nameStruct(int structType, String name, String... fields) {
        int[] ids = new int[1 + fields.length];
        ids[0] = dbgString(name);
        for (int i = 0; i < fields.length; i++) {
            ids[1 + i] = dbgString(fields[i]);
        }
        dbgStructNames.put(structType, ids);
    }

    /**
     * Позиция узла в исходнике.
     *
     * @param node id узла
     * @param line строка (1-based)
     * @param col  столбец (1-based)
     */
    public void span(int node, int line, int col) {
        while (dbgLine.size() <= node) {
            dbgLine.add(0);
            dbgCol.add(0);
        }
        dbgLine.set(node, line);
        dbgCol.set(node, col);
    }

    // =========================================================================================
    // Сборка
    // =========================================================================================

    /**
     * Завершает сборку и создаёт неизменяемый модуль. Builder после этого можно выбросить.
     *
     * @return модуль
     */
    public IrModule build() {
        if (cur >= 0) {
            throw new IllegalStateException("function is not finished");
        }
        int[][] t = new int[IrModule.T_COLS][];
        for (int c = 0; c < IrModule.T_COLS; c++) {
            t[c] = types[c].toArray();
        }
        int[][] n = new int[IrModule.N_COLS][];
        for (int c = 0; c < IrModule.N_COLS; c++) {
            n[c] = nodes[c].toArray();
        }
        int[][] f = new int[IrModule.F_COLS][];
        for (int c = 0; c < IrModule.F_COLS; c++) {
            f[c] = funcs[c].toArray();
        }
        int typeCount = t[0].length;
        int funcCount = f[0].length;

        IntList pool = new IntList();
        int[] paramOff = new int[funcCount];
        int[] localOff = new int[funcCount];
        for (int i = 0; i < funcCount; i++) {
            int[] pn = dbgParamNames.get(i);
            if (pn == null) {
                paramOff[i] = -1;
            } else {
                paramOff[i] = pool.size();
                for (int v : pn) {
                    pool.add(v);
                }
            }
            IntList ln = dbgLocalNames.get(i);
            if (ln == null) {
                localOff[i] = -1;
            } else {
                localOff[i] = pool.size();
                for (int k = 0; k < ln.size(); k++) {
                    pool.add(ln.get(k));
                }
            }
        }
        int[] typeName = new int[typeCount];
        int[] typeFieldsOff = new int[typeCount];
        Arrays.fill(typeName, -1);
        Arrays.fill(typeFieldsOff, -1);
        for (int tid = Types.FIRST_DYNAMIC; tid < typeCount; tid++) {
            int[] ids = dbgStructNames.get(tid);
            if (ids != null) {
                typeName[tid] = ids[0];
                typeFieldsOff[tid] = pool.size();
                for (int k = 1; k < ids.length; k++) {
                    pool.add(ids[k]);
                }
            }
        }
        int nodeCount = n[0].length;
        int[] line = Arrays.copyOf(dbgLine.toArray(), Math.min(dbgLine.size(), nodeCount));
        int[] col = Arrays.copyOf(dbgCol.toArray(), Math.min(dbgCol.size(), nodeCount));
        DebugInfo debug = new DebugInfo(dbgStrings.toArray(new String[0]), dbgFuncName.toArray(), paramOff,
                localOff, typeName, typeFieldsOff, pool.toArray(), line, col);

        return new IrModule(new IrModule.Parts(Builtins.VERSION, t, strings.toArray(new String[0]),
                Arrays.copyOf(consts, constCount), n, lists.toArray(), f, debug));
    }
}
