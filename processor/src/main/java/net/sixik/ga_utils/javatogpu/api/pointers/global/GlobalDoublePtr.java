package net.sixik.ga_utils.javatogpu.api.pointers.global;

import net.sixik.ga_utils.javatogpu.api.annotations.GPUPointerAddressSpace;
import net.sixik.ga_utils.javatogpu.api.annotations.GPUIntrinsic;
import net.sixik.ga_utils.javatogpu.api.annotations.GPUPointerType;

/**
 * Typed OpenCL {@code __global double*} helper pointer.
 */
@GPUPointerType(valueType = "double", addressSpace = GPUPointerAddressSpace.GLOBAL)
public final class GlobalDoublePtr {

    public double value;

    public GlobalDoublePtr() {
    }

    public GlobalDoublePtr(double value) {
        this.value = value;
    }

    @GPUIntrinsic(code = "(({this}) + ({0}))")
    public GlobalDoublePtr add(int elements) {
        return this;
    }

    @GPUIntrinsic(code = "(({this}) - ({0}))")
    public GlobalDoublePtr sub(int elements) {
        return this;
    }
}
