package net.sixik.ga_utils.javatogpu.api;

import net.sixik.ga_utils.javatogpu.api.anotations.GPUIntrinsic;
import net.sixik.ga_utils.javatogpu.api.anotations.GPUVectorType;

/**
 * Java-side representation of the OpenCL {@code int2} vector type.
 */
@GPUVectorType(openClType = "int2", componentType = "int", fields = {"x", "y"})
public class Int2 {

    /**
     * First vector component.
     */
    public int x;
    /**
     * Second vector component.
     */
    public int y;

    public Int2() {
    }

    /**
     * Broadcast constructor. Fills all components with the same value.
     */
    public Int2(int value) {
        this.x = value;
        this.y = value;
    }

    /**
     * Creates a vector from explicit components.
     */
    public Int2(int x, int y) {
        this.x = x;
        this.y = y;
    }

    @GPUIntrinsic(operator = "+")
    public Int2 add(Int2 other) {
        return new Int2(x + other.x, y + other.y);
    }

    @GPUIntrinsic(operator = "-")
    public Int2 sub(Int2 other) {
        return new Int2(x - other.x, y - other.y);
    }

    @GPUIntrinsic(operator = "*")
    public Int2 mul(Int2 other) {
        return new Int2(x * other.x, y * other.y);
    }

    @GPUIntrinsic(operator = "/")
    public Int2 div(Int2 other) {
        return new Int2(x / other.x, y / other.y);
    }
}
