package net.sixik.ga_utils.javatogpu.api;

import net.sixik.ga_utils.javatogpu.api.annotations.GPUPointerType;

/**
 * Mutable double-by-reference wrapper for GPU helper calls.
 *
 * <pre>{@code
 * DoublePtr ptr = new DoublePtr(1.5);
 * Helpers.scale(ptr, 2.0);
 * }</pre>
 */
@GPUPointerType(valueType = "double")
public final class DoublePtr {

    /**
     * Wrapped scalar value.
     */
    public double value;

    public DoublePtr() {
    }

    /**
     * Creates a wrapper with an initial value.
     */
    public DoublePtr(double value) {
        this.value = value;
    }
}
