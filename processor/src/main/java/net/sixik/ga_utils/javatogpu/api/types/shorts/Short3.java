package net.sixik.ga_utils.javatogpu.api.types.shorts;

import net.sixik.ga_utils.javatogpu.api.annotations.GPUIntrinsic;
import net.sixik.ga_utils.javatogpu.api.annotations.GPUVectorType;

/**
 * Java-side representation of the OpenCL {@code short3} vector type.
 */
@GPUVectorType(openClType = "short3", componentType = "short", fields = {"x", "y", "z"})
public class Short3 {

    public short x;
    public short y;
    public short z;

    public Short3() {
    }

    public Short3(short value) {
        this.x = value;
        this.y = value;
        this.z = value;
    }

    public Short3(short x, short y, short z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @GPUIntrinsic(operator = "+")
    public Short3 add(Short3 other) { return new Short3((short) (x + other.x), (short) (y + other.y), (short) (z + other.z)); }

    @GPUIntrinsic(operator = "-")
    public Short3 sub(Short3 other) { return new Short3((short) (x - other.x), (short) (y - other.y), (short) (z - other.z)); }

    @GPUIntrinsic(operator = "*")
    public Short3 mul(Short3 other) { return new Short3((short) (x * other.x), (short) (y * other.y), (short) (z * other.z)); }

    @GPUIntrinsic(operator = "/")
    public Short3 div(Short3 other) { return new Short3((short) (x / other.x), (short) (y / other.y), (short) (z / other.z)); }
}
