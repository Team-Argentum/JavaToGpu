package net.sixik.ga_utils.javatogpu.api;

import net.sixik.ga_utils.javatogpu.api.annotations.GPUIntrinsic;
import net.sixik.ga_utils.javatogpu.api.annotations.GPUVectorType;

/**
 * Java-side representation of the OpenCL {@code long2} vector type.
 */
@GPUVectorType(openClType = "long2", componentType = "long", fields = {"x", "y"})
public class Long2 {

    /**
     * First vector component.
     */
    public long x;
    /**
     * Second vector component.
     */
    public long y;

    public Long2() {
    }

    /**
     * Broadcast constructor. Fills all components with the same value.
     */
    public Long2(long value) {
        this.x = value;
        this.y = value;
    }

    /**
     * Creates a vector from explicit components.
     */
    public Long2(long x, long y) {
        this.x = x;
        this.y = y;
    }

    @GPUIntrinsic(operator = "+")
    public Long2 add(Long2 other) { return new Long2(x + other.x, y + other.y); }

    @GPUIntrinsic(operator = "-")
    public Long2 sub(Long2 other) { return new Long2(x - other.x, y - other.y); }

    @GPUIntrinsic(operator = "*")
    public Long2 mul(Long2 other) { return new Long2(x * other.x, y * other.y); }

    @GPUIntrinsic(operator = "/")
    public Long2 div(Long2 other) { return new Long2(x / other.x, y / other.y); }
}
