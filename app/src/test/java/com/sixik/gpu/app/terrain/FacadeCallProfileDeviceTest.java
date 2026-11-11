package com.sixik.gpu.app.terrain;

import com.sixik.gpu.api.types.GpuFloatArray;
import com.sixik.gpu.api.types.GpuIntArray;
import com.sixik.gpu.ir.Result;
import com.sixik.gpu.runtime.GpuContext;
import com.sixik.gpu.runtime.JavaToGpu;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Locale;

/**
 * Накладные расходы одного вызова ядра через фасад ({@code TerrainNoise.countSolid(...)} на маленьких {@code GpuArray}):
 * вызовы не ждут устройство (асинхронная цепочка), поэтому среднее время на вызов - это работа хоста. Печатает среднее;
 * при {@code -Dprofile.jfr=<файл>} пишет запись Java Flight Recorder для разбора по методам.
 */
@Tag("device")
class FacadeCallProfileDeviceTest {

    @Test
    void perCallHostOverhead() throws Exception {
        Result<GpuContext> rc = GpuContext.builder().ignoreEnvironment().noDiskCache()
                .fallback(GpuContext.Fallback.NEVER).tryBuild();
        Assumptions.assumeTrue(rc.isOk(), "no OpenCL device");
        GpuIntArray partial = new GpuIntArray(64);
        GpuFloatArray density = new GpuFloatArray(1024);
        try (GpuContext ctx = rc.value(); JavaToGpu.Scope s = JavaToGpu.use(ctx)) {
            for (int i = 0; i < 2000; i++) {
                TerrainNoise.countSolid(partial, density);
            }
            partial.get(0);
            String jfr = System.getProperty("profile.jfr");
            jdk.jfr.Recording rec = null;
            if (jfr != null && !jfr.isBlank()) {
                rec = new jdk.jfr.Recording();
                rec.enable("jdk.ExecutionSample").withPeriod(java.time.Duration.ofMillis(1));
                rec.start();
            }
            int calls = 20_000;
            long t0 = System.nanoTime();
            for (int i = 0; i < calls; i++) {
                TerrainNoise.countSolid(partial, density);
            }
            long host = System.nanoTime() - t0;
            partial.get(0);
            long total = System.nanoTime() - t0;
            if (rec != null) {
                rec.stop();
                rec.dump(java.nio.file.Path.of(jfr));
                rec.close();
            }
            System.out.printf(Locale.ROOT, "facade call: %.1f us host per call, %.1f us per call incl. device%n",
                    host / 1e3 / calls, total / 1e3 / calls);

            // тот же вызов, но перед каждым меняется маленький вход на хосте: + одна асинхронная загрузка
            GpuFloatArray input = new GpuFloatArray(64);
            float[] fresh = new float[64];
            for (int i = 0; i < 2000; i++) {
                input.copyFrom(fresh);
                TerrainNoise.countSolid(partial, input);
            }
            partial.get(0);
            t0 = System.nanoTime();
            for (int i = 0; i < calls; i++) {
                fresh[0] = i;
                input.copyFrom(fresh); // вход ядра изменён на хосте: перед запуском - одна загрузка 256 байт
                TerrainNoise.countSolid(partial, input);
            }
            long withUpload = System.nanoTime() - t0;
            partial.get(0);
            System.out.printf(Locale.ROOT, "facade call with a 256 B upload: %.1f us host per call (upload ~%.1f us)%n",
                    withUpload / 1e3 / calls, (withUpload - host) / 1e3 / calls);

            // вызов сразу после блокирующего чтения (очередь пуста): так устроен проход игрового цикла
            int syncedCalls = 2000;
            long callAfterSync = 0;
            for (int i = 0; i < syncedCalls; i++) {
                long t = System.nanoTime();
                TerrainNoise.countSolid(partial, input);
                callAfterSync += System.nanoTime() - t;
                partial.get(0); // блокирующее чтение: ждёт ядро
                partial.set(0, 0); // хост снова владеет данными, следующее чтение будет новым
            }
            System.out.printf(Locale.ROOT, "facade call right after a blocking read: %.1f us%n",
                    callAfterSync / 1e3 / syncedCalls);
        }
    }
}
