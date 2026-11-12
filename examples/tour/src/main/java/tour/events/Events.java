package tour.events;

import com.sixik.gpu.api.Gpu;
import com.sixik.gpu.api.annotations.Global;
import com.sixik.gpu.api.annotations.Kernel;
import com.sixik.gpu.ir.Result;
import com.sixik.gpu.runtime.GpuContext;
import com.sixik.gpu.runtime.GpuEvent;
import com.sixik.gpu.runtime.JavaToGpu;
import com.sixik.gpu.runtime.Timings;
import tour.Show;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Урок 11. События, статистика и сгенерированный OpenCL C.
 *
 * <p>Контекст сообщает обо всём, что делает, слушателю {@code EventListener}: выбор устройства, компиляция
 * ({@code built}, {@code disk-hit}, {@code cache-hit}), передачи, запуски ядер, предупреждения, переход на JVM.
 * {@code Timings} - готовый слушатель, который суммирует время и байты по видам событий. {@code dumpSources(dir)}
 * пишет OpenCL C каждого собранного ядра в каталог - удобно смотреть, во что превратился Java-код (то же -
 * переменная {@code JAVATOGPU_DUMP} или свойство {@code javatogpu.dump}).</p>
 *
 * <p>Попробуйте: открыть файл {@code .cl} из {@code build/tour-opencl}; запустить урок второй раз и увидеть
 * {@code disk-hit} вместо {@code built} - программа взята из дискового кэша.</p>
 */
public final class Events {

    private Events() {
    }

    /**
     * Немного тригонометрии, чтобы было на что посмотреть в OpenCL C.
     *
     * @param in  углы
     * @param out {@code sin(a) * cos(2a)}
     */
    @Kernel
    public static void wave(@Global float[] in, @Global float[] out) {
        int i = Gpu.getGlobalId(0);
        float a = in[i];
        out[i] = Gpu.sin(a) * Gpu.cos(2f * a);
    }

    /** Программа урока. */
    public static void run() {
        Path dump = Path.of("build", "tour-opencl");
        Timings timings = new Timings();
        Result<GpuContext> rc = GpuContext.builder()
                .dumpSources(dump)
                .listener(e -> {
                    timings.onEvent(e);
                    if (e.kind() != GpuEvent.Kind.UPLOAD && e.kind() != GpuEvent.Kind.READBACK) {
                        System.out.printf(Locale.ROOT, "   [event] %-15s %-20s %s%n", e.kind(), e.subject(), e.detail());
                    }
                })
                .tryBuild();
        if (!rc.isOk()) {
            System.out.println("No OpenCL device: " + rc.diagnostics());
            System.out.println("Without a device the kernels run on the JVM, and there are no device events to show.");
            return;
        }
        try (GpuContext ctx = rc.value(); JavaToGpu.Scope s = JavaToGpu.use(ctx)) {
            float[] in = new float[1 << 18];
            float[] out = new float[in.length];
            for (int i = 0; i < in.length; i++) {
                in[i] = i * 0.001f;
            }
            Show.section("three calls of the same kernel");
            for (int r = 0; r < 3; r++) {
                wave(in, out);
            }
            Show.section("Timings");
            Timings.Snapshot t = timings.snapshot();
            for (GpuEvent.Kind k : GpuEvent.Kind.values()) {
                Timings.Total total = t.of(k);
                if (total.count() > 0) {
                    System.out.printf(Locale.ROOT, "%-15s %3d events %9.3f ms %,12d bytes%n", k, total.count(),
                            total.nanos() / 1e6, total.bytes());
                }
            }
            System.out.println("compile: built " + t.built() + ", memory hits " + t.memoryHits() + ", disk hits "
                    + t.diskHits());
        }
        Show.section("generated OpenCL C in " + dump.toAbsolutePath());
        try (Stream<Path> files = Files.list(dump)) {
            Path cl = files.filter(p -> p.toString().endsWith(".cl")).findFirst().orElse(null);
            if (cl != null) {
                System.out.println(Files.readString(cl));
            }
        } catch (IOException e) {
            System.out.println("cannot read the dump: " + e);
        }
    }
}
