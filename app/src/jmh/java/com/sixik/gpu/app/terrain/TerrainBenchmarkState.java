package com.sixik.gpu.app.terrain;

import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;

/** Общие параметры JMH; один исполнитель на trial, подготовка и сверка вне тайминга. */
@State(Scope.Benchmark)
public class TerrainBenchmarkState {

    /** Измеряемый backend; GPU не имеет fallback и обязан быть видеокартой. */
    @Param({"CPU", "CPU_POOL", "GPU_ARRAYS", "GPU_PREPARED", "GPU_RESIDENT"})
    public TerrainExecutor.Backend backend;
    /** Число чанков в одном синхронном вызове. */
    @Param({"1", "8", "64"})
    public int chunksPerCall;
    /** Плотный расчёт блоков либо разреженные узлы сетки. */
    @Param({"COARSE", "DENSE"})
    public TerrainBatch.Sampling sampling;
    /** Число октав fBm; дополнительно всегда вычисляется одна октава domain warp. */
    @Param({"6"})
    public int octaves;
    /** Потоки CPU-пула; для прочих backend параметр не влияет на выполнение. */
    @Param({"20"})
    public int cpuWorkers;
    /** Переиспользуемые входы и выход. */
    protected TerrainBatch batch;
    /** Исполнитель с заранее созданными потоками/скомпилированным GPU-ядром. */
    protected TerrainExecutor executor;

    /**
     * Создаёт ресурсы и сверяет каждый результат с прямой CPU-реализацией.
     * @throws IllegalStateException при несовпадении результатов или неверном числе октав
     */
    @Setup(Level.Trial)
    public void setupTerrain() {
        if (octaves < 1 || octaves > 12) {
            throw new IllegalStateException("octaves must be in [1, 12]");
        }
        batch = new TerrainBatch(chunksPerCall, sampling);
        batch.octaves = octaves;
        executor = new TerrainExecutor(backend, batch, cpuWorkers);
        batch.computeCpu(0, batch.output.length);
        float[] expected = batch.output.clone();
        executor.compute();
        for (int i = 0; i < expected.length; i++) {
            // 2^-13 блока высоты: запас на порядок float-операций CPU/драйвера,
            // меньше 0.00013 блока. Fast math выключен, FMA contraction запрещён emitter-ом.
            if (!Float.isFinite(batch.output[i]) || Math.abs(expected[i] - batch.output[i]) > 0x1.0p-13f) {
                throw new IllegalStateException("CPU/GPU mismatch at " + i + ": " + expected[i] + " vs " + batch.output[i]);
            }
        }
        System.out.printf("TERRAIN device=%s backend=%s chunks=%d samples=%d sampling=%s workers=%d%n",
                executor.deviceName(), backend, chunksPerCall, batch.output.length, sampling, cpuWorkers);
    }

    /** Освобождает ресурсы trial, включая контекст GPU и постоянные CPU-потоки. */
    @TearDown(Level.Trial)
    public void closeTerrain() {
        if (executor != null) {
            executor.close();
        }
    }
}
