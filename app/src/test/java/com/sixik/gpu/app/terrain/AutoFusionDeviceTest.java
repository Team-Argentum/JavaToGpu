package com.sixik.gpu.app.terrain;

import com.sixik.gpu.api.types.GpuFloatArray;
import com.sixik.gpu.api.types.GpuIntArray;
import com.sixik.gpu.ir.Result;
import com.sixik.gpu.runtime.GpuContext;
import com.sixik.gpu.runtime.GpuEvent;
import com.sixik.gpu.runtime.JavaToGpu;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.IntSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Автоматическое слияние «ядро → свёртка» на устройстве: обычный код {@code generateResident(density, ...)} и затем
 * {@code RangeReductions.solid(density)} - без слияния (два ядра, плотности пишутся в память), со слиянием (рантайм
 * откладывает генерацию и подставляет её в свёртку) и явная свёртка {@code TerrainNoise.solidCount}. Проход - как в
 * игровом цикле: загрузить координаты порции, посчитать породу. Число породы обязано совпасть; печатаются медианы.
 */
@Tag("device")
class AutoFusionDeviceTest {

    private static final int WARMUP = 20;
    private static final int PASSES = 200;

    private static GpuContext open(boolean fusion, List<GpuEvent> events) {
        Result<GpuContext> rc = GpuContext.builder().ignoreEnvironment().noDiskCache().fusion(fusion)
                .listener(events::add).fallback(GpuContext.Fallback.NEVER).tryBuild();
        Assumptions.assumeTrue(rc.isOk(), "no OpenCL device");
        return rc.value();
    }

    @Test
    void automaticFusionMatchesTheExplicitReduction() {
        List<GpuEvent> events = new ArrayList<>();
        try (GpuContext plain = open(false, new ArrayList<>()); GpuContext fused = open(true, events)) {
            System.out.println("grid    chunks samples  | two kernels ms  auto-fused ms  solidCount ms");
            for (TerrainBatch.Sampling grid : TerrainBatch.Sampling.values()) {
                for (int chunks : new int[]{8, 64}) {
                    measure(plain, fused, grid, chunks);
                }
            }
            assertTrue(events.stream().anyMatch(e -> e.kind() == GpuEvent.Kind.KERNEL
                    && "fused into a reduction".equals(e.detail())), "the fused path was taken");
            // без слияния генерация исполняется на каждом проходе, со слиянием - ни разу
            assertTrue(events.stream().noneMatch(e -> e.kind() == GpuEvent.Kind.KERNEL
                    && e.detail().startsWith("LaunchConfig[") && !e.detail().contains("/ 256]")),
                    "the deferred generation never ran on its own");
        }
    }

    private static void measure(GpuContext plain, GpuContext fused, TerrainBatch.Sampling g, int chunks) {
        TerrainBatch batch = new TerrainBatch(chunks, g);
        int n = batch.output.length;
        IntSupplier pair = pass(plain, batch, false);
        IntSupplier auto = pass(fused, batch, false);
        IntSupplier explicit = pass(fused, batch, true);
        int want = pair.getAsInt();
        assertEquals(want, auto.getAsInt(), "auto-fused count");
        assertEquals(want, explicit.getAsInt(), "solidCount");
        double[] ms = new double[3];
        IntSupplier[] all = {pair, auto, explicit};
        // варианты - блоками: чередование между двумя контекстами драйвера добавило бы переключение контекста
        for (int v = 0; v < 3; v++) {
            long[] times = new long[PASSES];
            for (int r = 0; r < WARMUP + PASSES; r++) {
                long t = System.nanoTime();
                int c = all[v].getAsInt();
                long dt = System.nanoTime() - t;
                assertEquals(want, c);
                if (r >= WARMUP) {
                    times[r - WARMUP] = dt;
                }
            }
            Arrays.sort(times);
            ms[v] = times[PASSES / 2] / 1e6;
        }
        System.out.printf(Locale.ROOT, "%-7s %6d %8d | %14.3f %13.3f %14.3f%n", g, chunks, n, ms[0], ms[1], ms[2]);
    }

    /** Проход игрового цикла в своём контексте: свои массивы, чтобы они не переезжали между контекстами. */
    private static IntSupplier pass(GpuContext ctx, TerrainBatch batch, boolean explicit) {
        TerrainBatch.Sampling g = batch.sampling;
        int n = batch.output.length;
        GpuIntArray x = new GpuIntArray(batch.chunkX.length);
        GpuIntArray z = new GpuIntArray(batch.chunkZ.length);
        GpuFloatArray density = new GpuFloatArray(n);
        return () -> {
            try (JavaToGpu.Scope s = JavaToGpu.use(ctx)) {
                x.copyFrom(batch.chunkX);
                z.copyFrom(batch.chunkZ);
                if (explicit) {
                    return TerrainNoise.solidCount(x, z, n, g.horizontal, g.vertical, g.stepXZ, g.stepY, batch.seed,
                            batch.octaves);
                }
                TerrainNoise.generateResident(density, x, z, g.horizontal, g.vertical, g.stepXZ, g.stepY, batch.seed,
                        batch.octaves);
                return RangeReductions.solid(density);
            }
        };
    }
}
