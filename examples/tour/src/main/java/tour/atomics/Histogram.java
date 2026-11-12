package tour.atomics;

import com.sixik.gpu.api.Gpu;
import com.sixik.gpu.api.annotations.Global;
import com.sixik.gpu.api.annotations.Kernel;
import com.sixik.gpu.api.types.GlobalIntPtr;
import tour.Show;

import java.util.Locale;
import java.util.Random;

/**
 * Урок 6. Атомики: гистограмма.
 *
 * <p>Когда много work-item'ов пишут в одну ячейку, обычное {@code bins[b]++} теряет обновления: чтение и запись двух
 * потоков перемешиваются. {@code Gpu.atomicAdd(GlobalIntPtr.of(bins, b), 1)} делает «прочитать-прибавить-записать»
 * неделимо и возвращает старое значение. Есть {@code atomicSub/Min/Max/And/Or/Xor/Xchg}, для {@code int} и
 * {@code long} (64-битные - если устройство поддерживает), в global- и local-памяти.</p>
 *
 * <p>Атомики дороги, когда все бьют в одну ячейку (здесь - самый частый столбец). Стандартный приём - сначала
 * гистограмма группы в {@code @Local}-памяти, потом одно сложение на столбец.</p>
 *
 * <p>Попробуйте: заменить {@code atomicAdd} на {@code bins[b] += 1} и сравнить сумму столбцов с числом значений на
 * устройстве (на JVM ядро исполняется последовательно, и разницы не будет).</p>
 */
public final class Histogram {

    private Histogram() {
    }

    /**
     * Раскладывает значения по столбцам и находит максимум.
     *
     * @param values значения
     * @param bins   счётчики столбцов
     * @param top    {@code top[0]} - наибольшее значение в тысячных (атомарный максимум)
     * @param lo     нижняя граница диапазона
     * @param hi     верхняя граница диапазона
     */
    @Kernel
    public static void histogram(@Global float[] values, @Global int[] bins, @Global int[] top, float lo, float hi) {
        int i = Gpu.getGlobalId(0);
        int nb = bins.length;
        int b = (int) ((values[i] - lo) / (hi - lo) * nb);
        b = b < 0 ? 0 : b >= nb ? nb - 1 : b;
        Gpu.atomicAdd(GlobalIntPtr.of(bins, b), 1);
        Gpu.atomicMax(GlobalIntPtr.of(top, 0), (int) (values[i] * 1000f));
    }

    /** Программа урока. */
    public static void run() {
        int n = 1 << 20;
        float[] values = new float[n];
        Random rnd = new Random(42);
        float max = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < n; i++) {
            values[i] = (float) rnd.nextGaussian();
            max = Math.max(max, values[i]);
        }
        int[] bins = new int[24];
        int[] top = {Integer.MIN_VALUE};
        histogram(values, bins, top, -3f, 3f);
        int peak = 0;
        long total = 0;
        for (int c : bins) {
            peak = Math.max(peak, c);
            total += c;
        }
        for (int b = 0; b < bins.length; b++) {
            float from = -3f + 6f * b / bins.length;
            System.out.printf(Locale.ROOT, "%5.2f %7d %s%n", from, bins[b], "#".repeat(bins[b] * 60 / peak));
        }
        System.out.println("total " + total + " of " + n + ", max " + top[0] / 1000f);
        Show.check(total == n, "every value counted exactly once");
        Show.check(top[0] == (int) (max * 1000f), "atomic max");
    }
}
