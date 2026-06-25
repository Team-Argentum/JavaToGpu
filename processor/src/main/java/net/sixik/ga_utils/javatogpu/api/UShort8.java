package net.sixik.ga_utils.javatogpu.api;

import net.sixik.ga_utils.javatogpu.api.annotations.GPUIntrinsic;
import net.sixik.ga_utils.javatogpu.api.annotations.GPUVectorType;

/**
 * Java-side representation of the OpenCL {@code ushort8} vector type.
 */
@GPUVectorType(openClType = "ushort8", componentType = "short", fields = {"s0", "s1", "s2", "s3", "s4", "s5", "s6", "s7"})
public class UShort8 {

    public short s0;
    public short s1;
    public short s2;
    public short s3;
    public short s4;
    public short s5;
    public short s6;
    public short s7;

    public UShort8() {
    }

    public UShort8(short value) {
        this.s0 = value;
        this.s1 = value;
        this.s2 = value;
        this.s3 = value;
        this.s4 = value;
        this.s5 = value;
        this.s6 = value;
        this.s7 = value;
    }

    public UShort8(short s0, short s1, short s2, short s3, short s4, short s5, short s6, short s7) {
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
    public UShort8 add(UShort8 other) { return new UShort8((short) (s0 + other.s0), (short) (s1 + other.s1), (short) (s2 + other.s2), (short) (s3 + other.s3), (short) (s4 + other.s4), (short) (s5 + other.s5), (short) (s6 + other.s6), (short) (s7 + other.s7)); }

    @GPUIntrinsic(operator = "-")
    public UShort8 sub(UShort8 other) { return new UShort8((short) (s0 - other.s0), (short) (s1 - other.s1), (short) (s2 - other.s2), (short) (s3 - other.s3), (short) (s4 - other.s4), (short) (s5 - other.s5), (short) (s6 - other.s6), (short) (s7 - other.s7)); }

    @GPUIntrinsic(operator = "*")
    public UShort8 mul(UShort8 other) { return new UShort8((short) (s0 * other.s0), (short) (s1 * other.s1), (short) (s2 * other.s2), (short) (s3 * other.s3), (short) (s4 * other.s4), (short) (s5 * other.s5), (short) (s6 * other.s6), (short) (s7 * other.s7)); }

    @GPUIntrinsic(operator = "/")
    public UShort8 div(UShort8 other) { return new UShort8((short) (s0 / other.s0), (short) (s1 / other.s1), (short) (s2 / other.s2), (short) (s3 / other.s3), (short) (s4 / other.s4), (short) (s5 / other.s5), (short) (s6 / other.s6), (short) (s7 / other.s7)); }
}
