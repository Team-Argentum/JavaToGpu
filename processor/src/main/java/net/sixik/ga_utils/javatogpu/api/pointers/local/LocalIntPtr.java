package net.sixik.ga_utils.javatogpu.api.pointers.local;

import net.sixik.ga_utils.javatogpu.api.annotations.GPUPointerAddressSpace;
import net.sixik.ga_utils.javatogpu.api.annotations.GPUIntrinsic;
import net.sixik.ga_utils.javatogpu.api.annotations.GPUPointerType;

@GPUPointerType(valueType = "int", addressSpace = GPUPointerAddressSpace.LOCAL)
public final class LocalIntPtr {

    public int value;

    public LocalIntPtr() {
    }

    public LocalIntPtr(int value) {
        this.value = value;
    }

    @GPUIntrinsic(code = "(({this}) + ({0}))")
    public LocalIntPtr add(int elements) {
        return this;
    }

    @GPUIntrinsic(code = "(({this}) - ({0}))")
    public LocalIntPtr sub(int elements) {
        return this;
    }
}
