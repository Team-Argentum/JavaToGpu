package net.sixik.ga_utils.javatogpu.api;

import net.sixik.ga_utils.javatogpu.api.annotations.GPUScalarAliasType;

/**
 * Java-side wrapper for the OpenCL {@code uchar} scalar type.
 *
 * <p>Java has no unsigned byte, so {@link #value} stores the raw 8-bit pattern in a regular {@code byte}.
 */
@GPUScalarAliasType(backendType = "uchar", valueType = "byte")
public final class UByte {

    /**
     * Wrapped raw 8-bit value.
     */
    public byte value;

    public UByte() {
    }

    public UByte(byte value) {
        this.value = value;
    }
}
