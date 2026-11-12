package tour;

import com.sixik.gpu.runtime.EventListener;
import com.sixik.gpu.runtime.GpuContext;
import com.sixik.gpu.runtime.GpuEvent;
import com.sixik.gpu.runtime.JavaToGpu;

import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Общие мелочи уроков: заголовки, печать массивов, замер времени и учёт того, где реально исполнялись ядра (на
 * устройстве или на JVM). Сами уроки от него не зависят по смыслу - это только вывод.
 */
public final class Show {

    private static final AtomicInteger DEVICE_LAUNCHES = new AtomicInteger();
    private static final AtomicInteger FALLBACKS = new AtomicInteger();
    private static volatile EventListener lessonListener = EventListener.NONE;

    private Show() {
    }

    /**
     * Настраивает контекст по умолчанию для урока: слушатель тура плюс настройки урока. Вызывается до первого вызова
     * ядра урока (тур сбрасывает контекст между уроками).
     *
     * @param extra    настройки урока (может быть {@code null})
     * @param listener слушатель событий урока (может быть {@code null})
     */
    public static void configure(Consumer<GpuContext.Builder> extra, EventListener listener) {
        lessonListener = listener == null ? EventListener.NONE : listener;
        JavaToGpu.configureDefault(b -> {
            b.listener(Show::onEvent);
            if (extra != null) {
                extra.accept(b);
            }
        });
    }

    /**
     * Меняет слушателя событий урока (можно в любой момент, в отличие от {@link #configure}).
     *
     * @param listener слушатель либо {@code null}
     */
    public static void listen(EventListener listener) {
        lessonListener = listener == null ? EventListener.NONE : listener;
    }

    /** Слушатель тура: считает запуски на устройстве и переходы на JVM, остальное отдаёт уроку. */
    static void onEvent(GpuEvent e) {
        if (e.kind() == GpuEvent.Kind.KERNEL && e.detail().startsWith("LaunchConfig")) {
            DEVICE_LAUNCHES.incrementAndGet();
        } else if (e.kind() == GpuEvent.Kind.FALLBACK) {
            FALLBACKS.incrementAndGet();
        }
        lessonListener.onEvent(e);
    }

    /** Сбрасывает счётчики перед уроком. */
    static void reset() {
        DEVICE_LAUNCHES.set(0);
        FALLBACKS.set(0);
        lessonListener = EventListener.NONE;
    }

    /** @return сводка: сколько ядер ушло на устройство и сколько на JVM */
    static String backends() {
        if (DEVICE_LAUNCHES.get() == 0) {
            return "no launches on a device through the default context (the kernels ran on the JVM,"
                    + " or the lesson used its own context)";
        }
        return String.format(Locale.ROOT, "%d kernel launches on the device, %d fallbacks to the JVM",
                DEVICE_LAUNCHES.get(), FALLBACKS.get());
    }

    /** @param title заголовок раздела урока */
    public static void section(String title) {
        System.out.println();
        System.out.println("-- " + title);
    }

    /**
     * @param label подпись
     * @param a     массив
     * @param n     сколько первых элементов показать
     */
    public static void head(String label, float[] a, int n) {
        System.out.println(label + " " + Arrays.toString(Arrays.copyOf(a, Math.min(n, a.length)))
                + (a.length > n ? " ... (" + a.length + " values)" : ""));
    }

    /**
     * @param label подпись
     * @param a     массив
     * @param n     сколько первых элементов показать
     */
    public static void head(String label, int[] a, int n) {
        System.out.println(label + " " + Arrays.toString(Arrays.copyOf(a, Math.min(n, a.length)))
                + (a.length > n ? " ... (" + a.length + " values)" : ""));
    }

    /**
     * Медиана времени нескольких прогонов действия (первый прогон - разогрев: компиляция ядра, загрузка данных).
     *
     * @param runs   число замеряемых прогонов
     * @param action действие
     * @return медиана в миллисекундах
     */
    public static double millis(int runs, Runnable action) {
        action.run();
        long[] t = new long[runs];
        for (int i = 0; i < runs; i++) {
            long s = System.nanoTime();
            action.run();
            t[i] = System.nanoTime() - s;
        }
        Arrays.sort(t);
        return t[runs / 2] / 1e6;
    }

    /**
     * @param what подпись
     * @param ms   миллисекунды
     */
    public static void time(String what, double ms) {
        System.out.printf(Locale.ROOT, "%-44s %8.3f ms%n", what, ms);
    }

    /**
     * Проверка результата урока: при расхождении бросает исключение (тур останавливается на ошибке).
     *
     * @param ok      условие
     * @param message что не совпало
     */
    public static void check(boolean ok, String message) {
        if (!ok) {
            throw new AssertionError(message);
        }
    }
}
