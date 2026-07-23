package net.sixik.ga_utils.javatogpu.api.pointers.constant;

import net.sixik.ga_utils.javatogpu.api.annotations.GPUPointerAddressSpace;
import net.sixik.ga_utils.javatogpu.api.annotations.GPUIntrinsic;
import net.sixik.ga_utils.javatogpu.api.annotations.GPUPointerType;

@GPUPointerType(valueType = "double", addressSpace = GPUPointerAddressSpace.CONSTANT)
public final class ConstantDoublePtr {

    public double value;

    public ConstantDoublePtr() {
    }

    public ConstantDoublePtr(double value) {
        this.value = value;
    }

    @GPUIntrinsic(code = "(({this}) + ({0}))")
    public ConstantDoublePtr add(int elements) {
        return this;
    }

    @GPUIntrinsic(code = "(({this}) - ({0}))")
    public ConstantDoublePtr sub(int elements) {
        return this;
    }
}
