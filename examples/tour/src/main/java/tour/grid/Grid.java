package tour.grid;

import com.sixik.gpu.api.Gpu;
import com.sixik.gpu.api.annotations.Global;
import com.sixik.gpu.api.annotations.Kernel;
import com.sixik.gpu.runtime.JavaToGpu;
import com.sixik.gpu.runtime.LaunchConfig;
import tour.Show;

/**
 * Урок 2. Двумерный запуск.
 *
 * <p>Изображению удобнее сетка work-item'ов: {@code getGlobalId(0)} - столбец, {@code getGlobalId(1)} - строка,
 * {@code getGlobalSize(0)} - ширина. Размер сетки задаёт область {@code JavaToGpu.launch(LaunchConfig.of2D(w, h))}:
 * все вызовы ядер внутри неё запускаются с этим размером (без области размер - длина первого массива, одномерно).</p>
 *
 * <p>Выход пишется по индексу {@code y * w + x}, и каждый элемент записан ровно одним work-item'ом. Рантайм это
 * распознаёт ({@code WriteCoverage}) и не загружает массив на устройство перед запуском - только читает результат.</p>
 *
 * <p>Попробуйте: другие {@code cx, cy, scale} (например, -0.745, 0.1, 0.00002); больше {@code maxIter}; поменять
 * местами x и y в индексе и посмотреть, что станет с картинкой.</p>
 */
public final class Grid {

    private Grid() {
    }

    /**
     * Число итераций {@code z = z^2 + c} до выхода за радиус 2 для точки пикселя.
     *
     * @param out     число итераций по пикселям, {@code w * h}
     * @param maxIter предел итераций
     * @param cx      центр по вещественной оси
     * @param cy      центр по мнимой оси
     * @param scale   шаг по пикселю
     */
    @Kernel
    public static void mandelbrot(@Global int[] out, int maxIter, float cx, float cy, float scale) {
        int x = Gpu.getGlobalId(0);
        int y = Gpu.getGlobalId(1);
        int w = Gpu.getGlobalSize(0);
        int h = Gpu.getGlobalSize(1);
        float re = cx + (x - w * 0.5f) * scale;
        float im = cy + (y - h * 0.5f) * scale * 2f;    // символ в консоли примерно вдвое выше ширины
        float zr = 0f;
        float zi = 0f;
        int it = 0;
        while (it < maxIter && zr * zr + zi * zi < 4f) {
            float t = zr * zr - zi * zi + re;
            zi = 2f * zr * zi + im;
            zr = t;
            it++;
        }
        out[y * w + x] = it;
    }

    /** Программа урока. */
    public static void run() {
        int w = 96;
        int h = 32;
        int[] img = new int[w * h];
        try (JavaToGpu.Scope s = JavaToGpu.launch(LaunchConfig.of2D(w, h))) {
            mandelbrot(img, 64, -0.6f, 0f, 0.032f);
        }
        String shades = " .:-=+*#%@";
        StringBuilder sb = new StringBuilder();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int it = img[y * w + x];
                sb.append(it >= 64 ? '@' : shades.charAt(it * (shades.length() - 1) / 64));
            }
            sb.append('\n');
        }
        System.out.print(sb);
        Show.check(img[(h / 2) * w + w / 2 + 10] == 64, "the centre of the set is inside");

        Show.section("Full HD, 256 iterations");
        int fw = 1920;
        int fh = 1080;
        int[] frame = new int[fw * fh];
        double ms = Show.millis(5, () -> {
            try (JavaToGpu.Scope s = JavaToGpu.launch(LaunchConfig.of2D(fw, fh))) {
                mandelbrot(frame, 256, -0.6f, 0f, 0.0017f);
            }
        });
        Show.time("mandelbrot 1920x1080 (output never uploaded)", ms);
    }
}
