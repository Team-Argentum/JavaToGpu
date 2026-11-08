package com.sixik.gpu.ir.interpreter.samples;

import com.sixik.gpu.ir.HostObjects;
import com.sixik.gpu.ir.IrModule;
import com.sixik.gpu.ir.Types;

import java.util.Random;
import java.util.function.Supplier;

/**
 * Воспроизводимые случайные аргументы для образцов-ядер по сигнатуре ядра: массивы нужного Java-вида и скаляры
 * нужного класса. Целые ненулевые и умеренные (чтобы ядра, которые делят на входные данные, не делили на ноль),
 * вещественные - в пределах {@code +-20} плюс {@code 0, -0, NaN, +-Inf}.
 *
 * <p>Нужны тестам, сверяющим исполнение на устройстве с интерпретатором: фабрика вызывается дважды и обязана
 * давать равные, но независимые массивы.</p>
 */
public final class SampleArgs {

    /** Длина массивов, достаточная для всех образцов при запуске на 16-24 work-item'ах. */
    public static final int ARRAY_LENGTH = 1024;

    private SampleArgs() {
    }

    /**
     * Образцы, которые нельзя сверять на устройстве с интерпретатором: гонки между work-item'ами (все пишут в одну
     * ячейку или читают чужие записи), алиасинг (на устройстве два аргумента - два буфера) и приватный массив в 1 МиБ,
     * который интерпретатор осиливает, а устройство отвергает ({@code CL_OUT_OF_RESOURCES}).
     *
     * @param label имя вида {@code "MemoryKernels.prefixSum"}
     * @return {@code true}, если образец на устройстве не сверяется
     */
    public static boolean notComparableOnDevice(String label) {
        return label.equals("MemoryKernels.prefixSum") || label.equals("MemoryKernels.aliasing")
                || label.equals("MemoryKernels.lengths") || label.equals("MemoryKernels.manyCalls");
    }

    /**
     * @param m    модуль
     * @param t    тип параметра
     * @return тип элемента для массива и сам тип для скаляра
     */
    public static int scalarOf(IrModule m, int t) {
        return m.typeKind(t) == Types.KIND_ARRAY ? Types.elem(m, t) : t;
    }

    /**
     * @param m    модуль
     * @param f    функция-ядро
     * @param seed начальное значение генератора
     * @return фабрика аргументов; каждый вызов возвращает новый набор с теми же значениями
     */
    public static Supplier<Object[]> random(IrModule m, int f, long seed) {
        return () -> {
            Random r = new Random(seed);
            Object[] out = new Object[m.fParamCount(f)];
            for (int i = 0; i < out.length; i++) {
                int t = m.fParamType(f, i);
                int e = scalarOf(m, t);
                if (m.typeKind(t) == Types.KIND_ARRAY) {
                    out[i] = randomArray(r, e);
                } else {
                    out[i] = switch (e) {
                        case Types.I32 -> nonZeroInt(r);
                        case Types.I64 -> (long) nonZeroInt(r) * 1000003L;
                        case Types.F32 -> randomFloat(r);
                        default -> (double) randomFloat(r);
                    };
                }
            }
            return out;
        };
    }


    /**
     * Аргументы по сигнатуре Java-метода образца: помимо примитивов и массивов примитивов строит объекты векторов и
     * {@code @Struct} (скаляры-агрегаты и массивы объектов); значения листьев случайные по тем же правилам.
     *
     * @param k    ядро образца
     * @param seed начальное значение генератора
     * @return фабрика аргументов
     */
    public static Supplier<Object[]> random(SampleKernels.Kernel k, long seed) {
        IrModule m = k.module();
        int f = m.functionCount() - 1;
        Class<?>[] types = k.method().getParameterTypes();
        Supplier<Object[]> primitives = random(m, f, seed);
        return () -> {
            Random r = new Random(seed ^ 0x5DEECE66DL);
            Object[] out = primitives.get();
            for (int i = 0; i < out.length; i++) {
                int t = m.fParamType(f, i);
                Class<?> c = types[i];
                if (c.isArray() && !c.getComponentType().isPrimitive()) {
                    Object[] arr = (Object[]) java.lang.reflect.Array.newInstance(c.getComponentType(), ARRAY_LENGTH);
                    for (int e = 0; e < arr.length; e++) {
                        arr[e] = randomObject(m, Types.elem(m, t), c.getComponentType(), r);
                    }
                    out[i] = arr;
                } else if (!c.isPrimitive() && !c.isArray()) {
                    out[i] = randomObject(m, t, c, r);
                }
            }
            return out;
        };
    }

    /**
     * @param k ядро образца
     * @return {@code true}, если среди параметров есть векторы или struct (хост передаёт объекты)
     */
    public static boolean hasObjectArguments(SampleKernels.Kernel k) {
        for (Class<?> c : k.method().getParameterTypes()) {
            Class<?> base = c.isArray() ? c.getComponentType() : c;
            if (!base.isPrimitive()) {
                return true;
            }
        }
        return false;
    }

    private static Object randomObject(IrModule m, int t, Class<?> cls, Random r) {
        int[] leafTypes = HostObjects.leafTypes(m, t);
        long[] leaves = new long[leafTypes.length];
        for (int i = 0; i < leaves.length; i++) {
            leaves[i] = switch (leafTypes[i]) {
                case Types.F32 -> Float.floatToRawIntBits(randomFloat(r));
                case Types.F64 -> Double.doubleToRawLongBits(randomFloat(r));
                case Types.I64, Types.U64 -> (long) nonZeroInt(r) * 1000003L;
                default -> nonZeroInt(r);
            };
        }
        return HostObjects.rebuild(m, t, cls, null, leaves, 0);
    }


    /**
     * @param k ядро образца
     * @return {@code true}, если среди параметров (включая поля векторов и struct) есть {@code float/double}: такие
     *         результаты на устройстве сверяются с допуском в ULP
     */
    public static boolean hasFloatingPoint(SampleKernels.Kernel k) {
        for (Class<?> c : k.method().getParameterTypes()) {
            if (containsFloat(c.isArray() ? c.getComponentType() : c)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsFloat(Class<?> c) {
        if (c == float.class || c == double.class) {
            return true;
        }
        if (c.isPrimitive()) {
            return false;
        }
        for (java.lang.reflect.Field f : c.getDeclaredFields()) {
            int mod = f.getModifiers();
            if (java.lang.reflect.Modifier.isPublic(mod) && !java.lang.reflect.Modifier.isStatic(mod)
                    && containsFloat(f.getType())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Сравнение объектов хоста (векторов и struct) по открытым полям в порядке объявления: вещественные - в ULP
     * ({@code NaN} равен {@code NaN}, знак нуля учитывается), остальное точно.
     *
     * @param expected ожидаемый объект
     * @param actual   полученный объект
     * @param ulps     допуск в ULP (0 - побитовое равенство)
     * @return причина расхождения либо {@code null}
     */
    public static String objectDifference(Object expected, Object actual, int ulps) {
        if (expected == null || actual == null) {
            return expected == actual ? null : "null против значения";
        }
        if (expected.getClass() != actual.getClass()) {
            return "разные классы";
        }
        for (java.lang.reflect.Field f : expected.getClass().getDeclaredFields()) {
            int mod = f.getModifiers();
            if (!java.lang.reflect.Modifier.isPublic(mod) || java.lang.reflect.Modifier.isStatic(mod)) {
                continue;
            }
            try {
                Object x = f.get(expected);
                Object y = f.get(actual);
                String why;
                if (x instanceof Float fx) {
                    why = sameFloat(fx, (Float) y, ulps) ? null : "поле " + f.getName() + ": " + fx + " / " + y;
                } else if (x instanceof Double dx) {
                    why = sameDouble(dx, (Double) y, ulps) ? null : "поле " + f.getName() + ": " + dx + " / " + y;
                } else if (x == null || x instanceof Number || x instanceof Boolean || x instanceof Character) {
                    why = java.util.Objects.equals(x, y) ? null : "поле " + f.getName() + ": " + x + " / " + y;
                } else {
                    String inner = objectDifference(x, y, ulps);
                    why = inner == null ? null : "поле " + f.getName() + ": " + inner;
                }
                if (why != null) {
                    return why;
                }
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }
        return null;
    }

    private static boolean sameFloat(float e, float a, int ulps) {
        if (Float.isNaN(e) || Float.isNaN(a)) {
            return Float.isNaN(e) && Float.isNaN(a);
        }
        if (Float.floatToRawIntBits(e) == Float.floatToRawIntBits(a)) {
            return true;
        }
        if (ulps == 0 || e == 0f || a == 0f) {
            return false;
        }
        return Math.abs(ordered(Float.floatToRawIntBits(e)) - ordered(Float.floatToRawIntBits(a))) <= ulps;
    }

    private static boolean sameDouble(double e, double a, int ulps) {
        if (Double.isNaN(e) || Double.isNaN(a)) {
            return Double.isNaN(e) && Double.isNaN(a);
        }
        if (Double.doubleToRawLongBits(e) == Double.doubleToRawLongBits(a)) {
            return true;
        }
        if (ulps == 0 || e == 0d || a == 0d) {
            return false;
        }
        long x = Double.doubleToRawLongBits(e);
        long y = Double.doubleToRawLongBits(a);
        return Math.abs((x >= 0 ? x : Long.MIN_VALUE - x) - (y >= 0 ? y : Long.MIN_VALUE - y)) <= ulps;
    }

    private static long ordered(int bits) {
        return bits >= 0 ? bits : Integer.MIN_VALUE - (long) bits;
    }

    private static int nonZeroInt(Random r) {
        int v = r.nextInt(201) - 100;
        return v == 0 ? 17 : v;
    }

    private static float randomFloat(Random r) {
        return switch (r.nextInt(20)) {
            case 0 -> 0f;
            case 1 -> -0f;
            case 2 -> Float.NaN;
            case 3 -> Float.POSITIVE_INFINITY;
            case 4 -> Float.NEGATIVE_INFINITY;
            default -> (r.nextFloat() - 0.5f) * 40f;
        };
    }

    private static Object randomArray(Random r, int elem) {
        int n = ARRAY_LENGTH;
        switch (elem) {
            case Types.I8, Types.U8: {
                byte[] a = new byte[n];
                for (int i = 0; i < n; i++) {
                    a[i] = (byte) (r.nextInt(100) + 1);
                }
                return a;
            }
            case Types.I16: {
                short[] a = new short[n];
                for (int i = 0; i < n; i++) {
                    a[i] = (short) (r.nextInt(3000) + 1);
                }
                return a;
            }
            case Types.U16: {
                char[] a = new char[n];
                for (int i = 0; i < n; i++) {
                    a[i] = (char) (r.nextInt(60000) + 1);
                }
                return a;
            }
            case Types.I32, Types.U32: {
                int[] a = new int[n];
                for (int i = 0; i < n; i++) {
                    a[i] = nonZeroInt(r);
                }
                return a;
            }
            case Types.I64, Types.U64: {
                long[] a = new long[n];
                for (int i = 0; i < n; i++) {
                    a[i] = (long) nonZeroInt(r) * 1000003L;
                }
                return a;
            }
            case Types.F32: {
                float[] a = new float[n];
                for (int i = 0; i < n; i++) {
                    a[i] = randomFloat(r);
                }
                return a;
            }
            default: {
                double[] a = new double[n];
                for (int i = 0; i < n; i++) {
                    a[i] = randomFloat(r);
                }
                return a;
            }
        }
    }
}
