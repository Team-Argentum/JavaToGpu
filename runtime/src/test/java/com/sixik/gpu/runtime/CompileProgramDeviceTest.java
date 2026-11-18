package com.sixik.gpu.runtime;

import com.sixik.gpu.ir.Result;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link GpuContext#compileProgram} на настоящем драйвере (шаг B6): одна программа из трёх ядер с общей функцией,
 * каждое ядро считает своё, программа из образа на диске - так же. Без драйвера тест пропускается.
 */
@Tag("device")
class CompileProgramDeviceTest {

    private static GpuContext open(Path dir, List<GpuEvent> events) {
        Result<GpuContext> r = GpuContext.builder().ignoreEnvironment().diskCache(dir).listener(events::add).tryBuild();
        Assumptions.assumeTrue(r.isOk(), "no OpenCL device");
        return r.value();
    }

    private static void check(GpuContext ctx) {
        Result<CompiledProgram> r = ctx.compileProgram(CompileProgramTest.module());
        assertTrue(r.isOk(), r.diagnostics().toString());
        CompiledProgram p = r.value();
        int n = 1000;
        int[] a = new int[n];
        for (int i = 0; i < n; i++) {
            a[i] = i - 7;
        }
        assertTrue(ctx.launch(p.kernel("square"), LaunchConfig.of1D(n), (Object) a).isOk());
        assertTrue(ctx.launch(p.kernel("plusOne"), LaunchConfig.of1D(n), (Object) a).isOk());
        int[] expected = new int[n];
        for (int i = 0; i < n; i++) {
            expected[i] = (i - 7) * (i - 7) + 1;
        }
        assertArrayEquals(expected, a);
        assertTrue(ctx.launch(p.kernel("fill"), LaunchConfig.of1D(n), (Object) a).isOk());
        for (int i = 0; i < n; i++) {
            expected[i] = i * i;
        }
        assertArrayEquals(expected, a);
    }

    @Test
    void programOfThreeKernelsRunsAndComesFromTheDisk(@TempDir Path dir) {
        List<GpuEvent> first = new ArrayList<>();
        try (GpuContext ctx = open(dir, first)) {
            check(ctx);
        }
        assertEquals(1, first.stream().filter(e -> e.kind() == GpuEvent.Kind.COMPILE
                && e.detail().startsWith("built; program of 3 kernels")).count(), first.toString());
        List<GpuEvent> second = new ArrayList<>();
        try (GpuContext ctx = open(dir, second)) {
            check(ctx);
        }
        assertTrue(second.stream().anyMatch(e -> e.kind() == GpuEvent.Kind.COMPILE
                && e.detail().startsWith("disk-hit; program of 3 kernels")), second.toString());
    }
}
