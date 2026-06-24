package net.sixik.ga_utils.javatogpu.api;

import net.sixik.ga_utils.javatogpu.api.anotations.GPUIntrinsic;
import net.sixik.ga_utils.javatogpu.api.anotations.GPUVectorType;

/**
 * Java-side representation of the OpenCL {@code uint3} vector type.
 *
 * <p>Java has no unsigned {@code int}, so every component is stored as a regular {@code int} carrying the raw
 * 32-bit value.
 */
@GPUVectorType(openClType = "uint3", componentType = "int", fields = {"x", "y", "z"})
public class UInt3 {

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

    public UInt3() {
    }

    /**
     * Broadcast constructor. Fills all components with the same value.
     */
    public UInt3(int value) {
        this.x = value;
        this.y = value;
        this.z = value;
    }

    /**
     * Creates a vector from explicit components.
     */
    public UInt3(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @GPUIntrinsic(operator = "+")
    public UInt3 add(UInt3 other) { return new UInt3(x + other.x, y + other.y, z + other.z); }

    @GPUIntrinsic(operator = "-")
    public UInt3 sub(UInt3 other) { return new UInt3(x - other.x, y - other.y, z - other.z); }

    @GPUIntrinsic(operator = "*")
    public UInt3 mul(UInt3 other) { return new UInt3(x * other.x, y * other.y, z * other.z); }

    @GPUIntrinsic(operator = "/")
    public UInt3 div(UInt3 other) { return new UInt3(x / other.x, y / other.y, z / other.z); }
}
