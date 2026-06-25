package net.sixik.ga_utils.javatogpu.api;

import net.sixik.ga_utils.javatogpu.api.annotations.GPUIntrinsic;
import net.sixik.ga_utils.javatogpu.api.annotations.GPUVectorType;

/**
 * Java-side representation of the OpenCL {@code float2} vector type.
 *
 * <p>Use this type in local variables, helper parameters/returns and struct fields.
 *
 * <pre>{@code
 * Float2 uv = new Float2(0.5f, 1.0f);
 * float sum = uv.x + uv.y;
 * }</pre>
 */
@GPUVectorType(openClType = "float2", componentType = "float", fields = {"x", "y"})
public class Float2 {

    /**
     * First vector component.
     */
    public float x;

    /**
     * Second vector component.
     */
    public float y;

    public Float2() {
    }

    /**
     * Broadcast constructor. Fills all components with the same value.
     */
    public Float2(float value) {
        this.x = value;
        this.y = value;
    }

    /**
     * Creates a vector from explicit components.
     */
    public Float2(float x, float y) {
        this.x = x;
        this.y = y;
    }

    @GPUIntrinsic(operator = "+")
    public Float2 add(Float2 other) { return new Float2(x + other.x, y + other.y); }

    @GPUIntrinsic(operator = "-")
    public Float2 sub(Float2 other) { return new Float2(x - other.x, y - other.y); }

    @GPUIntrinsic(operator = "*")
    public Float2 mul(Float2 other) { return new Float2(x * other.x, y * other.y); }

    @GPUIntrinsic(operator = "/")
    public Float2 div(Float2 other) { return new Float2(x / other.x, y / other.y); }
}
