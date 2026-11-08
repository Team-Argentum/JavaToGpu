package com.sixik.gpu.runtime;

/**
 * Счётчики кэша скомпилированных ядер контекста.
 *
 * @param hits       запросов, обслуженных кэшем в памяти
 * @param misses     запросов, не найденных в памяти (потребовали диска либо генерации и компиляции)
 * @param entries    ядер в кэше в памяти (включая неудачные компиляции: их повтор бессмыслен)
 * @param diskHits   промахов памяти, обслуженных бинарным образом с диска
 * @param diskMisses промахов памяти, для которых образа на диске не оказалось, он был повреждён или драйвер его не принял
 * @param diskWrites образов, записанных на диск после компиляции из исходника
 */
public record CacheStats(long hits, long misses, int entries, long diskHits, long diskMisses, long diskWrites) {

    /** Статистика без дискового кэша. */
    public CacheStats(long hits, long misses, int entries) {
        this(hits, misses, entries, 0, 0, 0);
    }
}
