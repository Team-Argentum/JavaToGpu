package net.sixik.ga_utils.javatogpu.api.pointers.global;

import net.sixik.ga_utils.javatogpu.api.annotations.GPUPointerAddressSpace;
import net.sixik.ga_utils.javatogpu.api.annotations.GPUIntrinsic;
import net.sixik.ga_utils.javatogpu.api.annotations.GPUPointerType;

/**
 * Typed OpenCL {@code __global int*} helper pointer.
 */
@GPUPointerType(valueType = "int", addressSpace = GPUPointerAddressSpace.GLOBAL)
public final class GlobalIntPtr {

    public int value;

    public GlobalIntPtr() {
    }

    public GlobalIntPtr(int value) {
        this.value = value;
    }

    @GPUIntrinsic(code = "(({this}) + ({0}))")
    public GlobalIntPtr add(int elements) {
        return this;
    }

    @GPUIntrinsic(code = "(({this}) - ({0}))")
    public GlobalIntPtr sub(int elements) {
        return this;
    }
}
