package com.sixik.gpu.runtime;

/**
 * Приёмник событий {@link GpuContext}: логирование, метрики, трассировка. Исключение, брошенное приёмником,
 * подавляется - наблюдатель не должен ломать вычисление. Вызывается синхронно в потоке, породившем событие.
 */
@FunctionalInterface
public interface EventListener {

    /** Приёмник, который ничего не делает. */
    EventListener NONE = event -> {
    };

    /**
     * @param event событие
     */
    void onEvent(GpuEvent event);
}
