package net.sixik.ga_utils.javatogpu.api;

import net.sixik.ga_utils.javatogpu.api.annotations.GPUIntrinsic;
import net.sixik.ga_utils.javatogpu.api.annotations.GPUVectorType;

/**
 * Java-side representation of the OpenCL {@code uint2} vector type.
 */
@GPUVectorType(openClType = "uint2", componentType = "int", fields = {"x", "y"})
public class UInt2 {

    public int x;
    public int y;

    public UInt2() {
    }

    public UInt2(int value) {
        this.x = value;
        this.y = value;
    }

    public UInt2(int x, int y) {
        this.x = x;
        this.y = y;
    }

    @GPUIntrinsic(operator = "+")
    public UInt2 add(UInt2 other) { return new UInt2(x + other.x, y + other.y); }

    @GPUIntrinsic(operator = "-")
    public UInt2 sub(UInt2 other) { return new UInt2(x - other.x, y - other.y); }

    @GPUIntrinsic(operator = "*")
    public UInt2 mul(UInt2 other) { return new UInt2(x * other.x, y * other.y); }

    @GPUIntrinsic(operator = "/")
    public UInt2 div(UInt2 other) { return new UInt2(x / other.x, y / other.y); }
}
