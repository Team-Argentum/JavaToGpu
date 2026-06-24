package net.sixik.ga_utils.javatogpu.api;

import net.sixik.ga_utils.javatogpu.api.anotations.GPUIntrinsic;
import net.sixik.ga_utils.javatogpu.api.anotations.GPUVectorType;

/**
 * Java-side representation of the OpenCL {@code ushort2} vector type.
 */
@GPUVectorType(openClType = "ushort2", componentType = "short", fields = {"x", "y"})
public class UShort2 {

    public short x;
    public short y;

    public UShort2() {
    }

    public UShort2(short value) {
        this.x = value;
        this.y = value;
    }

    public UShort2(short x, short y) {
        this.x = x;
        this.y = y;
    }

    @GPUIntrinsic(operator = "+")
    public UShort2 add(UShort2 other) { return new UShort2((short) (x + other.x), (short) (y + other.y)); }

    @GPUIntrinsic(operator = "-")
    public UShort2 sub(UShort2 other) { return new UShort2((short) (x - other.x), (short) (y - other.y)); }

    @GPUIntrinsic(operator = "*")
    public UShort2 mul(UShort2 other) { return new UShort2((short) (x * other.x), (short) (y * other.y)); }

    @GPUIntrinsic(operator = "/")
    public UShort2 div(UShort2 other) { return new UShort2((short) (x / other.x), (short) (y / other.y)); }
}
