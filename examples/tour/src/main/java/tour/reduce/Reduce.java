package tour.reduce;

import com.sixik.gpu.api.Gpu;
import com.sixik.gpu.api.annotations.Reduction;
import com.sixik.gpu.api.annotations.Struct;
import com.sixik.gpu.api.types.Float3;
import com.sixik.gpu.api.types.GpuFloatArray;
import tour.Show;

import java.util.Locale;
import java.util.Random;

/**
 * Урок 8. Свёртки: {@code @Reduction}.
 *
 * <p>Посчитать по массиву одно число - сумму, количество, минимум - на GPU неудобно вручную (урок {@code local}).
 * {@code @Reduction} позволяет написать <b>обычный последовательный цикл</b>, а рантайм сам исполнит его параллельно:
 * каждый work-item проходит свою часть итераций, группы складывают значения деревом, хост сворачивает суммы групп.
 * Вызов возвращает результат.</p>
 *
 * <p>Форма тела: вычисления до цикла; аккумулятор с начальным значением; {@code for (int i = start; i < end; i++)};
 * аккумулятор меняется только как {@code acc = acc op e} ({@code +=}, {@code ++}, {@code Gpu.min/max}, {@code &=},
 * {@code |=}, {@code ^=}); {@code return acc} сразу после цикла. Итерации независимы: не писать в память, не менять
 * переменные снаружи, не выходить {@code break}. Несколько аккумуляторов - несколько результатов за один проход:
 * верните {@code new MinMax(lo, hi)} ({@code @Struct}) или вектор; вектор-аккумулятор сворачивается покомпонентно.</p>
 *
 * <p>Порядок сложения на GPU другой, поэтому суммы {@code float/double} могут отличаться в младших битах; целые и
 * min/max - точно. Попробуйте: добавить {@code break} в цикл ({@code GPU0018} с причиной); прочитать аккумулятор
 * в условии; посчитать дисперсию (сумма и сумма квадратов в одном {@code @Struct}).</p>
 */
public final class Reduce {

    private Reduce() {
    }

    /** Наименьшее и наибольшее значение. */
    @Struct
    public static final class MinMax {
        public float lo;
        public float hi;

        /** Пустое значение (нужно хосту). */
        public MinMax() {
        }

        /**
         * @param lo минимум
         * @param hi максимум
         */
        public MinMax(float lo, float hi) {
            this.lo = lo;
            this.hi = hi;
        }
    }

    /** Сумма, число и максимум значений выше порога. */
    @Struct
    public static final class Above {
        public double sum;
        public int count;
        public float max;

        /** Пустое значение (нужно хосту). */
        public Above() {
        }

        /**
         * @param sum   сумма
         * @param count число
         * @param max   максимум
         */
        public Above(double sum, int count, float max) {
            this.sum = sum;
            this.count = count;
            this.max = max;
        }
    }

    /**
     * @param v значения
     * @return сумма (накапливается в {@code double})
     */
    @Reduction
    public static double sum(GpuFloatArray v) {
        double s = 0;
        for (int i = 0; i < v.length(); i++) {
            s += v.get(i);
        }
        return s;
    }

    /**
     * @param v значения
     * @return минимум и максимум за один проход
     */
    @Reduction
    public static MinMax range(GpuFloatArray v) {
        float lo = Float.POSITIVE_INFINITY;
        float hi = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < v.length(); i++) {
            lo = Gpu.min(lo, v.get(i));
            hi = Gpu.max(hi, v.get(i));
        }
        return new MinMax(lo, hi);
    }

    /**
     * @param v         значения
     * @param threshold порог
     * @return сумма, число и максимум значений выше порога - три аккумулятора разных типов
     */
    @Reduction
    public static Above above(GpuFloatArray v, float threshold) {
        double sum = 0;
        int count = 0;
        float max = threshold;
        for (int i = 0; i < v.length(); i++) {
            float x = v.get(i);
            if (x > threshold) {
                sum += x;
                count++;
                max = Gpu.max(max, x);
            }
        }
        return new Above(sum, count, max);
    }

    /**
     * @param xs координаты x
     * @param ys координаты y
     * @param zs координаты z
     * @return сумма точек - векторный аккумулятор (центр масс = сумма / число)
     */
    @Reduction
    public static Float3 total(GpuFloatArray xs, GpuFloatArray ys, GpuFloatArray zs) {
        Float3 acc = new Float3(0f, 0f, 0f);
        for (int i = 0; i < xs.length(); i++) {
            acc = acc.add(new Float3(xs.get(i), ys.get(i), zs.get(i)));
        }
        return acc;
    }

    /** Программа урока. */
    public static void run() {
        int n = 1 << 22;
        Random rnd = new Random(7);
        GpuFloatArray v = new GpuFloatArray(n);
        double want = 0;
        float lo = Float.POSITIVE_INFINITY;
        float hi = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < n; i++) {
            float x = (float) rnd.nextGaussian();
            v.set(i, x);
            want += x;
            lo = Math.min(lo, x);
            hi = Math.max(hi, x);
        }
        double s = sum(v);
        MinMax r = range(v);
        Above a = above(v, 2f);
        System.out.printf(Locale.ROOT, "sum %.4f (JVM loop %.4f)%n", s, want);
        System.out.printf(Locale.ROOT, "range [%.4f, %.4f] (JVM loop [%.4f, %.4f])%n", r.lo, r.hi, lo, hi);
        System.out.printf(Locale.ROOT, "above 2: %d values, mean %.4f, max %.4f%n", a.count, a.sum / a.count, a.max);
        Show.check(Math.abs(s - want) < 1e-6 * n, "sum");
        Show.check(r.lo == lo && r.hi == hi, "min and max are exact");

        Show.section("vector accumulator: centre of mass of a ring");
        int m = 100_000;
        GpuFloatArray xs = new GpuFloatArray(m);
        GpuFloatArray ys = new GpuFloatArray(m);
        GpuFloatArray zs = new GpuFloatArray(m);
        for (int i = 0; i < m; i++) {
            double t = 2 * Math.PI * i / m;
            xs.set(i, (float) (5 + Math.cos(t)));
            ys.set(i, (float) (-2 + Math.sin(t)));
            zs.set(i, 1f);
        }
        Float3 c = total(xs, ys, zs);
        System.out.printf(Locale.ROOT, "centre (%.3f, %.3f, %.3f), expected (5, -2, 1)%n", c.x / m, c.y / m, c.z / m);
        Show.check(Math.abs(c.x / m - 5) < 1e-3 && Math.abs(c.y / m + 2) < 1e-3, "centre of mass");

        Show.section("one pass for two numbers");
        Show.time("range (min and max together)", Show.millis(10, () -> range(v)));
        Show.time("sum", Show.millis(10, () -> sum(v)));
    }
}
