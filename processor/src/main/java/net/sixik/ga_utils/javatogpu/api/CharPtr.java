package net.sixik.ga_utils.javatogpu.api;

import net.sixik.ga_utils.javatogpu.api.annotations.GPUPointerType;

/**
 * Mutable char-by-reference wrapper for GPU helper calls.
 */
@GPUPointerType(valueType = "char")
public final class CharPtr {

    /**
     * Wrapped scalar value.
     */
    public char value;

    public CharPtr() {
    }

    /**
     * Creates a wrapper with an initial value.
     */
    public CharPtr(char value) {
        this.value = value;
    }
}
