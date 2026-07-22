package net.sixik.ga_utils.javatogpu.api.pointers.constant;

import net.sixik.ga_utils.javatogpu.api.annotations.GPUPointerAddressSpace;
import net.sixik.ga_utils.javatogpu.api.annotations.GPUIntrinsic;
import net.sixik.ga_utils.javatogpu.api.annotations.GPUPointerType;

@GPUPointerType(valueType = "short", addressSpace = GPUPointerAddressSpace.CONSTANT)
public final class ConstantShortPtr {

    public short value;

    public ConstantShortPtr() {
    }

    public ConstantShortPtr(short value) {
        this.value = value;
    }

    @GPUIntrinsic(code = "(({this}) + ({0}))")
    public ConstantShortPtr add(int elements) {
        return this;
    }

    @GPUIntrinsic(code = "(({this}) - ({0}))")
    public ConstantShortPtr sub(int elements) {
        return this;
    }
}
