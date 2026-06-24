package net.sixik.ga_utils.javatogpu.api;

import net.sixik.ga_utils.javatogpu.api.anotations.GPUIntrinsic;
import net.sixik.ga_utils.javatogpu.api.anotations.GPUVectorType;

/**
 * Java-side representation of the OpenCL {@code ushort4} vector type.
 */
@GPUVectorType(openClType = "ushort4", componentType = "short", fields = {"x", "y", "z", "w"})
public class UShort4 {

    public short x;
    public short y;
    public short z;
    public short w;

    public UShort4() {
    }

    public UShort4(short value) {
        this.x = value;
        this.y = value;
        this.z = value;
        this.w = value;
    }

    public UShort4(short x, short y, short z, short w) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.w = w;
    }

    @GPUIntrinsic(operator = "+")
    public UShort4 add(UShort4 other) { return new UShort4((short) (x + other.x), (short) (y + other.y), (short) (z + other.z), (short) (w + other.w)); }

    @GPUIntrinsic(operator = "-")
    public UShort4 sub(UShort4 other) { return new UShort4((short) (x - other.x), (short) (y - other.y), (short) (z - other.z), (short) (w - other.w)); }

    @GPUIntrinsic(operator = "*")
    public UShort4 mul(UShort4 other) { return new UShort4((short) (x * other.x), (short) (y * other.y), (short) (z * other.z), (short) (w * other.w)); }

    @GPUIntrinsic(operator = "/")
    public UShort4 div(UShort4 other) { return new UShort4((short) (x / other.x), (short) (y / other.y), (short) (z / other.z), (short) (w / other.w)); }
}
