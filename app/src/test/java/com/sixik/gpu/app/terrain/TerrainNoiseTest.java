package com.sixik.gpu.app.terrain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Проверяет математические опорные значения, раскладку образцов и CPU-пул. */
class TerrainNoiseTest {

    /**
     * Сверяет известные значения quintic fade, точно представимые в float.
     * @param t доля ячейки
     * @param expected ожидаемый вес
     */
    @ParameterizedTest
    @CsvSource({"0, 0", "0.25, 0.103515625", "0.5, 0.5", "0.75, 0.896484375", "1, 1"})
    void fade_referenceValues_matchesPolynomial(float t, float expected) {
        assertEquals(expected, TerrainNoise.fade(t), 0f);
    }

    /** Проверяет узлы, отрицательные координаты, непрерывность и диапазон шума. */
    @Test
    void noise_latticeAndNegativeCoordinates_matchesAnchors() {
        assertEquals(-1f, TerrainNoise.lattice(0, 0, 0, 0), 0f);
        for (int x = -5; x <= 5; x++) {
            assertEquals(TerrainNoise.lattice(x, -2, 3, 123), TerrainNoise.noise(x, -2, 3, 123), 0f);
            float left = TerrainNoise.noise(x - 0x1.0p-12f, 0.25f, -0.5f, 123);
            float right = TerrainNoise.noise(x + 0x1.0p-12f, 0.25f, -0.5f, 123);
            // При quintic fade изменение около узла имеет третий порядок по dx;
            // 2^-18 покрывает округление промежуточных float-интерполяций.
            assertEquals(left, right, 0x1.0p-18f);
            assertTrue(left >= -1.000001f && left <= 1.000001f);
        }
    }

    /** Проверяет границы плотности, обусловленные суммой амплитуд и вертикальным градиентом. */
    @Test
    void density_worldHeight_hasGroundAndAir() {
        for (int seed : new int[]{0, 1, -1, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
            float bottom = TerrainNoise.density(-512, 0, 512, seed, 6);
            float top = TerrainNoise.density(-512, 360, 512, seed, 6);
            assertTrue(bottom >= 96f && bottom <= 192f);
            assertTrue(top >= -264f && top <= -168f);
            assertTrue(Float.isFinite(TerrainNoise.density(0, 144, 0, seed, 12)));
        }
    }

    /**
     * Проверяет CPU-пул поэлементно и координаты последнего образца.
     * @param sampling плотная либо разреженная геометрия
     */
    @ParameterizedTest
    @EnumSource(TerrainBatch.Sampling.class)
    void compute_parallel_matchesSerialAndLayout(TerrainBatch.Sampling sampling) {
        TerrainBatch batch = new TerrainBatch(2, sampling);
        batch.moveTo(TerrainBatch.VISIBLE_CHUNKS - 1);
        assertArrayEquals(new int[]{32, -32}, batch.chunkX);
        assertArrayEquals(new int[]{32, -32}, batch.chunkZ);
        batch.computeCpu(0, batch.output.length);
        float[] expected = batch.output.clone();
        int last = batch.output.length - 1;
        float lastDensity = TerrainNoise.density(-512 + (sampling.horizontal - 1) * sampling.stepXZ,
                (sampling.vertical - 1) * sampling.stepY,
                -512 + (sampling.horizontal - 1) * sampling.stepXZ, batch.seed, batch.octaves);
        assertEquals(lastDensity, expected[last], 0f);
        try (TerrainExecutor executor = new TerrainExecutor(TerrainExecutor.Backend.CPU_POOL, batch, 4)) {
            for (int run = 0; run < 3; run++) {
                assertArrayEquals(expected, executor.compute(), 0f);
            }
        }
    }

    /** Проверяет размеры: радиус окна, количество блоков и узлов сетки, некорректные бюджеты. */
    @Test
    void geometry_radius32AndHeight360_matchesWorkload() {
        assertEquals(4225, TerrainBatch.VISIBLE_CHUNKS);
        assertEquals(389376000L, (long) TerrainBatch.VISIBLE_CHUNKS * TerrainBatch.Sampling.DENSE.samplesPerChunk());
        assertEquals(4858750L, (long) TerrainBatch.VISIBLE_CHUNKS * TerrainBatch.Sampling.COARSE.samplesPerChunk());
        assertThrows(IllegalArgumentException.class, () -> new TerrainBatch(0, TerrainBatch.Sampling.COARSE));
        assertThrows(IllegalArgumentException.class, () -> new TerrainBatch(4226, TerrainBatch.Sampling.COARSE));
    }
}
