package net.sixik.ga_utils.javatogpu.api;

import net.sixik.ga_utils.javatogpu.api.anotations.GPUIntrinsic;
import net.sixik.ga_utils.javatogpu.api.anotations.GPUVectorType;

/**
 * Java-side representation of the OpenCL {@code uchar16} vector type.
 */
@GPUVectorType(openClType = "uchar16", componentType = "byte", fields = {"s0", "s1", "s2", "s3", "s4", "s5", "s6", "s7", "s8", "s9", "sa", "sb", "sc", "sd", "se", "sf"})
public class UByte16 {

    public byte s0;
    public byte s1;
    public byte s2;
    public byte s3;
    public byte s4;
    public byte s5;
    public byte s6;
    public byte s7;
    public byte s8;
    public byte s9;
    public byte sa;
    public byte sb;
    public byte sc;
    public byte sd;
    public byte se;
    public byte sf;

    public UByte16() {
    }

    public UByte16(byte value) {
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

    public UByte16(byte s0, byte s1, byte s2, byte s3, byte s4, byte s5, byte s6, byte s7, byte s8, byte s9, byte sa, byte sb, byte sc, byte sd, byte se, byte sf) {
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
    public UByte16 add(UByte16 other) { return new UByte16((byte) (s0 + other.s0), (byte) (s1 + other.s1), (byte) (s2 + other.s2), (byte) (s3 + other.s3), (byte) (s4 + other.s4), (byte) (s5 + other.s5), (byte) (s6 + other.s6), (byte) (s7 + other.s7), (byte) (s8 + other.s8), (byte) (s9 + other.s9), (byte) (sa + other.sa), (byte) (sb + other.sb), (byte) (sc + other.sc), (byte) (sd + other.sd), (byte) (se + other.se), (byte) (sf + other.sf)); }

    @GPUIntrinsic(operator = "-")
    public UByte16 sub(UByte16 other) { return new UByte16((byte) (s0 - other.s0), (byte) (s1 - other.s1), (byte) (s2 - other.s2), (byte) (s3 - other.s3), (byte) (s4 - other.s4), (byte) (s5 - other.s5), (byte) (s6 - other.s6), (byte) (s7 - other.s7), (byte) (s8 - other.s8), (byte) (s9 - other.s9), (byte) (sa - other.sa), (byte) (sb - other.sb), (byte) (sc - other.sc), (byte) (sd - other.sd), (byte) (se - other.se), (byte) (sf - other.sf)); }

    @GPUIntrinsic(operator = "*")
    public UByte16 mul(UByte16 other) { return new UByte16((byte) (s0 * other.s0), (byte) (s1 * other.s1), (byte) (s2 * other.s2), (byte) (s3 * other.s3), (byte) (s4 * other.s4), (byte) (s5 * other.s5), (byte) (s6 * other.s6), (byte) (s7 * other.s7), (byte) (s8 * other.s8), (byte) (s9 * other.s9), (byte) (sa * other.sa), (byte) (sb * other.sb), (byte) (sc * other.sc), (byte) (sd * other.sd), (byte) (se * other.se), (byte) (sf * other.sf)); }

    @GPUIntrinsic(operator = "/")
    public UByte16 div(UByte16 other) { return new UByte16((byte) (s0 / other.s0), (byte) (s1 / other.s1), (byte) (s2 / other.s2), (byte) (s3 / other.s3), (byte) (s4 / other.s4), (byte) (s5 / other.s5), (byte) (s6 / other.s6), (byte) (s7 / other.s7), (byte) (s8 / other.s8), (byte) (s9 / other.s9), (byte) (sa / other.sa), (byte) (sb / other.sb), (byte) (sc / other.sc), (byte) (sd / other.sd), (byte) (se / other.se), (byte) (sf / other.sf)); }
}
