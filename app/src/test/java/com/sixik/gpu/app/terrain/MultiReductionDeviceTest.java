package com.sixik.gpu.app.terrain;

import com.sixik.gpu.api.types.GpuFloatArray;
import com.sixik.gpu.api.types.GpuIntArray;
import com.sixik.gpu.ir.Result;
import com.sixik.gpu.runtime.GpuContext;
import com.sixik.gpu.runtime.JavaToGpu;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Locale;
import java.util.SplittableRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Свёртки с несколькими аккумуляторами на устройстве: результат-{@code @Struct} совпадает с последовательным (минимум,
 * максимум и целые суммы точны), и один проход {@code range} сравнивается по времени с двумя свёртками {@code low} +
 * {@code high}. Данные в {@code GpuFloatArray} загружены заранее, поэтому замер - время самих проходов. Печатает медианы.
 */
@Tag("device")
class MultiReductionDeviceTest {

    private static final int RUNS = 30;

    private static GpuContext open() {
        Result<GpuContext> rc = GpuContext.builder().ignoreEnvironment().noDiskCache()
                .fallback(GpuContext.Fallback.NEVER).tryBuild();
        Assumptions.assumeTrue(rc.isOk(), "no OpenCL device");
        return rc.value();
    }

    @Test
    void structResultsMatchTheSequentialOnes() {
        try (GpuContext ctx = open(); JavaToGpu.Scope s = JavaToGpu.use(ctx)) {
            SplittableRandom rnd = new SplittableRandom(7);
            for (int n : new int[]{1, 255, 256, 257, 100_000, 3_000_001}) {
                GpuFloatArray f = new GpuFloatArray(n);
                GpuIntArray k = new GpuIntArray(n);
                float lo = Float.POSITIVE_INFINITY;
                float hi = Float.NEGATIVE_INFINITY;
                long sum = 0;
                int count = 0;
                int max = 0;
                for (int i = 0; i < n; i++) {
                    float x = (float) (rnd.nextDouble() * 2000 - 1000);
                    int y = rnd.nextInt(-1_000_000, 1_000_000);
                    f.set(i, x);
                    k.set(i, y);
                    lo = Math.min(lo, x);
                    hi = Math.max(hi, x);
                    if (y > 0) {
                        sum += y;
                        count++;
                        max = Math.max(max, y);
                    }
                }
                RangeReductions.MinMax r = RangeReductions.range(f);
                assertEquals(lo, r.lo, "n=" + n);
                assertEquals(hi, r.hi, "n=" + n);
                RangeReductions.Positives p = RangeReductions.positives(k);
                assertEquals(sum, p.sum, "n=" + n);
                assertEquals(count, p.count, "n=" + n);
                assertEquals(max, p.max, "n=" + n);

                // векторные аккумуляторы: Float3 (на устройстве 16 байт) и Int4 внутри struct
                double total = 0;
                int positive = 0;
                int[] top = {Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
                for (int i = 0; i < n; i++) {
                    total += f.get(i);
                    positive += f.get(i) > 0f ? 1 : 0;
                    int y = k.get(i);
                    top[0] = Math.max(top[0], y);
                    top[1] = Math.max(top[1], -y);
                    top[2] = Math.max(top[2], y % 1000);
                    top[3] = Math.max(top[3], y / 7);
                }
                com.sixik.gpu.api.types.Float3 t = RangeReductions.tally(f);
                assertEquals(n, t.x, "n=" + n);
                assertEquals(positive, t.y, "n=" + n);
                assertEquals(total, t.z, Math.max(1e-2, Math.abs(total) * 1e-4) + n * 1e-3, "n=" + n);
                RangeReductions.Box b = RangeReductions.box(k);
                assertEquals(java.util.List.of(top[0], top[1], top[2], top[3]), java.util.List.of(b.hi.x, b.hi.y, b.hi.z,
                        b.hi.w), "n=" + n);
                assertEquals(n, b.count, "n=" + n);
            }
        }
    }

    @Test
    void onePassVersusTwoReductions() {
        try (GpuContext ctx = open(); JavaToGpu.Scope s = JavaToGpu.use(ctx)) {
            for (int n : new int[]{100_000, 1 << 20, 1 << 24}) {
                GpuFloatArray v = new GpuFloatArray(n);
                for (int i = 0; i < n; i++) {
                    v.set(i, (i * 7919 % 10007) - 5000f);
                }
                long[] one = new long[RUNS];
                long[] two = new long[RUNS];
                for (int i = 0; i < RUNS + 5; i++) {
                    long t = System.nanoTime();
                    RangeReductions.MinMax r = RangeReductions.range(v);
                    long t1 = System.nanoTime() - t;
                    t = System.nanoTime();
                    float lo = RangeReductions.low(v);
                    float hi = RangeReductions.high(v);
                    long t2 = System.nanoTime() - t;
                    assertEquals(lo, r.lo);
                    assertEquals(hi, r.hi);
                    if (i >= 5) {
                        one[i - 5] = t1;
                        two[i - 5] = t2;
                    }
                }
                Arrays.sort(one);
                Arrays.sort(two);
                System.out.printf(Locale.ROOT, "n=%,d: range (one pass) %.3f ms, low + high %.3f ms%n", n,
                        one[RUNS / 2] / 1e6, two[RUNS / 2] / 1e6);
            }
        }
    }
}
