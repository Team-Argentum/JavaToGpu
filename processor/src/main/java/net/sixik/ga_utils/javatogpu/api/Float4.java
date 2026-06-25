package net.sixik.ga_utils.javatogpu.api;

import net.sixik.ga_utils.javatogpu.api.annotations.GPUIntrinsic;
import net.sixik.ga_utils.javatogpu.api.annotations.GPUVectorType;

/**
 * Java-side representation of the OpenCL {@code float4} vector type.
 */
@GPUVectorType(openClType = "float4", componentType = "float", fields = {"x", "y", "z", "w"})
public class Float4 {

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
    /**
     * Fourth vector component.
     */
    public float w;

    public Float4() {
    }

    /**
     * Broadcast constructor. Fills all components with the same value.
     */
    public Float4(float value) {
        this.x = value;
        this.y = value;
        this.z = value;
        this.w = value;
    }

    /**
     * Creates a vector from explicit components.
     */
    public Float4(float x, float y, float z, float w) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.w = w;
    }

    @GPUIntrinsic(operator = "+")
    public Float4 add(Float4 other) {
        return new Float4(x + other.x, y + other.y, z + other.z, w + other.w);
    }

    @GPUIntrinsic(operator = "-")
    public Float4 sub(Float4 other) {
        return new Float4(x - other.x, y - other.y, z - other.z, w - other.w);
    }

    @GPUIntrinsic(operator = "*")
    public Float4 mul(Float4 other) {
        return new Float4(x * other.x, y * other.y, z * other.z, w * other.w);
    }

    @GPUIntrinsic(operator = "/")
    public Float4 div(Float4 other) {
        return new Float4(x / other.x, y / other.y, z / other.z, w / other.w);
    }
}
