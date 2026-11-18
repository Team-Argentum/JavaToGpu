package com.sixik.gpu.runtime.ffm;

import com.sixik.gpu.runtime.device.AttachedMemory;
import com.sixik.gpu.runtime.device.HostArena;
import com.sixik.gpu.runtime.device.HostMemory;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

/**
 * Мост между {@link HostMemory} ядра рантайма и {@code java.lang.foreign}.
 *
 * <p>Виды {@code GpuBuffer.readView()/writeView()} в ядре рантайма - {@link HostMemory}; с бэкендом FFM это та же
 * память, и {@link #segment} отдаёт её как {@code MemorySegment} без копирования:</p>
 * <pre>{@code
 * MemorySegment v = FfmMemory.segment(buffer.readView());
 * float x = v.getAtIndex(ValueLayout.JAVA_FLOAT, i);
 * }</pre>
 */
public final class FfmMemory {

    private FfmMemory() {
    }

    /**
     * @param memory память хоста
     * @return она же как сегмент; для памяти не из FFM ({@code HeapMemory} фейкового слоя) -
     *         {@link IllegalArgumentException}
     */
    public static MemorySegment segment(HostMemory memory) {
        HostMemory m = memory;
        while (m instanceof AttachedMemory a) {
            m = a.unwrap();
        }
        if (m instanceof SegmentMemory s) {
            return s.seg;
        }
        throw new IllegalArgumentException("not FFM memory: " + memory);
    }

    /**
     * @param segment сегмент
     * @return он же как {@link HostMemory}
     */
    public static HostMemory wrap(MemorySegment segment) {
        return new SegmentMemory(segment);
    }

    /**
     * @param kind вид
     * @return арена FFM этого вида
     */
    public static HostArena arena(HostArena.Kind kind) {
        return new FfmArena(kind);
    }

    /** Арена FFM: {@code CONFINED/SHARED/AUTO} - те же арены {@code java.lang.foreign}. */
    private static final class FfmArena implements HostArena {
        private final Arena arena;
        private final boolean auto;

        FfmArena(Kind kind) {
            this.auto = kind == Kind.AUTO;
            this.arena = switch (kind) {
                case CONFINED -> Arena.ofConfined();
                case SHARED -> Arena.ofShared();
                case AUTO -> Arena.ofAuto();
            };
        }

        @Override
        public HostMemory allocate(long bytes, long alignment) {
            return new SegmentMemory(arena.allocate(bytes, alignment));
        }

        @Override
        public HostMemory attach(HostMemory memory) {
            HostMemory m = memory;
            while (m instanceof AttachedMemory a) {
                m = a.unwrap();
            }
            if (m instanceof SegmentMemory s) {
                // reinterpret на ту же область: после закрытия арены обращение даёт IllegalStateException самого FFM
                @SuppressWarnings("restricted")
                MemorySegment view = s.seg.reinterpret(s.seg.byteSize(), arena, null);
                return new SegmentMemory(s.seg.isReadOnly() ? view.asReadOnly() : view);
            }
            throw new IllegalArgumentException("not FFM memory: " + memory);
        }

        @Override
        public void close() {
            if (!auto) {
                arena.close();
            }
        }
    }
}
