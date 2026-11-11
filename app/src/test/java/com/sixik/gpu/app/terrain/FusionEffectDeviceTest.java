package com.sixik.gpu.app.terrain;

import com.sixik.gpu.api.types.GpuFloatArray;
import com.sixik.gpu.api.types.GpuIntArray;
import com.sixik.gpu.ir.Result;
import com.sixik.gpu.runtime.GpuContext;
import com.sixik.gpu.runtime.JavaToGpu;
import com.sixik.gpu.runtime.LaunchConfig;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Locale;
import java.util.function.IntSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Сколько даёт слияние {@code generateResident} + {@code countSolid} в одно ядро (плотности не пишутся в память, один
 * запуск вместо двух). Сравнивает проход игрового цикла «загрузить координаты, сгенерировать, посчитать, прочитать суммы»
 * для пары ядер и двух слитых вручную вариантов: с формой запуска {@code countSolid} (шаг по сетке) и с формой
 * {@code generate} (work-item на образец, редукция в work-group). Печатает медиану прохода; проверяет только совпадение
 * числа «породы».
 */
@Tag("device")
class FusionEffectDeviceTest {

    private static final int WARMUP = 20;
    private static final int PASSES = 200;

    @Test
    void fusedVersusTwoKernels() {
        Result<GpuContext> rc = GpuContext.builder().ignoreEnvironment().noDiskCache()
                .fallback(GpuContext.Fallback.NEVER).tryBuild();
        Assumptions.assumeTrue(rc.isOk(), "no OpenCL device");
        try (GpuContext ctx = rc.value(); JavaToGpu.Scope s = JavaToGpu.use(ctx)) {
            System.out.println("grid    chunks samples  | pair ms  strided ms  groups ms  @Reduction ms");
            for (TerrainBatch.Sampling grid : TerrainBatch.Sampling.values()) {
                for (int chunks : new int[]{8, 64}) {
                    measure(grid, chunks);
                }
            }
        }
    }

    private static void measure(TerrainBatch.Sampling g, int chunks) {
        TerrainBatch batch = new TerrainBatch(chunks, g);
        int n = batch.output.length;
        GpuIntArray x = new GpuIntArray(chunks);
        GpuIntArray z = new GpuIntArray(chunks);
        GpuFloatArray density = new GpuFloatArray(n);
        GpuIntArray partial = new GpuIntArray(TerrainExecutor.partials(n));
        int groups = (n + TerrainNoise.FUSED_GROUP - 1) / TerrainNoise.FUSED_GROUP;
        GpuIntArray groupSums = new GpuIntArray(groups);
        LaunchConfig groupLaunch = LaunchConfig.of1D((long) groups * TerrainNoise.FUSED_GROUP);

        IntSupplier pair = () -> {
            upload(batch, x, z);
            TerrainNoise.generateResident(density, x, z, g.horizontal, g.vertical, g.stepXZ, g.stepY, batch.seed,
                    batch.octaves);
            TerrainNoise.countSolid(partial, density);
            return sum(partial);
        };
        IntSupplier strided = () -> {
            upload(batch, x, z);
            TerrainNoise.countSolidFusedStrided(partial, x, z, n, g.horizontal, g.vertical, g.stepXZ, g.stepY,
                    batch.seed, batch.octaves);
            return sum(partial);
        };
        IntSupplier grouped = () -> {
            upload(batch, x, z);
            try (JavaToGpu.Scope l = JavaToGpu.launch(groupLaunch)) {
                TerrainNoise.countSolidFusedGroups(groupSums, x, z, n, g.horizontal, g.vertical, g.stepXZ, g.stepY,
                        batch.seed, batch.octaves);
            }
            return sum(groupSums);
        };
        IntSupplier reduction = () -> {
            upload(batch, x, z);
            return TerrainNoise.solidCount(x, z, n, g.horizontal, g.vertical, g.stepXZ, g.stepY, batch.seed,
                    batch.octaves);
        };
        int expected = pair.getAsInt();
        assertEquals(expected, strided.getAsInt(), "strided fused count");
        assertEquals(expected, grouped.getAsInt(), "grouped fused count");
        assertEquals(expected, reduction.getAsInt(), "@Reduction count");
        double a = median(pair);
        double b = median(strided);
        double c = median(grouped);
        double d = median(reduction);
        // второй круг: порядок замеров влияет на частоты GPU, берём лучшее из двух
        a = Math.min(a, median(pair));
        b = Math.min(b, median(strided));
        c = Math.min(c, median(grouped));
        d = Math.min(d, median(reduction));
        System.out.printf(Locale.ROOT, "%-7s %6d %8d | %7.3f %10.3f %10.3f %13.3f%n", g, chunks, n, a, b, c, d);
    }

    private static void upload(TerrainBatch batch, GpuIntArray x, GpuIntArray z) {
        x.copyFrom(batch.chunkX);
        z.copyFrom(batch.chunkZ);
    }

    private static int sum(GpuIntArray a) {
        int s = 0;
        for (int i = 0; i < a.length(); i++) {
            s += a.get(i);
        }
        return s;
    }

    /** Медиана времени прохода в миллисекундах. */
    private static double median(IntSupplier pass) {
        for (int i = 0; i < WARMUP; i++) {
            pass.getAsInt();
        }
        double[] ms = new double[PASSES];
        for (int i = 0; i < PASSES; i++) {
            long t0 = System.nanoTime();
            pass.getAsInt();
            ms[i] = (System.nanoTime() - t0) / 1e6;
        }
        Arrays.sort(ms);
        return ms[PASSES / 2];
    }
}
