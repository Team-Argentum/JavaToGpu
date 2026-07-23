package net.sixik.ga_utils.javatogpu.api.types.integers;

import net.sixik.ga_utils.javatogpu.api.annotations.GPUIntrinsic;
import net.sixik.ga_utils.javatogpu.api.annotations.GPUVectorType;

/**
 * Java-side representation of the OpenCL {@code int16} vector type.
 */
@GPUVectorType(openClType = "int16", componentType = "int", fields = {"s0", "s1", "s2", "s3", "s4", "s5", "s6", "s7", "s8", "s9", "sa", "sb", "sc", "sd", "se", "sf"})
public class Int16 {

    public int s0;
    public int s1;
    public int s2;
    public int s3;
    public int s4;
    public int s5;
    public int s6;
    public int s7;
    public int s8;
    public int s9;
    public int sa;
    public int sb;
    public int sc;
    public int sd;
    public int se;
    public int sf;

    public Int16() {
    }

    public Int16(int value) {
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

    public Int16(int s0, int s1, int s2, int s3, int s4, int s5, int s6, int s7, int s8, int s9, int sa, int sb, int sc, int sd, int se, int sf) {
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
    public Int16 add(Int16 other) {
        return new Int16(s0 + other.s0, s1 + other.s1, s2 + other.s2, s3 + other.s3, s4 + other.s4, s5 + other.s5, s6 + other.s6, s7 + other.s7,
                s8 + other.s8, s9 + other.s9, sa + other.sa, sb + other.sb, sc + other.sc, sd + other.sd, se + other.se, sf + other.sf);
    }

    @GPUIntrinsic(operator = "-")
    public Int16 sub(Int16 other) {
        return new Int16(s0 - other.s0, s1 - other.s1, s2 - other.s2, s3 - other.s3, s4 - other.s4, s5 - other.s5, s6 - other.s6, s7 - other.s7,
                s8 - other.s8, s9 - other.s9, sa - other.sa, sb - other.sb, sc - other.sc, sd - other.sd, se - other.se, sf - other.sf);
    }

    @GPUIntrinsic(operator = "*")
    public Int16 mul(Int16 other) {
        return new Int16(s0 * other.s0, s1 * other.s1, s2 * other.s2, s3 * other.s3, s4 * other.s4, s5 * other.s5, s6 * other.s6, s7 * other.s7,
                s8 * other.s8, s9 * other.s9, sa * other.sa, sb * other.sb, sc * other.sc, sd * other.sd, se * other.se, sf * other.sf);
    }

    @GPUIntrinsic(operator = "/")
    public Int16 div(Int16 other) {
        return new Int16(s0 / other.s0, s1 / other.s1, s2 / other.s2, s3 / other.s3, s4 / other.s4, s5 / other.s5, s6 / other.s6, s7 / other.s7,
                s8 / other.s8, s9 / other.s9, sa / other.sa, sb / other.sb, sc / other.sc, sd / other.sd, se / other.se, sf / other.sf);
    }
}
