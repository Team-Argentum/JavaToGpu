package net.sixik.ga_utils.javatogpu.api;

import net.sixik.ga_utils.javatogpu.api.annotations.GPUPointerType;

/**
 * Mutable byte-by-reference wrapper for GPU helper calls.
 *
 * <p>Use pointer wrappers when a {@code @CCode} helper needs to mutate a scalar value "by reference".
 *
 * <pre>{@code
 * BytePtr ptr = new BytePtr((byte) 4);
 * Helpers.bump(ptr);
 * }</pre>
 */
@GPUPointerType(valueType = "byte")
public final class BytePtr {

    /**
     * Wrapped scalar value.
     */
    public byte value;

    public BytePtr() {
    }

    /**
     * Creates a wrapper with an initial value.
     */
    public BytePtr(byte value) {
        this.value = value;
    }
}
