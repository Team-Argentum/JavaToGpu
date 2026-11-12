package tour.functions;

import com.sixik.gpu.api.Gpu;
import com.sixik.gpu.api.annotations.DeviceFunction;
import com.sixik.gpu.api.annotations.Global;
import com.sixik.gpu.api.annotations.Kernel;
import com.sixik.gpu.api.types.FloatPtr;
import com.sixik.gpu.api.types.GlobalFloatPtr;
import tour.Show;

/**
 * Урок 4. Device-функции, указатели и constant-память.
 *
 * <p>Вспомогательный метод, который вызывает ядро, помечается {@code @DeviceFunction} (даже приватный): он
 * компилируется в функцию OpenCL. Рекурсии нет; исходник должен быть в той же компиляции.</p>
 *
 * <p>Java не умеет возвращать несколько значений и передавать примитив по ссылке, поэтому для «выходных» параметров
 * есть {@code FloatPtr} (и {@code IntPtr} и т.п.): ячейка в private-памяти work-item'а, функция пишет в
 * {@code p.value}. {@code GlobalFloatPtr.of(array, offset)} - указатель внутрь global-массива: функция получает
 * «строку матрицы» без копирования. {@code @Global(constant = true)} - массив только для чтения в constant-памяти
 * (на GPU кэшируется; записать в него - ошибка компиляции {@code GPU0122}).</p>
 *
 * <p>Попробуйте: убрать {@code @DeviceFunction} у {@code smoothstep} (ошибка {@code GPU0113}); записать в
 * {@code palette} внутри ядра; заменить {@code FloatPtr} на возврат struct.</p>
 */
public final class Functions {

    private Functions() {
    }

    /**
     * Плавный порог Эрмита.
     *
     * @param e0 нижний край
     * @param e1 верхний край
     * @param x  значение
     * @return 0..1
     */
    @DeviceFunction
    static float smoothstep(float e0, float e1, float x) {
        float t = Gpu.min(Gpu.max((x - e0) / (e1 - e0), 0f), 1f);
        return t * t * (3f - 2f * t);
    }

    /**
     * Перемешивание битов целого (хэш): детерминированный «шум».
     *
     * @param x вход
     * @return хэш
     */
    @DeviceFunction
    static int hash(int x) {
        x ^= x >>> 16;
        x *= 0x7feb352d;
        x ^= x >>> 15;
        x *= 0x846ca68b;
        x ^= x >>> 16;
        return x;
    }

    /**
     * Обновляет минимум и максимум через «выходные» параметры.
     *
     * @param lo минимум (ячейка)
     * @param hi максимум (ячейка)
     * @param v  значение
     */
    @DeviceFunction
    static void include(FloatPtr lo, FloatPtr hi, float v) {
        lo.value = Gpu.min(lo.value, v);
        hi.value = Gpu.max(hi.value, v);
    }

    /**
     * Сумма {@code n} элементов, начиная с указателя.
     *
     * @param row указатель на начало строки
     * @param n   длина строки
     * @return сумма
     */
    @DeviceFunction
    static float sum(GlobalFloatPtr row, int n) {
        float s = 0f;
        for (int j = 0; j < n; j++) {
            s += row.get(j);
        }
        return s;
    }

    /**
     * Тон по яркости: порог, палитра из constant-памяти и немного шума.
     *
     * @param in      яркость 0..1
     * @param palette 4 опорных значения палитры (только чтение)
     * @param out     тон
     */
    @Kernel
    public static void shade(@Global float[] in, @Global(constant = true) float[] palette, @Global float[] out) {
        int i = Gpu.getGlobalId(0);
        float t = smoothstep(0.2f, 0.8f, in[i]) * 3f;
        int k = (int) Gpu.min(t, 2.999f);
        float f = t - k;
        float noise = (hash(i) & 1023) / 1023f * 0.01f;
        out[i] = palette[k] + (palette[k + 1] - palette[k]) * f + noise;
    }

    /**
     * Минимум и максимум в окне {@code [i - r, i + r]}.
     *
     * @param in вход
     * @param lo минимум окна
     * @param hi максимум окна
     * @param r  радиус окна
     */
    @Kernel
    public static void window(@Global float[] in, @Global float[] lo, @Global float[] hi, int r) {
        int i = Gpu.getGlobalId(0);
        int n = in.length;
        FloatPtr a = new FloatPtr();
        FloatPtr b = new FloatPtr();
        a.value = in[i];
        b.value = in[i];
        for (int j = i - r; j <= i + r; j++) {
            int c = j < 0 ? 0 : j >= n ? n - 1 : j;
            include(a, b, in[c]);
        }
        lo[i] = a.value;
        hi[i] = b.value;
    }

    /**
     * Суммы строк матрицы: work-item на строку, строка передаётся функции указателем.
     *
     * @param matrix матрица по строкам
     * @param sums   суммы строк
     * @param cols   число столбцов
     */
    @Kernel
    public static void rowSums(@Global float[] sums, @Global float[] matrix, int cols) {
        int r = Gpu.getGlobalId(0);
        sums[r] = sum(GlobalFloatPtr.of(matrix, r * cols), cols);
    }

    /** Программа урока. */
    public static void run() {
        Show.section("shade: smoothstep + constant palette + hash noise");
        float[] in = new float[16];
        for (int i = 0; i < in.length; i++) {
            in[i] = i / 15f;
        }
        float[] palette = {0f, 0.2f, 0.9f, 1f};
        float[] tone = new float[in.length];
        shade(in, palette, tone);
        Show.head("tone", tone, 16);
        Show.check(tone[0] < 0.02f && tone[15] > 0.99f, "tone ends");

        Show.section("window min/max via FloatPtr out-parameters");
        float[] wave = new float[12];
        for (int i = 0; i < wave.length; i++) {
            wave[i] = (float) Math.sin(i * 0.9);
        }
        float[] lo = new float[wave.length];
        float[] hi = new float[wave.length];
        window(wave, lo, hi, 1);
        Show.head("wave", wave, 12);
        Show.head("lo  ", lo, 12);
        Show.head("hi  ", hi, 12);
        Show.check(lo[5] == Math.min(wave[4], Math.min(wave[5], wave[6])), "window min");

        Show.section("row sums via GlobalFloatPtr");
        int rows = 4;
        int cols = 5;
        float[] m = new float[rows * cols];
        for (int i = 0; i < m.length; i++) {
            m[i] = i;
        }
        float[] sums = new float[rows];
        rowSums(sums, m, cols);                  // global size = sums.length: work-item на строку
        Show.head("row sums", sums, rows);
        Show.check(sums[3] == 15 + 16 + 17 + 18 + 19, "row sum");
    }
}
