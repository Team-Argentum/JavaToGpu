package net.sixik.ga_utils.javatogpu.api.pointers.constant;

import net.sixik.ga_utils.javatogpu.api.annotations.GPUPointerAddressSpace;
import net.sixik.ga_utils.javatogpu.api.annotations.GPUIntrinsic;
import net.sixik.ga_utils.javatogpu.api.annotations.GPUPointerType;

@GPUPointerType(valueType = "long", addressSpace = GPUPointerAddressSpace.CONSTANT)
public final class ConstantLongPtr {

    public long value;

    public ConstantLongPtr() {
    }

    public ConstantLongPtr(long value) {
        this.value = value;
    }

    @GPUIntrinsic(code = "(({this}) + ({0}))")
    public ConstantLongPtr add(int elements) {
        return this;
    }

    @GPUIntrinsic(code = "(({this}) - ({0}))")
    public ConstantLongPtr sub(int elements) {
        return this;
    }
}
