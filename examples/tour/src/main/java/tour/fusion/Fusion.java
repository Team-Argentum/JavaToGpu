package tour.fusion;

import com.sixik.gpu.api.Gpu;
import com.sixik.gpu.api.annotations.Kernel;
import com.sixik.gpu.api.annotations.Reduction;
import com.sixik.gpu.api.types.GpuFloatArray;
import com.sixik.gpu.api.types.GpuIntArray;
import com.sixik.gpu.runtime.GpuEvent;
import tour.Show;

/**
 * Урок 9. Автоматическое слияние «ядро → ядро → свёртка».
 *
 * <p>Ядро, которое только заполняет массив ({@code out.set(Gpu.getGlobalId(0), value)} на верхнем уровне, без других
 * записей), - «производитель». Вызов такого ядра через обычный метод на {@code GpuArray} рантайм не исполняет сразу,
 * а откладывает. Если следующее ядро читает этот массив поэлементно ({@code in.get(gid)}), вычисление производителя
 * вставляется в него; если затем {@code @Reduction} читает результат - вся цепочка становится одним ядром свёртки, и
 * промежуточные массивы не пишутся в память вовсе.</p>
 *
 * <p>Ничего не теряется: отложенное ядро всё-таки исполняется, как только его результат может понадобиться - хост
 * читает массив, массив передан ядру, которое читает «соседей», хост меняет вход, контекст закрывается. Ниже это
 * видно по событиям: {@code deferred}, {@code fused into ...}, и обычный запуск, когда хост читает массив.</p>
 *
 * <p>Попробуйте: прочитать {@code height.get(0)} между вызовами; поменять {@code classify} так, чтобы она читала
 * {@code height.get(i + 1)} (слияния не будет - события это покажут); выключить слияние переменной окружения
 * {@code JAVATOGPU_FUSION=off} и сравнить время.</p>
 */
public final class Fusion {

    private Fusion() {
    }

    /**
     * Производитель: высота «рельефа» в точке - сумма синусоид.
     *
     * @param height выход
     * @param seeds  параметры волн (маленький вход: при откладывании копируется)
     * @param width  ширина сетки
     */
    @Kernel
    public static void terrain(GpuFloatArray height, GpuIntArray seeds, int width) {
        int i = Gpu.getGlobalId(0);
        float x = (i % width) * 0.01f;
        float y = (i / width) * 0.01f;
        float h = 0f;
        for (int k = 0; k < seeds.length(); k++) {
            float f = 1f + seeds.get(k) * 0.5f;
            h += Gpu.sin(x * f + k) * Gpu.cos(y * f - k) / f;
        }
        height.set(i, h);
    }

    /**
     * Читает высоту поэлементно и сам только заполняет массив: в него вставится {@code terrain}, и он тоже отложится.
     *
     * @param water  1 - вода (высота ниже уровня), 0 - суша
     * @param height высота
     * @param level  уровень воды
     */
    @Kernel
    public static void classify(GpuIntArray water, GpuFloatArray height, float level) {
        int i = Gpu.getGlobalId(0);
        water.set(i, height.get(i) < level ? 1 : 0);
    }

    /**
     * @param water маска воды
     * @return число клеток воды
     */
    @Reduction
    public static int count(GpuIntArray water) {
        int c = 0;
        for (int i = 0; i < water.length(); i++) {
            c += water.get(i);
        }
        return c;
    }

    /** Программа урока. */
    public static void run() {
        // события рантайма: откладывание и слияние видны прямо по ходу урока
        Show.listen(e -> {
            if (e.kind() == GpuEvent.Kind.KERNEL && !e.detail().startsWith("LaunchConfig")) {
                System.out.println("   [runtime] kernel " + e.subject() + ": " + e.detail());
            } else if (e.kind() == GpuEvent.Kind.KERNEL) {
                System.out.println("   [runtime] launch " + e.subject() + " " + e.detail());
            }
        });
        int w = 1024;
        int h = 1024;
        GpuIntArray seeds = GpuIntArray.of(1, 3, 5, 9);
        GpuFloatArray height = new GpuFloatArray(w * h);
        GpuIntArray water = new GpuIntArray(w * h);

        Show.section("terrain -> classify -> count");
        terrain(height, seeds, w);
        classify(water, height, 0f);
        int c = count(water);
        System.out.println("water cells: " + c + " of " + w * h);

        Show.section("now the host reads a cell: the deferred kernels run");
        System.out.println("height[0] = " + height.get(0) + ", water[0] = " + water.get(0));
        int check = 0;
        for (int v : water.toArray()) {
            check += v;
        }
        Show.check(check == c, "the fused count equals the count over the materialized mask");

        Show.section("timing: the whole chain each frame");
        Show.listen(null);
        double ms = Show.millis(10, () -> {
            terrain(height, seeds, w);
            classify(water, height, 0f);
            count(water);
        });
        Show.time("terrain + classify + count", ms);
        System.out.println("Set JAVATOGPU_FUSION=off and run again to compare with three separate kernels.");
    }
}
