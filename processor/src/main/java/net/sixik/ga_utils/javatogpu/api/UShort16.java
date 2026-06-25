package net.sixik.ga_utils.javatogpu.api;

import net.sixik.ga_utils.javatogpu.api.annotations.GPUIntrinsic;
import net.sixik.ga_utils.javatogpu.api.annotations.GPUVectorType;

/**
 * Java-side representation of the OpenCL {@code ushort16} vector type.
 */
@GPUVectorType(openClType = "ushort16", componentType = "short", fields = {"s0", "s1", "s2", "s3", "s4", "s5", "s6", "s7", "s8", "s9", "sa", "sb", "sc", "sd", "se", "sf"})
public class UShort16 {

    public short s0;
    public short s1;
    public short s2;
    public short s3;
    public short s4;
    public short s5;
    public short s6;
    public short s7;
    public short s8;
    public short s9;
    public short sa;
    public short sb;
    public short sc;
    public short sd;
    public short se;
    public short sf;

    public UShort16() {
    }

    public UShort16(short value) {
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

    public UShort16(short s0, short s1, short s2, short s3, short s4, short s5, short s6, short s7, short s8, short s9, short sa, short sb, short sc, short sd, short se, short sf) {
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
    public UShort16 add(UShort16 other) { return new UShort16((short) (s0 + other.s0), (short) (s1 + other.s1), (short) (s2 + other.s2), (short) (s3 + other.s3), (short) (s4 + other.s4), (short) (s5 + other.s5), (short) (s6 + other.s6), (short) (s7 + other.s7), (short) (s8 + other.s8), (short) (s9 + other.s9), (short) (sa + other.sa), (short) (sb + other.sb), (short) (sc + other.sc), (short) (sd + other.sd), (short) (se + other.se), (short) (sf + other.sf)); }

    @GPUIntrinsic(operator = "-")
    public UShort16 sub(UShort16 other) { return new UShort16((short) (s0 - other.s0), (short) (s1 - other.s1), (short) (s2 - other.s2), (short) (s3 - other.s3), (short) (s4 - other.s4), (short) (s5 - other.s5), (short) (s6 - other.s6), (short) (s7 - other.s7), (short) (s8 - other.s8), (short) (s9 - other.s9), (short) (sa - other.sa), (short) (sb - other.sb), (short) (sc - other.sc), (short) (sd - other.sd), (short) (se - other.se), (short) (sf - other.sf)); }

    @GPUIntrinsic(operator = "*")
    public UShort16 mul(UShort16 other) { return new UShort16((short) (s0 * other.s0), (short) (s1 * other.s1), (short) (s2 * other.s2), (short) (s3 * other.s3), (short) (s4 * other.s4), (short) (s5 * other.s5), (short) (s6 * other.s6), (short) (s7 * other.s7), (short) (s8 * other.s8), (short) (s9 * other.s9), (short) (sa * other.sa), (short) (sb * other.sb), (short) (sc * other.sc), (short) (sd * other.sd), (short) (se * other.se), (short) (sf * other.sf)); }

    @GPUIntrinsic(operator = "/")
    public UShort16 div(UShort16 other) { return new UShort16((short) (s0 / other.s0), (short) (s1 / other.s1), (short) (s2 / other.s2), (short) (s3 / other.s3), (short) (s4 / other.s4), (short) (s5 / other.s5), (short) (s6 / other.s6), (short) (s7 / other.s7), (short) (s8 / other.s8), (short) (s9 / other.s9), (short) (sa / other.sa), (short) (sb / other.sb), (short) (sc / other.sc), (short) (sd / other.sd), (short) (se / other.se), (short) (sf / other.sf)); }
}
