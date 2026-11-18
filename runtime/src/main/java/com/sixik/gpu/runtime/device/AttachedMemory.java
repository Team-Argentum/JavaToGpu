package com.sixik.gpu.runtime.device;

/**
 * Вид на чужую {@link HostMemory}, действительный, пока жив срок жизни арены ({@link HostArena#attach}). Нужен для
 * памяти, реализация которой не основана на {@link AbstractHostMemory}; свою память такие арены привязывают без
 * обёртки ({@link AbstractHostMemory#withScope}).
 */
public final class AttachedMemory implements HostMemory {

    private final HostMemory m;
    private final AbstractHostMemory.Scope scope;

    private AttachedMemory(HostMemory m, AbstractHostMemory.Scope scope) {
        this.m = m;
        this.scope = scope;
    }

    /**
     * @param memory чужая память
     * @param scope  срок жизни вида
     * @return вид, недоступный после закрытия {@code scope}
     */
    public static HostMemory attach(HostMemory memory, AbstractHostMemory.Scope scope) {
        if (memory instanceof AbstractHostMemory a) {
            return a.withScope(scope);
        }
        if (memory instanceof AttachedMemory w) {
            return new AttachedMemory(w.m, scope);
        }
        return new AttachedMemory(memory, scope);
    }

    private HostMemory m() {
        if (!scope.isAlive()) {
            throw new IllegalStateException("host memory is already released (its owner is closed)");
        }
        return m;
    }

    @Override public long byteSize() { return m.byteSize(); }
    @Override public long address() { return m.address(); }
    @Override public boolean isReadOnly() { return m.isReadOnly(); }
    @Override public boolean isShared() { return scope.isShared() && m.isShared(); }
    @Override public HostMemory asSlice(long offset, long size) { return new AttachedMemory(m().asSlice(offset, size), scope); }
    @Override public HostMemory asReadOnly() { return new AttachedMemory(m().asReadOnly(), scope); }
    @Override public byte getByte(long o) { return m().getByte(o); }
    @Override public void setByte(long o, byte v) { m().setByte(o, v); }
    @Override public short getShort(long o) { return m().getShort(o); }
    @Override public void setShort(long o, short v) { m().setShort(o, v); }
    @Override public char getChar(long o) { return m().getChar(o); }
    @Override public void setChar(long o, char v) { m().setChar(o, v); }
    @Override public int getInt(long o) { return m().getInt(o); }
    @Override public void setInt(long o, int v) { m().setInt(o, v); }
    @Override public long getLong(long o) { return m().getLong(o); }
    @Override public void setLong(long o, long v) { m().setLong(o, v); }
    @Override public float getFloat(long o) { return m().getFloat(o); }
    @Override public void setFloat(long o, float v) { m().setFloat(o, v); }
    @Override public double getDouble(long o) { return m().getDouble(o); }
    @Override public void setDouble(long o, double v) { m().setDouble(o, v); }
    @Override public void copyFrom(byte[] s, int f, long o, int c) { m().copyFrom(s, f, o, c); }
    @Override public void copyFrom(short[] s, int f, long o, int c) { m().copyFrom(s, f, o, c); }
    @Override public void copyFrom(char[] s, int f, long o, int c) { m().copyFrom(s, f, o, c); }
    @Override public void copyFrom(int[] s, int f, long o, int c) { m().copyFrom(s, f, o, c); }
    @Override public void copyFrom(long[] s, int f, long o, int c) { m().copyFrom(s, f, o, c); }
    @Override public void copyFrom(float[] s, int f, long o, int c) { m().copyFrom(s, f, o, c); }
    @Override public void copyFrom(double[] s, int f, long o, int c) { m().copyFrom(s, f, o, c); }
    @Override public void copyTo(long o, byte[] d, int f, int c) { m().copyTo(o, d, f, c); }
    @Override public void copyTo(long o, short[] d, int f, int c) { m().copyTo(o, d, f, c); }
    @Override public void copyTo(long o, char[] d, int f, int c) { m().copyTo(o, d, f, c); }
    @Override public void copyTo(long o, int[] d, int f, int c) { m().copyTo(o, d, f, c); }
    @Override public void copyTo(long o, long[] d, int f, int c) { m().copyTo(o, d, f, c); }
    @Override public void copyTo(long o, float[] d, int f, int c) { m().copyTo(o, d, f, c); }
    @Override public void copyTo(long o, double[] d, int f, int c) { m().copyTo(o, d, f, c); }

    /** @return обёрнутая память (для бэкенда: передать драйверу её адрес) */
    public HostMemory unwrap() {
        return m();
    }
}
