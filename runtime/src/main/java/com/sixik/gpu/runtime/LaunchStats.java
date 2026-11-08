package com.sixik.gpu.runtime;

/**
 * Итог запуска: тайминги и объёмы передачи (REWORK_ARCHITECTURE.md, разд. 2: Timings/статистика остаются в v1).
 *
 * @param uploadNanos   передача данных на устройство
 * @param kernelNanos   выполнение ядра (постановка в очередь и ожидание)
 * @param readbackNanos возврат данных на хост
 * @param bytesUp       байт передано на устройство
 * @param bytesDown     байт возвращено на хост
 */
public record LaunchStats(long uploadNanos, long kernelNanos, long readbackNanos, long bytesUp, long bytesDown) {
}
