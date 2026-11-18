package com.sixik.gpu.runtime.device;

/**
 * Память хоста, которую слой доступа ({@link DeviceApi}) передаёт драйверу: промежуточные буферы передач, закреплённая
 * память, упакованные аргументы по значению.
 *
 * <p>Ядро рантайма собирается под Java 17 и не знает {@code java.lang.foreign}, поэтому нативная память описана этим
 * интерфейсом, а реализует её бэкенд: {@code runtime-opencl-ffm} - через {@code MemorySegment}, {@code
 * runtime-opencl-lwjgl} - через {@code MemoryUtil} LWJGL, фейковый слой тестов - кучей ({@link HeapMemory}).</p>
 *
 * <p>Правила:</p>
 * <ul>
 *   <li>смещения - в байтах от начала памяти; порядок байт - родной; доступ к многобайтным значениям не обязан быть
 *       выровненным;</li>
 *   <li>выход за границы - {@link IndexOutOfBoundsException}; запись в память только для чтения -
 *       {@link IllegalArgumentException} (как у {@code MemorySegment}); обращение после закрытия владельца ({@link HostArena}) -
 *       {@link IllegalStateException}, а не чтение освобождённой памяти;</li>
 *   <li>копирование с Java-массивами задаётся в элементах массива ({@code from}, {@code count}) и в байтах памяти
 *       ({@code offset}).</li>
 * </ul>
 */
public interface HostMemory {

    /** @return размер в байтах */
    long byteSize();

    /** @return нативный адрес начала либо {@code 0}, если память лежит в куче JVM */
    long address();

    /** @return {@code true}, если память только для чтения */
    boolean isReadOnly();

    /**
     * @return {@code true}, если к памяти можно обращаться из любого потока (иначе - только из потока-владельца, и
     * копирование нельзя делить между потоками)
     */
    boolean isShared();

    /**
     * @param offset начало части
     * @param size   размер части
     * @return вид на часть этой памяти (тот же владелец, та же доступность)
     */
    HostMemory asSlice(long offset, long size);

    /** @return вид только для чтения на ту же память */
    HostMemory asReadOnly();

    // ---- значения ------------------------------------------------------------------------------------

    byte getByte(long offset);

    void setByte(long offset, byte value);

    short getShort(long offset);

    void setShort(long offset, short value);

    char getChar(long offset);

    void setChar(long offset, char value);

    int getInt(long offset);

    void setInt(long offset, int value);

    long getLong(long offset);

    void setLong(long offset, long value);

    float getFloat(long offset);

    void setFloat(long offset, float value);

    double getDouble(long offset);

    void setDouble(long offset, double value);

    // ---- копирование с Java-массивами ---------------------------------------------------------------

    void copyFrom(byte[] src, int from, long offset, int count);

    void copyFrom(short[] src, int from, long offset, int count);

    void copyFrom(char[] src, int from, long offset, int count);

    void copyFrom(int[] src, int from, long offset, int count);

    void copyFrom(long[] src, int from, long offset, int count);

    void copyFrom(float[] src, int from, long offset, int count);

    void copyFrom(double[] src, int from, long offset, int count);

    void copyTo(long offset, byte[] dst, int from, int count);

    void copyTo(long offset, short[] dst, int from, int count);

    void copyTo(long offset, char[] dst, int from, int count);

    void copyTo(long offset, int[] dst, int from, int count);

    void copyTo(long offset, long[] dst, int from, int count);

    void copyTo(long offset, float[] dst, int from, int count);

    void copyTo(long offset, double[] dst, int from, int count);

    /**
     * Копирует {@code bytes} байт между двумя памятями (любых реализаций). Реализация может ускорить случай своей же
     * памяти; по умолчанию - через промежуточный массив.
     *
     * @param src       источник
     * @param srcOffset смещение в источнике
     * @param dst       приёмник
     * @param dstOffset смещение в приёмнике
     * @param bytes     число байт
     */
    static void copy(HostMemory src, long srcOffset, HostMemory dst, long dstOffset, long bytes) {
        byte[] chunk = new byte[(int) Math.min(bytes, 1 << 16)];
        long done = 0;
        while (done < bytes) {
            int n = (int) Math.min(chunk.length, bytes - done);
            src.copyTo(srcOffset + done, chunk, 0, n);
            dst.copyFrom(chunk, 0, dstOffset + done, n);
            done += n;
        }
    }
}
