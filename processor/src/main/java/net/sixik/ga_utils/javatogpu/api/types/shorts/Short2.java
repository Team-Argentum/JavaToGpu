package net.sixik.ga_utils.javatogpu.api.types.shorts;

import net.sixik.ga_utils.javatogpu.api.annotations.GPUIntrinsic;
import net.sixik.ga_utils.javatogpu.api.annotations.GPUVectorType;

/**
 * Java-side representation of the OpenCL {@code short2} vector type.
 */
@GPUVectorType(openClType = "short2", componentType = "short", fields = {"x", "y"})
public class Short2 {

    public short x;
    public short y;

    public Short2() {
    }

    public Short2(short value) {
        this.x = value;
        this.y = value;
    }

    public Short2(short x, short y) {
        this.x = x;
        this.y = y;
    }

    @GPUIntrinsic(operator = "+")
    public Short2 add(Short2 other) { return new Short2((short) (x + other.x), (short) (y + other.y)); }

    @GPUIntrinsic(operator = "-")
    public Short2 sub(Short2 other) { return new Short2((short) (x - other.x), (short) (y - other.y)); }

    @GPUIntrinsic(operator = "*")
    public Short2 mul(Short2 other) { return new Short2((short) (x * other.x), (short) (y * other.y)); }

    @GPUIntrinsic(operator = "/")
    public Short2 div(Short2 other) { return new Short2((short) (x / other.x), (short) (y / other.y)); }
}
