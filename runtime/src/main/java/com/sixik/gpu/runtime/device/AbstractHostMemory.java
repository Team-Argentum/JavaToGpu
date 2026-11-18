package com.sixik.gpu.runtime.device;

import java.util.Objects;

/**
 * Основа реализаций {@link HostMemory} без FFM: границы, «только чтение» и проверка жизни владельца сделаны здесь, а
 * подкласс даёт только сырой доступ к хранилищу по абсолютному смещению ({@link #base} + смещение вида). Так устроены
 * память кучи ({@link HeapMemory}) и нативная память бэкенда LWJGL.
 */
public abstract class AbstractHostMemory implements HostMemory {

    /** Срок жизни памяти: общий у арены и всех видов на её память. */
    public static final class Scope {
        private volatile boolean alive = true;
        private final boolean shared;

        /** @param shared доступна ли память любому потоку */
        public Scope(boolean shared) {
            this.shared = shared;
        }

        /** @return {@code true}, пока владелец не закрыт */
        public boolean isAlive() {
            return alive;
        }

        /** Помечает память недоступной; дальнейшие обращения бросают {@link IllegalStateException}. */
        public void close() {
            alive = false;
        }

        /** @return доступна ли память любому потоку */
        public boolean isShared() {
            return shared;
        }
    }

    /** Смещение начала вида в хранилище. */
    protected final long base;
    private final long size;
    private final boolean readOnly;
    /** Срок жизни. */
    protected final Scope scope;

    /**
     * @param base     смещение начала вида в хранилище
     * @param size     размер вида
     * @param readOnly только для чтения
     * @param scope    срок жизни
     */
    protected AbstractHostMemory(long base, long size, boolean readOnly, Scope scope) {
        this.base = base;
        this.size = size;
        this.readOnly = readOnly;
        this.scope = Objects.requireNonNull(scope);
    }

    /**
     * Новый вид на то же хранилище.
     *
     * @param base     абсолютное смещение
     * @param size     размер
     * @param readOnly только для чтения
     * @param scope    срок жизни
     * @return вид
     */
    protected abstract AbstractHostMemory view(long base, long size, boolean readOnly, Scope scope);

    /** @return адрес хранилища ({@code 0} для кучи); адрес вида - это значение плюс {@link #base} */
    protected abstract long storageAddress();

    // сырой доступ по абсолютному смещению в хранилище
    protected abstract byte rawGetByte(long at);

    protected abstract void rawSetByte(long at, byte v);

    protected abstract short rawGetShort(long at);

    protected abstract void rawSetShort(long at, short v);

    protected abstract int rawGetInt(long at);

    protected abstract void rawSetInt(long at, int v);

    protected abstract long rawGetLong(long at);

    protected abstract void rawSetLong(long at, long v);

    /**
     * Копирует {@code count} элементов массива примитивов в хранилище ({@code toStorage}) либо обратно.
     *
     * @param array     массив ({@code byte[] … double[]})
     * @param from      первый элемент
     * @param at        абсолютное смещение в хранилище
     * @param count     число элементов
     * @param elemSize  размер элемента в байтах
     * @param toStorage направление
     */
    protected abstract void rawCopy(Object array, int from, long at, int count, int elemSize, boolean toStorage);

    /**
     * Привязывает этот вид к другому сроку жизни (для {@link HostArena#attach}).
     *
     * @param other срок жизни
     * @return вид на ту же память
     */
    public AbstractHostMemory withScope(Scope other) {
        return view(base, size, readOnly, other);
    }

    // ---- проверки --------------------------------------------------------------------------------

    private long at(long offset, long bytes) {
        if (!scope.isAlive()) {
            throw new IllegalStateException("host memory is already released (its owner is closed)");
        }
        if (offset < 0 || bytes < 0 || offset > size - bytes) {
            throw new IndexOutOfBoundsException("offset " + offset + " + " + bytes + " bytes outside " + size);
        }
        return base + offset;
    }

    private long wat(long offset, long bytes) {
        if (readOnly) {
            throw new IllegalArgumentException("read-only host memory");
        }
        return at(offset, bytes);
    }

    private static void range(int length, int from, int count) {
        Objects.checkFromIndexSize(from, count, length);
    }

    // ---- HostMemory ------------------------------------------------------------------------------

    @Override
    public long byteSize() {
        return size;
    }

    @Override
    public long address() {
        long a = storageAddress();
        return a == 0 ? 0 : a + base;
    }

    @Override
    public boolean isReadOnly() {
        return readOnly;
    }

    @Override
    public boolean isShared() {
        return scope.isShared();
    }

    @Override
    public HostMemory asSlice(long offset, long size) {
        long b = at(offset, size);
        return view(b, size, readOnly, scope);
    }

    @Override
    public HostMemory asReadOnly() {
        return readOnly ? this : view(base, size, true, scope);
    }

    @Override
    public byte getByte(long offset) {
        return rawGetByte(at(offset, 1));
    }

    @Override
    public void setByte(long offset, byte value) {
        rawSetByte(wat(offset, 1), value);
    }

    @Override
    public short getShort(long offset) {
        return rawGetShort(at(offset, 2));
    }

    @Override
    public void setShort(long offset, short value) {
        rawSetShort(wat(offset, 2), value);
    }

    @Override
    public char getChar(long offset) {
        return (char) rawGetShort(at(offset, 2));
    }

    @Override
    public void setChar(long offset, char value) {
        rawSetShort(wat(offset, 2), (short) value);
    }

    @Override
    public int getInt(long offset) {
        return rawGetInt(at(offset, 4));
    }

    @Override
    public void setInt(long offset, int value) {
        rawSetInt(wat(offset, 4), value);
    }

    @Override
    public long getLong(long offset) {
        return rawGetLong(at(offset, 8));
    }

    @Override
    public void setLong(long offset, long value) {
        rawSetLong(wat(offset, 8), value);
    }

    @Override
    public float getFloat(long offset) {
        return Float.intBitsToFloat(rawGetInt(at(offset, 4)));
    }

    @Override
    public void setFloat(long offset, float value) {
        rawSetInt(wat(offset, 4), Float.floatToRawIntBits(value));
    }

    @Override
    public double getDouble(long offset) {
        return Double.longBitsToDouble(rawGetLong(at(offset, 8)));
    }

    @Override
    public void setDouble(long offset, double value) {
        rawSetLong(wat(offset, 8), Double.doubleToRawLongBits(value));
    }

    private void in(Object a, int length, int from, long offset, int count, int es) {
        range(length, from, count);
        rawCopy(a, from, wat(offset, (long) count * es), count, es, true);
    }

    private void out(Object a, int length, long offset, int from, int count, int es) {
        range(length, from, count);
        rawCopy(a, from, at(offset, (long) count * es), count, es, false);
    }

    @Override
    public void copyFrom(byte[] src, int from, long offset, int count) {
        in(src, src.length, from, offset, count, 1);
    }

    @Override
    public void copyFrom(short[] src, int from, long offset, int count) {
        in(src, src.length, from, offset, count, 2);
    }

    @Override
    public void copyFrom(char[] src, int from, long offset, int count) {
        in(src, src.length, from, offset, count, 2);
    }

    @Override
    public void copyFrom(int[] src, int from, long offset, int count) {
        in(src, src.length, from, offset, count, 4);
    }

    @Override
    public void copyFrom(long[] src, int from, long offset, int count) {
        in(src, src.length, from, offset, count, 8);
    }

    @Override
    public void copyFrom(float[] src, int from, long offset, int count) {
        in(src, src.length, from, offset, count, 4);
    }

    @Override
    public void copyFrom(double[] src, int from, long offset, int count) {
        in(src, src.length, from, offset, count, 8);
    }

    @Override
    public void copyTo(long offset, byte[] dst, int from, int count) {
        out(dst, dst.length, offset, from, count, 1);
    }

    @Override
    public void copyTo(long offset, short[] dst, int from, int count) {
        out(dst, dst.length, offset, from, count, 2);
    }

    @Override
    public void copyTo(long offset, char[] dst, int from, int count) {
        out(dst, dst.length, offset, from, count, 2);
    }

    @Override
    public void copyTo(long offset, int[] dst, int from, int count) {
        out(dst, dst.length, offset, from, count, 4);
    }

    @Override
    public void copyTo(long offset, long[] dst, int from, int count) {
        out(dst, dst.length, offset, from, count, 8);
    }

    @Override
    public void copyTo(long offset, float[] dst, int from, int count) {
        out(dst, dst.length, offset, from, count, 4);
    }

    @Override
    public void copyTo(long offset, double[] dst, int from, int count) {
        out(dst, dst.length, offset, from, count, 8);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + size + " bytes" + (readOnly ? ", read-only" : "")
                + (scope.isAlive() ? "" : ", released") + "]";
    }
}
