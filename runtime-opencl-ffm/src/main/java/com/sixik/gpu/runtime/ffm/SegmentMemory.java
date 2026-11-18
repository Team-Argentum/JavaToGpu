package com.sixik.gpu.runtime.ffm;

import com.sixik.gpu.runtime.device.HostMemory;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

/**
 * {@link HostMemory} поверх {@code MemorySegment}: все проверки (границы, «только чтение», жизнь арены, поток-владелец)
 * делает сам FFM, поэтому обёртка ничего не добавляет к цене обращения.
 */
final class SegmentMemory implements HostMemory {

    private static final ValueLayout.OfShort SHORT = ValueLayout.JAVA_SHORT_UNALIGNED;
    private static final ValueLayout.OfChar CHAR = ValueLayout.JAVA_CHAR_UNALIGNED;
    private static final ValueLayout.OfInt INT = ValueLayout.JAVA_INT_UNALIGNED;
    private static final ValueLayout.OfLong LONG = ValueLayout.JAVA_LONG_UNALIGNED;
    private static final ValueLayout.OfFloat FLOAT = ValueLayout.JAVA_FLOAT_UNALIGNED;
    private static final ValueLayout.OfDouble DOUBLE = ValueLayout.JAVA_DOUBLE_UNALIGNED;

    /** Поток-«зонд»: никогда не запускается, нужен лишь, чтобы узнать, доступна ли память чужим потокам. */
    private static final Thread PROBE = new Thread(() -> {
    });

    final MemorySegment seg;

    SegmentMemory(MemorySegment seg) {
        this.seg = seg;
    }

    @Override public long byteSize() { return seg.byteSize(); }
    @Override public long address() { return seg.address(); }
    @Override public boolean isReadOnly() { return seg.isReadOnly(); }
    @Override public boolean isShared() { return seg.isAccessibleBy(PROBE); }
    @Override public HostMemory asSlice(long offset, long size) { return new SegmentMemory(seg.asSlice(offset, size)); }
    @Override public HostMemory asReadOnly() { return seg.isReadOnly() ? this : new SegmentMemory(seg.asReadOnly()); }

    @Override public byte getByte(long o) { return seg.get(ValueLayout.JAVA_BYTE, o); }
    @Override public void setByte(long o, byte v) { seg.set(ValueLayout.JAVA_BYTE, o, v); }
    @Override public short getShort(long o) { return seg.get(SHORT, o); }
    @Override public void setShort(long o, short v) { seg.set(SHORT, o, v); }
    @Override public char getChar(long o) { return seg.get(CHAR, o); }
    @Override public void setChar(long o, char v) { seg.set(CHAR, o, v); }
    @Override public int getInt(long o) { return seg.get(INT, o); }
    @Override public void setInt(long o, int v) { seg.set(INT, o, v); }
    @Override public long getLong(long o) { return seg.get(LONG, o); }
    @Override public void setLong(long o, long v) { seg.set(LONG, o, v); }
    @Override public float getFloat(long o) { return seg.get(FLOAT, o); }
    @Override public void setFloat(long o, float v) { seg.set(FLOAT, o, v); }
    @Override public double getDouble(long o) { return seg.get(DOUBLE, o); }
    @Override public void setDouble(long o, double v) { seg.set(DOUBLE, o, v); }

    @Override public void copyFrom(byte[] s, int f, long o, int c) { MemorySegment.copy(s, f, seg, ValueLayout.JAVA_BYTE, o, c); }
    @Override public void copyFrom(short[] s, int f, long o, int c) { MemorySegment.copy(s, f, seg, SHORT, o, c); }
    @Override public void copyFrom(char[] s, int f, long o, int c) { MemorySegment.copy(s, f, seg, CHAR, o, c); }
    @Override public void copyFrom(int[] s, int f, long o, int c) { MemorySegment.copy(s, f, seg, INT, o, c); }
    @Override public void copyFrom(long[] s, int f, long o, int c) { MemorySegment.copy(s, f, seg, LONG, o, c); }
    @Override public void copyFrom(float[] s, int f, long o, int c) { MemorySegment.copy(s, f, seg, FLOAT, o, c); }
    @Override public void copyFrom(double[] s, int f, long o, int c) { MemorySegment.copy(s, f, seg, DOUBLE, o, c); }
    @Override public void copyTo(long o, byte[] d, int f, int c) { MemorySegment.copy(seg, ValueLayout.JAVA_BYTE, o, d, f, c); }
    @Override public void copyTo(long o, short[] d, int f, int c) { MemorySegment.copy(seg, SHORT, o, d, f, c); }
    @Override public void copyTo(long o, char[] d, int f, int c) { MemorySegment.copy(seg, CHAR, o, d, f, c); }
    @Override public void copyTo(long o, int[] d, int f, int c) { MemorySegment.copy(seg, INT, o, d, f, c); }
    @Override public void copyTo(long o, long[] d, int f, int c) { MemorySegment.copy(seg, LONG, o, d, f, c); }
    @Override public void copyTo(long o, float[] d, int f, int c) { MemorySegment.copy(seg, FLOAT, o, d, f, c); }
    @Override public void copyTo(long o, double[] d, int f, int c) { MemorySegment.copy(seg, DOUBLE, o, d, f, c); }

    @Override
    public String toString() {
        return "SegmentMemory[" + seg + "]";
    }
}
