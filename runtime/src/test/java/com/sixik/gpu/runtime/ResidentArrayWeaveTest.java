package com.sixik.gpu.runtime;

import com.sixik.gpu.api.WorkItem;
import com.sixik.gpu.api.WorkItemContext;
import com.sixik.gpu.api.types.GpuFloatArray;
import com.sixik.gpu.api.types.GpuIntArray;
import com.sixik.gpu.weaver.KernelCompiler;
import com.sixik.gpu.weaver.Weaver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ядро с {@code GpuFloatArray} проходит процессор и weaver, а оставленное weaver'ом тело {@code <имя>$jvm} (JVM-fallback)
 * работает с теми же объектами без устройства.
 */
class ResidentArrayWeaveTest {

    private static final String SOURCE = """
            package demo;

            import com.sixik.gpu.api.Gpu;
            import com.sixik.gpu.api.annotations.DeviceFunction;
            import com.sixik.gpu.api.annotations.Kernel;
            import com.sixik.gpu.api.types.GpuFloatArray;
            import com.sixik.gpu.api.types.GpuIntArray;

            public class Res {

                @DeviceFunction
                static float twice(GpuFloatArray a, int i) {
                    return a.get(i) * 2f;
                }

                @Kernel
                public static void k(GpuFloatArray a, GpuFloatArray out, GpuIntArray count) {
                    int i = Gpu.getGlobalId(0);
                    if (i < a.length()) {
                        out.set(i, twice(a, i) + 1f);
                        count.set(i, count.get(i) + 1);
                    }
                }
            }
            """;

    @Test
    void jvmBodyRunsOnResidentArrays(@TempDir Path dir) throws Exception {
        KernelCompiler.Compiled r = KernelCompiler.compile(dir, SOURCE);
        assertTrue(r.success(), r.diagnostics());
        assertEquals(1, Weaver.weaveTree(dir).kernels());
        try (URLClassLoader loader = new URLClassLoader(new URL[]{dir.toUri().toURL()}, getClass().getClassLoader())) {
            Class<?> c = loader.loadClass("demo.Res");
            Method body = c.getDeclaredMethod("k$jvm", GpuFloatArray.class, GpuFloatArray.class, GpuIntArray.class);
            GpuFloatArray a = GpuFloatArray.of(1, 2, 3);
            GpuFloatArray out = new GpuFloatArray(3);
            GpuIntArray count = GpuIntArray.of(5, 5, 5);
            WorkItem.run(WorkItemContext.of1D(3, 1), () -> {
                WorkItemContext ctx = WorkItem.current();
                try {
                    for (int i = 0; i < 3; i++) {
                        ctx.setGlobalId(i, 0, 0);
                        body.invoke(null, a, out, count);
                    }
                } catch (ReflectiveOperationException e) {
                    throw new AssertionError(e);
                }
            });
            assertArrayEquals(new float[]{3, 5, 7}, out.toArray());
            assertArrayEquals(new int[]{6, 6, 6}, count.toArray());
        }
    }
}
