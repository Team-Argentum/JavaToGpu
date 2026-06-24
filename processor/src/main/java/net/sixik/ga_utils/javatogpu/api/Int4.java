package net.sixik.ga_utils.javatogpu.api;

import net.sixik.ga_utils.javatogpu.api.anotations.GPUIntrinsic;
import net.sixik.ga_utils.javatogpu.api.anotations.GPUVectorType;

/**
 * Java-side representation of the OpenCL {@code int4} vector type.
 */
@GPUVectorType(openClType = "int4", componentType = "int", fields = {"x", "y", "z", "w"})
public class Int4 {

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

    public Int4() {
    }

    /**
     * Broadcast constructor. Fills all components with the same value.
     */
    public Int4(int value) {
        this.x = value;
        this.y = value;
        this.z = value;
        this.w = value;
    }

    /**
     * Creates a vector from explicit components.
     */
    public Int4(int x, int y, int z, int w) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.w = w;
    }

    @GPUIntrinsic(operator = "+")
    public Int4 add(Int4 other) {
        return new Int4(x + other.x, y + other.y, z + other.z, w + other.w);
    }

    @GPUIntrinsic(operator = "-")
    public Int4 sub(Int4 other) {
        return new Int4(x - other.x, y - other.y, z - other.z, w - other.w);
    }

    @GPUIntrinsic(operator = "*")
    public Int4 mul(Int4 other) {
        return new Int4(x * other.x, y * other.y, z * other.z, w * other.w);
    }

    @GPUIntrinsic(operator = "/")
    public Int4 div(Int4 other) {
        return new Int4(x / other.x, y / other.y, z / other.z, w / other.w);
    }
}
