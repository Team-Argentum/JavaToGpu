package com.sixik.gpu.app.terrain;

import com.sixik.gpu.ir.Result;
import com.sixik.gpu.runtime.CompiledKernel;
import com.sixik.gpu.runtime.GpuContext;
import com.sixik.gpu.runtime.JavaToGpu;
import com.sixik.gpu.runtime.LaunchConfig;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

/**
 * Размер work-group обычного вызова: фасад запускает ядро без {@code local}, и размер выбирает драйвер. Замер ядра
 * ландшафта {@code TerrainNoise.generate} без {@code local} и с явными 64..1024 (варианты чередуются по раундам,
 * результат обязан совпасть); печатаются медианы.
 */
@Tag("device")
class WorkGroupSizeDeviceTest {

    private static final int RUNS = 40;
    private static final long[] LOCALS = {0, 32, 64, 128, 256, 512, 1024};

    @Test
    void driverChosenVersusExplicitWorkGroups() {
        Result<GpuContext> rc = GpuContext.builder().ignoreEnvironment().noDiskCache()
                .fallback(GpuContext.Fallback.NEVER).fusion(false).tryBuild();
        Assumptions.assumeTrue(rc.isOk(), "no OpenCL device");
        try (GpuContext ctx = rc.value()) {
            CompiledKernel k = ctx.compile(JavaToGpu.loadModule(TerrainNoise.class, "generate")).value();
            System.out.println("kernel max work-group " + k.maxWorkGroupSize() + ", registers " + k.registers());
            for (TerrainBatch.Sampling g : TerrainBatch.Sampling.values()) {
                for (int chunks : new int[]{8, 64}) {
                    TerrainBatch batch = new TerrainBatch(chunks, g);
                    int n = batch.output.length;
                    try (com.sixik.gpu.runtime.GpuBuffer out = ctx.allocate(com.sixik.gpu.ir.Types.F32, n);
                         com.sixik.gpu.runtime.GpuBuffer x = ctx.upload(batch.chunkX);
                         com.sixik.gpu.runtime.GpuBuffer z = ctx.upload(batch.chunkZ)) {
                        Object[] args = {out, x, z, g.horizontal, g.vertical, g.stepXZ, g.stepY, batch.seed,
                                batch.octaves};
                        long[][] times = new long[LOCALS.length][RUNS];
                        float[] reference = null;
                        for (int r = 0; r < RUNS + 5; r++) {
                            for (int v = 0; v < LOCALS.length; v++) {
                                long local = LOCALS[v];
                                long global = local == 0 ? n : (n + local - 1) / local * local;
                                if (global != n || local > k.maxWorkGroupSize()) {
                                    continue; // размер запуска обязан делиться на группу; группа - в пределе ядра
                                }
                                LaunchConfig cfg = local == 0 ? LaunchConfig.of1D(n) : LaunchConfig.of1D(n, local);
                                long t = System.nanoTime();
                                var launched = ctx.launch(k, cfg, args);
                                long dt = System.nanoTime() - t;
                                org.junit.jupiter.api.Assertions.assertTrue(launched.isOk(),
                                        "local " + local + ": " + launched.diagnostics());
                                if (r >= 5) {
                                    times[v][r - 5] = dt;
                                }
                                if (r == 0) {
                                    float[] got = new float[n];
                                    out.read(got);
                                    if (reference == null) {
                                        reference = got;
                                    } else {
                                        assertArrayEquals(reference, got, "local " + local);
                                    }
                                }
                            }
                        }
                        StringBuilder sb = new StringBuilder(String.format(Locale.ROOT, "%-6s %2d chunks (%,d):", g,
                                chunks, n));
                        for (int v = 0; v < LOCALS.length; v++) {
                            if (times[v][RUNS - 1] == 0) {
                                continue;
                            }
                            Arrays.sort(times[v]);
                            sb.append(String.format(Locale.ROOT, "  %s %.3f", LOCALS[v] == 0 ? "auto" : LOCALS[v],
                                    times[v][RUNS / 2] / 1e6));
                        }
                        System.out.println(sb);
                    }
                }
            }
        }
    }
}
