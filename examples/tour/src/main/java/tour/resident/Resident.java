package tour.resident;

import com.sixik.gpu.api.Gpu;
import com.sixik.gpu.api.annotations.Global;
import com.sixik.gpu.api.annotations.Kernel;
import com.sixik.gpu.api.types.GpuFloatArray;
import tour.Show;

/**
 * Урок 7. Данные, которые живут на устройстве.
 *
 * <p>Обычный {@code float[]} рантайм обязан загружать перед каждым вызовом и читать обратно после: массив Java не
 * сообщает, менял ли его хост. {@code GpuFloatArray} (и {@code GpuIntArray}, {@code GpuLongArray},
 * {@code GpuDoubleArray}) знает, где свежие данные: ядро оставляет результат на устройстве, а на хост он читается
 * лениво - при первом {@code get}/{@code toArray}/{@code copyTo}. Запись с хоста ({@code set}, {@code fill},
 * {@code copyFrom}) помечает копию устройства устаревшей. В ядре это {@code a.get(i)}, {@code a.set(i, v)},
 * {@code a.length()} - компилируется в то же, что {@code a[i]}.</p>
 *
 * <p>Здесь - уравнение теплопроводности на отрезке, много шагов подряд «туда-обратно» между двумя массивами.
 * С {@code float[]} каждый шаг гоняет оба массива через шину; с {@code GpuFloatArray} - ни разу, кроме последнего
 * чтения. Вызовы с такими массивами ещё и не ждут завершения ядра: ожидание - один раз, при чтении на хосте.</p>
 *
 * <p>Попробуйте: прочитать {@code a.get(0)} внутри цикла (каждое чтение - синхронизация и передача); изменить
 * {@code a.set(...)} с хоста посередине - следующий вызов загрузит массив заново.</p>
 */
public final class Resident {

    private Resident() {
    }

    /**
     * Шаг явной схемы теплопроводности: {@code out[i] = in[i] + k (in[i-1] - 2 in[i] + in[i+1])}, края закреплены.
     *
     * @param in  температура
     * @param out температура на следующем шаге
     * @param k   коэффициент, не больше 0.5
     */
    @Kernel
    public static void heat(GpuFloatArray in, GpuFloatArray out, float k) {
        int i = Gpu.getGlobalId(0);
        int n = in.length();
        if (i == 0 || i == n - 1) {
            out.set(i, in.get(i));
        } else {
            out.set(i, in.get(i) + k * (in.get(i - 1) - 2f * in.get(i) + in.get(i + 1)));
        }
    }

    /**
     * Тот же шаг на обычных массивах - для сравнения.
     *
     * @param in  температура
     * @param out температура на следующем шаге
     * @param k   коэффициент
     */
    @Kernel
    public static void heatArrays(@Global float[] in, @Global float[] out, float k) {
        int i = Gpu.getGlobalId(0);
        int n = in.length;
        if (i == 0 || i == n - 1) {
            out[i] = in[i];
        } else {
            out[i] = in[i] + k * (in[i - 1] - 2f * in[i] + in[i + 1]);
        }
    }

    /** Программа урока. */
    public static void run() {
        int n = 1 << 20;
        int steps = 100;
        float[] start = new float[n];
        start[n / 2] = 1000f;                         // горячая точка посередине

        float[] a1 = start.clone();
        float[] b1 = new float[n];
        double arrays = Show.millis(3, () -> {
            System.arraycopy(start, 0, a1, 0, n);
            for (int s = 0; s < steps; s++) {
                heatArrays(s % 2 == 0 ? a1 : b1, s % 2 == 0 ? b1 : a1, 0.25f);
            }
        });

        GpuFloatArray a = GpuFloatArray.of(start);
        GpuFloatArray b = new GpuFloatArray(n);
        double resident = Show.millis(3, () -> {
            a.copyFrom(start);                         // хост перезаписал: следующий вызов загрузит a
            for (int s = 0; s < steps; s++) {
                heat(s % 2 == 0 ? a : b, s % 2 == 0 ? b : a, 0.25f);
            }
            a.get(n / 2);                              // единственное чтение - и ожидание всех шагов
        });

        Show.time(steps + " steps with float[] (copied every call)", arrays);
        Show.time(steps + " steps with GpuFloatArray (stays on device)", resident);
        float[] profile = new float[9];
        for (int j = 0; j < profile.length; j++) {
            profile[j] = a.get(n / 2 - 4 + j);
        }
        Show.head("temperature around the centre", profile, 9);
        Show.check(Math.abs(a.get(n / 2) - a1[n / 2]) < 1e-3f, "both ways compute the same");
        float total = 0f;
        for (float v : a.toArray()) {
            total += v;
        }
        System.out.printf("total heat %.2f (conserved: 1000)%n", total);
        Show.check(Math.abs(total - 1000f) < 0.5f, "heat is conserved");
    }
}
