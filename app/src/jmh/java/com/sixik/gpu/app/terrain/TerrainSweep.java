package com.sixik.gpu.app.terrain;

import com.sixik.gpu.runtime.JavaToGpu;

import java.nio.file.Path;

/** Дополнительный wall-clock прогон всего видимого окна; результаты следует отличать от статистики JMH. */
public final class TerrainSweep {

    /** Запрещает создание экземпляров CLI. */
    private TerrainSweep() {
    }

    private static final Path DEFAULT_DUMP_DIRECTORY = Path.of("build", "opencl");

    /**
     * Генерирует всё окно порциями по 64 чанка; последний неполный пакет дополняется повторными чанками.
     * Компиляция GPU и 5 прогревочных порций исключены из тайминга, результаты всех порций читаются хостом.
     * @param args необязательный режим COARSE или DENSE, по умолчанию COARSE
     */
    public static void main(String[] args) {
        TerrainBatch.Sampling sampling = args.length == 0 ? TerrainBatch.Sampling.COARSE
                : TerrainBatch.Sampling.valueOf(args[0]);
        int chunks = 64;
        int launches = (TerrainBatch.VISIBLE_CHUNKS + chunks - 1) / chunks;
        System.out.printf("WINDOW radius=32 chunks=%d height=360 sampling=%s uniqueSamples=%d submittedSamples=%d%n",
                TerrainBatch.VISIBLE_CHUNKS, sampling,
                (long) TerrainBatch.VISIBLE_CHUNKS * sampling.samplesPerChunk(),
                (long) launches * chunks * sampling.samplesPerChunk());

        JavaToGpu.configureDefault(s -> s.dumpSources(DEFAULT_DUMP_DIRECTORY));
        for (TerrainExecutor.Backend backend : TerrainExecutor.Backend.values()) {
            TerrainBatch batch = new TerrainBatch(chunks, sampling);
            try (TerrainExecutor executor = new TerrainExecutor(backend, batch, 20)) {
                for (int warmup = 0; warmup < 5; warmup++) {
                    executor.compute();
                }
                double checksum = 0;
                long start = System.nanoTime();
                for (int call = 0; call < launches; call++) {
                    batch.moveTo(call * chunks);
                    float[] output = executor.compute();
                    // Достаточно проверять контрольную сумму нескольких результатов каждой порции:
                    // полная поэлементная проверка backend уже есть в JUnit и @Setup JMH.
                    checksum += output[0] + output[output.length / 2] + output[output.length - 1];
                }
                double seconds = (System.nanoTime() - start) * 1e-9;
                System.out.printf("SWEEP backend=%s device=%s seconds=%.6f launches=%d checksum=%.9f%n",
                        backend, executor.deviceName(), seconds, launches, checksum);
            }
        }
    }
}
