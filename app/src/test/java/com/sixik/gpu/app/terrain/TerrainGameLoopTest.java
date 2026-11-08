package com.sixik.gpu.app.terrain;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Проверяет работу бюджета чанков и повторных проходов после хостовых проверок. */
class TerrainGameLoopTest {

    /**
     * Сверяет тики одного потока и пула, включая переход границы полного видимого окна.
     * @param maxCalls число проходов на тик, один либо три
     */
    @ParameterizedTest
    @ValueSource(ints = {1, 3})
    void tick_repeatedConditionalCalls_matchesParallelCpu(int maxCalls) {
        TerrainBatch serial = new TerrainBatch(64, TerrainBatch.Sampling.COARSE);
        TerrainBatch parallel = new TerrainBatch(64, TerrainBatch.Sampling.COARSE);
        // Для проверки планировщика достаточно одной октавы; сами шесть октав покрыты тестом математики.
        serial.octaves = 1;
        parallel.octaves = 1;
        TerrainGameLoop a = new TerrainGameLoop(serial);
        TerrainGameLoop b = new TerrainGameLoop(parallel);
        try (TerrainExecutor cpu = new TerrainExecutor(TerrainExecutor.Backend.CPU, serial, 1);
             TerrainExecutor pool = new TerrainExecutor(TerrainExecutor.Backend.CPU_POOL, parallel, 4)) {
            assertThrows(IllegalArgumentException.class, () -> a.tick(cpu, 0));
            assertThrows(IllegalArgumentException.class, () -> a.tick(cpu, 4));
            for (int tick = 0; tick < 70; tick++) {
                assertEquals(a.tick(cpu, maxCalls), b.tick(pool, maxCalls), "tick=" + tick);
                assertEquals(maxCalls, a.lastCalls());
                assertEquals(maxCalls, b.lastCalls());
            }
        }
    }
}
