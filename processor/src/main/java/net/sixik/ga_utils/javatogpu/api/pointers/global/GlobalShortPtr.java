package net.sixik.ga_utils.javatogpu.api.pointers.global;

import net.sixik.ga_utils.javatogpu.api.annotations.GPUPointerAddressSpace;
import net.sixik.ga_utils.javatogpu.api.annotations.GPUIntrinsic;
import net.sixik.ga_utils.javatogpu.api.annotations.GPUPointerType;

@GPUPointerType(valueType = "short", addressSpace = GPUPointerAddressSpace.GLOBAL)
public final class GlobalShortPtr {

    public short value;

    public GlobalShortPtr() {
    }

    public GlobalShortPtr(short value) {
        this.value = value;
    }

    @GPUIntrinsic(code = "(({this}) + ({0}))")
    public GlobalShortPtr add(int elements) {
        return this;
    }

    @GPUIntrinsic(code = "(({this}) - ({0}))")
    public GlobalShortPtr sub(int elements) {
        return this;
    }
}
