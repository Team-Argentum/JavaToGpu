package net.sixik.ga_utils.javatogpu.api;

import net.sixik.ga_utils.javatogpu.api.anotations.GPUIntrinsic;
import net.sixik.ga_utils.javatogpu.api.anotations.GPUVectorType;

/**
 * Java-side representation of the OpenCL {@code ulong3} vector type.
 */
@GPUVectorType(openClType = "ulong3", componentType = "long", fields = {"x", "y", "z"})
public class ULong3 {

    public long x;
    public long y;
    public long z;

    public ULong3() {
    }

    public ULong3(long value) {
        this.x = value;
        this.y = value;
        this.z = value;
    }

    public ULong3(long x, long y, long z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @GPUIntrinsic(operator = "+")
    public ULong3 add(ULong3 other) { return new ULong3(x + other.x, y + other.y, z + other.z); }

    @GPUIntrinsic(operator = "-")
    public ULong3 sub(ULong3 other) { return new ULong3(x - other.x, y - other.y, z - other.z); }

    @GPUIntrinsic(operator = "*")
    public ULong3 mul(ULong3 other) { return new ULong3(x * other.x, y * other.y, z * other.z); }

    @GPUIntrinsic(operator = "/")
    public ULong3 div(ULong3 other) { return new ULong3(x / other.x, y / other.y, z / other.z); }
}
