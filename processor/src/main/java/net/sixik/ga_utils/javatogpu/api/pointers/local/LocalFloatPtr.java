package net.sixik.ga_utils.javatogpu.api.pointers.local;

import net.sixik.ga_utils.javatogpu.api.annotations.GPUPointerAddressSpace;
import net.sixik.ga_utils.javatogpu.api.annotations.GPUIntrinsic;
import net.sixik.ga_utils.javatogpu.api.annotations.GPUPointerType;

/**
 * Typed OpenCL {@code __local float*} helper pointer.
 */
@GPUPointerType(valueType = "float", addressSpace = GPUPointerAddressSpace.LOCAL)
public final class LocalFloatPtr {

    public float value;

    public LocalFloatPtr() {
    }

    public LocalFloatPtr(float value) {
        this.value = value;
    }

    /**
     * Returns a pointer moved by the given element offset.
     */
    @GPUIntrinsic(code = "(({this}) + ({0}))")
    public LocalFloatPtr add(int elements) {
        return this;
    }

    @GPUIntrinsic(code = "(({this}) - ({0}))")
    public LocalFloatPtr sub(int elements) {
        return this;
    }
}
