package com.sixik.gpu.app.terrain;

import com.sixik.gpu.ir.IrModule;
import com.sixik.gpu.ir.Result;
import com.sixik.gpu.runtime.GpuContext;
import com.sixik.gpu.runtime.GpuEvent;
import com.sixik.gpu.runtime.JavaToGpu;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Задержка первого вызова: сколько стоит сборка ядра драйвером без кэшей (кэш драйвера NVIDIA выключен в
 * {@code deviceTest}), из дискового кэша и из памяти. Печатает время {@code compile} по ядрам ландшафта.
 */
@Tag("device")
class FirstCallDeviceTest {

    private static final String[] KERNELS = {"generate", "generateResident", "countSolid"};

    /** Прогрев класса в фоне: сколько он длится, и первый вызов после него - попадание в кэш. */
    @Test
    void warmUpMakesTheFirstCallCheap() throws Exception {
        List<GpuEvent> events = java.util.Collections.synchronizedList(new ArrayList<>());
        Result<GpuContext> rc = GpuContext.builder().ignoreEnvironment().noDiskCache().listener(events::add)
                .fallback(GpuContext.Fallback.NEVER).fusion(false).tryBuild();
        Assumptions.assumeTrue(rc.isOk(), "no OpenCL device");
        try (GpuContext ctx = rc.value(); JavaToGpu.Scope s = JavaToGpu.use(ctx)) {
            long t = System.nanoTime();
            JavaToGpu.WarmUp w = JavaToGpu.warmUp(TerrainNoise.class).get();
            long warm = System.nanoTime() - t;
            org.junit.jupiter.api.Assertions.assertTrue(w.problems().isEmpty(), w.problems().toString());
            IrModule m = JavaToGpu.loadModule(TerrainNoise.class, "generate");
            t = System.nanoTime();
            org.junit.jupiter.api.Assertions.assertTrue(ctx.compile(m).isOk());
            long first = System.nanoTime() - t;
            System.out.printf(Locale.ROOT, "warm-up of %d kernels %.0f ms, then the first generate compile %.2f ms%n",
                    w.compiled(), warm / 1e6, first / 1e6);
        }
    }

    @Test
    void compileLatencyColdDiskAndMemory(@TempDir Path dir) {
        long[][] ms = new long[3][KERNELS.length];
        for (int pass = 0; pass < 2; pass++) {
            List<GpuEvent> events = new ArrayList<>();
            Result<GpuContext> rc = GpuContext.builder().ignoreEnvironment().diskCache(dir).listener(events::add)
                    .fallback(GpuContext.Fallback.NEVER).fusion(false).tryBuild();
            Assumptions.assumeTrue(rc.isOk(), "no OpenCL device");
            try (GpuContext ctx = rc.value()) {
                for (int i = 0; i < KERNELS.length; i++) {
                    IrModule m = JavaToGpu.loadModule(TerrainNoise.class, KERNELS[i]);
                    long t = System.nanoTime();
                    boolean ok = ctx.compile(m).isOk();
                    ms[pass][i] = System.nanoTime() - t;
                    if (!ok) {
                        throw new AssertionError(KERNELS[i] + " did not compile");
                    }
                    if (pass == 1) {
                        long t2 = System.nanoTime();
                        ctx.compile(m);
                        ms[2][i] = System.nanoTime() - t2;
                    }
                }
            }
        }
        String[] rows = {"cold (driver build)", "disk cache", "memory cache"};
        for (int r = 0; r < 3; r++) {
            StringBuilder sb = new StringBuilder(String.format(Locale.ROOT, "%-20s", rows[r]));
            for (int i = 0; i < KERNELS.length; i++) {
                sb.append(String.format(Locale.ROOT, "  %s %.1f ms", KERNELS[i], ms[r][i] / 1e6));
            }
            System.out.println(sb);
        }
    }
}
