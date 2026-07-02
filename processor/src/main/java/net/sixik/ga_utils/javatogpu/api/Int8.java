package net.sixik.ga_utils.javatogpu.api;

import net.sixik.ga_utils.javatogpu.api.annotations.GPUIntrinsic;
import net.sixik.ga_utils.javatogpu.api.annotations.GPUVectorType;

/**
 * Java-side representation of the OpenCL {@code int8} vector type.
 */
@GPUVectorType(openClType = "int8", componentType = "int", fields = {"s0", "s1", "s2", "s3", "s4", "s5", "s6", "s7"})
public class Int8 {

    public int s0;
    public int s1;
    public int s2;
    public int s3;
    public int s4;
    public int s5;
    public int s6;
    public int s7;

    public Int8() {
    }

    public Int8(int value) {
        this.s0 = value;
        this.s1 = value;
        this.s2 = value;
        this.s3 = value;
        this.s4 = value;
        this.s5 = value;
        this.s6 = value;
        this.s7 = value;
    }

    public Int8(int s0, int s1, int s2, int s3, int s4, int s5, int s6, int s7) {
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
    public Int8 add(Int8 other) { return new Int8(s0 + other.s0, s1 + other.s1, s2 + other.s2, s3 + other.s3, s4 + other.s4, s5 + other.s5, s6 + other.s6, s7 + other.s7); }

    @GPUIntrinsic(operator = "-")
    public Int8 sub(Int8 other) { return new Int8(s0 - other.s0, s1 - other.s1, s2 - other.s2, s3 - other.s3, s4 - other.s4, s5 - other.s5, s6 - other.s6, s7 - other.s7); }

    @GPUIntrinsic(operator = "*")
    public Int8 mul(Int8 other) { return new Int8(s0 * other.s0, s1 * other.s1, s2 * other.s2, s3 * other.s3, s4 * other.s4, s5 * other.s5, s6 * other.s6, s7 * other.s7); }

    @GPUIntrinsic(operator = "/")
    public Int8 div(Int8 other) { return new Int8(s0 / other.s0, s1 / other.s1, s2 / other.s2, s3 / other.s3, s4 / other.s4, s5 / other.s5, s6 / other.s6, s7 / other.s7); }
}
