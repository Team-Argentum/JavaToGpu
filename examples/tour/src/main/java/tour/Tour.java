package tour;

import com.sixik.gpu.runtime.JavaToGpu;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Тур по библиотеке: набор коротких уроков, каждый - самостоятельная программа с подробными комментариями. Читать
 * исходник урока, запускать, менять и запускать снова - так быстрее всего понять, как что работает.
 *
 * <pre>
 *   ./gradlew -p examples/tour run                  список уроков
 *   ./gradlew -p examples/tour run --args=hello     один урок
 *   ./gradlew -p examples/tour runAll               все подряд
 * </pre>
 *
 * <p>Между уроками контекст по умолчанию сбрасывается, поэтому каждый урок начинает с чистого листа. После урока
 * печатается, сколько ядер реально ушло на устройство и сколько - на JVM (если устройства нет, большинство уроков
 * работает и так - через JVM-fallback).</p>
 */
public final class Tour {

    /** Урок: название и программа. */
    private record Lesson(String title, Runnable body) {
    }

    private static final Map<String, Lesson> LESSONS = new LinkedHashMap<>();

    static {
        LESSONS.put("hello", new Lesson("01. First kernel: @Kernel, getGlobalId, an ordinary call",
                tour.hello.Hello::run));
        LESSONS.put("grid", new Lesson("02. 2D launches: LaunchConfig.of2D, a Mandelbrot set in ASCII",
                tour.grid.Grid::run));
        LESSONS.put("types", new Lesson("03. Vectors and @Struct: a particle system", tour.types.Particles::run));
        LESSONS.put("functions", new Lesson("04. @DeviceFunction, pointers, constant memory",
                tour.functions.Functions::run));
        LESSONS.put("local", new Lesson("05. Work-groups: @WorkGroup, @Local, barrier", tour.local.BlockSum::run));
        LESSONS.put("atomics", new Lesson("06. Atomics: a histogram", tour.atomics.Histogram::run));
        LESSONS.put("resident", new Lesson("07. GpuFloatArray: data that lives on the device",
                tour.resident.Resident::run));
        LESSONS.put("reduce", new Lesson("08. @Reduction: sums, min/max, several results in one pass",
                tour.reduce.Reduce::run));
        LESSONS.put("fusion", new Lesson("09. Automatic fusion: kernel -> kernel -> reduction",
                tour.fusion.Fusion::run));
        LESSONS.put("buffers", new Lesson("10. Explicit API: GpuContext, GpuBuffer, prepared launches, views",
                tour.buffers.Buffers::run));
        LESSONS.put("events", new Lesson("11. Events, timings, the generated OpenCL", tour.events.Events::run));
        LESSONS.put("context", new Lesson("12. Context, device choice, fallback, warm-up", tour.context.Context::run));
    }

    private Tour() {
    }

    /**
     * @param args имя урока, {@code all} или пусто (список уроков)
     */
    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("JavaToGpu tour. Run a lesson: ./gradlew -p examples/tour run --args=<name>");
            System.out.println("or all of them: ./gradlew -p examples/tour runAll");
            System.out.println();
            LESSONS.forEach((name, l) -> System.out.printf("  %-10s %s%n", name, l.title()));
            return;
        }
        if (args[0].equals("all")) {
            LESSONS.forEach(Tour::runLesson);
            return;
        }
        Lesson l = LESSONS.get(args[0]);
        if (l == null) {
            throw new IllegalArgumentException("no lesson '" + args[0] + "'; lessons: " + LESSONS.keySet());
        }
        runLesson(args[0], l);
    }

    private static void runLesson(String name, Lesson l) {
        JavaToGpu.resetDefault();
        Show.reset();
        Show.configure(null, null); // урок может переопределить до своего первого вызова ядра
        System.out.println();
        System.out.println("==== " + l.title() + " ====");
        long t = System.nanoTime();
        l.body().run();
        System.out.printf("==== %s done in %.0f ms: %s%n", name, (System.nanoTime() - t) / 1e6, Show.backends());
        JavaToGpu.resetDefault();
    }
}
