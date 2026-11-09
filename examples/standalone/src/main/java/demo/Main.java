package demo;

import com.sixik.gpu.runtime.GpuContext;
import com.sixik.gpu.runtime.GpuEvent;
import com.sixik.gpu.runtime.JavaToGpu;
import com.sixik.gpu.runtime.device.DevicePolicy;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

/** Проверяет обычный вызов ядра с явным выбором JVM или обязательного устройства. */
public final class Main {

    /** Не создаёт экземпляров приложения. */
    private Main() {
    }

    /**
     * Запускает пример и проверяет результат и фактический путь исполнения.
     *
     * @param args пусто для автоматического выбора, fallback для JVM, device для обязательного OpenCL
     * @throws IllegalArgumentException если задан неизвестный режим
     * @throws AssertionError если результат или путь исполнения неверен
     */
    public static void main(String[] args) {
        String mode = args.length == 0 ? "auto" : args[0];
        if (!mode.equals("auto") && !mode.equals("fallback") && !mode.equals("device")) {
            throw new IllegalArgumentException("mode: auto, fallback or device");
        }
        AtomicInteger launches = new AtomicInteger();
        AtomicInteger fallbacks = new AtomicInteger();
        JavaToGpu.configureDefault(builder -> {
            builder.ignoreEnvironment().listener(event -> {
                if (event.kind() == GpuEvent.Kind.KERNEL) {
                    launches.incrementAndGet();
                } else if (event.kind() == GpuEvent.Kind.FALLBACK) {
                    fallbacks.incrementAndGet();
                }
            });
            if (mode.equals("fallback")) {
                builder.policy(DevicePolicy.DEFAULT.withVendor("standalone-no-such-vendor"));
            } else if (mode.equals("device")) {
                builder.fallback(GpuContext.Fallback.NEVER);
            }
        });
        try {
            float[] input = {1, 2, 3, 4};
            float[] output = new float[4];
            Kernels.scale(input, output, 2f);
            if (!Arrays.equals(output, new float[]{3, 5, 7, 9})) {
                throw new AssertionError("wrong kernel result: " + Arrays.toString(output));
            }
            boolean ranOnDevice = launches.get() > 0;
            if (mode.equals("device") && (!ranOnDevice || fallbacks.get() != 0)) {
                throw new AssertionError("device execution was required");
            }
            if (mode.equals("fallback") && ranOnDevice) {
                throw new AssertionError("JVM execution was required");
            }
            System.out.println("RESULT " + Arrays.toString(output));
            System.out.println("BACKEND " + (ranOnDevice ? "OPENCL" : "JVM"));
        } finally {
            JavaToGpu.resetDefault();
        }
    }
}
