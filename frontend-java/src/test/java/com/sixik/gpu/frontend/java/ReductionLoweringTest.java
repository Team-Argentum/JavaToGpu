package com.sixik.gpu.frontend.java;

import com.sixik.gpu.ir.IrModule;
import com.sixik.gpu.ir.Result;
import com.sixik.gpu.ir.Types;
import com.sixik.gpu.ir.interpreter.Interpreter;
import com.sixik.gpu.ir.interpreter.NdRange;
import com.sixik.gpu.ir.optimizer.Reductions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.function.IntFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Свёртки {@code @Reduction}: фронтенд пишет тело как device-функцию с результатом, {@link Reductions} распознаёт
 * шаблон и строит параллельное ядро. Ядро исполняется интерпретатором IR при разном числе групп и итераций, суммы групп
 * сворачиваются как на хосте и сверяются с последовательным результатом; нарушения шаблона - ошибки компиляции.
 */
class ReductionLoweringTest {

    private static final String HEAD = """
            package demo;

            import com.sixik.gpu.api.Gpu;
            import com.sixik.gpu.api.annotations.*;
            import com.sixik.gpu.api.types.*;

            public final class R {
            """;

    /** Небольшая группа: интерпретатор исполняет work-item'ы последовательно. */
    private static final int WG = 16;
    private static final int[] SIZES = {0, 1, 5, 16, 17, 100, 333};
    private static final int[] GROUPS = {1, 3, 8};

    private static FrontendHarness.Outcome compile(String members) {
        return FrontendHarness.compile(HEAD + members + "\n}\n");
    }

    private static IrModule module(String members) {
        FrontendHarness.Outcome r = compile(members);
        assertTrue(r.success(), r.describe());
        return r.module();
    }

    private static void rejects(String code, String fragment, String members) {
        FrontendHarness.Outcome r = compile(members);
        assertFalse(r.success(), "expected error " + code + "\n" + r.describe());
        assertTrue(r.errorCodes().contains(code), code + " was not reported; reported: " + r.errorCodes() + "\n"
                + r.describe());
        assertTrue(r.describe().contains(fragment), "message must mention '" + fragment + "':\n" + r.describe());
    }

    /**
     * Исполняет построенное ядро свёртки интерпретатором с заданным числом групп и сворачивает суммы групп.
     *
     * @param m      модуль фронтенда
     * @param groups число групп
     * @param args   аргументы исходного метода
     * @return результат свёртки (упакованный)
     */
    private static Object runParallel(IrModule m, int groups, Object... args) {
        Result<Reductions.Lowered> l = Reductions.lower(m, WG);
        assertTrue(l.isOk(), String.valueOf(l.diagnostics()));
        Reductions.Lowered lowered = l.value();
        int type = lowered.plan().type();
        Object partial = switch (type) {
            case Types.I32 -> new int[groups];
            case Types.I64 -> new long[groups];
            case Types.F32 -> new float[groups];
            default -> new double[groups];
        };
        Object[] full = Arrays.copyOf(args, args.length + 1);
        full[args.length] = partial;
        var run = Interpreter.run(lowered.module(), NdRange.of1D(groups * WG, WG), full);
        assertTrue(run.isOk(), String.valueOf(run.diagnostics()));
        return combine(lowered.plan().combine(), partial);
    }

    /** Свёртка сумм групп по порядку - так же, как это делает рантайм. */
    private static Object combine(Reductions.Combine c, Object sums) {
        if (sums instanceof int[] a) {
            int r = a[0];
            for (int i = 1; i < a.length; i++) {
                r = switch (c) {
                    case ADD -> r + a[i];
                    case MIN -> Math.min(r, a[i]);
                    case MAX -> Math.max(r, a[i]);
                    case AND -> r & a[i];
                    case OR -> r | a[i];
                    case XOR -> r ^ a[i];
                };
            }
            return r;
        }
        if (sums instanceof long[] a) {
            long r = a[0];
            for (int i = 1; i < a.length; i++) {
                r = switch (c) {
                    case ADD -> r + a[i];
                    case MIN -> Math.min(r, a[i]);
                    case MAX -> Math.max(r, a[i]);
                    case AND -> r & a[i];
                    case OR -> r | a[i];
                    case XOR -> r ^ a[i];
                };
            }
            return r;
        }
        float[] a = (float[]) sums;
        float r = a[0];
        for (int i = 1; i < a.length; i++) {
            r = c == Reductions.Combine.MAX ? Math.max(r, a[i]) : c == Reductions.Combine.MIN ? Math.min(r, a[i])
                    : r + a[i];
        }
        return r;
    }

    private static float[] floats(int n) {
        float[] a = new float[n];
        for (int i = 0; i < n; i++) {
            a[i] = ((i * 37) % 23) - 11.5f;
        }
        return a;
    }

    private static int[] ints(int n) {
        int[] a = new int[n];
        for (int i = 0; i < n; i++) {
            a[i] = (i * 7919) % 1009 - 500;
        }
        return a;
    }

    /** Для всех размеров и чисел групп параллельный результат совпадает с последовательным. */
    private static void agrees(IrModule m, IntFunction<Object[]> args, IntFunction<Object> expected) {
        for (int n : SIZES) {
            for (int groups : GROUPS) {
                assertEquals(expected.apply(n), runParallel(m, groups, args.apply(n)), "n=" + n + " groups=" + groups);
            }
        }
    }

    @Test
    void countWithAConditionalIncrement() {
        IrModule m = module("""
                @Reduction
                public static int positives(GpuFloatArray v, int n) {
                    int count = 0;
                    for (int i = 0; i < n; i++) {
                        if (v.get(i) > 0f) {
                            count++;
                        }
                    }
                    return count;
                }
                """);
        Reductions.Plan p = Reductions.analyze(m).value();
        assertEquals(Reductions.Combine.ADD, p.combine());
        assertEquals(Types.I32, p.type());
        assertEquals(new Reductions.Bound(Reductions.Bound.Kind.CONST, 0), p.start());
        assertEquals(new Reductions.Bound(Reductions.Bound.Kind.PARAM, 1), p.end());
        agrees(m, n -> new Object[]{floats(n), n}, n -> {
            int c = 0;
            for (float x : floats(n)) {
                c += x > 0f ? 1 : 0;
            }
            return c;
        });
    }

    @Test
    void initialValueCountsOnceAndMinusIsAddition() {
        IrModule m = module("""
                @Reduction
                public static long balance(int[] a, long opening) {
                    int n = a.length;
                    long total = opening * 2;
                    for (int i = 0; i < n; i++) {
                        total -= a[i];
                    }
                    return total;
                }
                """);
        Reductions.Plan p = Reductions.analyze(m).value();
        assertEquals(new Reductions.Bound(Reductions.Bound.Kind.LENGTH, 0), p.end(), "end through a prologue local");
        agrees(m, n -> new Object[]{ints(n), 1000L}, n -> {
            long t = 2000;
            for (int x : ints(n)) {
                t -= x;
            }
            return t;
        });
    }

    @Test
    void maximumAndMinimum() {
        IrModule max = module("""
                @Reduction
                public static float peak(float[] a, int n) {
                    float best = -1000f;
                    for (int i = 0; i < n; i++) {
                        best = Gpu.max(best, a[i] * 2f);
                    }
                    return best;
                }
                """);
        agrees(max, n -> new Object[]{floats(n), n}, n -> {
            float b = -1000f;
            for (float x : floats(n)) {
                b = Math.max(b, x * 2f);
            }
            return b;
        });
        IrModule min = module("""
                @Reduction
                public static int lowest(int[] a, int n) {
                    int low = 1 << 20;
                    for (int i = 0; i < n; i++) {
                        low = Gpu.min(a[i], low);
                    }
                    return low;
                }
                """);
        assertEquals(Reductions.Combine.MIN, Reductions.analyze(min).value().combine());
        agrees(min, n -> new Object[]{ints(n), n}, n -> {
            int b = 1 << 20;
            for (int x : ints(n)) {
                b = Math.min(b, x);
            }
            return b;
        });
    }

    @Test
    void xorOverARangeWithAnOffsetStart() {
        IrModule m = module("""
                @Reduction
                public static int hash(int[] a, int from, int to) {
                    int h = 0;
                    for (int i = from; i < to; i++) {
                        h ^= a[i] * 31 + i;
                    }
                    return h;
                }
                """);
        Reductions.Plan p = Reductions.analyze(m).value();
        assertEquals(new Reductions.Bound(Reductions.Bound.Kind.PARAM, 1), p.start());
        agrees(m, n -> new Object[]{ints(n + 3), 3, n + 3}, n -> {
            int h = 0;
            int[] a = ints(n + 3);
            for (int i = 3; i < n + 3; i++) {
                h ^= a[i] * 31 + i;
            }
            return h;
        });
    }

    @Test
    void bodyMayUseItsOwnLocalsLoopsPrivateArraysAndDeviceFunctions() {
        IrModule m = module("""
                @DeviceFunction
                static int weight(int x) {
                    return x < 0 ? -x : x;
                }

                @Reduction
                public static int busy(int[] a, int n) {
                    int sum = 0;
                    for (int i = 0; i < n; i++) {
                        int[] digits = new int[4];
                        int v = weight(a[i]);
                        int k = 0;
                        while (true) {
                            if (k == 4 || v == 0) {
                                break;
                            }
                            digits[k] = v % 10;
                            v = v / 10;
                            k++;
                        }
                        for (int j = 0; j < k; j++) {
                            if (digits[j] == 7) {
                                continue;
                            }
                            sum += digits[j];
                        }
                    }
                    return sum;
                }
                """);
        agrees(m, n -> new Object[]{ints(n), n}, n -> {
            int sum = 0;
            for (int x : ints(n)) {
                int v = Math.abs(x);
                for (int k = 0; k < 4 && v != 0; k++, v /= 10) {
                    int d = v % 10;
                    sum += d == 7 ? 0 : d;
                }
            }
            return sum;
        });
    }

    @Test
    void endsComputedOnTheDeviceAreUnknownToTheHost() {
        IrModule m = module("""
                @Reduction
                public static int firstHalf(int[] a) {
                    int s = 0;
                    for (int i = 0; i < a.length / 2; i++) {
                        s += a[i];
                    }
                    return s;
                }
                """);
        assertEquals(Reductions.Bound.Kind.UNKNOWN, Reductions.analyze(m).value().end().kind());
        agrees(m, n -> new Object[]{ints(n)}, n -> {
            int s = 0;
            int[] a = ints(n);
            for (int i = 0; i < n / 2; i++) {
                s += a[i];
            }
            return s;
        });
    }

    // ---- нарушения шаблона ------------------------------------------------------------------------

    @Test
    void signatureErrors() {
        rejects("GPU0016", "must return int, long, float or double", """
                @Reduction
                public static void nothing(int[] a) {
                }
                """);
        rejects("GPU0017", "both @Kernel and @Reduction", """
                @Kernel
                @Reduction
                public static void both(int[] a) {
                }
                """);
    }

    @Test
    void bodiesThatAreNotIndependentIterationsAreRejected() {
        rejects("GPU0018", "`break` out of the reduction loop", """
                @Reduction
                public static int r(int[] a, int n) {
                    int s = 0;
                    for (int i = 0; i < n; i++) {
                        if (a[i] < 0) {
                            break;
                        }
                        s += a[i];
                    }
                    return s;
                }
                """);
        rejects("GPU0018", "writing memory in the loop body", """
                @Reduction
                public static int r(int[] a, int n) {
                    int s = 0;
                    for (int i = 0; i < n; i++) {
                        a[i] = 0;
                        s++;
                    }
                    return s;
                }
                """);
        rejects("GPU0018", "variable prev is declared before the loop and changed in it", """
                @Reduction
                public static int r(int[] a, int n) {
                    int s = 0;
                    int prev = 0;
                    for (int i = 0; i < n; i++) {
                        s += a[i] - prev;
                        prev = a[i];
                    }
                    return s;
                }
                """);
        rejects("GPU0018", "not read otherwise", """
                @Reduction
                public static int r(int[] a, int n) {
                    int s = 0;
                    for (int i = 0; i < n; i++) {
                        if (s < 100) {
                            s += a[i];
                        }
                    }
                    return s;
                }
                """);
        rejects("GPU0018", "different operations", """
                @Reduction
                public static int r(int[] a, int n) {
                    int s = 0;
                    for (int i = 0; i < n; i++) {
                        s += a[i];
                        s ^= i;
                    }
                    return s;
                }
                """);
        rejects("GPU0018", "getGlobalId", """
                @Reduction
                public static int r(int[] a, int n) {
                    int s = 0;
                    for (int i = 0; i < n; i++) {
                        s += Gpu.getGlobalId(0);
                    }
                    return s;
                }
                """);
    }

    @Test
    void loopsOfAnotherShapeAreRejected() {
        rejects("GPU0018", "must step by one", """
                @Reduction
                public static int r(int[] a, int n) {
                    int s = 0;
                    for (int i = 0; i < n; i += 2) {
                        s += a[i];
                    }
                    return s;
                }
                """);
        rejects("GPU0018", "must be a loop", """
                @Reduction
                public static int r(int[] a, int n) {
                    int s = 0;
                    int i = 0;
                    while (i < n) {
                        s += a[i];
                        i++;
                    }
                    return s;
                }
                """);
        rejects("GPU0018", "must not depend on the loop index or the accumulator", """
                @Reduction
                public static int r(int[] a, int n) {
                    int s = 0;
                    for (int i = 0; i < n - s; i++) {
                        s += 1;
                    }
                    return s;
                }
                """);
    }
}
