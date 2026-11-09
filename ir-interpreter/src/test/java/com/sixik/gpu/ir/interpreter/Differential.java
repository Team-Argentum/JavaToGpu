package com.sixik.gpu.ir.interpreter;

import com.sixik.gpu.api.WorkItem;
import com.sixik.gpu.api.WorkItemContext;
import com.sixik.gpu.ir.Diagnostic;
import com.sixik.gpu.ir.interpreter.samples.SampleKernels;
import com.sixik.gpu.ir.interpreter.samples.SampleKernels.Kernel;
import com.sixik.gpu.ir.IrModule;
import com.sixik.gpu.ir.Result;

import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodType;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Дифференциальный стенд M4: одно и то же ядро исполняется
 * <ol>
 *   <li>прямым вызовом оригинального Java-метода по work-item'ам (эталон JVM, {@link WorkItem}),</li>
 *   <li>интерпретатором по IR, который записал в ресурсы фронтенд при сборке образцов.</li>
 * </ol>
 * Результаты сравниваются поэлементно: целые, логика и вещественные - побитово (с единым NaN); для builtins
 * с допуском в ULP можно передать допустимое расстояние. Если JVM-метод бросил исключение (индекс за границами,
 * деление на ноль), интерпретатор обязан вернуть диагностику.
 *
 * <p>IR берётся из {@code META-INF/gpu/kernels.idx} и {@code META-INF/gpu/ir/*.gir} - то есть проверяется весь путь
 * «исходник &rarr; фронтенд &rarr; ресурс &rarr; чтение &rarr; исполнение».</p>
 */
final class Differential {

    /** Страховка: образцы малы, а зациклившийся интерпретатор должен упасть с IN-R03, а не повесить сборку. */
    private static final Interpreter.Options BUDGET = new Interpreter.Options(200_000_000L);

    private Differential() {
    }

    /**
     * @param cls    класс образца
     * @param method имя ядра
     * @return ядро образца (см. {@link SampleKernels})
     */
    static Kernel kernel(Class<?> cls, String method) {
        return SampleKernels.kernel(cls, method);
    }

    // =============================================================================================
    // сравнение
    // =============================================================================================

    /**
     * Исполняет ядро на JVM и в интерпретаторе и сравнивает все аргументы-массивы.
     *
     * @param cls   класс образца
     * @param name  имя ядра
     * @param range виртуальный NDRange
     * @param args  фабрика аргументов; вызывается дважды и обязана возвращать равные, но независимые массивы
     */
    static void check(Class<?> cls, String name, NdRange range, Supplier<Object[]> args) {
        check(cls, name, range, args, 0);
    }

    /**
     * То же, но вещественные результаты могут расходиться не более чем на {@code ulps} ULP.
     *
     * @param cls   класс образца
     * @param name  имя ядра
     * @param range виртуальный NDRange
     * @param args  фабрика аргументов
     * @param ulps  допустимое расстояние в ULP (0 - побитовое равенство)
     */
    static void check(Class<?> cls, String name, NdRange range, Supplier<Object[]> args, int ulps) {
        Kernel k = kernel(cls, name);
        Object[] jvmArgs = args.get();
        Object[] irArgs = args.get();

        Throwable jvmFailure = runOnJvm(k, range, jvmArgs);
        Result<Interpreter.Execution> result = Interpreter.run(k.module(), k.module().functionCount() - 1, range, BUDGET, irArgs);

        if (jvmFailure != null) {
            assertTrue(!result.isOk(), name + ": the JVM threw " + jvmFailure.getCause()
                    + ", but the interpreter finished successfully");
            return;
        }
        assertTrue(result.isOk(), name + ": interpreter refused: " + result.diagnostics());
        for (int i = 0; i < jvmArgs.length; i++) {
            assertArrayEquals(name, i, jvmArgs[i], irArgs[i], ulps);
        }
    }

    /** @return {@code null} при успехе либо исключение вызова (обёрнутое в {@link InvocationTargetException}) */
    private static Throwable runOnJvm(Kernel k, NdRange range, Object[] args) {
        int[] global = {range.global(0), range.global(1), range.global(2)};
        int[] local = new int[3];
        for (int d = 0; d < 3; d++) {
            int req = k.module().fReqWorkGroup(k.module().functionCount() - 1, d);
            local[d] = req > 0 ? req : (range.local(d) != 0 ? range.local(d) : 1);
        }
        WorkItemContext ctx = new WorkItemContext(range.dim(), global, local);
        Throwable[] failure = new Throwable[1];
        WorkItem.run(ctx, () -> {
            try {
                for (int z = 0; z < global[2]; z++) {
                    for (int y = 0; y < global[1]; y++) {
                        for (int x = 0; x < global[0]; x++) {
                            ctx.setGlobalId(x, y, z);
                            k.method().invoke(null, args);
                        }
                    }
                }
            } catch (InvocationTargetException e) {
                failure[0] = e;
            } catch (IllegalAccessException e) {
                throw new AssertionError(e);
            }
        });
        return failure[0];
    }

    private static void assertArrayEquals(String name, int arg, Object expected, Object actual, int ulps) {
        if (expected instanceof int[] e) {
            int[] a = (int[]) actual;
            assertEquals(e.length, a.length);
            for (int i = 0; i < e.length; i++) {
                if (e[i] != a[i]) {
                    fail(name + " arg" + arg + "[" + i + "]: JVM=" + e[i] + " IR=" + a[i]);
                }
            }
        } else if (expected instanceof long[] e) {
            long[] a = (long[]) actual;
            for (int i = 0; i < e.length; i++) {
                if (e[i] != a[i]) {
                    fail(name + " arg" + arg + "[" + i + "]: JVM=" + e[i] + " IR=" + a[i]);
                }
            }
        } else if (expected instanceof float[] e) {
            float[] a = (float[]) actual;
            for (int i = 0; i < e.length; i++) {
                if (!sameFloat(e[i], a[i], ulps)) {
                    fail(name + " arg" + arg + "[" + i + "]: JVM=" + e[i] + " IR=" + a[i]);
                }
            }
        } else if (expected instanceof double[] e) {
            double[] a = (double[]) actual;
            for (int i = 0; i < e.length; i++) {
                if (!sameDouble(e[i], a[i], ulps)) {
                    fail(name + " arg" + arg + "[" + i + "]: JVM=" + e[i] + " IR=" + a[i]);
                }
            }
        } else if (expected instanceof byte[] e) {
            assertEquals(List.of(box(e)), List.of(box((byte[]) actual)), name + " arg" + arg);
        } else if (expected instanceof short[] e) {
            short[] a = (short[]) actual;
            for (int i = 0; i < e.length; i++) {
                assertEquals(e[i], a[i], name + " arg" + arg + "[" + i + "]");
            }
        } else if (expected instanceof char[] e) {
            char[] a = (char[]) actual;
            for (int i = 0; i < e.length; i++) {
                assertEquals(e[i], a[i], name + " arg" + arg + "[" + i + "]");
            }
        } else if (expected instanceof boolean[] e) {
            boolean[] a = (boolean[]) actual;
            for (int i = 0; i < e.length; i++) {
                assertEquals(e[i], a[i], name + " arg" + arg + "[" + i + "]");
            }
        } else if (expected instanceof Object[] e) {
            Object[] a = (Object[]) actual;
            assertEquals(e.length, a.length);
            for (int i = 0; i < e.length; i++) {
                String why = objectDifference(e[i], a[i], ulps);
                if (why != null) {
                    fail(name + " arg" + arg + "[" + i + "]: " + why + " (JVM=" + describeObject(e[i]) + ", IR="
                            + describeObject(a[i]) + ")");
                }
            }
        }
        // скалярные аргументы не изменяются
    }

    /**
     * Поэлементное сравнение объектов хоста (векторы и struct) по открытым полям в порядке объявления; вещественные
     * - в ULP, остальное точно.
     *
     * @return причина расхождения либо {@code null}
     */
    static String objectDifference(Object e, Object a, int ulps) {
        if (e == null || a == null) {
            return e == a ? null : "null versus a value";
        }
        if (e.getClass() != a.getClass()) {
            return "different classes";
        }
        for (java.lang.reflect.Field f : e.getClass().getDeclaredFields()) {
            int mod = f.getModifiers();
            if (!java.lang.reflect.Modifier.isPublic(mod) || java.lang.reflect.Modifier.isStatic(mod)) {
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
                } else if (x == null || x.getClass().isPrimitive() || x instanceof Number || x instanceof Boolean
                        || x instanceof Character) {
                    why = java.util.Objects.equals(x, y) ? null : "field " + f.getName();
                } else {
                    String inner = objectDifference(x, y, ulps);
                    why = inner == null ? null : "field " + f.getName() + ": " + inner;
                }
                if (why != null) {
                    return why;
                }
            } catch (IllegalAccessException ex) {
                throw new AssertionError(ex);
            }
        }
        return null;
    }

    private static String describeObject(Object o) {
        if (o == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder(o.getClass().getSimpleName()).append('{');
        for (java.lang.reflect.Field f : o.getClass().getDeclaredFields()) {
            int mod = f.getModifiers();
            if (java.lang.reflect.Modifier.isPublic(mod) && !java.lang.reflect.Modifier.isStatic(mod)) {
                try {
                    sb.append(f.getName()).append('=').append(f.get(o)).append(' ');
                } catch (IllegalAccessException ex) {
                    sb.append(f.getName()).append("=?");
                }
            }
        }
        return sb.append('}').toString();
    }

    private static Byte[] box(byte[] a) {
        Byte[] r = new Byte[a.length];
        for (int i = 0; i < a.length; i++) {
            r[i] = a[i];
        }
        return r;
    }

    /** Все NaN считаются равными (полезная нагрузка NaN не определена); остальное - расстояние в ULP. */
    static boolean sameFloat(float e, float a, int ulps) {
        if (Float.isNaN(e) || Float.isNaN(a)) {
            return Float.isNaN(e) && Float.isNaN(a);
        }
        if (Float.floatToRawIntBits(e) == Float.floatToRawIntBits(a)) {
            return true;
        }
        if (ulps == 0 || Float.isInfinite(e) || Float.isInfinite(a)) {
            return false;
        }
        return Math.abs(ordered(Float.floatToRawIntBits(e)) - ordered(Float.floatToRawIntBits(a))) <= ulps;
    }

    static boolean sameDouble(double e, double a, int ulps) {
        if (Double.isNaN(e) || Double.isNaN(a)) {
            return Double.isNaN(e) && Double.isNaN(a);
        }
        if (Double.doubleToRawLongBits(e) == Double.doubleToRawLongBits(a)) {
            return true;
        }
        if (ulps == 0 || Double.isInfinite(e) || Double.isInfinite(a)) {
            return false;
        }
        return Math.abs(orderedLong(Double.doubleToRawLongBits(e)) - orderedLong(Double.doubleToRawLongBits(a))) <= ulps;
    }

    /** Монотонное отображение битов float в целые: соседние float отличаются на 1, {@code -0} и {@code +0} совпадают. */
    private static long ordered(int bits) {
        return bits < 0 ? Integer.MIN_VALUE - (long) bits : bits;
    }

    private static long orderedLong(long bits) {
        return bits < 0 ? Long.MIN_VALUE - bits : bits;
    }

    /** Диагностика в виде текста для сообщений. */
    static String describe(List<Diagnostic> diagnostics) {
        return diagnostics.toString();
    }
}
