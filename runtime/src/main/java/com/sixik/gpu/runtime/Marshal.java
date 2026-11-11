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
        copyToNativeFast(array, seg);
        return seg;
    }

    /** Поток-«зонд»: никогда не запускается, нужен лишь, чтобы узнать, доступна ли память чужим потокам. */
    private static final Thread PROBE = new Thread(() -> {
    });

    /**
     * Копирует массив в уже выделенную нативную память (для повторных запусков без новых аллокаций).
     *
     * @param array источник
     * @param seg   приёмник не короче {@code byteSize(array)} байт
     */
    /** Начиная с этого размера копирование массива делится между потоками (меньшие быстрее копировать в одном). */
    static final long PARALLEL_COPY_BYTES = 4L << 20;

    /** Сколько частей при параллельном копировании: копирование упирается в память, больше 8 потоков не помогают. */
    private static final int COPY_PARTS = Math.max(1, Math.min(8, Runtime.getRuntime().availableProcessors()));

    /**
     * Копирует примитивный массив в нативную память; большие массивы - несколькими потоками.
     *
     * <p>Один поток копирует около 9-10 ГБ/с, этого не хватает, чтобы не отставать от DMA шины PCIe (~20 ГБ/с из
     * закреплённой памяти): для 24 МБ копирование занимало бы больше времени, чем сама передача. Части не пересекаются,
     * поэтому порядок их выполнения не важен. Память confined-арены другим потокам недоступна - для неё копирование
     * идёт в одном потоке.</p>
     *
     * @param array примитивный массив
     * @param seg   приёмник (не короче содержимого массива)
     */
    static void copyToNativeFast(Object array, MemorySegment seg) {
        copyFast(array, seg, true);
    }

    /**
     * Копирует нативную память в примитивный массив; большие массивы - несколькими потоками.
     *
     * @param seg   источник
     * @param array примитивный массив
     */
    static void fromNativeFast(MemorySegment seg, Object array) {
        copyFast(array, seg, false);
    }

    private static void copyFast(Object array, MemorySegment seg, boolean toNative) {
        int n = length(array);
        long bytes = byteSize(array);
        // память confined-арены доступна только своему потоку: делить копирование нельзя
        boolean shared = seg.isAccessibleBy(PROBE);
        if (!shared || bytes < PARALLEL_COPY_BYTES || COPY_PARTS == 1 || array instanceof boolean[]) {
            if (toNative) {
                copyToNative(array, seg);
            } else {
                fromNative(seg, array);
            }
            return;
        }
        java.util.stream.IntStream.range(0, COPY_PARTS).parallel().forEach(p -> {
            int from = (int) ((long) n * p / COPY_PARTS);
            int to = (int) ((long) n * (p + 1) / COPY_PARTS);
            copyRange(array, seg, from, to - from, toNative);
        });
    }

    /**
     * Копирует {@code count} элементов начиная с {@code from} в одну из сторон; элемент {@code i} массива соответствует
     * смещению {@code i * размер элемента} в {@code seg}.
     *
     * @param array    примитивный массив (не {@code boolean[]})
     * @param seg      нативная память
     * @param from     первый элемент
     * @param count    число элементов
     * @param toNative направление: в нативную память либо из неё
     */
    private static void copyRange(Object array, MemorySegment seg, int from, int count, boolean toNative) {
        switch (array) {
            case byte[] x -> {
                if (toNative) {
                    MemorySegment.copy(x, from, seg, ValueLayout.JAVA_BYTE, from, count);
                } else {
                    MemorySegment.copy(seg, ValueLayout.JAVA_BYTE, from, x, from, count);
                }
            }
            case short[] x -> {
                if (toNative) {
                    MemorySegment.copy(x, from, seg, ValueLayout.JAVA_SHORT, 2L * from, count);
                } else {
                    MemorySegment.copy(seg, ValueLayout.JAVA_SHORT, 2L * from, x, from, count);
                }
            }
            case char[] x -> {
                if (toNative) {
                    MemorySegment.copy(x, from, seg, ValueLayout.JAVA_CHAR, 2L * from, count);
                } else {
                    MemorySegment.copy(seg, ValueLayout.JAVA_CHAR, 2L * from, x, from, count);
                }
            }
            case int[] x -> {
                if (toNative) {
                    MemorySegment.copy(x, from, seg, ValueLayout.JAVA_INT, 4L * from, count);
                } else {
                    MemorySegment.copy(seg, ValueLayout.JAVA_INT, 4L * from, x, from, count);
                }
            }
            case long[] x -> {
                if (toNative) {
                    MemorySegment.copy(x, from, seg, ValueLayout.JAVA_LONG, 8L * from, count);
                } else {
                    MemorySegment.copy(seg, ValueLayout.JAVA_LONG, 8L * from, x, from, count);
                }
            }
            case float[] x -> {
                if (toNative) {
                    MemorySegment.copy(x, from, seg, ValueLayout.JAVA_FLOAT, 4L * from, count);
                } else {
                    MemorySegment.copy(seg, ValueLayout.JAVA_FLOAT, 4L * from, x, from, count);
                }
            }
            case double[] x -> {
                if (toNative) {
                    MemorySegment.copy(x, from, seg, ValueLayout.JAVA_DOUBLE, 8L * from, count);
                } else {
                    MemorySegment.copy(seg, ValueLayout.JAVA_DOUBLE, 8L * from, x, from, count);
                }
            }
            default -> throw new IllegalArgumentException("unsupported array " + array.getClass());
        }
    }

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
