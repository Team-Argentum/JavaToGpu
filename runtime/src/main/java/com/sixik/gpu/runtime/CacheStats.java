package com.sixik.gpu.runtime;

/**
 * Счётчики кэша скомпилированных ядер контекста.
 *
 * @param hits    запросов, обслуженных из кэша
 * @param misses  запросов, потребовавших генерации и компиляции
 * @param entries ядер в кэше (включая неудачные компиляции: их повтор бессмыслен)
 */
public record CacheStats(long hits, long misses, int entries) {
}
