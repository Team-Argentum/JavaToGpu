package com.sixik.gpu.runtime.lwjgl;

import com.sixik.gpu.runtime.device.AbstractHostMemory;
import com.sixik.gpu.runtime.device.HostArena;
import com.sixik.gpu.runtime.device.HostMemory;
import org.lwjgl.system.MemoryUtil;

import java.lang.ref.Cleaner;
import java.util.ArrayList;
import java.util.List;

/**
 * Нативная память хоста через {@link MemoryUtil} LWJGL (Java 17, без FFM). Границы, «только чтение» и жизнь владельца
 * проверяет {@link AbstractHostMemory}; доступ - {@code memGetX}/{@code memPutX} по адресу.
 */
final class NativeMemory extends AbstractHostMemory {

    private static final Cleaner CLEANER = Cleaner.create();

    /** Хранилище: адрес и, для auto-арены, ссылка, которая держит память живой, пока жив хоть один вид. */
    static final class Storage {
        final long address;

        Storage(long address) {
            this.address = address;
        }
    }

    private final Storage storage;

    NativeMemory(Storage storage, long base, long size, boolean readOnly, Scope scope) {
        super(base, size, readOnly, scope);
        this.storage = storage;
    }

    /**
     * Память по чужому адресу (отображение закреплённого буфера драйвера): освобождает её владелец адреса.
     *
     * @param address адрес
     * @param size    размер
     * @return память, доступная любому потоку
     */
    static NativeMemory wrap(long address, long size) {
        return new NativeMemory(new Storage(address), 0, size, false, new Scope(true));
    }

    @Override
    protected AbstractHostMemory view(long base, long size, boolean readOnly, Scope scope) {
        return new NativeMemory(storage, base, size, readOnly, scope);
    }

    @Override
    protected long storageAddress() {
        return storage.address;
    }

    @Override protected byte rawGetByte(long at) { return MemoryUtil.memGetByte(storage.address + at); }
    @Override protected void rawSetByte(long at, byte v) { MemoryUtil.memPutByte(storage.address + at, v); }
    @Override protected short rawGetShort(long at) { return MemoryUtil.memGetShort(storage.address + at); }
    @Override protected void rawSetShort(long at, short v) { MemoryUtil.memPutShort(storage.address + at, v); }
    @Override protected int rawGetInt(long at) { return MemoryUtil.memGetInt(storage.address + at); }
    @Override protected void rawSetInt(long at, int v) { MemoryUtil.memPutInt(storage.address + at, v); }
    @Override protected long rawGetLong(long at) { return MemoryUtil.memGetLong(storage.address + at); }
    @Override protected void rawSetLong(long at, long v) { MemoryUtil.memPutLong(storage.address + at, v); }

    @Override
    protected void rawCopy(Object array, int from, long at, int count, int elemSize, boolean toStorage) {
        long a = storage.address + at;
        // цепочка instanceof, а не switch по шаблонам: модуль собирается под Java 17
        if (array instanceof byte[] x) {
            if (toStorage) MemoryUtil.memByteBuffer(a, count).put(x, from, count);
            else MemoryUtil.memByteBuffer(a, count).get(x, from, count);
        } else if (array instanceof short[] x) {
            if (toStorage) MemoryUtil.memShortBuffer(a, count).put(x, from, count);
            else MemoryUtil.memShortBuffer(a, count).get(x, from, count);
        } else if (array instanceof char[] x) {
            if (toStorage) MemoryUtil.memCharBuffer(a, count).put(x, from, count);
            else MemoryUtil.memCharBuffer(a, count).get(x, from, count);
        } else if (array instanceof int[] x) {
            if (toStorage) MemoryUtil.memIntBuffer(a, count).put(x, from, count);
            else MemoryUtil.memIntBuffer(a, count).get(x, from, count);
        } else if (array instanceof long[] x) {
            if (toStorage) MemoryUtil.memLongBuffer(a, count).put(x, from, count);
            else MemoryUtil.memLongBuffer(a, count).get(x, from, count);
        } else if (array instanceof float[] x) {
            if (toStorage) MemoryUtil.memFloatBuffer(a, count).put(x, from, count);
            else MemoryUtil.memFloatBuffer(a, count).get(x, from, count);
        } else if (array instanceof double[] x) {
            if (toStorage) MemoryUtil.memDoubleBuffer(a, count).put(x, from, count);
            else MemoryUtil.memDoubleBuffer(a, count).get(x, from, count);
        } else {
            throw new IllegalArgumentException("unsupported array " + array.getClass());
        }
    }

    /** Арена нативной памяти: {@code nmemAlignedAlloc}, освобождение при закрытии либо (auto) сборщиком мусора. */
    static final class Arena implements HostArena {
        private final Kind kind;
        private final Scope scope;
        private final List<Long> owned = new ArrayList<>();
        private boolean closed;

        Arena(Kind kind) {
            this.kind = kind;
            this.scope = new Scope(kind != Kind.CONFINED);
        }

        @Override
        public synchronized HostMemory allocate(long bytes, long alignment) {
            if (closed) {
                throw new IllegalStateException("the arena is closed");
            }
            if (bytes < 0) {
                throw new IllegalArgumentException("negative size " + bytes);
            }
            long align = Math.max(alignment, 16);
            long address = MemoryUtil.nmemAlignedAlloc(align, Math.max(bytes, 1));
            if (address == 0) {
                throw new OutOfMemoryError("native allocation of " + bytes + " bytes failed");
            }
            MemoryUtil.memSet(address, 0, Math.max(bytes, 1));
            Storage s = new Storage(address);
            if (kind == Kind.AUTO) {
                CLEANER.register(s, () -> MemoryUtil.nmemAlignedFree(address));
            } else {
                owned.add(address);
            }
            return new NativeMemory(s, 0, bytes, false, scope);
        }

        @Override
        public HostMemory attach(HostMemory memory) {
            return com.sixik.gpu.runtime.device.AttachedMemory.attach(memory, scope);
        }

        @Override
        public synchronized void close() {
            if (closed || kind == Kind.AUTO) {
                return;
            }
            closed = true;
            scope.close();
            for (long a : owned) {
                MemoryUtil.nmemAlignedFree(a);
            }
            owned.clear();
        }
    }
}
