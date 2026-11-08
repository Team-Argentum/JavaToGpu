package com.sixik.gpu.runtime;

/**
 * Событие жизни контекста для логов, метрик и трассировки (REWORK_ARCHITECTURE.md, разд. 10: один {@link EventListener}
 * вместо шины событий, журналов и сервисов логирования).
 *
 * @param kind    вид события
 * @param subject о чём событие: имя устройства, точка входа ядра, описание буфера
 * @param nanos   длительность в наносекундах (0 для мгновенных событий)
 * @param bytes   перенесённые байты (для {@link Kind#UPLOAD} и {@link Kind#READBACK}), иначе 0
 * @param detail  подробности: {@code "cache-hit"} / {@code "built"}, текст предупреждения и т.п.
 */
public record GpuEvent(Kind kind, String subject, long nanos, long bytes, String detail) {

    /** Виды событий. */
    public enum Kind {
        /** Выбрано устройство. */
        DEVICE_SELECTED,
        /** Ядро получено из кэша или скомпилировано ({@code detail}). */
        COMPILE,
        /** Данные переданы на устройство. */
        UPLOAD,
        /** Ядро выполнено. */
        KERNEL,
        /** Данные прочитаны с устройства. */
        READBACK,
        /** Предупреждение (например, предупреждения build log драйвера). */
        WARNING,
        /** Ядро выполнено на JVM вместо устройства ({@code detail} - причина). */
        FALLBACK,
        /** Контекст закрыт. */
        CLOSED
    }
}
