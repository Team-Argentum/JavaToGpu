package net.sixik.ga_utils.javatogpu.api;

import net.sixik.ga_utils.javatogpu.api.annotations.GPUIntrinsic;
import net.sixik.ga_utils.javatogpu.api.annotations.GPUVectorType;

/**
 * Java-side representation of the OpenCL {@code int3} vector type.
 */
@GPUVectorType(openClType = "int3", componentType = "int", fields = {"x", "y", "z"})
public class Int3 {

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

    public Int3() {
    }

    /**
     * Broadcast constructor. Fills all components with the same value.
     */
    public Int3(int value) {
        this.x = value;
        this.y = value;
        this.z = value;
    }

    /**
     * Creates a vector from explicit components.
     */
    public Int3(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @GPUIntrinsic(operator = "+")
    public Int3 add(Int3 other) {
        return new Int3(x + other.x, y + other.y, z + other.z);
    }

    @GPUIntrinsic(operator = "-")
    public Int3 sub(Int3 other) {
        return new Int3(x - other.x, y - other.y, z - other.z);
    }

    @GPUIntrinsic(operator = "*")
    public Int3 mul(Int3 other) {
        return new Int3(x * other.x, y * other.y, z * other.z);
    }

    @GPUIntrinsic(operator = "/")
    public Int3 div(Int3 other) {
        return new Int3(x / other.x, y / other.y, z / other.z);
    }
}
