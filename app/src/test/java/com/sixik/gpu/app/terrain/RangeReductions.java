package com.sixik.gpu.app.terrain;

import com.sixik.gpu.api.Gpu;
import com.sixik.gpu.api.annotations.Reduction;
import com.sixik.gpu.api.annotations.Struct;
import com.sixik.gpu.api.types.GpuFloatArray;
import com.sixik.gpu.api.types.GpuIntArray;
import com.sixik.gpu.api.types.Float3;
import com.sixik.gpu.api.types.Int4;

/** Свёртки для {@code MultiReductionDeviceTest}: несколько аккумуляторов против отдельных свёрток. */
final class RangeReductions {

    private RangeReductions() {
    }

    /** Наименьшее и наибольшее значение. */
    @Struct
    public static final class MinMax {
        public float lo;
        public float hi;

        public MinMax() {
        }

        public MinMax(float lo, float hi) {
            this.lo = lo;
            this.hi = hi;
        }
    }

    /** Сумма, число и наибольшее значение положительных элементов. */
    @Struct
    public static final class Positives {
        public long sum;
        public int count;
        public int max;

        public Positives() {
        }

        public Positives(long sum, int count, int max) {
            this.sum = sum;
            this.count = count;
            this.max = max;
        }
    }

    /** Покомпонентный максимум и число элементов. */
    @Struct
    public static final class Box {
        public Int4 hi;
        public int count;

        public Box() {
            this.hi = new Int4(0, 0, 0, 0);
        }

        public Box(Int4 hi, int count) {
            this.hi = hi;
            this.count = count;
        }
    }

    /** Число элементов, число положительных и сумма - векторный аккумулятор {@code Float3}. */
    @Reduction
    public static Float3 tally(GpuFloatArray v) {
        Float3 acc = new Float3(0f, 0f, 0f);
        for (int i = 0; i < v.length(); i++) {
            float x = v.get(i);
            acc = acc.add(new Float3(1f, x > 0f ? 1f : 0f, x));
        }
        return acc;
    }

    @Reduction
    public static Box box(GpuIntArray v) {
        Int4 hi = new Int4(Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE);
        int count = 0;
        for (int i = 0; i < v.length(); i++) {
            int x = v.get(i);
            hi = Gpu.max(hi, new Int4(x, -x, x % 1000, x / 7));
            count++;
        }
        return new Box(hi, count);
    }

    @Reduction
    public static MinMax range(GpuFloatArray v) {
        float lo = Float.POSITIVE_INFINITY;
        float hi = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < v.length(); i++) {
            float x = v.get(i);
            lo = Gpu.min(lo, x);
            hi = Gpu.max(hi, x);
        }
        return new MinMax(lo, hi);
    }

    /** Число положительных значений - свёртка по массиву плотностей (для автоматического слияния). */
    @Reduction
    public static int solid(GpuFloatArray density) {
        int count = 0;
        for (int i = 0; i < density.length(); i++) {
            if (density.get(i) > 0f) {
                count++;
            }
        }
        return count;
    }

    @Reduction
    public static float low(GpuFloatArray v) {
        float lo = Float.POSITIVE_INFINITY;
        for (int i = 0; i < v.length(); i++) {
            lo = Gpu.min(lo, v.get(i));
        }
        return lo;
    }

    @Reduction
    public static float high(GpuFloatArray v) {
        float hi = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < v.length(); i++) {
            hi = Gpu.max(hi, v.get(i));
        }
        return hi;
    }

    @Reduction
    public static Positives positives(GpuIntArray v) {
        long sum = 0L;
        int count = 0;
        int max = 0;
        for (int i = 0; i < v.length(); i++) {
            int x = v.get(i);
            if (x > 0) {
                sum += x;
                count++;
                max = Gpu.max(max, x);
            }
        }
        return new Positives(sum, count, max);
    }
}
