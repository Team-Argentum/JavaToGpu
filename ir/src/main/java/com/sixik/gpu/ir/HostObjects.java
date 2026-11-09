package com.sixik.gpu.ir;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/**
 * Соответствие значений-агрегатов IR (вектор, struct) Java-объектам хоста (REWORK_ARCHITECTURE.md, 5.20, решение O3).
 *
 * <p>Агрегат раскладывается на <b>листья</b> - скаляры в порядке объявления: вектор из {@code n} компонент даёт
 * {@code n} листьев, struct - листья своих полей подряд (вложенные агрегаты разворачиваются). Лист представлен
 * {@code long} в каноническом виде (тот же, что у {@code ir-interpreter}): {@code BOOL} 0/1; {@code I8/U8/I16/U16} -
 * число (со знаком / без знака); {@code I32/U32/F32} - биты, расширенные знаком до 64; {@code I64/U64/F64} - сырые биты.
 * Размер значения в байтах и смещения листьев в памяти устройства даёт {@link AbiLayout} ({@link #leafOffsets}).</p>
 *
 * <p>Java-класс сопоставляется типу <b>по порядку объявления</b> открытых нестатических полей: поле {@code i} класса -
 * компонента (поле) {@code i} типа IR. Для векторов ({@code Float4}: {@code x,y,z,w}; {@code Float8}: {@code s0..s7})
 * это генерируемые классы {@code api}; для {@code @Struct} - классы пользователя. Типы полей Java проверяются:
 * {@code int/long/float/double} (и {@code byte/short/char/boolean} для типов хранения) должны соответствовать типу листа.
 * Получение полей идёт через отражение, но один раз на класс (кэш {@link ClassValue}); без {@code api} в зависимостях.</p>
 *
 * <p>Объекты хоста должны быть непустыми: {@code null} в массиве структур - ошибка, а не нули (на JVM он дал бы
 * {@code NullPointerException}).</p>
 */
public final class HostObjects {

    private HostObjects() {
    }

    // ---- форма типа -------------------------------------------------------------------------------

    /**
     * @param m модуль
     * @param t тип
     * @return {@code true} для вектора и struct
     */
    public static boolean isAggregate(IrModule m, int t) {
        if (t < 0 || t >= m.typeCount()) {
            return false;
        }
        int k = m.typeKind(t);
        return k == Types.KIND_VEC || k == Types.KIND_STRUCT;
    }

    /**
     * Число скалярных листьев значения.
     *
     * @param m модуль
     * @param t тип: скаляр (1), вектор, struct
     * @return число листьев; для остальных типов (массив, указатель, {@code VOID}) - 0
     */
    public static int slots(IrModule m, int t) {
        if (Types.isScalar(t)) {
            return 1;
        }
        if (t < 0 || t >= m.typeCount()) {
            return 0;
        }
        switch (m.typeKind(t)) {
            case Types.KIND_VEC:
                return m.typeB(t);
            case Types.KIND_STRUCT: {
                int n = 0;
                for (int i = 0; i < m.structFieldCount(t); i++) {
                    n += slots(m, m.structFieldType(t, i));
                }
                return n;
            }
            default:
                return 0;
        }
    }

    /**
     * Смещение в листьях, с которого начинается поле struct.
     *
     * @param m          модуль
     * @param structType struct-тип
     * @param field      индекс поля
     * @return номер первого листа поля внутри значения struct
     */
    public static int slotOffset(IrModule m, int structType, int field) {
        int n = 0;
        for (int i = 0; i < field; i++) {
            n += slots(m, m.structFieldType(structType, i));
        }
        return n;
    }

    /**
     * Скалярные типы листьев в порядке раскладки.
     *
     * @param m модуль
     * @param t тип агрегата
     * @return типы листьев
     */
    public static int[] leafTypes(IrModule m, int t) {
        int[] out = new int[slots(m, t)];
        fillTypes(m, t, out, new int[1]);
        return out;
    }

    private static void fillTypes(IrModule m, int t, int[] out, int[] pos) {
        if (Types.isScalar(t)) {
            out[pos[0]++] = t;
        } else if (m.typeKind(t) == Types.KIND_VEC) {
            for (int i = 0; i < m.typeB(t); i++) {
                out[pos[0]++] = m.typeA(t);
            }
        } else {
            for (int i = 0; i < m.structFieldCount(t); i++) {
                fillTypes(m, m.structFieldType(t, i), out, pos);
            }
        }
    }

    /**
     * Смещения листьев в памяти устройства (раскладка {@link AbiLayout}).
     *
     * @param m модуль
     * @param t тип агрегата
     * @return смещение каждого листа от начала значения, байт
     */
    public static int[] leafOffsets(IrModule m, int t) {
        int[] out = new int[slots(m, t)];
        fillOffsets(m, t, 0, out, new int[1]);
        return out;
    }

    private static void fillOffsets(IrModule m, int t, int base, int[] out, int[] pos) {
        if (Types.isScalar(t)) {
            out[pos[0]++] = base;
        } else if (m.typeKind(t) == Types.KIND_VEC) {
            int size = Types.bits(m.typeA(t)) / 8;
            for (int i = 0; i < m.typeB(t); i++) {
                out[pos[0]++] = base + i * size;
            }
        } else {
            for (int i = 0; i < m.structFieldCount(t); i++) {
                fillOffsets(m, m.structFieldType(t, i), base + AbiLayout.fieldOffset(m, t, i), out, pos);
            }
        }
    }

    // ---- канонические значения --------------------------------------------------------------------

    /**
     * Приводит биты к каноническому виду типа.
     *
     * @param type скалярный тип
     * @param bits младшие биты значения (для 32-битных - младшие 32)
     * @return каноническое значение
     */
    public static long canon(int type, long bits) {
        return switch (type) {
            case Types.BOOL -> bits != 0 ? 1 : 0;
            case Types.I8 -> (byte) bits;
            case Types.U8 -> bits & 0xFFL;
            case Types.I16 -> (short) bits;
            case Types.U16 -> bits & 0xFFFFL;
            case Types.I32, Types.U32, Types.F32 -> (int) bits;
            default -> bits;
        };
    }

    // ---- соответствие классов ---------------------------------------------------------------------

    /** Открытые нестатические поля класса в порядке объявления. */
    private static final ClassValue<Field[]> FIELDS = new ClassValue<>() {
        @Override
        protected Field[] computeValue(Class<?> type) {
            List<Field> out = new ArrayList<>();
            for (Field f : type.getDeclaredFields()) {
                int mod = f.getModifiers();
                if (Modifier.isPublic(mod) && !Modifier.isStatic(mod)) {
                    out.add(f);
                }
            }
            return out.toArray(new Field[0]);
        }
    };

    /**
     * Проверяет, что Java-класс описывает значение типа IR.
     *
     * @param m   модуль
     * @param t   тип агрегата
     * @param cls класс элемента массива или значения
     * @return пустая строка, если класс подходит; иначе причина
     */
    public static String mismatch(IrModule m, int t, Class<?> cls) {
        Field[] fields = FIELDS.get(cls);
        if (m.typeKind(t) == Types.KIND_VEC) {
            if (fields.length != m.typeB(t)) {
                return "class " + cls.getName() + " has " + fields.length + " public fields, while the vector has "
                        + m.typeB(t) + " components";
            }
            for (int i = 0; i < fields.length; i++) {
                String why = scalarMismatch(m.typeA(t), fields[i]);
                if (!why.isEmpty()) {
                    return why;
                }
            }
            return "";
        }
        if (fields.length != m.structFieldCount(t)) {
            return "class " + cls.getName() + " has " + fields.length + " public fields, while the struct has "
                    + m.structFieldCount(t);
        }
        for (int i = 0; i < fields.length; i++) {
            int ft = m.structFieldType(t, i);
            String why = Types.isScalar(ft) ? scalarMismatch(ft, fields[i]) : mismatch(m, ft, fields[i].getType());
            if (!why.isEmpty()) {
                return why;
            }
        }
        return "";
    }

    private static String scalarMismatch(int irType, Field f) {
        Class<?> c = f.getType();
        boolean ok = switch (irType) {
            case Types.BOOL, Types.U8 -> c == boolean.class || c == byte.class;
            case Types.I8 -> c == byte.class;
            case Types.I16 -> c == short.class;
            case Types.U16 -> c == char.class || c == short.class;
            case Types.I32, Types.U32 -> c == int.class;
            case Types.I64, Types.U64 -> c == long.class;
            case Types.F32 -> c == float.class;
            case Types.F64 -> c == double.class;
            default -> false;
        };
        return ok ? "" : "field " + f.getDeclaringClass().getSimpleName() + "." + f.getName() + " of type " + c.getSimpleName()
                + " does not match IR scalar t" + irType;
    }

    // ---- чтение и запись --------------------------------------------------------------------------

    /**
     * Раскладывает объект хоста на канонические листья.
     *
     * @param m     модуль
     * @param t     тип агрегата
     * @param value объект
     * @param out   приёмник
     * @param off   с какого индекса писать; нужно {@code slots(m, t)} ячеек
     * @throws IllegalArgumentException если объект {@code null}, класс не подходит или значение вложенного поля {@code null}
     */
    public static void flatten(IrModule m, int t, Object value, long[] out, int off) {
        read(m, t, value, out, new int[]{off});
    }

    private static void read(IrModule m, int t, Object value, long[] out, int[] pos) {
        if (value == null) {
            throw new IllegalArgumentException("aggregate value is null");
        }
        Field[] fields = FIELDS.get(value.getClass());
        try {
            if (m.typeKind(t) == Types.KIND_VEC) {
                if (fields.length != m.typeB(t)) {
                    throw new IllegalArgumentException(mismatch(m, t, value.getClass()));
                }
                for (Field f : fields) {
                    out[pos[0]++] = leaf(m.typeA(t), f, value);
                }
                return;
            }
            if (fields.length != m.structFieldCount(t)) {
                throw new IllegalArgumentException(mismatch(m, t, value.getClass()));
            }
            for (int i = 0; i < fields.length; i++) {
                int ft = m.structFieldType(t, i);
                if (Types.isScalar(ft)) {
                    out[pos[0]++] = leaf(ft, fields[i], value);
                } else {
                    read(m, ft, fields[i].get(value), out, pos);
                }
            }
        } catch (IllegalAccessException e) {
            throw new IllegalArgumentException("field is inaccessible: " + e.getMessage());
        }
    }

    private static long leaf(int irType, Field f, Object owner) throws IllegalAccessException {
        Class<?> c = f.getType();
        long bits;
        if (c == int.class) {
            bits = f.getInt(owner);
        } else if (c == long.class) {
            bits = f.getLong(owner);
        } else if (c == float.class) {
            bits = Float.floatToRawIntBits(f.getFloat(owner));
        } else if (c == double.class) {
            bits = Double.doubleToRawLongBits(f.getDouble(owner));
        } else if (c == byte.class) {
            bits = f.getByte(owner);
        } else if (c == short.class) {
            bits = f.getShort(owner);
        } else if (c == char.class) {
            bits = f.getChar(owner);
        } else if (c == boolean.class) {
            bits = f.getBoolean(owner) ? 1 : 0;
        } else {
            throw new IllegalArgumentException("field " + f.getName() + " has a non-scalar type " + c.getName());
        }
        return canon(irType, bits);
    }

    /**
     * Создаёт объект хоста из листьев либо обновляет существующий: struct изменяется на месте (идентичность объекта
     * сохраняется, как у массива, записанного ядром), вектор неизменяем и создаётся заново.
     *
     * @param m        модуль
     * @param t        тип агрегата
     * @param cls      класс объекта (для вложенных - тип поля)
     * @param existing существующий объект того же класса либо {@code null}
     * @param in       листья
     * @param off      с какого индекса читать
     * @return объект с новым значением
     * @throws IllegalArgumentException если класс не подходит или его нельзя создать (нужен открытый конструктор: у
     *                                  vector - по всем компонентам, у struct - без аргументов)
     */
    public static Object rebuild(IrModule m, int t, Class<?> cls, Object existing, long[] in, int off) {
        return write(m, t, cls, existing, in, new int[]{off});
    }

    private static Object write(IrModule m, int t, Class<?> cls, Object existing, long[] in, int[] pos) {
        Field[] fields = FIELDS.get(cls);
        try {
            if (m.typeKind(t) == Types.KIND_VEC) {
                Class<?>[] types = new Class<?>[fields.length];
                Object[] args = new Object[fields.length];
                for (int i = 0; i < fields.length; i++) {
                    types[i] = fields[i].getType();
                    args[i] = box(m.typeA(t), types[i], in[pos[0]++]);
                }
                Constructor<?> ctor = cls.getConstructor(types);
                return ctor.newInstance(args);
            }
            Object target = existing != null ? existing : cls.getConstructor().newInstance();
            for (int i = 0; i < fields.length; i++) {
                int ft = m.structFieldType(t, i);
                if (Types.isScalar(ft)) {
                    store(fields[i], target, in[pos[0]++]);
                } else {
                    Object old = fields[i].get(target);
                    fields[i].set(target, write(m, ft, fields[i].getType(), old, in, pos));
                }
            }
            return target;
        } catch (ReflectiveOperationException e) {
            throw new IllegalArgumentException("failed to create " + cls.getName() + ": " + e);
        }
    }

    private static Object box(int irType, Class<?> c, long v) {
        if (c == int.class) {
            return (int) v;
        } else if (c == long.class) {
            return v;
        } else if (c == float.class) {
            return Float.intBitsToFloat((int) v);
        } else if (c == double.class) {
            return Double.longBitsToDouble(v);
        } else if (c == byte.class) {
            return (byte) v;
        } else if (c == short.class) {
            return (short) v;
        } else if (c == char.class) {
            return (char) v;
        } else if (c == boolean.class) {
            return v != 0;
        }
        throw new IllegalArgumentException("not a scalar type " + c.getName() + " for t" + irType);
    }

    private static void store(Field f, Object owner, long v) throws IllegalAccessException {
        Class<?> c = f.getType();
        if (c == int.class) {
            f.setInt(owner, (int) v);
        } else if (c == long.class) {
            f.setLong(owner, v);
        } else if (c == float.class) {
            f.setFloat(owner, Float.intBitsToFloat((int) v));
        } else if (c == double.class) {
            f.setDouble(owner, Double.longBitsToDouble(v));
        } else if (c == byte.class) {
            f.setByte(owner, (byte) v);
        } else if (c == short.class) {
            f.setShort(owner, (short) v);
        } else if (c == char.class) {
            f.setChar(owner, (char) v);
        } else if (c == boolean.class) {
            f.setBoolean(owner, v != 0);
        }
    }
}
