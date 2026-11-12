package tour.context;

import com.sixik.gpu.api.Gpu;
import com.sixik.gpu.api.annotations.Global;
import com.sixik.gpu.api.annotations.Kernel;
import com.sixik.gpu.ir.Result;
import com.sixik.gpu.runtime.GpuContext;
import com.sixik.gpu.runtime.GpuEvent;
import com.sixik.gpu.runtime.GpuException;
import com.sixik.gpu.runtime.JavaToGpu;
import com.sixik.gpu.runtime.device.DeviceApi;
import com.sixik.gpu.runtime.device.DeviceInfo;
import com.sixik.gpu.runtime.device.DevicePolicy;
import com.sixik.gpu.runtime.device.OpenClApi;
import tour.Show;

import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * Урок 12. Контекст, выбор устройства, fallback и прогрев.
 *
 * <p>Обычный вызов ядра использует ленивый контекст по умолчанию; его настраивают до первого вызова
 * ({@code JavaToGpu.configureDefault}). Свой контекст ограничивают областью {@code JavaToGpu.use(ctx)}. Устройство
 * выбирает политика ({@code DevicePolicy}: вид, производитель, память) или явное имя ({@code builder.device("nvidia:0")},
 * переменная {@code JAVATOGPU_DEVICE}).</p>
 *
 * <p>Fallback: если устройства нет или ядро не собралось, по умолчанию исходное тело исполняется на JVM (и приходит
 * событие {@code FALLBACK} с причиной). {@code Fallback.NEVER} превращает это в исключение - для серверов, где тихий
 * переход на CPU хуже ошибки. {@code JavaToGpu.warmUp(...)} собирает ядра заранее в фоне - первый вызов не ждёт
 * компиляции драйвером.</p>
 */
public final class Context {

    private Context() {
    }

    /**
     * @param out квадраты номеров
     */
    @Kernel
    public static void squares(@Global int[] out) {
        int i = Gpu.getGlobalId(0);
        out[i] = i * i;
    }

    /** Программа урока. */
    public static void run() {
        Show.section("OpenCL devices on this machine");
        try {
            DeviceApi api = OpenClApi.load();
            List<DeviceInfo> devices = DeviceInfo.enumerate(api);
            devices.forEach(d -> System.out.println("  " + d.describe() + ", driver " + d.driverVersion()));
            if (devices.isEmpty()) {
                System.out.println("  none: kernels will run on the JVM");
            }
        } catch (RuntimeException | LinkageError e) {
            System.out.println("  no OpenCL runtime: " + e.getMessage());
        }

        Show.section("a policy that matches no device -> JVM fallback");
        Result<GpuContext> none = GpuContext.builder().ignoreEnvironment()
                .policy(DevicePolicy.DEFAULT.withVendor("no-such-vendor")).tryBuild();
        System.out.println("tryBuild: " + (none.isOk() ? "a device" : none.diagnostics().get(0).code() + " "
                + none.diagnostics().get(0).message()));
        JavaToGpu.resetDefault();
        Show.configure(b -> b.ignoreEnvironment().policy(DevicePolicy.DEFAULT.withVendor("no-such-vendor")),
                e -> {
                    if (e.kind() == GpuEvent.Kind.FALLBACK || e.kind() == GpuEvent.Kind.WARNING) {
                        System.out.println("   [event] " + e.kind() + ": " + e.detail());
                    }
                });
        int[] out = new int[8];
        squares(out);                                   // устройства нет - исполняется на JVM
        System.out.println("(no context was created, so the reason goes to the log as a WARNING, not to a listener)");
        Show.head("squares on the JVM", out, 8);
        Show.check(out[7] == 49, "fallback result");

        Show.section("Fallback.NEVER: no silent CPU path");
        JavaToGpu.resetDefault();
        Show.configure(b -> b.ignoreEnvironment().policy(DevicePolicy.DEFAULT.withVendor("no-such-vendor"))
                .fallback(GpuContext.Fallback.NEVER), null);
        try {
            squares(new int[8]);
            System.out.println("unexpected: the call succeeded");
        } catch (GpuException e) {
            System.out.println("GpuException: " + e.diagnostics().get(0).code() + " - as configured");
        }

        Show.section("own context + warm-up");
        JavaToGpu.resetDefault();
        Result<GpuContext> rc = GpuContext.builder().tryBuild();
        if (!rc.isOk()) {
            System.out.println("no device for this part: " + rc.diagnostics());
            return;
        }
        try (GpuContext ctx = rc.value(); JavaToGpu.Scope s = JavaToGpu.use(ctx)) {
            long t = System.nanoTime();
            JavaToGpu.WarmUp w = JavaToGpu.warmUp(Context.class).get();   // обычно не ждут: прогрев идёт в фоне
            System.out.printf("warm-up compiled %d kernel(s) in %.1f ms, problems: %s%n", w.compiled(),
                    (System.nanoTime() - t) / 1e6, w.problems());
            t = System.nanoTime();
            squares(out);
            System.out.printf("first call after warm-up: %.2f ms on %s%n", (System.nanoTime() - t) / 1e6,
                    ctx.device().name());
        } catch (InterruptedException | ExecutionException e) {
            throw new IllegalStateException(e);
        }
    }
}
