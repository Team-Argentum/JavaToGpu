package net.sixik.ga_utils.javatogpu.api;

import net.sixik.ga_utils.javatogpu.api.annotations.GPUPointerType;

/**
 * Mutable short-by-reference wrapper for GPU helper calls.
 */
@GPUPointerType(valueType = "short")
public final class ShortPtr {

    /**
     * Wrapped scalar value.
     */
    public short value;

    public ShortPtr() {
    }

    /**
     * Creates a wrapper with an initial value.
     */
    public ShortPtr(short value) {
        this.value = value;
    }
}
