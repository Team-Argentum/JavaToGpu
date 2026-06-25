package net.sixik.ga_utils.javatogpu.api;

import net.sixik.ga_utils.javatogpu.api.annotations.GPUPointerType;

/**
 * Mutable float-by-reference wrapper for GPU helper calls.
 *
 * <pre>{@code
 * FloatPtr ptr = new FloatPtr();
 * Helpers.fill(ptr);
 * float value = ptr.value;
 * }</pre>
 */
@GPUPointerType(valueType = "float")
public final class FloatPtr {

    /**
     * Wrapped scalar value.
     */
    public float value;

    public FloatPtr() {
    }

    /**
     * Creates a wrapper with an initial value.
     */
    public FloatPtr(float value) {
        this.value = value;
    }
}
