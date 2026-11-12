package tour.hello;

import com.sixik.gpu.api.Gpu;
import com.sixik.gpu.api.annotations.Global;
import com.sixik.gpu.api.annotations.Kernel;
import tour.Show;

/**
 * Урок 1. Первое ядро.
 *
 * <p>Ядро - обычный {@code static void} метод с {@code @Kernel}. Тело пишется так, будто его исполняет <b>один</b>
 * work-item (поток GPU): {@code Gpu.getGlobalId(0)} - номер этого work-item'а, и каждый обрабатывает свой элемент.
 * Вызывается ядро как обычный метод: {@code Hello.saxpy(...)}. При сборке processor переводит тело в IR, weaver
 * подменяет тело вызовом рантайма, а рантайм компилирует OpenCL C и запускает столько work-item'ов, какова длина
 * первого массива. Нет устройства - то же тело исполнится на JVM (fallback).</p>
 *
 * <p>Попробуйте: поменять формулу; передать массив короче первого (ядро выйдет за границу - на JVM это исключение);
 * задать переменную окружения {@code JAVATOGPU_DUMP=build/opencl} и посмотреть сгенерированный OpenCL C.</p>
 */
public final class Hello {

    private Hello() {
    }

    /**
     * {@code y[i] = a * x[i] + y[i]} - классический SAXPY.
     *
     * @param x вход
     * @param y вход и выход
     * @param a множитель
     */
    @Kernel
    public static void saxpy(@Global float[] x, @Global float[] y, float a) {
        int i = Gpu.getGlobalId(0);
        y[i] = a * x[i] + y[i];
    }

    /**
     * Каждый work-item пишет свой номер: видно, что ядро исполняют много потоков, по одному на элемент.
     *
     * @param out выход
     */
    @Kernel
    public static void ids(@Global int[] out) {
        int i = Gpu.getGlobalId(0);
        out[i] = i * 10;
    }

    /** Программа урока. */
    public static void run() {
        Show.section("saxpy: y = 2x + y");
        float[] x = {1, 2, 3, 4, 5, 6, 7, 8};
        float[] y = {10, 10, 10, 10, 10, 10, 10, 10};
        saxpy(x, y, 2f);                                     // обычный вызов метода
        Show.head("y =", y, 8);
        Show.check(y[7] == 26f, "saxpy result");

        Show.section("one work-item per element");
        int[] out = new int[16];
        ids(out);                                            // global size = out.length = 16
        Show.head("ids =", out, 16);
        Show.check(out[15] == 150, "ids result");

        Show.section("a large array");
        int n = 1 << 22;
        float[] bx = new float[n];
        float[] by = new float[n];
        for (int i = 0; i < n; i++) {
            bx[i] = i;
        }
        Show.time("saxpy over 4M floats (arrays copied each call)", Show.millis(5, () -> saxpy(bx, by, 0.5f)));
        System.out.println("Each call uploads x and y and reads y back: for repeated work see lesson 'resident'.");
    }
}
