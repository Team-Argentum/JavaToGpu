package net.sixik.ga_utils.javatogpu.api.pointers.constant;

import net.sixik.ga_utils.javatogpu.api.annotations.GPUPointerAddressSpace;
import net.sixik.ga_utils.javatogpu.api.annotations.GPUIntrinsic;
import net.sixik.ga_utils.javatogpu.api.annotations.GPUPointerType;

@GPUPointerType(valueType = "int", addressSpace = GPUPointerAddressSpace.CONSTANT)
public final class ConstantIntPtr {

    public int value;

    public ConstantIntPtr() {
    }

    public ConstantIntPtr(int value) {
        this.value = value;
    }

    @GPUIntrinsic(code = "(({this}) + ({0}))")
    public ConstantIntPtr add(int elements) {
        return this;
    }

    @GPUIntrinsic(code = "(({this}) - ({0}))")
    public ConstantIntPtr sub(int elements) {
        return this;
    }
}
