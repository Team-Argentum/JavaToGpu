package net.sixik.ga_utils.javatogpu.api;

import net.sixik.ga_utils.javatogpu.api.annotations.GPUPointerType;

/**
 * Mutable int-by-reference wrapper for GPU helper calls.
 */
@GPUPointerType(valueType = "int")
public final class IntPtr {

    /**
     * Wrapped scalar value.
     */
    public int value;

    public IntPtr() {
    }

    /**
     * Creates a wrapper with an initial value.
     */
    public IntPtr(int value) {
        this.value = value;
    }
}
