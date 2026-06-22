package net.sixik.ga_utils.javatogpu.api;

/**
 * Java-side wrapper for the OpenCL {@code uint} scalar type.
 *
 * <p>Java has no unsigned int, so {@link #value} stores the raw 32-bit pattern in a regular {@code int}.
 */
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
