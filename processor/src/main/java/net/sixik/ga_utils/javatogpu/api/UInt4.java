package net.sixik.ga_utils.javatogpu.api;

/**
 * Java-side representation of the OpenCL {@code uint4} vector type.
 *
 * <p>Java has no unsigned {@code int}, so every component is stored as a regular {@code int} carrying the raw
 * 32-bit value.
 */
public class UInt4 {

    /**
     * First vector component.
     */
    public int x;
    /**
     * Second vector component.
     */
    public int y;
    /**
     * Third vector component.
     */
    public int z;
    /**
     * Fourth vector component.
     */
    public int w;

    public UInt4() {
    }

    /**
     * Broadcast constructor. Fills all components with the same value.
     */
    public UInt4(int value) {
        this.x = value;
        this.y = value;
        this.z = value;
        this.w = value;
    }

    /**
     * Creates a vector from explicit components.
     */
    public UInt4(int x, int y, int z, int w) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.w = w;
    }
}
