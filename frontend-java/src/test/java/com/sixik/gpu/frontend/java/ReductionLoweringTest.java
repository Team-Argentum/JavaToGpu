package com.sixik.gpu.frontend.java;

import com.sixik.gpu.ir.IrModule;
import com.sixik.gpu.ir.Result;
import com.sixik.gpu.ir.Types;
import com.sixik.gpu.ir.interpreter.Interpreter;
import com.sixik.gpu.ir.interpreter.NdRange;
import com.sixik.gpu.ir.optimizer.Reductions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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
     * @return результат свёртки (упакованный) либо, для нескольких аккумуляторов, список итогов по аккумуляторам
     */
    private static Object runParallel(IrModule m, int groups, Object... args) {
        Result<Reductions.Lowered> l = Reductions.lower(m, WG);
        assertTrue(l.isOk(), String.valueOf(l.diagnostics()));
        Reductions.Lowered lowered = l.value();
        List<Reductions.Accumulator> accs = lowered.plan().accumulators();
        Object[] full = Arrays.copyOf(args, args.length + accs.size());
        for (int j = 0; j < accs.size(); j++) {
            full[lowered.partialParam() + j] = switch (accs.get(j).type()) {
                case Types.I32 -> new int[groups];
                case Types.I64 -> new long[groups];
                case Types.F32 -> new float[groups];
                default -> new double[groups];
            };
        }
        var run = Interpreter.run(lowered.module(), NdRange.of1D(groups * WG, WG), full);
        assertTrue(run.isOk(), String.valueOf(run.diagnostics()));
        List<Object> results = new ArrayList<>();
        for (int j = 0; j < accs.size(); j++) {
            results.add(combine(accs.get(j).combine(), full[lowered.partialParam() + j]));
        }
        return lowered.plan().aggregate() ? results : results.get(0);
    }

    /** Единственный аккумулятор распознанной свёртки. */
    private static Reductions.Accumulator single(Reductions.Plan p) {
        assertEquals(1, p.accumulators().size());
        return p.accumulators().get(0);
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
        if (sums instanceof double[] a) {
            double r = a[0];
            for (int i = 1; i < a.length; i++) {
                r = c == Reductions.Combine.MAX ? Math.max(r, a[i]) : c == Reductions.Combine.MIN ? Math.min(r, a[i])
                        : r + a[i];
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
        assertEquals(new Reductions.Accumulator(Reductions.Combine.ADD, Types.I32), single(p));
        assertFalse(p.aggregate());
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
        assertEquals(Reductions.Combine.MIN, single(Reductions.analyze(min).value()).combine());
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

    // ---- несколько аккумуляторов ------------------------------------------------------------------

    private static final String MIN_MAX = """
            @Struct
            public static final class MinMax {
                public float lo;
                public float hi;
                public MinMax() { }
                public MinMax(float lo, float hi) { this.lo = lo; this.hi = hi; }
            }
            """;

    @Test
    void minimumAndMaximumInOnePassIntoAStruct() {
        IrModule m = module(MIN_MAX + """
                @Reduction
                public static MinMax range(float[] a, int n) {
                    float lo = 1000f;
                    float hi = -1000f;
                    for (int i = 0; i < n; i++) {
                        float x = a[i] * 3f;
                        lo = Gpu.min(lo, x);
                        hi = Gpu.max(x, hi);
                    }
                    return new MinMax(lo, hi);
                }
                """);
        Reductions.Plan p = Reductions.analyze(m).value();
        assertTrue(p.aggregate());
        assertEquals(List.of(new Reductions.Accumulator(Reductions.Combine.MIN, Types.F32),
                new Reductions.Accumulator(Reductions.Combine.MAX, Types.F32)), p.accumulators());
        agrees(m, n -> new Object[]{floats(n), n}, n -> {
            float lo = 1000f;
            float hi = -1000f;
            for (float x : floats(n)) {
                lo = Math.min(lo, x * 3f);
                hi = Math.max(hi, x * 3f);
            }
            return List.of(lo, hi);
        });
    }

    @Test
    void mixedTypesAndOperationsIntoAStructAndAVector() {
        IrModule stats = module("""
                @Struct
                public static final class Stats {
                    public double sum;
                    public int count;
                    public long bits;
                    public Stats() { }
                    public Stats(double sum, int count, long bits) { this.sum = sum; this.count = count; this.bits = bits; }
                }

                @Reduction
                public static Stats stats(int[] a) {
                    double sum = 0.5;
                    int count = 0;
                    long bits = 0L;
                    for (int i = 0; i < a.length; i++) {
                        int v = a[i];
                        if (v > 0) {
                            sum += v;
                            count++;
                        }
                        bits |= 1L << (v & 63);
                    }
                    return new Stats(sum, count, bits);
                }
                """);
        assertEquals(List.of(Reductions.Combine.ADD, Reductions.Combine.ADD, Reductions.Combine.OR),
                Reductions.analyze(stats).value().accumulators().stream().map(Reductions.Accumulator::combine).toList());
        agrees(stats, n -> new Object[]{ints(n)}, n -> {
            double sum = 0.5;
            int count = 0;
            long bits = 0;
            for (int v : ints(n)) {
                if (v > 0) {
                    sum += v;
                    count++;
                }
                bits |= 1L << (v & 63);
            }
            return List.of(sum, count, bits);
        });
        IrModule vec = module("""
                @Reduction
                public static Int2 signs(int[] a, int n) {
                    int neg = 0;
                    int pos = 0;
                    for (int i = 0; i < n; i++) {
                        neg += a[i] < 0 ? 1 : 0;
                        pos += a[i] > 0 ? 1 : 0;
                    }
                    return new Int2(neg, pos);
                }
                """);
        agrees(vec, n -> new Object[]{ints(n), n}, n -> {
            int neg = 0;
            int pos = 0;
            for (int v : ints(n)) {
                neg += v < 0 ? 1 : 0;
                pos += v > 0 ? 1 : 0;
            }
            return List.of(neg, pos);
        });
    }

    @Test
    void vectorAccumulatorsReduceComponentWise() {
        // сумма Float3, возвращённая как есть
        IrModule sum = module("""
                @Reduction
                public static Float3 centroid(float[] a, int n) {
                    Float3 acc = new Float3(1f, 0f, 0f);
                    for (int i = 0; i < n; i++) {
                        acc = acc.add(new Float3(a[i], a[i] * 2f, 1f));
                    }
                    return acc;
                }
                """);
        Reductions.Plan p = Reductions.analyze(sum).value();
        assertEquals(3, p.accumulators().size(), "one accumulator per component");
        agrees(sum, n -> new Object[]{floats(n), n}, n -> {
            float x = 1f;
            float y = 0f;
            float z = 0f;
            for (float v : floats(n)) {
                x += v;
                y += v * 2f;
                z += 1f;
            }
            return List.of(x, y, z);
        });
        // покомпонентный max Int4 и вектор внутри struct рядом со скаляром
        IrModule mixed = module("""
                @Struct
                public static final class Box {
                    public Int4 hi;
                    public int count;
                    public Box() { this.hi = new Int4(0, 0, 0, 0); }
                    public Box(Int4 hi, int count) { this.hi = hi; this.count = count; }
                }

                @Reduction
                public static Box box(int[] a, int n) {
                    Int4 hi = new Int4(-1 << 30, -1 << 30, -1 << 30, -1 << 30);
                    int count = 0;
                    for (int i = 0; i < n; i++) {
                        int v = a[i];
                        hi = Gpu.max(hi, new Int4(v, -v, v % 7, v / 3));
                        count += 1;
                    }
                    return new Box(hi, count);
                }
                """);
        assertEquals(5, Reductions.analyze(mixed).value().accumulators().size());
        agrees(mixed, n -> new Object[]{ints(n), n}, n -> {
            int[] hi = {-1 << 30, -1 << 30, -1 << 30, -1 << 30};
            int count = 0;
            for (int v : ints(n)) {
                hi[0] = Math.max(hi[0], v);
                hi[1] = Math.max(hi[1], -v);
                hi[2] = Math.max(hi[2], v % 7);
                hi[3] = Math.max(hi[3], v / 3);
                count++;
            }
            return List.of(hi[0], hi[1], hi[2], hi[3], count);
        });
        rejects("GPU0018", "at most 64 are supported", """
                @Struct
                public static final class Big {
                    public Double8 s;
                    public int n;
                    public Big() { this.s = Double8.splat(0); }
                    public Big(Double8 s, int n) { this.s = s; this.n = n; }
                }

                @Reduction
                public static Big big(double[] a) {
                    Double8 s = Double8.splat(0);
                    int n = 0;
                    for (int i = 0; i < a.length; i++) {
                        s = s.add(Double8.splat(a[i]));
                        n++;
                    }
                    return new Big(s, n);
                }
                """);
    }

    @Test
    void severalAccumulatorRulesAreEnforced() {
        rejects("GPU0018", "must be an accumulator variable", MIN_MAX + """
                @Reduction
                public static MinMax half(float[] a) {
                    float lo = 0f;
                    for (int i = 0; i < a.length; i++) {
                        lo = Gpu.min(lo, a[i]);
                    }
                    return new MinMax(lo, 1f);
                }
                """);
        rejects("GPU0018", "used for two components", MIN_MAX + """
                @Reduction
                public static MinMax twice(float[] a) {
                    float lo = 0f;
                    for (int i = 0; i < a.length; i++) {
                        lo = Gpu.min(lo, a[i]);
                    }
                    return new MinMax(lo, lo);
                }
                """);
        rejects("GPU0018", "not read otherwise", MIN_MAX + """
                @Reduction
                public static MinMax coupled(float[] a) {
                    float lo = 0f;
                    float hi = 0f;
                    for (int i = 0; i < a.length; i++) {
                        lo = Gpu.min(lo, a[i]);
                        hi = Gpu.max(hi, lo);
                    }
                    return new MinMax(lo, hi);
                }
                """);
        rejects("GPU0018", "never updates the accumulator hi", MIN_MAX + """
                @Reduction
                public static MinMax idle(float[] a) {
                    float lo = 0f;
                    float hi = 0f;
                    for (int i = 0; i < a.length; i++) {
                        lo = Gpu.min(lo, a[i]);
                    }
                    return new MinMax(lo, hi);
                }
                """);
        rejects("GPU0018", "is not int, long, float or double or a vector of them", MIN_MAX + """
                @Struct
                public static final class Box {
                    public MinMax r;
                    public int n;
                    public Box() { this.r = new MinMax(); }
                    public Box(MinMax r, int n) { this.r = r; this.n = n; }
                }

                @Reduction
                public static Box nested(float[] a) {
                    int n = 0;
                    for (int i = 0; i < a.length; i++) {
                        n++;
                    }
                    return new Box(new MinMax(0f, 0f), n);
                }
                """);
        rejects("GPU0018", "at most 8 accumulators", """
                @Reduction
                public static Float16 wide(float[] a) {
                    float s = 0f;
                    for (int i = 0; i < a.length; i++) {
                        s += a[i];
                    }
                    return new Float16(s, s, s, s, s, s, s, s, s, s, s, s, s, s, s, s);
                }
                """);
    }

    // ---- нарушения шаблона ------------------------------------------------------------------------

    @Test
    void signatureErrors() {
        rejects("GPU0016", "must return int, long, float, double, or a vector or @Struct", """
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
