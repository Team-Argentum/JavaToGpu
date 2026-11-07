package com.sixik.gpu.ir;

/**
 * Идентификаторы и предикаты таблицы типов IR (REWORK_ARCHITECTURE.md, 5.1-5.5).
 *
 * <p>Скалярные типы - <b>фиксированные константы-id</b> {@code 0..11}; id {@code 12..15} зарезервированы
 * (например, под {@code F16}); динамические типы ({@code Vec}, {@code Ptr}, {@code Array}, {@code Struct})
 * получают id начиная с {@link #FIRST_DYNAMIC} в порядке создания. Знак целого - метка типа, а не отдельное
 * семейство правил: опкоды полиморфны, знак определяется типом операндов.</p>
 *
 * <p>Класс не хранит состояния: все предикаты - статические функции над id и таблицей типов модуля.</p>
 */
public final class Types {

    private Types() {
    }

    /** Тип отсутствия значения (результат стейтментов). */
    public static final int VOID = 0;
    /** Логический тип; только значение (нельзя в массиве, поле struct, параметре kernel). */
    public static final int BOOL = 1;
    /** Знаковое 8-битное целое; тип хранения (арифметики нет). */
    public static final int I8 = 2;
    /** Беззнаковое 8-битное целое; тип хранения. */
    public static final int U8 = 3;
    /** Знаковое 16-битное целое; тип хранения. */
    public static final int I16 = 4;
    /** Беззнаковое 16-битное целое; тип хранения. */
    public static final int U16 = 5;
    /** Знаковое 32-битное целое. */
    public static final int I32 = 6;
    /** Беззнаковое 32-битное целое. */
    public static final int U32 = 7;
    /** Знаковое 64-битное целое. */
    public static final int I64 = 8;
    /** Беззнаковое 64-битное целое. */
    public static final int U64 = 9;
    /** 32-битное число с плавающей точкой IEEE 754. */
    public static final int F32 = 10;
    /** 64-битное число с плавающей точкой IEEE 754 (требует возможность {@link Features#FP64}). */
    public static final int F64 = 11;

    /** Первый id динамического типа; id {@code 12..15} зарезервированы. */
    public static final int FIRST_DYNAMIC = 16;

    /** Вид типа: скаляр (фиксированные id). */
    public static final int KIND_SCALAR = 0;
    /** Вид типа: вектор {@code (скаляр, lanes)}; колонки: {@code a}=скаляр, {@code b}=lanes. */
    public static final int KIND_VEC = 1;
    /** Вид типа: указатель; {@code a}=address space, {@code b}=pointee. */
    public static final int KIND_PTR = 2;
    /** Вид типа: массив с неявной длиной; {@code a}=address space, {@code b}=элемент. */
    public static final int KIND_ARRAY = 3;
    /** Вид типа: структура; {@code a}=зарезервировано, {@code b}=смещение списка полей, {@code c}=число полей, {@code d}=align (0 = естественный). */
    public static final int KIND_STRUCT = 4;
    /** Вид типа: зарезервированная строка таблицы (id 12..15). */
    public static final int KIND_RESERVED = 5;
    /** Вид типа: зарезервировано под images (не v1). */
    public static final int KIND_IMAGE = 6;
    /** Вид типа: зарезервировано под samplers (не v1). */
    public static final int KIND_SAMPLER = 7;

    /** Допустимые значения числа компонент вектора. */
    private static final int[] LANES = {2, 3, 4, 8, 16};

    /**
     * Проверяет, что число компонент допустимо для вектора (правило {@code IR-T07}).
     *
     * @param lanes число компонент
     * @return {@code true}, если {@code lanes} входит в {@code {2,3,4,8,16}}
     */
    public static boolean isValidLanes(int lanes) {
        for (int l : LANES) {
            if (l == lanes) {
                return true;
            }
        }
        return false;
    }

    /**
     * @param t id типа
     * @return {@code true}, если {@code t} - один из фиксированных скалярных id ({@code BOOL..F64}, без {@code VOID})
     */
    public static boolean isScalar(int t) {
        return t >= BOOL && t <= F64;
    }

    /**
     * @param t id скалярного типа
     * @return {@code true} для целых типов всех ширин (знаковых и беззнаковых)
     */
    public static boolean isInteger(int t) {
        return t >= I8 && t <= U64;
    }

    /**
     * @param t id скалярного типа
     * @return {@code true} для {@code F32} и {@code F64}
     */
    public static boolean isFloat(int t) {
        return t == F32 || t == F64;
    }

    /**
     * @param t id скалярного типа
     * @return {@code true} для знаковых целых и вещественных (у вещественных знак есть всегда)
     */
    public static boolean isSigned(int t) {
        return t == I8 || t == I16 || t == I32 || t == I64 || isFloat(t);
    }

    /**
     * Целые 32/64 бит и вещественные: только на них определена арифметика (REWORK_ARCHITECTURE.md, 5.1).
     *
     * @param t id скалярного типа
     * @return {@code true}, если тип допускает арифметику
     */
    public static boolean isArith(int t) {
        return t == I32 || t == U32 || t == I64 || t == U64 || t == F32 || t == F64;
    }

    /**
     * Целые 32/64 бит: на них определены побитовые операции и сдвиги.
     *
     * @param t id скалярного типа
     * @return {@code true} для {@code I32, U32, I64, U64}
     */
    public static boolean isInt32or64(int t) {
        return t == I32 || t == U32 || t == I64 || t == U64;
    }

    /**
     * Размер скалярного типа в битах.
     *
     * @param t id скалярного типа
     * @return число бит, либо 0 для {@code VOID}, {@code BOOL} и не-скаляров
     */
    public static int bits(int t) {
        return switch (t) {
            case I8, U8 -> 8;
            case I16, U16 -> 16;
            case I32, U32, F32 -> 32;
            case I64, U64, F64 -> 64;
            default -> 0;
        };
    }

    /**
     * Вид типа.
     *
     * @param m модуль
     * @param t id типа
     * @return одна из констант {@code KIND_*}
     */
    public static int kind(IrModule m, int t) {
        return m.typeKind(t);
    }

    /**
     * @param m модуль
     * @param t id типа
     * @return {@code true}, если {@code t} - вектор
     */
    public static boolean isVector(IrModule m, int t) {
        return m.typeKind(t) == KIND_VEC;
    }

    /**
     * Скалярный тип компоненты: сам {@code t} для скаляра, элемент для вектора.
     *
     * @param m модуль
     * @param t id скаляра или вектора
     * @return id скалярного типа, либо {@code -1}, если {@code t} не скаляр и не вектор
     */
    public static int scalarOf(IrModule m, int t) {
        if (isScalar(t)) {
            return t;
        }
        if (isVector(m, t)) {
            return m.typeA(t);
        }
        return -1;
    }

    /**
     * Число компонент: 1 для скаляра.
     *
     * @param m модуль
     * @param t id скаляра или вектора
     * @return число компонент, либо {@code 0}, если {@code t} не скаляр и не вектор
     */
    public static int lanes(IrModule m, int t) {
        if (isScalar(t)) {
            return 1;
        }
        if (isVector(m, t)) {
            return m.typeB(t);
        }
        return 0;
    }

    /**
     * @param m модуль
     * @param t id типа
     * @return {@code true}, если {@code t} - указатель или массив (то есть тип с address space)
     */
    public static boolean hasAddressSpace(IrModule m, int t) {
        int k = m.typeKind(t);
        return k == KIND_PTR || k == KIND_ARRAY;
    }

    /**
     * Address space указателя или массива.
     *
     * @param m модуль
     * @param t id типа {@code Ptr} или {@code Array}
     * @return значение из {@link AddressSpace}
     */
    public static int space(IrModule m, int t) {
        return m.typeA(t);
    }

    /**
     * Тип элемента массива либо pointee указателя.
     *
     * @param m модуль
     * @param t id типа {@code Ptr} или {@code Array}
     * @return id типа элемента
     */
    public static int elem(IrModule m, int t) {
        return m.typeB(t);
    }
}
