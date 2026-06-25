package net.sixik.ga_utils.javatogpu.api;

import net.sixik.ga_utils.javatogpu.api.annotations.GPUScalarAliasType;

/**
 * Java-side wrapper for the OpenCL {@code uint} scalar type.
 *
 * <p>Java has no unsigned int, so {@link #value} stores the raw 32-bit pattern in a regular {@code int}.
 */
@GPUScalarAliasType(backendType = "uint", valueType = "int")
public final class UInt {

    /**
     * Wrapped raw 32-bit value.
     */
    public int value;

    public UInt() {
    }

    public UInt(int value) {
        this.value = value;
    }
}
