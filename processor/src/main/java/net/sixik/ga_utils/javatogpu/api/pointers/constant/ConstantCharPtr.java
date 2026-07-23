package net.sixik.ga_utils.javatogpu.api.pointers.constant;

import net.sixik.ga_utils.javatogpu.api.annotations.GPUPointerAddressSpace;
import net.sixik.ga_utils.javatogpu.api.annotations.GPUIntrinsic;
import net.sixik.ga_utils.javatogpu.api.annotations.GPUPointerType;

@GPUPointerType(valueType = "char", addressSpace = GPUPointerAddressSpace.CONSTANT)
public final class ConstantCharPtr {

    public char value;

    public ConstantCharPtr() {
    }

    public ConstantCharPtr(char value) {
        this.value = value;
    }

    @GPUIntrinsic(code = "(({this}) + ({0}))")
    public ConstantCharPtr add(int elements) {
        return this;
    }

    @GPUIntrinsic(code = "(({this}) - ({0}))")
    public ConstantCharPtr sub(int elements) {
        return this;
    }
}
