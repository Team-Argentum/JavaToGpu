package net.sixik.ga_utils.javatogpu.api;

import net.sixik.ga_utils.javatogpu.api.annotations.GPUIntrinsic;
import net.sixik.ga_utils.javatogpu.api.annotations.GPUVectorType;

/**
 * Java-side representation of the OpenCL {@code ulong16} vector type.
 */
@GPUVectorType(openClType = "ulong16", componentType = "long", fields = {"s0", "s1", "s2", "s3", "s4", "s5", "s6", "s7", "s8", "s9", "sa", "sb", "sc", "sd", "se", "sf"})
public class ULong16 {

    public long s0;
    public long s1;
    public long s2;
    public long s3;
    public long s4;
    public long s5;
    public long s6;
    public long s7;
    public long s8;
    public long s9;
    public long sa;
    public long sb;
    public long sc;
    public long sd;
    public long se;
    public long sf;

    public ULong16() {
    }

    public ULong16(long value) {
        this.s0 = value;
        this.s1 = value;
        this.s2 = value;
        this.s3 = value;
        this.s4 = value;
        this.s5 = value;
        this.s6 = value;
        this.s7 = value;
        this.s8 = value;
        this.s9 = value;
        this.sa = value;
        this.sb = value;
        this.sc = value;
        this.sd = value;
        this.se = value;
        this.sf = value;
    }

    public ULong16(long s0, long s1, long s2, long s3, long s4, long s5, long s6, long s7, long s8, long s9, long sa, long sb, long sc, long sd, long se, long sf) {
        this.s0 = s0;
        this.s1 = s1;
        this.s2 = s2;
        this.s3 = s3;
        this.s4 = s4;
        this.s5 = s5;
        this.s6 = s6;
        this.s7 = s7;
        this.s8 = s8;
        this.s9 = s9;
        this.sa = sa;
        this.sb = sb;
        this.sc = sc;
        this.sd = sd;
        this.se = se;
        this.sf = sf;
    }

    @GPUIntrinsic(operator = "+")
    public ULong16 add(ULong16 other) { return new ULong16(s0 + other.s0, s1 + other.s1, s2 + other.s2, s3 + other.s3, s4 + other.s4, s5 + other.s5, s6 + other.s6, s7 + other.s7, s8 + other.s8, s9 + other.s9, sa + other.sa, sb + other.sb, sc + other.sc, sd + other.sd, se + other.se, sf + other.sf); }

    @GPUIntrinsic(operator = "-")
    public ULong16 sub(ULong16 other) { return new ULong16(s0 - other.s0, s1 - other.s1, s2 - other.s2, s3 - other.s3, s4 - other.s4, s5 - other.s5, s6 - other.s6, s7 - other.s7, s8 - other.s8, s9 - other.s9, sa - other.sa, sb - other.sb, sc - other.sc, sd - other.sd, se - other.se, sf - other.sf); }

    @GPUIntrinsic(operator = "*")
    public ULong16 mul(ULong16 other) { return new ULong16(s0 * other.s0, s1 * other.s1, s2 * other.s2, s3 * other.s3, s4 * other.s4, s5 * other.s5, s6 * other.s6, s7 * other.s7, s8 * other.s8, s9 * other.s9, sa * other.sa, sb * other.sb, sc * other.sc, sd * other.sd, se * other.se, sf * other.sf); }

    @GPUIntrinsic(operator = "/")
    public ULong16 div(ULong16 other) { return new ULong16(s0 / other.s0, s1 / other.s1, s2 / other.s2, s3 / other.s3, s4 / other.s4, s5 / other.s5, s6 / other.s6, s7 / other.s7, s8 / other.s8, s9 / other.s9, sa / other.sa, sb / other.sb, sc / other.sc, sd / other.sd, se / other.se, sf / other.sf); }
}
