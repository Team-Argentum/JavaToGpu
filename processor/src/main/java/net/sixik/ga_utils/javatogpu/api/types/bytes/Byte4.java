package net.sixik.ga_utils.javatogpu.api.types.bytes;

import net.sixik.ga_utils.javatogpu.api.annotations.GPUIntrinsic;
import net.sixik.ga_utils.javatogpu.api.annotations.GPUVectorType;

/**
 * Java-side representation of the OpenCL {@code char4} vector type.
 */
@GPUVectorType(openClType = "char4", componentType = "byte", fields = {"x", "y", "z", "w"})
public class Byte4 {

    public byte x;
    public byte y;
    public byte z;
    public byte w;

    public Byte4() {
    }

    public Byte4(byte value) {
        this.x = value;
        this.y = value;
        this.z = value;
        this.w = value;
    }

    public Byte4(byte x, byte y, byte z, byte w) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.w = w;
    }

    @GPUIntrinsic(operator = "+")
    public Byte4 add(Byte4 other) { return new Byte4((byte) (x + other.x), (byte) (y + other.y), (byte) (z + other.z), (byte) (w + other.w)); }

    @GPUIntrinsic(operator = "-")
    public Byte4 sub(Byte4 other) { return new Byte4((byte) (x - other.x), (byte) (y - other.y), (byte) (z - other.z), (byte) (w - other.w)); }

    @GPUIntrinsic(operator = "*")
    public Byte4 mul(Byte4 other) { return new Byte4((byte) (x * other.x), (byte) (y * other.y), (byte) (z * other.z), (byte) (w * other.w)); }

    @GPUIntrinsic(operator = "/")
    public Byte4 div(Byte4 other) { return new Byte4((byte) (x / other.x), (byte) (y / other.y), (byte) (z / other.z), (byte) (w / other.w)); }
}
