package net.sixik.ga_utils.javatogpu.api;

import net.sixik.ga_utils.javatogpu.api.anotations.GPUIntrinsic;
import net.sixik.ga_utils.javatogpu.api.anotations.GPUVectorType;

/**
 * Java-side representation of the OpenCL {@code uchar3} vector type.
 */
@GPUVectorType(openClType = "uchar3", componentType = "byte", fields = {"x", "y", "z"})
public class UByte3 {

    public byte x;
    public byte y;
    public byte z;

    public UByte3() {
    }

    public UByte3(byte value) {
        this.x = value;
        this.y = value;
        this.z = value;
    }

    public UByte3(byte x, byte y, byte z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @GPUIntrinsic(operator = "+")
    public UByte3 add(UByte3 other) { return new UByte3((byte) (x + other.x), (byte) (y + other.y), (byte) (z + other.z)); }

    @GPUIntrinsic(operator = "-")
    public UByte3 sub(UByte3 other) { return new UByte3((byte) (x - other.x), (byte) (y - other.y), (byte) (z - other.z)); }

    @GPUIntrinsic(operator = "*")
    public UByte3 mul(UByte3 other) { return new UByte3((byte) (x * other.x), (byte) (y * other.y), (byte) (z * other.z)); }

    @GPUIntrinsic(operator = "/")
    public UByte3 div(UByte3 other) { return new UByte3((byte) (x / other.x), (byte) (y / other.y), (byte) (z / other.z)); }
}
