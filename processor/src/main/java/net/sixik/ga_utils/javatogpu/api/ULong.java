package net.sixik.ga_utils.javatogpu.api;

/**
 * Java-side wrapper for the OpenCL {@code ulong} scalar type.
 *
 * <p>Java has no unsigned long, so {@link #value} stores the raw 64-bit pattern in a regular {@code long}.
 */
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
