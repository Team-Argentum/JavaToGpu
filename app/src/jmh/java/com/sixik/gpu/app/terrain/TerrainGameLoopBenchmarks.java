package com.sixik.gpu.app.terrain;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

import java.util.concurrent.TimeUnit;

/** Тик движка: сканирование окна радиуса 32, бюджет чанков, 1/3 условных прохода и классификация. */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 1, jvmArgsAppend = {"--enable-native-access=ALL-UNNAMED", "-Xms512m", "-Xmx2g"})
@Threads(1)
public class TerrainGameLoopBenchmarks extends TerrainBenchmarkState {

    /** Максимум вызовов генератора после проверок: только основной либо основной и два дополнительных. */
    @Param({"1", "3"})
    public int maxCalls;
    /** Метаданные окна и состояние игрового цикла. */
    private TerrainGameLoop game;

    /** Создаёт модель игрового цикла после подготовки общей порции. */
    @Setup(Level.Trial)
    public void setupGame() {
        game = new TerrainGameLoop(batch);
        // В тестовой геометрии есть и воздух, и порода: проверяем, что заявленные проходы выполняются.
        game.tick(executor, maxCalls);
        if (game.lastCalls() != maxCalls) {
            throw new IllegalStateException("Conditional passes did not execute: " + game.lastCalls());
        }
    }

    /**
     * Выполняет один тик с зависимыми от результата повторными вызовами.
     * @return контрольная сумма, включающая хостовую классификацию и число запусков
     */
    @Benchmark
    public long tick() {
        return game.tick(executor, maxCalls);
    }
}
