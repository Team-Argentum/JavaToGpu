package com.sixik.gpu.app.terrain;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

import java.util.concurrent.TimeUnit;

/** Полная синхронная стоимость одной порции; GPU включает выделения/передачи согласно backend. */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 1, jvmArgsAppend = {"--enable-native-access=ALL-UNNAMED", "-Xms512m", "-Xmx2g"})
@Threads(1)
public class TerrainBatchBenchmarks extends TerrainBenchmarkState {

    /**
     * Вычисляет весь output и возвращает его JMH, исключая dead-code elimination.
     * @return массив chunksPerCall * samplesPerChunk плотностей
     */
    @Benchmark
    public float[] generate() {
        return executor.compute();
    }
}
