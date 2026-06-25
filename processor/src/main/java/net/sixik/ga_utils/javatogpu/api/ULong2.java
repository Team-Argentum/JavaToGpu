package net.sixik.ga_utils.javatogpu.api;

import net.sixik.ga_utils.javatogpu.api.annotations.GPUIntrinsic;
import net.sixik.ga_utils.javatogpu.api.annotations.GPUVectorType;

/**
 * Java-side representation of the OpenCL {@code ulong2} vector type.
 */
@GPUVectorType(openClType = "ulong2", componentType = "long", fields = {"x", "y"})
public class ULong2 {

    public long x;
    public long y;

    public ULong2() {
    }

    public ULong2(long value) {
        this.x = value;
        this.y = value;
    }

    public ULong2(long x, long y) {
        this.x = x;
        this.y = y;
    }

    @GPUIntrinsic(operator = "+")
    public ULong2 add(ULong2 other) { return new ULong2(x + other.x, y + other.y); }

    @GPUIntrinsic(operator = "-")
    public ULong2 sub(ULong2 other) { return new ULong2(x - other.x, y - other.y); }

    @GPUIntrinsic(operator = "*")
    public ULong2 mul(ULong2 other) { return new ULong2(x * other.x, y * other.y); }

    @GPUIntrinsic(operator = "/")
    public ULong2 div(ULong2 other) { return new ULong2(x / other.x, y / other.y); }
}
