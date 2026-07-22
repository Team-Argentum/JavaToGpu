package net.sixik.ga_utils.javatogpu.api.pointers.local;

import net.sixik.ga_utils.javatogpu.api.annotations.GPUPointerAddressSpace;
import net.sixik.ga_utils.javatogpu.api.annotations.GPUIntrinsic;
import net.sixik.ga_utils.javatogpu.api.annotations.GPUPointerType;

@GPUPointerType(valueType = "double", addressSpace = GPUPointerAddressSpace.LOCAL)
public final class LocalDoublePtr {

    public double value;

    public LocalDoublePtr() {
    }

    public LocalDoublePtr(double value) {
        this.value = value;
    }

    @GPUIntrinsic(code = "(({this}) + ({0}))")
    public LocalDoublePtr add(int elements) {
        return this;
    }

    @GPUIntrinsic(code = "(({this}) - ({0}))")
    public LocalDoublePtr sub(int elements) {
        return this;
    }
}
