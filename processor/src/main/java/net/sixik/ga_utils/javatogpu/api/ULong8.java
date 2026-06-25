package net.sixik.ga_utils.javatogpu.api;

import net.sixik.ga_utils.javatogpu.api.annotations.GPUIntrinsic;
import net.sixik.ga_utils.javatogpu.api.annotations.GPUVectorType;

/**
 * Java-side representation of the OpenCL {@code ulong8} vector type.
 */
@GPUVectorType(openClType = "ulong8", componentType = "long", fields = {"s0", "s1", "s2", "s3", "s4", "s5", "s6", "s7"})
public class ULong8 {

    public long s0;
    public long s1;
    public long s2;
    public long s3;
    public long s4;
    public long s5;
    public long s6;
    public long s7;

    public ULong8() {
    }

    public ULong8(long value) {
        this.s0 = value;
        this.s1 = value;
        this.s2 = value;
        this.s3 = value;
        this.s4 = value;
        this.s5 = value;
        this.s6 = value;
        this.s7 = value;
    }

    public ULong8(long s0, long s1, long s2, long s3, long s4, long s5, long s6, long s7) {
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
    public ULong8 add(ULong8 other) { return new ULong8(s0 + other.s0, s1 + other.s1, s2 + other.s2, s3 + other.s3, s4 + other.s4, s5 + other.s5, s6 + other.s6, s7 + other.s7); }

    @GPUIntrinsic(operator = "-")
    public ULong8 sub(ULong8 other) { return new ULong8(s0 - other.s0, s1 - other.s1, s2 - other.s2, s3 - other.s3, s4 - other.s4, s5 - other.s5, s6 - other.s6, s7 - other.s7); }

    @GPUIntrinsic(operator = "*")
    public ULong8 mul(ULong8 other) { return new ULong8(s0 * other.s0, s1 * other.s1, s2 * other.s2, s3 * other.s3, s4 * other.s4, s5 * other.s5, s6 * other.s6, s7 * other.s7); }

    @GPUIntrinsic(operator = "/")
    public ULong8 div(ULong8 other) { return new ULong8(s0 / other.s0, s1 / other.s1, s2 / other.s2, s3 / other.s3, s4 / other.s4, s5 / other.s5, s6 / other.s6, s7 / other.s7); }
}
