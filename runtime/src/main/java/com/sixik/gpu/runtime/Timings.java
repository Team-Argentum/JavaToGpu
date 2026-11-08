package com.sixik.gpu.runtime;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLongArray;

/**
 * Накопитель таймингов и счётчиков кэша поверх единственного {@link EventListener} (REWORK_ARCHITECTURE.md, разд. 10,
 * 5.24): подключается как приёмник событий и копит по каждому виду события число, суммарное время и байты, а для
 * компиляции - сколько ядер собрано, взято из памяти и взято с диска.
 *
 * <pre>{@code
 * Timings timings = new Timings();
 * GpuContext ctx = GpuContext.builder().listener(timings).build();
 * ... запуски ...
 * Timings.Snapshot s = timings.snapshot();
 * }</pre>
 *
 * <p>Потокобезопасен, не хранит события и не выделяет память на событие (массивы счётчиков фиксированного размера).</p>
 */
public final class Timings implements EventListener {

    private static final int KINDS = GpuEvent.Kind.values().length;
    private static final int BUILT = 0;
    private static final int MEMORY_HIT = 1;
    private static final int DISK_HIT = 2;
    private static final int FAILED = 3;

    private final AtomicLongArray count = new AtomicLongArray(KINDS);
    private final AtomicLongArray nanos = new AtomicLongArray(KINDS);
    private final AtomicLongArray bytes = new AtomicLongArray(KINDS);
    private final AtomicLongArray compile = new AtomicLongArray(4);

    /**
     * Сводка по виду события.
     *
     * @param count число событий
     * @param nanos суммарная длительность, нс
     * @param bytes суммарно перенесено байт
     */
    public record Total(long count, long nanos, long bytes) {
    }

    /**
     * Снимок накопленных значений.
     *
     * @param byKind       итоги по видам событий (все виды присутствуют)
     * @param built        ядер собрано компилятором драйвера из исходника
     * @param memoryHits   запросов компиляции, обслуженных кэшем в памяти
     * @param diskHits     ядер, собранных из бинарного образа с диска
     * @param failedBuilds неудачных компиляций
     */
    public record Snapshot(Map<GpuEvent.Kind, Total> byKind, long built, long memoryHits, long diskHits, long failedBuilds) {

        /** @param kind вид @return итог по виду */
        public Total of(GpuEvent.Kind kind) {
            return byKind.get(kind);
        }
    }

    @Override
    public void onEvent(GpuEvent event) {
        int k = event.kind().ordinal();
        count.incrementAndGet(k);
        nanos.addAndGet(k, event.nanos());
        bytes.addAndGet(k, event.bytes());
        if (event.kind() == GpuEvent.Kind.COMPILE) {
            String d = event.detail();
            if (d.startsWith("cache-hit")) {
                compile.incrementAndGet(MEMORY_HIT);
            } else if (d.startsWith("disk-hit")) {
                compile.incrementAndGet(DISK_HIT);
            } else if (d.startsWith("failed")) {
                compile.incrementAndGet(FAILED);
            } else {
                compile.incrementAndGet(BUILT);
            }
        }
    }

    /** @return текущие значения; последующие события в снимок не попадают */
    public Snapshot snapshot() {
        Map<GpuEvent.Kind, Total> map = new EnumMap<>(GpuEvent.Kind.class);
        for (GpuEvent.Kind kind : GpuEvent.Kind.values()) {
            int k = kind.ordinal();
            map.put(kind, new Total(count.get(k), nanos.get(k), bytes.get(k)));
        }
        return new Snapshot(Map.copyOf(map), compile.get(BUILT), compile.get(MEMORY_HIT), compile.get(DISK_HIT),
                compile.get(FAILED));
    }

    /** Обнуляет все счётчики. */
    public void reset() {
        for (int i = 0; i < KINDS; i++) {
            count.set(i, 0);
            nanos.set(i, 0);
            bytes.set(i, 0);
        }
        for (int i = 0; i < 4; i++) {
            compile.set(i, 0);
        }
    }
}
