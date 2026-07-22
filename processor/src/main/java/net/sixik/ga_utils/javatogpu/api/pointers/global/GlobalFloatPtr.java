package net.sixik.ga_utils.javatogpu.api.pointers.global;

import net.sixik.ga_utils.javatogpu.api.annotations.GPUPointerAddressSpace;
import net.sixik.ga_utils.javatogpu.api.annotations.GPUIntrinsic;
import net.sixik.ga_utils.javatogpu.api.annotations.GPUPointerType;

/**
 * Typed OpenCL {@code __global float*} helper pointer.
 */
@GPUPointerType(valueType = "float", addressSpace = GPUPointerAddressSpace.GLOBAL)
public final class GlobalFloatPtr {

    public float value;

    public GlobalFloatPtr() {
    }

    public GlobalFloatPtr(float value) {
        this.value = value;
    }

    /**
     * Returns a pointer moved by the given element offset.
     */
    @GPUIntrinsic(code = "(({this}) + ({0}))")
    public GlobalFloatPtr add(int elements) {
        return this;
    }

    @GPUIntrinsic(code = "(({this}) - ({0}))")
    public GlobalFloatPtr sub(int elements) {
        return this;
    }
}
