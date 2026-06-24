package net.sixik.ga_utils.javatogpu.api;

import net.sixik.ga_utils.javatogpu.api.anotations.GPUIntrinsic;
import net.sixik.ga_utils.javatogpu.api.anotations.GPUVectorType;

/**
 * Java-side representation of the OpenCL {@code uchar4} vector type.
 */
@GPUVectorType(openClType = "uchar4", componentType = "byte", fields = {"x", "y", "z", "w"})
public class UByte4 {

    public byte x;
    public byte y;
    public byte z;
    public byte w;

    public UByte4() {
    }

    public UByte4(byte value) {
        this.x = value;
        this.y = value;
        this.z = value;
        this.w = value;
    }

    public UByte4(byte x, byte y, byte z, byte w) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.w = w;
    }

    @GPUIntrinsic(operator = "+")
    public UByte4 add(UByte4 other) { return new UByte4((byte) (x + other.x), (byte) (y + other.y), (byte) (z + other.z), (byte) (w + other.w)); }

    @GPUIntrinsic(operator = "-")
    public UByte4 sub(UByte4 other) { return new UByte4((byte) (x - other.x), (byte) (y - other.y), (byte) (z - other.z), (byte) (w - other.w)); }

    @GPUIntrinsic(operator = "*")
    public UByte4 mul(UByte4 other) { return new UByte4((byte) (x * other.x), (byte) (y * other.y), (byte) (z * other.z), (byte) (w * other.w)); }

    @GPUIntrinsic(operator = "/")
    public UByte4 div(UByte4 other) { return new UByte4((byte) (x / other.x), (byte) (y / other.y), (byte) (z / other.z), (byte) (w / other.w)); }
}
