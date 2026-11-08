package com.sixik.gpu.runtime;

/**
 * Буфер в памяти устройства с явным владением и временем жизни (REWORK_ARCHITECTURE.md, 9: «первоклассный объект -
 * {@code GpuBuffer}»). Данные остаются на устройстве между запусками, поэтому цепочка ядер не гоняет их через хост.
 *
 * <p>Создаётся {@link GpuContext#allocate} или {@link GpuContext#upload}; закрывается {@link #close()} (либо вместе с
 * контекстом). Объект <b>не потокобезопасен</b>: один буфер нельзя использовать конкурентно из разных потоков.
 * Массивы хоста в вызовах ядра - сахар над временными буферами; явный буфер нужен, когда данные переживают вызов.</p>
 */
public final class GpuBuffer implements AutoCloseable {

    private final GpuContext owner;
    private final long handle;
    private final int elementType;
    private final long length;
    private boolean closed;

    GpuBuffer(GpuContext owner, long handle, int elementType, long length) {
        this.owner = owner;
        this.handle = handle;
        this.elementType = elementType;
        this.length = length;
    }

    /** @return дескриптор OpenCL (для рантайма) */
    long handle() {
        return handle;
    }

    /** @return контекст-владелец */
    GpuContext owner() {
        return owner;
    }

    /** @return тип элемента IR, с которым создан буфер */
    public int elementType() {
        return elementType;
    }

    /** @return число элементов */
    public long length() {
        return length;
    }

    /** @return размер в байтах */
    public long byteSize() {
        return length * Marshal.elementSize(elementType);
    }

    /** @return {@code true}, если буфер освобождён */
    public boolean isClosed() {
        return closed;
    }

    /**
     * Копирует массив хоста в начало буфера.
     *
     * @param hostArray Java-массив примитивов того же вида, что элемент буфера, не длиннее буфера
     * @return этот буфер
     * @throws GpuException если буфер закрыт, вид или длина массива не подходят, либо драйвер вернул ошибку
     */
    public GpuBuffer write(Object hostArray) {
        owner.write(this, hostArray);
        return this;
    }

    /**
     * Копирует начало буфера в массив хоста (столько элементов, какова длина массива).
     *
     * @param hostArray Java-массив примитивов того же вида, что элемент буфера, не длиннее буфера
     * @return этот буфер
     * @throws GpuException если буфер закрыт, вид или длина массива не подходят, либо драйвер вернул ошибку
     */
    public GpuBuffer read(Object hostArray) {
        owner.read(this, hostArray);
        return this;
    }

    /** Освобождает память устройства; повторный вызов безопасен. */
    @Override
    public void close() {
        owner.free(this);
    }

    void markClosed() {
        closed = true;
    }

    @Override
    public String toString() {
        return "GpuBuffer[" + length + " x t" + elementType + (closed ? ", closed" : "") + "]";
    }
}
