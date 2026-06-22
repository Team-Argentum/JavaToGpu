package net.sixik.ga_utils.javatogpu.api;

/**
 * Java-side wrapper for the OpenCL {@code ushort} scalar type.
 *
 * <p>Java has no unsigned short, so {@link #value} stores the raw 16-bit pattern in a regular {@code short}.
 */
public final class UShort {

    /**
     * Wrapped raw 16-bit value.
     */
    public short value;

    public UShort() {
    }

    public UShort(short value) {
        this.value = value;
    }
}
