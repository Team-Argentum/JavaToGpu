package com.sixik.gpu.ir;

/**
 * Каталог опкодов IR (REWORK_ARCHITECTURE.md, 5.10).
 *
 * <p>Узел хранится строкой SoA-таблицы {@code nodes} с колонками {@code op, type, a, b, c}. Назначение
 * колонок {@code a, b, c} зависит от опкода (операнд-узел, непосредственное значение или смещение списка);
 * полная схема - в {@link Operands}. Опкоды - числовые константы, чтобы таблица писалась в бинарный формат
 * без преобразований.</p>
 *
 * <p>Выражения и стейтменты лежат в одной таблице. У стейтментов результат {@link Types#VOID}.</p>
 */
public final class Op {

    private Op() {
    }

    // ---- константы -------------------------------------------------------------------------------
    /** Целая константа: {@code a}=значение (типы до 32 бит) либо индекс в таблице констант (64 бита). */
    public static final int CONST_INT = 1;
    /** Вещественная константа: {@code a}=сырые биты (F32) либо индекс в таблице констант (F64). */
    public static final int CONST_FLOAT = 2;
    /** Булева константа: {@code a}=0 или 1. */
    public static final int CONST_BOOL = 3;
    /** Специализационная константа (зарезервировано под AutoTune): {@code a}=id. */
    public static final int SPEC_CONST = 4;
    /** Параметр функции: {@code a}=индекс параметра. */
    public static final int PARAM = 5;

    // ---- арифметика ------------------------------------------------------------------------------
    /** Сложение: {@code a + b}. */
    public static final int ADD = 10;
    /** Вычитание: {@code a - b}. */
    public static final int SUB = 11;
    /** Умножение: {@code a * b}. */
    public static final int MUL = 12;
    /** Деление: {@code a / b}. */
    public static final int DIV = 13;
    /** Остаток: {@code a % b}. */
    public static final int REM = 14;
    /** Арифметическое отрицание: {@code -a}. */
    public static final int NEG = 15;

    // ---- побитовые и сдвиги ----------------------------------------------------------------------
    /** Побитовое И (для BOOL - eager логическое). */
    public static final int AND = 20;
    /** Побитовое ИЛИ (для BOOL - eager логическое). */
    public static final int OR = 21;
    /** Побитовое исключающее ИЛИ. */
    public static final int XOR = 22;
    /** Побитовое НЕ (для BOOL - логическое НЕ). */
    public static final int NOT = 23;
    /** Сдвиг влево; счётчик маскируется шириной типа. */
    public static final int SHL = 24;
    /** Сдвиг вправо: арифметический для знаковых типов, логический для беззнаковых. */
    public static final int SHR = 25;
    /** Логический сдвиг вправо для <b>знакового</b> типа (Java {@code >>>}). */
    public static final int USHR = 26;

    // ---- сравнения (только скаляры, результат BOOL) ---------------------------------------------
    /** Равно. */
    public static final int EQ = 30;
    /** Не равно. */
    public static final int NE = 31;
    /** Меньше. */
    public static final int LT = 32;
    /** Меньше или равно. */
    public static final int LE = 33;
    /** Больше. */
    public static final int GT = 34;
    /** Больше или равно. */
    public static final int GE = 35;

    // ---- выбор и приведения ---------------------------------------------------------------------
    /** Ленивый выбор: {@code a ? b : c}; вычисляется только выбранная ветка. */
    public static final int SELECT = 40;
    /** Явное преобразование значения к типу узла. */
    public static final int CONVERT = 41;
    /** Переинтерпретация битов значения как тип узла (размеры равны). */
    public static final int BITCAST = 42;

    // ---- локальные переменные -------------------------------------------------------------------
    /** Объявление локали с инициализацией: {@code a}=локаль, {@code b}=init. Стейтмент. */
    public static final int LOCAL_DECL = 50;
    /** Чтение локали: {@code a}=локаль. */
    public static final int LOCAL_GET = 51;
    /** Запись локали: {@code a}=локаль, {@code b}=значение. Стейтмент. */
    public static final int LOCAL_SET = 52;
    /** Адрес локали (локаль становится «в памяти»): {@code a}=локаль, тип {@code Ptr<private,T>}. */
    public static final int LOCAL_ADDR = 53;

    // ---- память ----------------------------------------------------------------------------------
    /** Адрес элемента: {@code a}=массив или указатель, {@code b}=индекс (I32). */
    public static final int ELEM_PTR = 60;
    /** Адрес поля struct: {@code a}=указатель на struct, {@code b}=индекс поля. */
    public static final int FIELD_PTR = 61;
    /** Чтение по указателю: {@code a}=указатель. */
    public static final int LOAD = 62;
    /** Запись по указателю: {@code a}=указатель, {@code b}=значение. Стейтмент. */
    public static final int STORE = 63;
    /** Длина массива: {@code a}=массив, тип I32. */
    public static final int ARRAY_LEN = 64;
    /** Объявление private-массива фиксированной длины: {@code a}=локаль, {@code b}=узел длины. Стейтмент. */
    public static final int PRIVATE_ARRAY_DECL = 65;
    /** Объявление local-массива фиксированной длины (только kernel): {@code a}=локаль, {@code b}=узел длины. Стейтмент. */
    public static final int LOCAL_ARRAY_DECL = 66;

    // ---- векторы ---------------------------------------------------------------------------------
    /** Размножение скаляра по всем компонентам вектора: {@code a}=скаляр. */
    public static final int SPLAT = 70;
    /** Извлечение компоненты: {@code a}=вектор, {@code b}=индекс компоненты (непосредственно). */
    public static final int EXTRACT = 71;
    /** Подстановка компоненты: {@code a}=вектор, {@code b}=индекс, {@code c}=скаляр. */
    public static final int INSERT = 72;
    /** Перестановка компонент: {@code a}=вектор, {@code b}=смещение списка индексов, {@code c}=длина. */
    public static final int SHUFFLE = 73;
    /** Сборка вектора: {@code a}=смещение списка скаляров, {@code b}=длина. */
    public static final int BUILD = 74;

    // ---- struct ----------------------------------------------------------------------------------
    /** Сборка struct: {@code a}=смещение списка значений полей, {@code b}=длина. */
    public static final int STRUCT_BUILD = 80;
    /** Чтение поля значения: {@code a}=struct, {@code b}=индекс поля. */
    public static final int FIELD_GET = 81;
    /** Новое значение struct с изменённым полем: {@code a}=struct, {@code b}=индекс поля, {@code c}=значение. */
    public static final int FIELD_SET = 82;

    // ---- вызовы ----------------------------------------------------------------------------------
    /** Вызов функции модуля: {@code a}=индекс функции, {@code b}=смещение списка аргументов, {@code c}=длина. */
    public static final int CALL = 90;
    /** Вызов builtin: {@code a}=id builtin, {@code b}=смещение списка аргументов, {@code c}=длина. */
    public static final int INTRINSIC = 91;

    // ---- стейтменты и control flow ---------------------------------------------------------------
    /** Вычисление выражения ради эффекта: {@code a}=выражение. Стейтмент. */
    public static final int EXPR_STMT = 100;
    /** Барьер рабочей группы: {@code a}=флаги (непосредственно). Стейтмент. */
    public static final int BARRIER = 101;
    /** Блок: {@code a}=смещение списка стейтментов, {@code b}=длина. Вводит область видимости. */
    public static final int BLOCK = 102;
    /** Условие: {@code a}=cond, {@code b}=then, {@code c}=else либо {@code -1}. */
    public static final int IF = 103;
    /** Цикл с предусловием: {@code a}=cond, {@code b}=тело, {@code c}=update либо {@code -1}. {@code continue} ведёт в update. */
    public static final int LOOP = 104;
    /** Цикл с постусловием: {@code a}=тело, {@code b}=cond. {@code continue} ведёт в проверку cond. */
    public static final int DO_WHILE = 105;
    /** Switch как в C (с проваливанием): {@code a}=ключ, {@code b}=смещение списка троек, {@code c}=число case. */
    public static final int SWITCH = 106;
    /** Выход из ближайшего цикла или switch. */
    public static final int BREAK = 107;
    /** Переход к следующей итерации ближайшего цикла. */
    public static final int CONTINUE = 108;
    /** Возврат из функции: {@code a}=значение либо {@code -1}. */
    public static final int RETURN = 109;

    /** Верхняя граница (исключительно) допустимых опкодов; таблица имён имеет такой размер. */
    public static final int LIMIT = 110;

    private static final String[] NAMES = new String[LIMIT];
    private static final boolean[] STATEMENT = new boolean[LIMIT];

    static {
        name(CONST_INT, "const_int", false);
        name(CONST_FLOAT, "const_float", false);
        name(CONST_BOOL, "const_bool", false);
        name(SPEC_CONST, "spec_const", false);
        name(PARAM, "param", false);
        name(ADD, "add", false);
        name(SUB, "sub", false);
        name(MUL, "mul", false);
        name(DIV, "div", false);
        name(REM, "rem", false);
        name(NEG, "neg", false);
        name(AND, "and", false);
        name(OR, "or", false);
        name(XOR, "xor", false);
        name(NOT, "not", false);
        name(SHL, "shl", false);
        name(SHR, "shr", false);
        name(USHR, "ushr", false);
        name(EQ, "eq", false);
        name(NE, "ne", false);
        name(LT, "lt", false);
        name(LE, "le", false);
        name(GT, "gt", false);
        name(GE, "ge", false);
        name(SELECT, "select", false);
        name(CONVERT, "convert", false);
        name(BITCAST, "bitcast", false);
        name(LOCAL_DECL, "local_decl", true);
        name(LOCAL_GET, "local_get", false);
        name(LOCAL_SET, "local_set", true);
        name(LOCAL_ADDR, "local_addr", false);
        name(ELEM_PTR, "elem_ptr", false);
        name(FIELD_PTR, "field_ptr", false);
        name(LOAD, "load", false);
        name(STORE, "store", true);
        name(ARRAY_LEN, "array_len", false);
        name(PRIVATE_ARRAY_DECL, "private_array_decl", true);
        name(LOCAL_ARRAY_DECL, "local_array_decl", true);
        name(SPLAT, "splat", false);
        name(EXTRACT, "extract", false);
        name(INSERT, "insert", false);
        name(SHUFFLE, "shuffle", false);
        name(BUILD, "build", false);
        name(STRUCT_BUILD, "struct_build", false);
        name(FIELD_GET, "field_get", false);
        name(FIELD_SET, "field_set", false);
        name(CALL, "call", false);
        name(INTRINSIC, "intrinsic", false);
        name(EXPR_STMT, "expr_stmt", true);
        name(BARRIER, "barrier", true);
        name(BLOCK, "block", true);
        name(IF, "if", true);
        name(LOOP, "loop", true);
        name(DO_WHILE, "do_while", true);
        name(SWITCH, "switch", true);
        name(BREAK, "break", true);
        name(CONTINUE, "continue", true);
        name(RETURN, "return", true);
    }

    private static void name(int op, String name, boolean statement) {
        NAMES[op] = name;
        STATEMENT[op] = statement;
    }

    /**
     * @param op код опкода
     * @return {@code true}, если код входит в каталог (то есть для него есть имя)
     */
    public static boolean isValid(int op) {
        return op > 0 && op < LIMIT && NAMES[op] != null;
    }

    /**
     * @param op код опкода
     * @return {@code true}, если опкод - стейтмент (результат {@code VOID}); {@code false} для выражений и неизвестных кодов
     */
    public static boolean isStatement(int op) {
        return op > 0 && op < LIMIT && STATEMENT[op];
    }

    /**
     * Имя опкода для дампов и диагностик.
     *
     * @param op код опкода
     * @return имя вида {@code "add"}, либо {@code "op#N"} для неизвестного кода
     */
    public static String nameOf(int op) {
        return isValid(op) ? NAMES[op] : "op#" + op;
    }

    /**
     * @param op код опкода
     * @return {@code true} для бинарных арифметических операций ({@code ADD..REM})
     */
    public static boolean isArithBinary(int op) {
        return op >= ADD && op <= REM;
    }

    /**
     * @param op код опкода
     * @return {@code true} для побитовых бинарных операций ({@code AND, OR, XOR})
     */
    public static boolean isBitwiseBinary(int op) {
        return op == AND || op == OR || op == XOR;
    }

    /**
     * @param op код опкода
     * @return {@code true} для сдвигов ({@code SHL, SHR, USHR})
     */
    public static boolean isShift(int op) {
        return op >= SHL && op <= USHR;
    }

    /**
     * @param op код опкода
     * @return {@code true} для сравнений ({@code EQ..GE})
     */
    public static boolean isCompare(int op) {
        return op >= EQ && op <= GE;
    }
}
