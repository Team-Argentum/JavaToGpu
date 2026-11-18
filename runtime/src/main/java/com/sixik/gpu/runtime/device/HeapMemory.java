package com.sixik.gpu.runtime.device;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * {@link HostMemory} в куче JVM ({@code byte[]}, до 2 ГиБ, адрес {@code 0}). Её дают слои доступа без нативной памяти
 * (фейковый слой тестов) и реализации {@link DeviceApi} по умолчанию. Драйверу такую память передать нельзя: бэкенд,
 * получив её, копирует данные в свою нативную память.
 */
public final class HeapMemory extends AbstractHostMemory {

    private static final ByteOrder ORDER = ByteOrder.nativeOrder();
    private static final VarHandle SHORT = MethodHandles.byteArrayViewVarHandle(short[].class, ORDER);
    private static final VarHandle INT = MethodHandles.byteArrayViewVarHandle(int[].class, ORDER);
    private static final VarHandle LONG = MethodHandles.byteArrayViewVarHandle(long[].class, ORDER);

    private final byte[] data;

    private HeapMemory(byte[] data, long base, long size, boolean readOnly, Scope scope) {
        super(base, size, readOnly, scope);
        this.data = data;
    }

    /**
     * Память кучи с собственным сроком жизни, который не кончается (для тестов и одиночных значений).
     *
     * @param bytes размер
     * @return память из нулей
     */
    public static HeapMemory allocate(long bytes) {
        return new HeapMemory(new byte[checkedSize(bytes)], 0, bytes, false, new Scope(true));
    }

    /**
     * Арена памяти кучи.
     *
     * @param kind вид (для кучи все виды доступны любому потоку; {@code CONFINED} лишь сообщает, что память одного
     *             потока, чтобы рантайм не делил копирование)
     * @return арена
     */
    public static HostArena arena(HostArena.Kind kind) {
        return new Arena(kind);
    }

    private static int checkedSize(long bytes) {
        if (bytes < 0 || bytes > Integer.MAX_VALUE - 8) {
            throw new IllegalArgumentException("heap host memory is limited to 2 GiB, requested " + bytes);
        }
        return (int) bytes;
    }

    @Override
    protected AbstractHostMemory view(long base, long size, boolean readOnly, Scope scope) {
        return new HeapMemory(data, base, size, readOnly, scope);
    }

    @Override
    protected long storageAddress() {
        return 0;
    }

    @Override
    protected byte rawGetByte(long at) {
        return data[(int) at];
    }

    @Override
    protected void rawSetByte(long at, byte v) {
        data[(int) at] = v;
    }

    @Override
    protected short rawGetShort(long at) {
        return (short) SHORT.get(data, (int) at);
    }

    @Override
    protected void rawSetShort(long at, short v) {
        SHORT.set(data, (int) at, v);
    }

    @Override
    protected int rawGetInt(long at) {
        return (int) INT.get(data, (int) at);
    }

    @Override
    protected void rawSetInt(long at, int v) {
        INT.set(data, (int) at, v);
    }

    @Override
    protected long rawGetLong(long at) {
        return (long) LONG.get(data, (int) at);
    }

    @Override
    protected void rawSetLong(long at, long v) {
        LONG.set(data, (int) at, v);
    }

    @Override
    protected void rawCopy(Object array, int from, long at, int count, int elemSize, boolean toStorage) {
        if (array instanceof byte[] b) {
            if (toStorage) {
                System.arraycopy(b, from, data, (int) at, count);
            } else {
                System.arraycopy(data, (int) at, b, from, count);
            }
            return;
        }
        ByteBuffer bb = ByteBuffer.wrap(data, (int) at, count * elemSize).slice().order(ORDER);
        if (array instanceof short[] x) {
            if (toStorage) bb.asShortBuffer().put(x, from, count); else bb.asShortBuffer().get(x, from, count);
        } else if (array instanceof char[] x) {
            if (toStorage) bb.asCharBuffer().put(x, from, count); else bb.asCharBuffer().get(x, from, count);
        } else if (array instanceof int[] x) {
            if (toStorage) bb.asIntBuffer().put(x, from, count); else bb.asIntBuffer().get(x, from, count);
        } else if (array instanceof long[] x) {
            if (toStorage) bb.asLongBuffer().put(x, from, count); else bb.asLongBuffer().get(x, from, count);
        } else if (array instanceof float[] x) {
            if (toStorage) bb.asFloatBuffer().put(x, from, count); else bb.asFloatBuffer().get(x, from, count);
        } else if (array instanceof double[] x) {
            if (toStorage) bb.asDoubleBuffer().put(x, from, count); else bb.asDoubleBuffer().get(x, from, count);
        } else {
            throw new IllegalArgumentException("unsupported array " + array.getClass());
        }
    }

    /** Арена кучи: закрытие делает её память недоступной, а саму память освобождает GC. */
    private static final class Arena implements HostArena {
        private final Kind kind;
        private final Scope scope;

        Arena(Kind kind) {
            this.kind = kind;
            this.scope = new Scope(kind != Kind.CONFINED);
        }

        @Override
        public HostMemory allocate(long bytes, long alignment) {
            return new HeapMemory(new byte[checkedSize(bytes)], 0, bytes, false, scope);
        }

        @Override
        public HostMemory attach(HostMemory memory) {
            return AttachedMemory.attach(memory, scope);
        }

        @Override
        public void close() {
            if (kind != Kind.AUTO) {
                scope.close();
            }
        }
    }
}
