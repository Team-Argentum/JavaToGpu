package net.sixik.ga_utils.javatogpu.api.pointers.local;

import net.sixik.ga_utils.javatogpu.api.annotations.GPUPointerAddressSpace;
import net.sixik.ga_utils.javatogpu.api.annotations.GPUIntrinsic;
import net.sixik.ga_utils.javatogpu.api.annotations.GPUPointerType;

@GPUPointerType(valueType = "long", addressSpace = GPUPointerAddressSpace.LOCAL)
public final class LocalLongPtr {

    public long value;

    public LocalLongPtr() {
    }

    public LocalLongPtr(long value) {
        this.value = value;
    }

    @GPUIntrinsic(code = "(({this}) + ({0}))")
    public LocalLongPtr add(int elements) {
        return this;
    }

    @GPUIntrinsic(code = "(({this}) - ({0}))")
    public LocalLongPtr sub(int elements) {
        return this;
    }
}
