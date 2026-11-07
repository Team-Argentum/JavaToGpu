package com.sixik.gpu.api;

import com.sixik.gpu.apigen.BuiltinsDef;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.MathContext;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Сверка JVM-реализаций builtins с таблицей допусков {@code builtins.def} (критерий приёмки M2): результат
 * {@code Gpu.*} не должен отличаться от независимого эталона больше чем на допуск ULP из таблицы.
 *
 * <p>ULP-расстояние двух конечных чисел одного типа - число представимых значений между ними: числа
 * отображаются в целые так, что порядок сохраняется ({@code x >= 0 -> bits(x)}, {@code x < 0 -> MIN - bits(x)}),
 * и берётся модуль разности. +0 и -0 дают одно и то же целое, то есть расстояние 0.</p>
 *
 * <p>Эталоны независимы от проверяемого кода: {@code sin/cos} сравниваются с {@code Math.*} (аппаратные
 * интринсики, а реализация использует {@code StrictMath}), {@code sqrt} и {@code fma} - с точной арифметикой
 * {@link BigDecimal} (единственное округление). Тест табличный: builtin с допуском, для которого здесь нет эталона,
 * роняет тест - новую функцию нельзя добавить в таблицу без проверки.</p>
 */
class GpuUlpToleranceTest {

    private static final float[] F32_EDGE = {
            0f, -0f, Float.MIN_VALUE, -Float.MIN_VALUE, Float.MIN_NORMAL, 1f, -1f, 0.5f, 3.1415927f, -2.7182817f,
            1e-10f, 1e10f, 12345.678f, 100f, Float.MAX_VALUE, -Float.MAX_VALUE,
            Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NaN};
    private static final double[] F64_EDGE = {
            0.0, -0.0, Double.MIN_VALUE, -Double.MIN_VALUE, Double.MIN_NORMAL, 1.0, -1.0, 0.5, Math.PI, -Math.E,
            1e-300, 1e300, 12345.678, 100.0, Double.MAX_VALUE, -Double.MAX_VALUE,
            Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NaN};

    private static BuiltinsDef.Def def() throws IOException {
        String path = System.getProperty("builtins.def");
        return BuiltinsDef.parse(Files.readString(Path.of(path), StandardCharsets.UTF_8));
    }

    // ---- ULP-расстояние ----------------------------------------------------------------------------

    static long ulpDistance(float a, float b) {
        return Math.abs((long) order(a) - order(b));
    }

    static long ulpDistance(double a, double b) {
        return Math.abs(order(a) - order(b));
    }

    private static int order(float x) {
        int bits = Float.floatToIntBits(x);
        return bits >= 0 ? bits : Integer.MIN_VALUE - bits;
    }

    private static long order(double x) {
        long bits = Double.doubleToLongBits(x);
        return bits >= 0 ? bits : Long.MIN_VALUE - bits;
    }

    @Test
    void ulpDistanceHelperIsCorrect() {
        assertTrue(ulpDistance(1f, Math.nextUp(1f)) == 1);
        assertTrue(ulpDistance(1f, Math.nextUp(Math.nextUp(1f))) == 2);
        assertTrue(ulpDistance(0f, -0f) == 0, "+0 и -0 совпадают");
        assertTrue(ulpDistance(Float.MIN_VALUE, -Float.MIN_VALUE) == 2, "через ноль: два шага");
        assertTrue(ulpDistance(-1f, Math.nextDown(-1f)) == 1);
        assertTrue(ulpDistance(1.0, Math.nextUp(1.0)) == 1);
        assertTrue(ulpDistance(Double.MIN_VALUE, -Double.MIN_VALUE) == 2);
    }

    // ---- эталоны ----------------------------------------------------------------------------------

    private static boolean finite(double... v) {
        for (double d : v) {
            if (Double.isNaN(d) || Double.isInfinite(d)) {
                return false;
            }
        }
        return true;
    }

    private static double exactFma64(double a, double b, double c) {
        if (finite(a, b) && !finite(c)) {
            return c; // слитное произведение конечно (не переполняется), поэтому результат определяет c
        }
        if (!finite(a, b, c)) {
            return a * b + c;
        }
        return new BigDecimal(a).multiply(new BigDecimal(b)).add(new BigDecimal(c)).doubleValue();
    }

    private static float exactFma32(float a, float b, float c) {
        if (finite(a, b) && !finite(c)) {
            return c; // слитное произведение конечно (не переполняется float), поэтому результат определяет c
        }
        if (!finite(a, b, c)) {
            return a * b + c;
        }
        return new BigDecimal(a).multiply(new BigDecimal(b)).add(new BigDecimal(c)).floatValue();
    }

    private static double exactSqrt64(double x) {
        if (Double.isNaN(x) || x < 0) {
            return Double.NaN;
        }
        if (x == 0 || Double.isInfinite(x)) {
            return x;
        }
        return new BigDecimal(x).sqrt(new MathContext(60)).doubleValue();
    }

    private static float exactSqrt32(float x) {
        if (Float.isNaN(x) || x < 0) {
            return Float.NaN;
        }
        if (x == 0 || Float.isInfinite(x)) {
            return x;
        }
        return new BigDecimal(x).sqrt(new MathContext(60)).floatValue();
    }

    /** Эталоны f32: имя builtin -> функция от массива аргументов. */
    private static final Map<String, Function<float[], Float>> REF32 = Map.of(
            "sin", a -> (float) Math.sin(a[0]),
            "cos", a -> (float) Math.cos(a[0]),
            "sqrt", a -> exactSqrt32(a[0]),
            "fma", a -> exactFma32(a[0], a[1], a[2]));

    /** Эталоны f64. */
    private static final Map<String, Function<double[], Double>> REF64 = Map.of(
            "sin", a -> Math.sin(a[0]),
            "cos", a -> Math.cos(a[0]),
            "sqrt", a -> exactSqrt64(a[0]),
            "fma", a -> exactFma64(a[0], a[1], a[2]));

    // ---- сверка -----------------------------------------------------------------------------------

    private static Method method(String name, Class<?> type, int arity) throws NoSuchMethodException {
        Class<?>[] params = new Class<?>[arity];
        java.util.Arrays.fill(params, type);
        return Gpu.class.getMethod(name, params);
    }

    private static List<float[]> argTuples32(int arity) {
        List<float[]> r = new java.util.ArrayList<>();
        if (arity == 1) {
            for (float x : F32_EDGE) {
                r.add(new float[]{x});
            }
        } else {
            // арность 3: достаточно подмножества, включающего и особые значения (полный перебор 19^3 избыточен)
            float[] small = {0f, -0f, 1f, -1f, 0.1f, 1e20f, -1e20f, Float.MIN_VALUE, Float.MAX_VALUE,
                    Float.POSITIVE_INFINITY, Float.NaN};
            for (float a : small) {
                for (float b : small) {
                    for (float c : small) {
                        r.add(new float[]{a, b, c});
                    }
                }
            }
        }
        return r;
    }

    private static List<double[]> argTuples64(int arity) {
        List<double[]> r = new java.util.ArrayList<>();
        if (arity == 1) {
            for (double x : F64_EDGE) {
                r.add(new double[]{x});
            }
        } else {
            double[] small = {0.0, -0.0, 1.0, -1.0, 0.1, 1e200, -1e200, Double.MIN_VALUE, Double.MAX_VALUE,
                    Double.POSITIVE_INFINITY, Double.NaN};
            for (double a : small) {
                for (double b : small) {
                    for (double c : small) {
                        r.add(new double[]{a, b, c});
                    }
                }
            }
        }
        return r;
    }

    @Test
    void float32ResultsWithinTableTolerance() throws Exception {
        for (BuiltinsDef.Builtin b : def().builtins()) {
            if (b.ulp() < 0 || !b.ret().equals("T")) {
                continue;
            }
            Function<float[], Float> ref = REF32.get(b.name());
            if (ref == null) {
                fail("builtin " + b.name() + " имеет допуск ULP, но в тесте нет эталона f32");
            }
            Method m = method(b.name(), float.class, b.args().size());
            for (float[] args : argTuples32(b.args().size())) {
                float got = call32(m, args);
                float want = ref.apply(args);
                check(b.name(), "f32", java.util.Arrays.toString(args), got, want, b.ulp());
            }
        }
    }

    @Test
    void float64ResultsWithinTableTolerance() throws Exception {
        for (BuiltinsDef.Builtin b : def().builtins()) {
            if (b.ulp64() < 0 || !b.ret().equals("T")) {
                continue;
            }
            Function<double[], Double> ref = REF64.get(b.name());
            if (ref == null) {
                fail("builtin " + b.name() + " имеет допуск ULP, но в тесте нет эталона f64");
            }
            Method m = method(b.name(), double.class, b.args().size());
            for (double[] args : argTuples64(b.args().size())) {
                double got = call64(m, args);
                double want = ref.apply(args);
                check(b.name(), "f64", java.util.Arrays.toString(args), got, want, b.ulp64());
            }
        }
    }

    @Test
    void tableHasExpectedTolerancesFromOpenClSpec() throws IOException {
        // значения из спецификации OpenCL C (full profile); изменение допуска - осознанное решение, а не побочный эффект
        Map<String, int[]> expected = Map.of(
                "sin", new int[]{4, 4},
                "cos", new int[]{4, 4},
                "sqrt", new int[]{3, 0},
                "fma", new int[]{0, 0});
        for (BuiltinsDef.Builtin b : def().builtins()) {
            int[] e = expected.get(b.name());
            if (e != null) {
                assertTrue(b.ulp() == e[0] && b.ulp64() == e[1],
                        b.name() + ": ожидалось " + e[0] + "/" + e[1] + ", в таблице " + b.ulp() + "/" + b.ulp64());
            }
        }
    }

    private static float call32(Method m, float[] args) throws Exception {
        Object[] boxed = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            boxed[i] = args[i];
        }
        return (float) invoke(m, boxed);
    }

    private static double call64(Method m, double[] args) throws Exception {
        Object[] boxed = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            boxed[i] = args[i];
        }
        return (double) invoke(m, boxed);
    }

    private static Object invoke(Method m, Object[] args) throws Exception {
        try {
            return m.invoke(null, args);
        } catch (InvocationTargetException e) {
            throw new AssertionError("исключение в " + m.getName(), e.getCause());
        }
    }

    private static void check(String name, String type, String args, double got, double want, int tolerance) {
        String where = name + "(" + type + ") args=" + args + " got=" + got + " want=" + want + " допуск=" + tolerance;
        if (Double.isNaN(want)) {
            assertTrue(Double.isNaN(got), "ожидался NaN: " + where);
        } else if (Double.isInfinite(want)) {
            assertTrue(got == want, "ожидалась бесконечность того же знака: " + where);
        } else {
            assertTrue(finite(got), "ожидалось конечное значение: " + where);
            long d = type.equals("f32") ? ulpDistance((float) got, (float) want) : ulpDistance(got, want);
            assertTrue(d <= tolerance, "расхождение " + d + " ULP: " + where);
        }
    }
}
