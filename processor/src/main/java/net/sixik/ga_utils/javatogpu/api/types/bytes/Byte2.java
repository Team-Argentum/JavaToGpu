package net.sixik.ga_utils.javatogpu.api.types.bytes;

import net.sixik.ga_utils.javatogpu.api.annotations.GPUIntrinsic;
import net.sixik.ga_utils.javatogpu.api.annotations.GPUVectorType;

/**
 * Java-side representation of the OpenCL {@code char2} vector type.
 */
@GPUVectorType(openClType = "char2", componentType = "byte", fields = {"x", "y"})
public class Byte2 {

    public byte x;
    public byte y;

    public Byte2() {
    }

    public Byte2(byte value) {
        this.x = value;
        this.y = value;
    }

    public Byte2(byte x, byte y) {
        this.x = x;
        this.y = y;
    }

    @GPUIntrinsic(operator = "+")
    public Byte2 add(Byte2 other) { return new Byte2((byte) (x + other.x), (byte) (y + other.y)); }

    @GPUIntrinsic(operator = "-")
    public Byte2 sub(Byte2 other) { return new Byte2((byte) (x - other.x), (byte) (y - other.y)); }

    @GPUIntrinsic(operator = "*")
    public Byte2 mul(Byte2 other) { return new Byte2((byte) (x * other.x), (byte) (y * other.y)); }

    @GPUIntrinsic(operator = "/")
    public Byte2 div(Byte2 other) { return new Byte2((byte) (x / other.x), (byte) (y / other.y)); }
}
