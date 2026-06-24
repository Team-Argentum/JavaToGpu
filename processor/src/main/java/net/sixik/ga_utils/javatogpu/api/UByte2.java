package net.sixik.ga_utils.javatogpu.api;

import net.sixik.ga_utils.javatogpu.api.anotations.GPUIntrinsic;
import net.sixik.ga_utils.javatogpu.api.anotations.GPUVectorType;

/**
 * Java-side representation of the OpenCL {@code uchar2} vector type.
 */
@GPUVectorType(openClType = "uchar2", componentType = "byte", fields = {"x", "y"})
public class UByte2 {

    public byte x;
    public byte y;

    public UByte2() {
    }

    public UByte2(byte value) {
        this.x = value;
        this.y = value;
    }

    public UByte2(byte x, byte y) {
        this.x = x;
        this.y = y;
    }

    @GPUIntrinsic(operator = "+")
    public UByte2 add(UByte2 other) { return new UByte2((byte) (x + other.x), (byte) (y + other.y)); }

    @GPUIntrinsic(operator = "-")
    public UByte2 sub(UByte2 other) { return new UByte2((byte) (x - other.x), (byte) (y - other.y)); }

    @GPUIntrinsic(operator = "*")
    public UByte2 mul(UByte2 other) { return new UByte2((byte) (x * other.x), (byte) (y * other.y)); }

    @GPUIntrinsic(operator = "/")
    public UByte2 div(UByte2 other) { return new UByte2((byte) (x / other.x), (byte) (y / other.y)); }
}
