package com.sixik.gpu.ir.interpreter;

import com.sixik.gpu.ir.HostObjects;
import com.sixik.gpu.ir.IrModule;
import com.sixik.gpu.ir.Types;

/**
 * Сопоставление хостовых Java-значений и канонического представления интерпретатора ({@link Alu}).
 *
 * <p>Массивы копируются в {@code long[]} канонических значений перед запуском и записываются обратно после
 * успешного завершения; параметры-скаляры принимаются строго по классу обёртки, чтобы ошибка в тесте
 * ({@code Integer} вместо {@code Long}) не маскировалась молчаливым преобразованием.</p>
 *
 * <table>
 *   <caption>Соответствие типов</caption>
 *   <tr><th>Тип IR</th><th>Хостовой массив</th><th>Хостовый скаляр</th></tr>
 *   <tr><td>I8</td><td>{@code byte[]}</td><td>-</td></tr>
 *   <tr><td>U8</td><td>{@code byte[]}, {@code boolean[]}</td><td>-</td></tr>
 *   <tr><td>I16</td><td>{@code short[]}</td><td>-</td></tr>
 *   <tr><td>U16</td><td>{@code char[]}, {@code short[]}</td><td>-</td></tr>
 *   <tr><td>I32, U32</td><td>{@code int[]}</td><td>{@link Integer}</td></tr>
 *   <tr><td>I64, U64</td><td>{@code long[]}</td><td>{@link Long}</td></tr>
 *   <tr><td>F32</td><td>{@code float[]}</td><td>{@link Float}</td></tr>
 *   <tr><td>F64</td><td>{@code double[]}</td><td>{@link Double}</td></tr>
 * </table>
 */
final class Host {

    private Host() {
    }

    /**
     * @param elem тип элемента массива IR
     * @param a    хостовый объект
     * @return {@code true}, если {@code a} - массив подходящего класса
     */
    static boolean arrayMatches(int elem, Object a) {
        return switch (elem) {
            case Types.I8 -> a instanceof byte[];
            case Types.U8 -> a instanceof byte[] || a instanceof boolean[];
            case Types.I16 -> a instanceof short[];
            case Types.U16 -> a instanceof char[] || a instanceof short[];
            case Types.I32, Types.U32 -> a instanceof int[];
            case Types.I64, Types.U64 -> a instanceof long[];
            case Types.F32 -> a instanceof float[];
            case Types.F64 -> a instanceof double[];
            default -> false;
        };
    }

    /**
     * Принимает ли параметр-массив с элементом {@code elem} этот объект; для вектора и struct - массив объектов
     * ({@code Object[]}) класса, поля которого соответствуют типу ({@link HostObjects#mismatch}).
     *
     * @param m    модуль
     * @param elem тип элемента массива IR
     * @param a    хостовый объект
     * @return {@code true}, если подходит
     */
    static boolean arrayMatches(IrModule m, int elem, Object a) {
        if (!HostObjects.isAggregate(m, elem)) {
            return arrayMatches(elem, a);
        }
        return a instanceof Object[] arr && HostObjects.mismatch(m, elem, arr.getClass().getComponentType()).isEmpty();
    }

    /**
     * @param m    модуль
     * @param elem тип элемента
     * @param a    переданный объект (для пояснения причины)
     * @return описание ожидаемого массива для сообщения об ошибке
     */
    static String arrayName(IrModule m, int elem, Object a) {
        if (!HostObjects.isAggregate(m, elem)) {
            return arrayName(elem);
        }
        if (a instanceof Object[] arr) {
            return "массив объектов, подходящий типу элемента: " + HostObjects.mismatch(m, elem, arr.getClass().getComponentType());
        }
        return "массив объектов (Object[]) с " + HostObjects.slots(m, elem) + " скалярными листьями в элементе";
    }

    /**
     * Копирует массив хоста в буфер: скаляры - по ячейке на элемент, вектор и struct - по {@code slots} ячеек.
     *
     * @param m    модуль
     * @param elem тип элемента (массив уже проверен {@link #arrayMatches(IrModule, int, Object)})
     * @param a    хостовый массив
     * @return буфер
     * @throws IllegalArgumentException если элемент массива объектов равен {@code null}
     */
    static long[] toBuffer(IrModule m, int elem, Object a) {
        if (!HostObjects.isAggregate(m, elem)) {
            return toBuffer(elem, a);
        }
        Object[] arr = (Object[]) a;
        int k = HostObjects.slots(m, elem);
        long[] buf = new long[arr.length * k];
        for (int i = 0; i < arr.length; i++) {
            try {
                HostObjects.flatten(m, elem, arr[i], buf, i * k);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("элемент " + i + ": " + e.getMessage());
            }
        }
        return buf;
    }

    /**
     * Записывает буфер обратно в массив хоста: struct обновляются на месте, векторы заменяются новыми объектами.
     *
     * @param m    модуль
     * @param elem тип элемента
     * @param buf  буфер
     * @param a    хостовый массив
     */
    static void fromBuffer(IrModule m, int elem, long[] buf, Object a) {
        if (!HostObjects.isAggregate(m, elem)) {
            fromBuffer(buf, a);
            return;
        }
        Object[] arr = (Object[]) a;
        int k = HostObjects.slots(m, elem);
        Class<?> cls = arr.getClass().getComponentType();
        for (int i = 0; i < arr.length; i++) {
            arr[i] = HostObjects.rebuild(m, elem, cls, arr[i], buf, i * k);
        }
    }

    /**
     * Описание ожидаемого хостового массива для сообщений об ошибках.
     *
     * @param elem тип элемента
     * @return имя Java-типа
     */
    static String arrayName(int elem) {
        return switch (elem) {
            case Types.I8 -> "byte[]";
            case Types.U8 -> "byte[] или boolean[]";
            case Types.I16 -> "short[]";
            case Types.U16 -> "char[] или short[]";
            case Types.I32, Types.U32 -> "int[]";
            case Types.I64, Types.U64 -> "long[]";
            case Types.F32 -> "float[]";
            case Types.F64 -> "double[]";
            default -> "?";
        };
    }

    /**
     * Копирует хостовый массив в буфер канонических значений.
     *
     * @param elem тип элемента (массив уже проверен {@link #arrayMatches})
     * @param a    хостовый массив
     * @return буфер той же длины
     */
    static long[] toBuffer(int elem, Object a) {
        if (a instanceof int[] x) {
            long[] r = new long[x.length];
            for (int i = 0; i < r.length; i++) {
                r[i] = x[i];
            }
            return r;
        }
        if (a instanceof float[] x) {
            long[] r = new long[x.length];
            for (int i = 0; i < r.length; i++) {
                r[i] = Float.floatToRawIntBits(x[i]);
            }
            return r;
        }
        if (a instanceof long[] x) {
            return x.clone();
        }
        if (a instanceof double[] x) {
            long[] r = new long[x.length];
            for (int i = 0; i < r.length; i++) {
                r[i] = Double.doubleToRawLongBits(x[i]);
            }
            return r;
        }
        if (a instanceof byte[] x) {
            long[] r = new long[x.length];
            for (int i = 0; i < r.length; i++) {
                r[i] = elem == Types.U8 ? x[i] & 0xFF : x[i];
            }
            return r;
        }
        if (a instanceof short[] x) {
            long[] r = new long[x.length];
            for (int i = 0; i < r.length; i++) {
                r[i] = elem == Types.U16 ? x[i] & 0xFFFF : x[i];
            }
            return r;
        }
        if (a instanceof char[] x) {
            long[] r = new long[x.length];
            for (int i = 0; i < r.length; i++) {
                r[i] = x[i];
            }
            return r;
        }
        boolean[] x = (boolean[]) a;
        long[] r = new long[x.length];
        for (int i = 0; i < r.length; i++) {
            r[i] = x[i] ? 1 : 0;
        }
        return r;
    }

    /**
     * Записывает буфер обратно в хостовый массив.
     *
     * @param buf буфер канонических значений
     * @param a   хостовый массив той же длины
     */
    static void fromBuffer(long[] buf, Object a) {
        if (a instanceof int[] x) {
            for (int i = 0; i < x.length; i++) {
                x[i] = (int) buf[i];
            }
        } else if (a instanceof float[] x) {
            for (int i = 0; i < x.length; i++) {
                x[i] = Float.intBitsToFloat((int) buf[i]);
            }
        } else if (a instanceof long[] x) {
            System.arraycopy(buf, 0, x, 0, x.length);
        } else if (a instanceof double[] x) {
            for (int i = 0; i < x.length; i++) {
                x[i] = Double.longBitsToDouble(buf[i]);
            }
        } else if (a instanceof byte[] x) {
            for (int i = 0; i < x.length; i++) {
                x[i] = (byte) buf[i];
            }
        } else if (a instanceof short[] x) {
            for (int i = 0; i < x.length; i++) {
                x[i] = (short) buf[i];
            }
        } else if (a instanceof char[] x) {
            for (int i = 0; i < x.length; i++) {
                x[i] = (char) buf[i];
            }
        } else {
            boolean[] x = (boolean[]) a;
            for (int i = 0; i < x.length; i++) {
                x[i] = buf[i] != 0;
            }
        }
    }

    /**
     * @param t скалярный тип параметра
     * @param v хостовое значение
     * @return {@code true}, если класс значения соответствует типу
     */
    static boolean scalarMatches(int t, Object v) {
        return switch (t) {
            case Types.I32, Types.U32 -> v instanceof Integer;
            case Types.I64, Types.U64 -> v instanceof Long;
            case Types.F32 -> v instanceof Float;
            case Types.F64 -> v instanceof Double;
            default -> false;
        };
    }

    /**
     * @param t скалярный тип параметра
     * @return имя ожидаемого класса обёртки
     */
    static String scalarName(int t) {
        return switch (t) {
            case Types.I32, Types.U32 -> "Integer";
            case Types.I64, Types.U64 -> "Long";
            case Types.F32 -> "Float";
            case Types.F64 -> "Double";
            default -> "?";
        };
    }

    /**
     * Переводит проверенное хостовое значение в каноническое.
     *
     * @param t скалярный тип параметра
     * @param v значение, прошедшее {@link #scalarMatches}
     * @return каноническое значение
     */
    static long toScalar(int t, Object v) {
        return switch (t) {
            case Types.I32, Types.U32 -> (Integer) v;
            case Types.I64, Types.U64 -> (Long) v;
            case Types.F32 -> Float.floatToRawIntBits((Float) v);
            default -> Double.doubleToRawLongBits((Double) v);
        };
    }
}
