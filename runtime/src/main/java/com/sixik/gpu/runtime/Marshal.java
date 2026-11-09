package com.sixik.gpu.runtime;

import com.sixik.gpu.ir.AbiLayout;
import com.sixik.gpu.ir.HostObjects;
import com.sixik.gpu.ir.IrModule;
import com.sixik.gpu.ir.Types;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

/**
 * Соответствие значений хоста типам IR и упаковка массивов в память устройства (то же соответствие, что у
 * {@code ir-interpreter}, чтобы JVM, интерпретатор и устройство принимали одинаковые аргументы).
 *
 * <p>Массив: {@code I8 - byte[]}; {@code U8 - byte[]} или {@code boolean[]} (один байт на элемент, {@code 0/1});
 * {@code I16 - short[]}; {@code U16 - char[]} или {@code short[]}; {@code I32/U32 - int[]}; {@code I64/U64 - long[]};
 * {@code F32 - float[]}; {@code F64 - double[]}. Скаляр: строго {@code Integer / Long / Float / Double}. Порядок
 * байт - родной (little-endian на всех поддерживаемых платформах), раскладка плотная: элемент занимает ровно свой
 * размер (REWORK_ARCHITECTURE.md, 5.3).</p>
 */
final class Marshal {

    private Marshal() {
    }

    /**
     * Тип элемента IR, естественный для Java-массива.
     *
     * @param array Java-массив примитивов
     * @return {@code I8, U8 (boolean[]), I16, U16 (char[]), I32, I64, F32, F64} либо {@code -1}, если это не поддержанный массив
     */
    static int elementTypeOf(Object array) {
        return switch (array) {
            case byte[] x -> Types.I8;
            case boolean[] x -> Types.U8;
            case short[] x -> Types.I16;
            case char[] x -> Types.U16;
            case int[] x -> Types.I32;
            case long[] x -> Types.I64;
            case float[] x -> Types.F32;
            case double[] x -> Types.F64;
            case null, default -> -1;
        };
    }

    /**
     * @param array поддержанный массив
     * @return число элементов
     */
    static int length(Object array) {
        return java.lang.reflect.Array.getLength(array);
    }

    /**
     * @param elemType тип элемента IR
     * @return размер элемента в байтах
     */
    static int elementSize(int elemType) {
        return Types.bits(elemType) / 8;
    }

    /**
     * Принимает ли параметр-массив с элементом {@code elemType} этот Java-массив.
     *
     * @param elemType тип элемента параметра
     * @param array    значение
     * @return {@code true}, если соответствие допустимо
     */
    static boolean accepts(int elemType, Object array) {
        return switch (elemType) {
            case Types.I8 -> array instanceof byte[];
            case Types.U8 -> array instanceof byte[] || array instanceof boolean[];
            case Types.I16 -> array instanceof short[];
            case Types.U16 -> array instanceof char[] || array instanceof short[];
            case Types.I32, Types.U32 -> array instanceof int[];
            case Types.I64, Types.U64 -> array instanceof long[];
            case Types.F32 -> array instanceof float[];
            case Types.F64 -> array instanceof double[];
            default -> false;
        };
    }

    /**
     * Совместимы ли тип элемента буфера и параметра: одинаковый размер и одинаковая «вещественность»
     * ({@code int[]}-буфер подходит параметрам {@code I32} и {@code U32}, но не {@code F32}).
     *
     * @param paramElem  элемент параметра
     * @param bufferElem элемент буфера
     * @return {@code true}, если буфер можно привязать к параметру
     */
    static boolean compatible(int paramElem, int bufferElem) {
        return Types.bits(paramElem) == Types.bits(bufferElem) && Types.isFloat(paramElem) == Types.isFloat(bufferElem);
    }

    /**
     * @param type  тип скалярного параметра
     * @param value значение
     * @return {@code true}, если значение допустимо
     */
    static boolean scalarMatches(int type, Object value) {
        return switch (type) {
            case Types.I32, Types.U32 -> value instanceof Integer;
            case Types.I64, Types.U64 -> value instanceof Long;
            case Types.F32 -> value instanceof Float;
            case Types.F64 -> value instanceof Double;
            default -> false;
        };
    }

    /**
     * @param value допустимый скаляр
     * @return его биты (для 4-байтных - младшие 32)
     */
    static long scalarBits(Object value) {
        return switch (value) {
            case Integer x -> x;
            case Long x -> x;
            case Float x -> Float.floatToRawIntBits(x);
            case Double x -> Double.doubleToRawLongBits(x);
            default -> throw new IllegalArgumentException("not a scalar: " + value);
        };
    }

    /**
     * @param type тип скаляра
     * @return размер в байтах (4 или 8)
     */
    static int scalarSize(int type) {
        return Types.bits(type) / 8;
    }

    /**
     * @param array поддержанный массив
     * @return размер содержимого в байтах
     */
    static long byteSize(Object array) {
        return (long) length(array) * elementSize(elementTypeOf(array));
    }

    /**
     * Копирует массив в новую нативную память.
     *
     * @param arena владелец памяти
     * @param array источник
     * @return сегмент ровно {@code byteSize(array)} байт (для пустого массива - 8 байт запаса не раскрывается)
     */
    static MemorySegment toNative(Arena arena, Object array) {
        MemorySegment seg = arena.allocate(Math.max(byteSize(array), 8), 8);
        copyToNative(array, seg);
        return seg;
    }

    /**
     * Копирует массив в уже выделенную нативную память (для повторных запусков без новых аллокаций).
     *
     * @param array источник
     * @param seg   приёмник не короче {@code byteSize(array)} байт
     */
    static void copyToNative(Object array, MemorySegment seg) {
        int n = length(array);
        switch (array) {
            case byte[] x -> MemorySegment.copy(x, 0, seg, ValueLayout.JAVA_BYTE, 0, n);
            case boolean[] x -> {
                for (int i = 0; i < n; i++) {
                    seg.set(ValueLayout.JAVA_BYTE, i, (byte) (x[i] ? 1 : 0));
                }
            }
            case short[] x -> MemorySegment.copy(x, 0, seg, ValueLayout.JAVA_SHORT, 0, n);
            case char[] x -> MemorySegment.copy(x, 0, seg, ValueLayout.JAVA_CHAR, 0, n);
            case int[] x -> MemorySegment.copy(x, 0, seg, ValueLayout.JAVA_INT, 0, n);
            case long[] x -> MemorySegment.copy(x, 0, seg, ValueLayout.JAVA_LONG, 0, n);
            case float[] x -> MemorySegment.copy(x, 0, seg, ValueLayout.JAVA_FLOAT, 0, n);
            case double[] x -> MemorySegment.copy(x, 0, seg, ValueLayout.JAVA_DOUBLE, 0, n);
            default -> throw new IllegalArgumentException("unsupported array " + array.getClass());
        }
    }

    /**
     * Копирует нативную память в массив.
     *
     * @param seg   источник (не короче содержимого массива)
     * @param array приёмник
     */
    static void fromNative(MemorySegment seg, Object array) {
        int n = length(array);
        switch (array) {
            case byte[] x -> MemorySegment.copy(seg, ValueLayout.JAVA_BYTE, 0, x, 0, n);
            case boolean[] x -> {
                for (int i = 0; i < n; i++) {
                    x[i] = seg.get(ValueLayout.JAVA_BYTE, i) != 0;
                }
            }
            case short[] x -> MemorySegment.copy(seg, ValueLayout.JAVA_SHORT, 0, x, 0, n);
            case char[] x -> MemorySegment.copy(seg, ValueLayout.JAVA_CHAR, 0, x, 0, n);
            case int[] x -> MemorySegment.copy(seg, ValueLayout.JAVA_INT, 0, x, 0, n);
            case long[] x -> MemorySegment.copy(seg, ValueLayout.JAVA_LONG, 0, x, 0, n);
            case float[] x -> MemorySegment.copy(seg, ValueLayout.JAVA_FLOAT, 0, x, 0, n);
            case double[] x -> MemorySegment.copy(seg, ValueLayout.JAVA_DOUBLE, 0, x, 0, n);
            default -> throw new IllegalArgumentException("unsupported array " + array.getClass());
        }
    }

    // ---- объекты: векторы и struct ------------------------------------------------------------------

    /**
     * Принимает ли параметр-массив с элементом-агрегатом этот объект: массив объектов ({@code Object[]}) класса, поля
     * которого соответствуют типу ({@link HostObjects#mismatch}).
     *
     * @param m    модуль
     * @param elem тип элемента (вектор или struct)
     * @param a    значение
     * @return пустая строка, если подходит; иначе причина
     */
    static String objectArrayProblem(IrModule m, int elem, Object a) {
        if (!(a instanceof Object[] arr)) {
            return "an array of objects (Object[]) is expected, passed " + describe(a);
        }
        String why = HostObjects.mismatch(m, elem, arr.getClass().getComponentType());
        if (!why.isEmpty()) {
            return why;
        }
        for (int i = 0; i < arr.length; i++) {
            if (arr[i] == null) {
                return "element " + i + " is null (on the device there is no 'no object' value for it)";
            }
        }
        return "";
    }

    /** @return размер массива объектов в памяти устройства по раскладке ABI */
    static long byteSize(IrModule m, int elem, Object[] a) {
        return (long) a.length * AbiLayout.sizeOf(m, elem);
    }

    /**
     * Упаковывает массив объектов по раскладке ABI ({@link AbiLayout}).
     *
     * @param arena владелец памяти
     * @param m     модуль
     * @param elem  тип элемента
     * @param a     массив непустых объектов
     * @return память по смещениям листьев, нулевые байты в выравнивающих промежутках
     */
    static MemorySegment toNative(Arena arena, IrModule m, int elem, Object[] a) {
        MemorySegment seg = arena.allocate(Math.max((long) a.length * AbiLayout.sizeOf(m, elem), 8), 8);
        copyToNative(m, elem, a, seg);
        return seg;
    }

    /**
     * То же в уже выделенную память: меняются только байты листьев, выравнивающие промежутки остаются нулевыми.
     *
     * @param seg приёмник не короче {@code a.length * sizeOf(elem)} байт
     */
    static void copyToNative(IrModule m, int elem, Object[] a, MemorySegment seg) {
        int stride = AbiLayout.sizeOf(m, elem);
        int[] types = HostObjects.leafTypes(m, elem);
        int[] offsets = HostObjects.leafOffsets(m, elem);
        long[] leaves = new long[types.length];
        for (int i = 0; i < a.length; i++) {
            HostObjects.flatten(m, elem, a[i], leaves, 0);
            for (int k = 0; k < leaves.length; k++) {
                putLeaf(seg, (long) i * stride + offsets[k], types[k], leaves[k]);
            }
        }
    }

    /** Обратная операция: обновляет struct на месте, заменяет векторы новыми объектами. */
    static void fromNative(MemorySegment seg, IrModule m, int elem, Object[] a) {
        int stride = AbiLayout.sizeOf(m, elem);
        int[] types = HostObjects.leafTypes(m, elem);
        int[] offsets = HostObjects.leafOffsets(m, elem);
        long[] leaves = new long[types.length];
        Class<?> cls = a.getClass().getComponentType();
        for (int i = 0; i < a.length; i++) {
            for (int k = 0; k < leaves.length; k++) {
                leaves[k] = getLeaf(seg, (long) i * stride + offsets[k], types[k]);
            }
            a[i] = HostObjects.rebuild(m, elem, cls, a[i], leaves, 0);
        }
    }

    /**
     * Упаковывает один агрегат (аргумент ядра по значению).
     *
     * @return память размером {@code AbiLayout.sizeOf}
     */
    static MemorySegment valueToNative(Arena arena, IrModule m, int type, Object value) {
        MemorySegment seg = arena.allocate(Math.max(AbiLayout.sizeOf(m, type), 8), 8);
        int[] types = HostObjects.leafTypes(m, type);
        int[] offsets = HostObjects.leafOffsets(m, type);
        long[] leaves = new long[types.length];
        HostObjects.flatten(m, type, value, leaves, 0);
        for (int k = 0; k < leaves.length; k++) {
            putLeaf(seg, offsets[k], types[k], leaves[k]);
        }
        return seg;
    }

    private static void putLeaf(MemorySegment seg, long offset, int type, long canon) {
        switch (Types.bits(type) == 0 ? 8 : Types.bits(type)) {
            case 8 -> seg.set(ValueLayout.JAVA_BYTE, offset, (byte) canon);
            case 16 -> seg.set(ValueLayout.JAVA_SHORT_UNALIGNED, offset, (short) canon);
            case 32 -> seg.set(ValueLayout.JAVA_INT_UNALIGNED, offset, (int) canon);
            default -> seg.set(ValueLayout.JAVA_LONG_UNALIGNED, offset, canon);
        }
    }

    private static long getLeaf(MemorySegment seg, long offset, int type) {
        long bits = switch (Types.bits(type) == 0 ? 8 : Types.bits(type)) {
            case 8 -> seg.get(ValueLayout.JAVA_BYTE, offset);
            case 16 -> seg.get(ValueLayout.JAVA_SHORT_UNALIGNED, offset);
            case 32 -> seg.get(ValueLayout.JAVA_INT_UNALIGNED, offset);
            default -> seg.get(ValueLayout.JAVA_LONG_UNALIGNED, offset);
        };
        return HostObjects.canon(type, bits);
    }

    /**
     * Название типа значения хоста для сообщений об ошибках.
     *
     * @param value значение
     * @return например {@code "int[]"}, {@code "Integer"}, {@code "null"}
     */
    static String describe(Object value) {
        if (value == null) {
            return "null";
        }
        return value.getClass().isArray() ? value.getClass().getComponentType() + "[]" : value.getClass().getSimpleName();
    }
}
