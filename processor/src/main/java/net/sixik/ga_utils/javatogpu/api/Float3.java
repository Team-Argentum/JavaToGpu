package net.sixik.ga_utils.javatogpu.api;

import net.sixik.ga_utils.javatogpu.api.annotations.GPUIntrinsic;
import net.sixik.ga_utils.javatogpu.api.annotations.GPUVectorType;

/**
 * Java-side representation of the OpenCL {@code float3} vector type.
 */
@GPUVectorType(openClType = "float3", componentType = "float", fields = {"x", "y", "z"})
public class Float3 {

    /**
     * First vector component.
     */
    public float x;
    /**
     * Second vector component.
     */
    public float y;
    /**
     * Third vector component.
     */
    public float z;

    public Float3() {
    }

    /**
     * Broadcast constructor. Fills all components with the same value.
     */
    public Float3(float value) {
        this.x = value;
        this.y = value;
        this.z = value;
    }

    /**
     * Creates a vector from explicit components.
     */
    public Float3(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @GPUIntrinsic(operator = "+")
    public Float3 add(Float3 other) { return new Float3(x + other.x, y + other.y, z + other.z); }

    @GPUIntrinsic(operator = "-")
    public Float3 sub(Float3 other) { return new Float3(x - other.x, y - other.y, z - other.z); }

    @GPUIntrinsic(operator = "*")
    public Float3 mul(Float3 other) { return new Float3(x * other.x, y * other.y, z * other.z); }

    @GPUIntrinsic(operator = "/")
    public Float3 div(Float3 other) { return new Float3(x / other.x, y / other.y, z / other.z); }
}
