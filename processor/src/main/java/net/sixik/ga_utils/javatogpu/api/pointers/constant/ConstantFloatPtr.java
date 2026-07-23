package net.sixik.ga_utils.javatogpu.api.pointers.constant;

import net.sixik.ga_utils.javatogpu.api.annotations.GPUPointerAddressSpace;
import net.sixik.ga_utils.javatogpu.api.annotations.GPUIntrinsic;
import net.sixik.ga_utils.javatogpu.api.annotations.GPUPointerType;

/**
 * Typed OpenCL {@code __constant float*} helper pointer.
 */
@GPUPointerType(valueType = "float", addressSpace = GPUPointerAddressSpace.CONSTANT)
public final class ConstantFloatPtr {

    public float value;

    public ConstantFloatPtr() {
    }

    public ConstantFloatPtr(float value) {
        this.value = value;
    }

    /**
     * Returns a pointer moved by the given element offset.
     */
    @GPUIntrinsic(code = "(({this}) + ({0}))")
    public ConstantFloatPtr add(int elements) {
        return this;
    }

    @GPUIntrinsic(code = "(({this}) - ({0}))")
    public ConstantFloatPtr sub(int elements) {
        return this;
    }
}
