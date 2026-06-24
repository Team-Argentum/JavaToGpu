package net.sixik.ga_utils.javatogpu.api;

import net.sixik.ga_utils.javatogpu.api.anotations.GPUIntrinsic;
import net.sixik.ga_utils.javatogpu.api.anotations.GPUVectorType;

/**
 * Java-side representation of the OpenCL {@code ushort3} vector type.
 */
@GPUVectorType(openClType = "ushort3", componentType = "short", fields = {"x", "y", "z"})
public class UShort3 {

    public short x;
    public short y;
    public short z;

    public UShort3() {
    }

    public UShort3(short value) {
        this.x = value;
        this.y = value;
        this.z = value;
    }

    public UShort3(short x, short y, short z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @GPUIntrinsic(operator = "+")
    public UShort3 add(UShort3 other) { return new UShort3((short) (x + other.x), (short) (y + other.y), (short) (z + other.z)); }

    @GPUIntrinsic(operator = "-")
    public UShort3 sub(UShort3 other) { return new UShort3((short) (x - other.x), (short) (y - other.y), (short) (z - other.z)); }

    @GPUIntrinsic(operator = "*")
    public UShort3 mul(UShort3 other) { return new UShort3((short) (x * other.x), (short) (y * other.y), (short) (z * other.z)); }

    @GPUIntrinsic(operator = "/")
    public UShort3 div(UShort3 other) { return new UShort3((short) (x / other.x), (short) (y / other.y), (short) (z / other.z)); }
}
