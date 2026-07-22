package net.sixik.ga_utils.javatogpu.api.types.bytes;

import net.sixik.ga_utils.javatogpu.api.annotations.GPUIntrinsic;
import net.sixik.ga_utils.javatogpu.api.annotations.GPUVectorType;

/**
 * Java-side representation of the OpenCL {@code char3} vector type.
 */
@GPUVectorType(openClType = "char3", componentType = "byte", fields = {"x", "y", "z"})
public class Byte3 {

    public byte x;
    public byte y;
    public byte z;

    public Byte3() {
    }

    public Byte3(byte value) {
        this.x = value;
        this.y = value;
        this.z = value;
    }

    public Byte3(byte x, byte y, byte z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @GPUIntrinsic(operator = "+")
    public Byte3 add(Byte3 other) { return new Byte3((byte) (x + other.x), (byte) (y + other.y), (byte) (z + other.z)); }

    @GPUIntrinsic(operator = "-")
    public Byte3 sub(Byte3 other) { return new Byte3((byte) (x - other.x), (byte) (y - other.y), (byte) (z - other.z)); }

    @GPUIntrinsic(operator = "*")
    public Byte3 mul(Byte3 other) { return new Byte3((byte) (x * other.x), (byte) (y * other.y), (byte) (z * other.z)); }

    @GPUIntrinsic(operator = "/")
    public Byte3 div(Byte3 other) { return new Byte3((byte) (x / other.x), (byte) (y / other.y), (byte) (z / other.z)); }
}
