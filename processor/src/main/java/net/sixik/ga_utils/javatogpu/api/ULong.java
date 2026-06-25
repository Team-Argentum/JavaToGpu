package net.sixik.ga_utils.javatogpu.api;

import net.sixik.ga_utils.javatogpu.api.annotations.GPUScalarAliasType;

/**
 * Java-side wrapper for the OpenCL {@code ulong} scalar type.
 *
 * <p>Java has no unsigned long, so {@link #value} stores the raw 64-bit pattern in a regular {@code long}.
 */
@GPUScalarAliasType(backendType = "ulong", valueType = "long")
public final class ULong {

    /**
     * Wrapped raw 64-bit value.
     */
    public long value;

    public ULong() {
    }

    public ULong(long value) {
        this.value = value;
    }
}
