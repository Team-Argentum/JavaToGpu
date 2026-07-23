package net.sixik.ga_utils.javatogpu.api.types.shorts;

import net.sixik.ga_utils.javatogpu.api.annotations.GPUIntrinsic;
import net.sixik.ga_utils.javatogpu.api.annotations.GPUVectorType;

/**
 * Java-side representation of the OpenCL {@code short4} vector type.
 */
@GPUVectorType(openClType = "short4", componentType = "short", fields = {"x", "y", "z", "w"})
public class Short4 {

    public short x;
    public short y;
    public short z;
    public short w;

    public Short4() {
    }

    public Short4(short value) {
        this.x = value;
        this.y = value;
        this.z = value;
        this.w = value;
    }

    public Short4(short x, short y, short z, short w) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.w = w;
    }

    @GPUIntrinsic(operator = "+")
    public Short4 add(Short4 other) { return new Short4((short) (x + other.x), (short) (y + other.y), (short) (z + other.z), (short) (w + other.w)); }

    @GPUIntrinsic(operator = "-")
    public Short4 sub(Short4 other) { return new Short4((short) (x - other.x), (short) (y - other.y), (short) (z - other.z), (short) (w - other.w)); }

    @GPUIntrinsic(operator = "*")
    public Short4 mul(Short4 other) { return new Short4((short) (x * other.x), (short) (y * other.y), (short) (z * other.z), (short) (w * other.w)); }

    @GPUIntrinsic(operator = "/")
    public Short4 div(Short4 other) { return new Short4((short) (x / other.x), (short) (y / other.y), (short) (z / other.z), (short) (w / other.w)); }
}
