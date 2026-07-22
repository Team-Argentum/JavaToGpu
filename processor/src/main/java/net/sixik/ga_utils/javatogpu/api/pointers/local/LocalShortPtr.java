package net.sixik.ga_utils.javatogpu.api.pointers.local;

import net.sixik.ga_utils.javatogpu.api.annotations.GPUPointerAddressSpace;
import net.sixik.ga_utils.javatogpu.api.annotations.GPUIntrinsic;
import net.sixik.ga_utils.javatogpu.api.annotations.GPUPointerType;

@GPUPointerType(valueType = "short", addressSpace = GPUPointerAddressSpace.LOCAL)
public final class LocalShortPtr {

    public short value;

    public LocalShortPtr() {
    }

    public LocalShortPtr(short value) {
        this.value = value;
    }

    @GPUIntrinsic(code = "(({this}) + ({0}))")
    public LocalShortPtr add(int elements) {
        return this;
    }

    @GPUIntrinsic(code = "(({this}) - ({0}))")
    public LocalShortPtr sub(int elements) {
        return this;
    }
}
