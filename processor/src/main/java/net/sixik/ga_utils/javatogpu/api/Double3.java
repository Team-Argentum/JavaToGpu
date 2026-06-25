package net.sixik.ga_utils.javatogpu.api;

import net.sixik.ga_utils.javatogpu.api.annotations.GPUIntrinsic;
import net.sixik.ga_utils.javatogpu.api.annotations.GPUVectorType;

/**
 * Java-side representation of the OpenCL {@code double3} vector type.
 */
@GPUVectorType(openClType = "double3", componentType = "double", fields = {"x", "y", "z"})
public class Double3 {

    /**
     * First vector component.
     */
    public double x;
    /**
     * Second vector component.
     */
    public double y;
    /**
     * Third vector component.
     */
    public double z;

    public Double3() {
    }

    /**
     * Broadcast constructor. Fills all components with the same value.
     */
    public Double3(double value) {
        this.x = value;
        this.y = value;
        this.z = value;
    }

    /**
     * Creates a vector from explicit components.
     */
    public Double3(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @GPUIntrinsic(operator = "+")
    public Double3 add(Double3 other) { return new Double3(x + other.x, y + other.y, z + other.z); }

    @GPUIntrinsic(operator = "-")
    public Double3 sub(Double3 other) { return new Double3(x - other.x, y - other.y, z - other.z); }

    @GPUIntrinsic(operator = "*")
    public Double3 mul(Double3 other) { return new Double3(x * other.x, y * other.y, z * other.z); }

    @GPUIntrinsic(operator = "/")
    public Double3 div(Double3 other) { return new Double3(x / other.x, y / other.y, z / other.z); }
}
