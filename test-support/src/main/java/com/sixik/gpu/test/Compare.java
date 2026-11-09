package com.sixik.gpu.test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * Сравнение результатов ядра между ветвями (REWORK_ARCHITECTURE.md, 8.3, 9.6): целые и логика - точно, вещественные -
 * в ULP.
 *
 * <p>Вещественные: при допуске 0 - побитовое равенство (знак нуля различается), при допуске больше нуля - расстояние
 * по упорядоченному представлению (знак нуля не различается). Все {@code NaN} равны друг другу: полезная нагрузка
 * {@code NaN} не определена. Скалярные аргументы передаются по значению и не сравниваются.</p>
 */
final class Compare {

    private Compare() {
    }

    /**
     * Сравнивает значение аргумента после запуска.
     *
     * @param expected значение эталонной ветви
     * @param actual   значение сравниваемой ветви
     * @param ulps     допустимое расстояние вещественных
     * @return описание первого расхождения либо {@code null}, если значения совпали (или это скаляр)
     */
    static String difference(Object expected, Object actual, int ulps) {
        if (expected == null || actual == null) {
            return expected == actual ? null : "null versus a value";
        }
        if (!expected.getClass().isArray()) {
            return null;
        }
        if (expected.getClass() != actual.getClass()) {
            return "different array types: " + expected.getClass().getSimpleName() + " and " + actual.getClass().getSimpleName();
        }
        switch (expected) {
            case int[] e -> {
                int[] a = (int[]) actual;
                if (e.length != a.length) {
                    return lengths(e.length, a.length);
                }
                for (int i = 0; i < e.length; i++) {
                    if (e[i] != a[i]) {
                        return at(i, e[i], a[i]);
                    }
                }
            }
            case long[] e -> {
                long[] a = (long[]) actual;
                if (e.length != a.length) {
                    return lengths(e.length, a.length);
                }
                for (int i = 0; i < e.length; i++) {
                    if (e[i] != a[i]) {
                        return at(i, e[i], a[i]);
                    }
                }
            }
            case float[] e -> {
                float[] a = (float[]) actual;
                if (e.length != a.length) {
                    return lengths(e.length, a.length);
                }
                for (int i = 0; i < e.length; i++) {
                    if (!sameFloat(e[i], a[i], ulps)) {
                        return at(i, e[i] + " (0x" + Integer.toHexString(Float.floatToRawIntBits(e[i])) + ")",
                                a[i] + " (0x" + Integer.toHexString(Float.floatToRawIntBits(a[i])) + ")")
                                + ", distance " + floatDistance(e[i], a[i]) + " ULP, tolerance " + ulps;
                    }
                }
            }
            case double[] e -> {
                double[] a = (double[]) actual;
                if (e.length != a.length) {
                    return lengths(e.length, a.length);
                }
                for (int i = 0; i < e.length; i++) {
                    if (!sameDouble(e[i], a[i], ulps)) {
                        return at(i, e[i] + " (0x" + Long.toHexString(Double.doubleToRawLongBits(e[i])) + ")",
                                a[i] + " (0x" + Long.toHexString(Double.doubleToRawLongBits(a[i])) + ")")
                                + ", distance " + doubleDistance(e[i], a[i]) + " ULP, tolerance " + ulps;
                    }
                }
            }
            case byte[] e -> {
                byte[] a = (byte[]) actual;
                if (e.length != a.length) {
                    return lengths(e.length, a.length);
                }
                for (int i = 0; i < e.length; i++) {
                    if (e[i] != a[i]) {
                        return at(i, e[i], a[i]);
                    }
                }
            }
            case short[] e -> {
                short[] a = (short[]) actual;
                if (e.length != a.length) {
                    return lengths(e.length, a.length);
                }
                for (int i = 0; i < e.length; i++) {
                    if (e[i] != a[i]) {
                        return at(i, e[i], a[i]);
                    }
                }
            }
            case char[] e -> {
                char[] a = (char[]) actual;
                if (e.length != a.length) {
                    return lengths(e.length, a.length);
                }
                for (int i = 0; i < e.length; i++) {
                    if (e[i] != a[i]) {
                        return at(i, (int) e[i], (int) a[i]);
                    }
                }
            }
            case boolean[] e -> {
                boolean[] a = (boolean[]) actual;
                if (e.length != a.length) {
                    return lengths(e.length, a.length);
                }
                for (int i = 0; i < e.length; i++) {
                    if (e[i] != a[i]) {
                        return at(i, e[i], a[i]);
                    }
                }
            }
            case Object[] e -> {
                Object[] a = (Object[]) actual;
                if (e.length != a.length) {
                    return lengths(e.length, a.length);
                }
                for (int i = 0; i < e.length; i++) {
                    String why = objectDifference(e[i], a[i], ulps);
                    if (why != null) {
                        return "[" + i + "]: " + why + " (expected " + describe(e[i]) + ", got " + describe(a[i]) + ")";
                    }
                }
            }
            default -> throw new IllegalArgumentException("unsupported array " + expected.getClass());
        }
        return null;
    }

    private static String lengths(int e, int a) {
        return "length " + a + " instead of " + e;
    }

    private static String at(int i, Object e, Object a) {
        return "[" + i + "]: expected " + e + ", got " + a;
    }

    /** Объекты хоста (векторы и struct): открытые поля в порядке объявления; вещественные - в ULP, остальное точно. */
    private static String objectDifference(Object e, Object a, int ulps) {
        if (e == null || a == null) {
            return e == a ? null : "null versus a value";
        }
        if (e.getClass() != a.getClass()) {
            return "different classes";
        }
        for (Field f : e.getClass().getFields()) {
            if (Modifier.isStatic(f.getModifiers())) {
                continue;
            }
            try {
                Object x = f.get(e);
                Object y = f.get(a);
                String why;
                if (x instanceof Float fx) {
                    why = sameFloat(fx, (Float) y, ulps) ? null : "field " + f.getName();
                } else if (x instanceof Double dx) {
                    why = sameDouble(dx, (Double) y, ulps) ? null : "field " + f.getName();
                } else if (x == null || x instanceof Number || x instanceof Boolean || x instanceof Character) {
                    why = java.util.Objects.equals(x, y) ? null : "field " + f.getName();
                } else {
                    String inner = objectDifference(x, y, ulps);
                    why = inner == null ? null : "field " + f.getName() + ": " + inner;
                }
                if (why != null) {
                    return why;
                }
            } catch (IllegalAccessException ex) {
                throw new IllegalStateException(ex);
            }
        }
        return null;
    }

    private static String describe(Object o) {
        if (o == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder(o.getClass().getSimpleName()).append('{');
        for (Field f : o.getClass().getFields()) {
            if (!Modifier.isStatic(f.getModifiers())) {
                try {
                    sb.append(f.getName()).append('=').append(f.get(o)).append(' ');
                } catch (IllegalAccessException ex) {
                    sb.append(f.getName()).append("=?");
                }
            }
        }
        return sb.append('}').toString();
    }

    // ---- вещественные ----------------------------------------------------------------------------

    /**
     * Равенство {@code float} в ULP. Формула: ULP-расстояние - разность «упорядоченных» целых представлений
     * {@code ord(x)}, где {@code ord(bits) = bits} для неотрицательных чисел и {@code Integer.MIN_VALUE - bits}
     * для отрицательных. Упорядоченное представление монотонно по значению, поэтому разность двух таких чисел -
     * число представимых {@code float} между значениями, то есть расстояние в ULP; при этом {@code -0.0} и {@code +0.0}
     * дают одно и то же {@code ord}. Допуск 0 требует побитового равенства.
     */
    static boolean sameFloat(float e, float a, int ulps) {
        if (Float.isNaN(e) || Float.isNaN(a)) {
            return Float.isNaN(e) && Float.isNaN(a);
        }
        if (ulps == 0) {
            return Float.floatToRawIntBits(e) == Float.floatToRawIntBits(a);
        }
        return floatDistance(e, a) <= ulps;
    }

    /** То же для {@code double}: {@code ord(bits) = bits} либо {@code Long.MIN_VALUE - bits}. */
    static boolean sameDouble(double e, double a, int ulps) {
        if (Double.isNaN(e) || Double.isNaN(a)) {
            return Double.isNaN(e) && Double.isNaN(a);
        }
        if (ulps == 0) {
            return Double.doubleToRawLongBits(e) == Double.doubleToRawLongBits(a);
        }
        return doubleDistance(e, a) <= ulps;
    }

    private static long floatDistance(float e, float a) {
        if (Float.isNaN(e) || Float.isNaN(a)) {
            return Float.isNaN(e) && Float.isNaN(a) ? 0 : Long.MAX_VALUE;
        }
        return Math.abs(orderedFloat(Float.floatToRawIntBits(e)) - orderedFloat(Float.floatToRawIntBits(a)));
    }

    private static long doubleDistance(double e, double a) {
        if (Double.isNaN(e) || Double.isNaN(a)) {
            return Double.isNaN(e) && Double.isNaN(a) ? 0 : Long.MAX_VALUE;
        }
        long x = orderedDouble(Double.doubleToRawLongBits(e));
        long y = orderedDouble(Double.doubleToRawLongBits(a));
        long d = x - y;
        return d < 0 ? (d == Long.MIN_VALUE ? Long.MAX_VALUE : -d) : d;
    }

    private static long orderedFloat(int bits) {
        return bits >= 0 ? bits : Integer.MIN_VALUE - (long) bits;
    }

    private static long orderedDouble(long bits) {
        return bits >= 0 ? bits : Long.MIN_VALUE - bits;
    }
}
