package com.sixik.gpu.runtime;

import com.sixik.gpu.weaver.KernelCompiler;
import com.sixik.gpu.weaver.Weaver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Критерий M7: {@code computeSine} запускается на реальном OpenCL-устройстве обычным вызовом Java-метода. Путь
 * целиком настоящий: исходник &rarr; javac + processor {@code frontend-java} &rarr; weaver &rarr; загрузка класса &rarr;
 * вызов &rarr; {@link JavaToGpu} &rarr; контекст по умолчанию (системный OpenCL) &rarr; ядро на GPU. Без драйвера тест
 * пропускается. Запуск: {@code ./gradlew :runtime:deviceTest}.
 */
@Tag("device")
class WovenKernelDeviceTest {

    private static final String SOURCE = """
            package demo;

            import com.sixik.gpu.api.Gpu;
            import com.sixik.gpu.api.annotations.Global;
            import com.sixik.gpu.api.annotations.Kernel;
            import com.sixik.gpu.api.annotations.WorkGroup;

            public class Sine {

                @Kernel
                @WorkGroup(x = 256, y = 1, z = 1)
                public static void computeSine(@Global float[] input, @Global float[] output) {
                    int id = Gpu.getGlobalId(0);
                    output[id] = Gpu.sin(input[id]) + 2.0f;
                }
            }
            """;

    @AfterEach
    void reset() {
        JavaToGpu.resetDefault();
    }

    @Test
    void computeSineRunsOnTheDeviceByAnOrdinaryCall(@TempDir Path dir) throws Exception {
        List<GpuEvent> events = new ArrayList<>();
        // строгий режим: если ядро не уйдёт на устройство, тест упадёт, а не пройдёт на JVM-fallback
        JavaToGpu.configureDefault(b -> b.listener(events::add).fallback(GpuContext.Fallback.NEVER));
        Assumptions.assumeTrue(JavaToGpu.defaultContext().isPresent(), "no OpenCL device");

        KernelCompiler.Compiled r = KernelCompiler.compile(dir, SOURCE);
        assertTrue(r.success(), r.diagnostics());
        assertEquals(1, Weaver.weaveTree(dir).kernels());
        try (URLClassLoader loader = new URLClassLoader(new URL[]{dir.toUri().toURL()}, getClass().getClassLoader())) {
            Class<?> sine = loader.loadClass("demo.Sine");
            int n = 1024;
            float[] in = new float[n];
            float[] out = new float[n];
            Random rnd = new Random(7);
            for (int i = 0; i < n; i++) {
                in[i] = (rnd.nextFloat() - 0.5f) * 20f;
            }
            float[] inBefore = in.clone();
            call(sine, "computeSine", in, out);

            for (int i = 0; i < n; i++) {
                float expected = (float) Math.sin(inBefore[i]) + 2f;
                assertTrue(ulps(expected, out[i]) <= 4, "i=" + i + ": " + expected + " / " + out[i]);
            }
            // $jvm - то же ядро на JVM: результаты совпадают с устройством в пределах допуска sin
            float[] jvm = new float[n];
            Method body = sine.getDeclaredMethod("computeSine$jvm", float[].class, float[].class);
            com.sixik.gpu.api.WorkItem.run(com.sixik.gpu.api.WorkItemContext.of1D(n, 256), () -> {
                com.sixik.gpu.api.WorkItemContext ctx = com.sixik.gpu.api.WorkItem.current();
                try {
                    for (int i = 0; i < n; i++) {
                        ctx.setGlobalId(i, 0, 0);
                        body.invoke(null, inBefore, jvm);
                    }
                } catch (ReflectiveOperationException e) {
                    throw new AssertionError(e);
                }
            });
            for (int i = 0; i < n; i++) {
                assertTrue(ulps(jvm[i], out[i]) <= 4, "the JVM and the device diverged at i=" + i);
            }
        }
        assertTrue(events.stream().anyMatch(e -> e.kind() == GpuEvent.Kind.KERNEL), "the kernel ran on the device");
        assertFalse(events.stream().anyMatch(e -> e.kind() == GpuEvent.Kind.FALLBACK));
    }

    private static void call(Class<?> c, String method, Object... args) throws Exception {
        for (Method m : c.getDeclaredMethods()) {
            if (m.getName().equals(method) && m.getParameterCount() == args.length) {
                try {
                    m.invoke(null, args);
                    return;
                } catch (InvocationTargetException e) {
                    if (e.getCause() instanceof RuntimeException re) {
                        throw re;
                    }
                    throw e;
                }
            }
        }
        throw new IllegalArgumentException(method);
    }

    private static long ulps(float a, float b) {
        if (Float.isNaN(a) || Float.isNaN(b)) {
            return Float.isNaN(a) && Float.isNaN(b) ? 0 : Long.MAX_VALUE;
        }
        return Math.abs(ordered(Float.floatToRawIntBits(a)) - ordered(Float.floatToRawIntBits(b)));
    }

    private static long ordered(int bits) {
        return bits >= 0 ? bits : Integer.MIN_VALUE - (long) bits;
    }
}
