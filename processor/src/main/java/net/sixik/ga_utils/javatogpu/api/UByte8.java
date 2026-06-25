package net.sixik.ga_utils.javatogpu.api;

import net.sixik.ga_utils.javatogpu.api.annotations.GPUIntrinsic;
import net.sixik.ga_utils.javatogpu.api.annotations.GPUVectorType;

/**
 * Java-side representation of the OpenCL {@code uchar8} vector type.
 */
@GPUVectorType(openClType = "uchar8", componentType = "byte", fields = {"s0", "s1", "s2", "s3", "s4", "s5", "s6", "s7"})
public class UByte8 {

    public byte s0;
    public byte s1;
    public byte s2;
    public byte s3;
    public byte s4;
    public byte s5;
    public byte s6;
    public byte s7;

    public UByte8() {
    }

    public UByte8(byte value) {
        this.s0 = value;
        this.s1 = value;
        this.s2 = value;
        this.s3 = value;
        this.s4 = value;
        this.s5 = value;
        this.s6 = value;
        this.s7 = value;
    }

    public UByte8(byte s0, byte s1, byte s2, byte s3, byte s4, byte s5, byte s6, byte s7) {
        this.s0 = s0;
        this.s1 = s1;
        this.s2 = s2;
        this.s3 = s3;
        this.s4 = s4;
        this.s5 = s5;
        this.s6 = s6;
        this.s7 = s7;
    }

    @GPUIntrinsic(operator = "+")
    public UByte8 add(UByte8 other) { return new UByte8((byte) (s0 + other.s0), (byte) (s1 + other.s1), (byte) (s2 + other.s2), (byte) (s3 + other.s3), (byte) (s4 + other.s4), (byte) (s5 + other.s5), (byte) (s6 + other.s6), (byte) (s7 + other.s7)); }

    @GPUIntrinsic(operator = "-")
    public UByte8 sub(UByte8 other) { return new UByte8((byte) (s0 - other.s0), (byte) (s1 - other.s1), (byte) (s2 - other.s2), (byte) (s3 - other.s3), (byte) (s4 - other.s4), (byte) (s5 - other.s5), (byte) (s6 - other.s6), (byte) (s7 - other.s7)); }

    @GPUIntrinsic(operator = "*")
    public UByte8 mul(UByte8 other) { return new UByte8((byte) (s0 * other.s0), (byte) (s1 * other.s1), (byte) (s2 * other.s2), (byte) (s3 * other.s3), (byte) (s4 * other.s4), (byte) (s5 * other.s5), (byte) (s6 * other.s6), (byte) (s7 * other.s7)); }

    @GPUIntrinsic(operator = "/")
    public UByte8 div(UByte8 other) { return new UByte8((byte) (s0 / other.s0), (byte) (s1 / other.s1), (byte) (s2 / other.s2), (byte) (s3 / other.s3), (byte) (s4 / other.s4), (byte) (s5 / other.s5), (byte) (s6 / other.s6), (byte) (s7 / other.s7)); }
}
