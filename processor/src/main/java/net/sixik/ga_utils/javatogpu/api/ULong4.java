package net.sixik.ga_utils.javatogpu.api;

import net.sixik.ga_utils.javatogpu.api.annotations.GPUIntrinsic;
import net.sixik.ga_utils.javatogpu.api.annotations.GPUVectorType;

/**
 * Java-side representation of the OpenCL {@code ulong4} vector type.
 */
@GPUVectorType(openClType = "ulong4", componentType = "long", fields = {"x", "y", "z", "w"})
public class ULong4 {

    public long x;
    public long y;
    public long z;
    public long w;

    public ULong4() {
    }

    public ULong4(long value) {
        this.x = value;
        this.y = value;
        this.z = value;
        this.w = value;
    }

    public ULong4(long x, long y, long z, long w) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.w = w;
    }

    @GPUIntrinsic(operator = "+")
    public ULong4 add(ULong4 other) { return new ULong4(x + other.x, y + other.y, z + other.z, w + other.w); }

    @GPUIntrinsic(operator = "-")
    public ULong4 sub(ULong4 other) { return new ULong4(x - other.x, y - other.y, z - other.z, w - other.w); }

    @GPUIntrinsic(operator = "*")
    public ULong4 mul(ULong4 other) { return new ULong4(x * other.x, y * other.y, z * other.z, w * other.w); }

    @GPUIntrinsic(operator = "/")
    public ULong4 div(ULong4 other) { return new ULong4(x / other.x, y / other.y, z / other.z, w / other.w); }
}
