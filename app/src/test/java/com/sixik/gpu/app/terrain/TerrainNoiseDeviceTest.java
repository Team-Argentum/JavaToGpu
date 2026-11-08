package com.sixik.gpu.app.terrain;

import com.sixik.gpu.runtime.GpuContext;
import com.sixik.gpu.runtime.device.DeviceApi;
import com.sixik.gpu.test.GPUTest;
import com.sixik.gpu.test.GpuAssert;
import com.sixik.gpu.test.KernelSample;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Проверяет весь реальный путь Java -> IR -> OpenCL на той же нагрузке, которая измеряется JMH. */
@Tag("device")
class TerrainNoiseDeviceTest {

    /**
     * Сверяет все результаты dense/coarse, повторные seed и оба варианта GPU runtime.
     * @param available OpenCL-контекст для проверки доступности видеокарты
     */
    @GPUTest(requireDevice = true)
    void generate_actualGpu_matchesCpuAcrossSeeds(GpuContext available) {
        Assumptions.assumeTrue((available.device().type() & DeviceApi.TYPE_GPU) != 0, "GPU required");
        for (TerrainBatch.Sampling sampling : TerrainBatch.Sampling.values()) {
            for (TerrainExecutor.Backend backend : new TerrainExecutor.Backend[]{
                    TerrainExecutor.Backend.GPU_ARRAYS, TerrainExecutor.Backend.GPU_PREPARED}) {
                TerrainBatch batch = new TerrainBatch(1, sampling);
                try (TerrainExecutor executor = new TerrainExecutor(backend, batch, 1)) {
                    for (int pass = 0; pass < 3; pass++) {
                        batch.seed = TerrainBatch.WORLD_SEED + pass * 0x632be5ab;
                        batch.moveTo(2112 + pass);
                        batch.computeCpu(0, batch.output.length);
                        float[] expected = batch.output.clone();
                        // 2^-13 блока — тот же допуск, который проверяет JMH setup.
                        assertArrayEquals(expected, executor.compute(), 0x1.0p-13f,
                                backend + " " + sampling + " pass=" + pass);
                    }
                }
            }
        }
    }

    /**
     * Сверяет небольшой отрицательный чанк ещё и с эталонным IR-интерпретатором.
     * @param context доступный контекст OpenCL
     */
    @GPUTest(requireDevice = true)
    void generate_smallGrid_agreesWithInterpreter(GpuContext context) {
        // На этой сетке y <= 16, плотность порядка 100: 16 ULP соответствует
        // абсолютному допуску около 2^-13 блока для цепочки интерполяций и fBm.
        KernelSample sample = KernelSample.named("terrain-small")
                .args(() -> new Object[]{new float[12], new int[]{-1}, new int[]{-2},
                        2, 3, 4, 8, TerrainBatch.WORLD_SEED, 6})
                .range(KernelSample.Range.of1D(12))
                .ulps(16)
                .build();
        GpuAssert.Report report = GpuAssert.sameResult(TerrainNoise.class, "generate", sample, context);
        assertTrue(report.ranOn(KernelSample.Leg.JVM) && report.ranOn(KernelSample.Leg.INTERPRETER)
                && report.ranOn(KernelSample.Leg.DEVICE), report.toString());
    }

    /**
     * Проверяет, что зависимые от результатов проверки и повторные проходы совпадают с CPU.
     * @param context доступный OpenCL-контекст
     */
    @GPUTest(requireDevice = true)
    void tick_gpuConditionalPasses_matchesCpu(GpuContext context) {
        Assumptions.assumeTrue((context.device().type() & DeviceApi.TYPE_GPU) != 0, "GPU required");
        TerrainBatch cpuBatch = new TerrainBatch(8, TerrainBatch.Sampling.COARSE);
        TerrainBatch gpuBatch = new TerrainBatch(8, TerrainBatch.Sampling.COARSE);
        TerrainGameLoop cpuGame = new TerrainGameLoop(cpuBatch);
        TerrainGameLoop gpuGame = new TerrainGameLoop(gpuBatch);
        try (TerrainExecutor cpu = new TerrainExecutor(TerrainExecutor.Backend.CPU, cpuBatch, 1);
             TerrainExecutor gpu = new TerrainExecutor(TerrainExecutor.Backend.GPU_PREPARED, gpuBatch, 1)) {
            for (int tick = 0; tick < 6; tick++) {
                int calls = tick % 2 == 0 ? 1 : 3;
                assertEquals(cpuGame.tick(cpu, calls), gpuGame.tick(gpu, calls), "tick=" + tick);
                assertEquals(calls, gpuGame.lastCalls());
            }
        }
    }
}
