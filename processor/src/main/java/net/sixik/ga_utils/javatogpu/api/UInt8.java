package net.sixik.ga_utils.javatogpu.api;

import net.sixik.ga_utils.javatogpu.api.anotations.GPUIntrinsic;
import net.sixik.ga_utils.javatogpu.api.anotations.GPUVectorType;

/**
 * Java-side representation of the OpenCL {@code uint8} vector type.
 */
@GPUVectorType(openClType = "uint8", componentType = "int", fields = {"s0", "s1", "s2", "s3", "s4", "s5", "s6", "s7"})
public class UInt8 {

    public int s0;
    public int s1;
    public int s2;
    public int s3;
    public int s4;
    public int s5;
    public int s6;
    public int s7;

    public UInt8() {
    }

    public UInt8(int value) {
        this.s0 = value;
        this.s1 = value;
        this.s2 = value;
        this.s3 = value;
        this.s4 = value;
        this.s5 = value;
        this.s6 = value;
        this.s7 = value;
    }

    public UInt8(int s0, int s1, int s2, int s3, int s4, int s5, int s6, int s7) {
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
    public UInt8 add(UInt8 other) { return new UInt8(s0 + other.s0, s1 + other.s1, s2 + other.s2, s3 + other.s3, s4 + other.s4, s5 + other.s5, s6 + other.s6, s7 + other.s7); }

    @GPUIntrinsic(operator = "-")
    public UInt8 sub(UInt8 other) { return new UInt8(s0 - other.s0, s1 - other.s1, s2 - other.s2, s3 - other.s3, s4 - other.s4, s5 - other.s5, s6 - other.s6, s7 - other.s7); }

    @GPUIntrinsic(operator = "*")
    public UInt8 mul(UInt8 other) { return new UInt8(s0 * other.s0, s1 * other.s1, s2 * other.s2, s3 * other.s3, s4 * other.s4, s5 * other.s5, s6 * other.s6, s7 * other.s7); }

    @GPUIntrinsic(operator = "/")
    public UInt8 div(UInt8 other) { return new UInt8(s0 / other.s0, s1 / other.s1, s2 / other.s2, s3 / other.s3, s4 / other.s4, s5 / other.s5, s6 / other.s6, s7 / other.s7); }
}
