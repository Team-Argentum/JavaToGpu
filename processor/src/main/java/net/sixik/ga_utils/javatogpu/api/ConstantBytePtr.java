package net.sixik.ga_utils.javatogpu.api;

import net.sixik.ga_utils.javatogpu.api.annotations.GPUPointerAddressSpace;
import net.sixik.ga_utils.javatogpu.api.annotations.GPUIntrinsic;
import net.sixik.ga_utils.javatogpu.api.annotations.GPUPointerType;

@GPUPointerType(valueType = "byte", addressSpace = GPUPointerAddressSpace.CONSTANT)
public final class ConstantBytePtr {

    public byte value;

    public ConstantBytePtr() {
    }

    public ConstantBytePtr(byte value) {
        this.value = value;
    }

    @GPUIntrinsic(code = "(({this}) + ({0}))")
    public ConstantBytePtr add(int bytes) {
        return this;
    }

    @GPUIntrinsic(code = "(({this}) - ({0}))")
    public ConstantBytePtr sub(int bytes) {
        return this;
    }

    @GPUIntrinsic(code = "(*((__constant char*) ((({this}) + ({0})))))")
    public byte readByteAt(int byteOffset) {
        return 0;
    }

    @GPUIntrinsic(code = "(*((__constant short*) ((({this}) + ({0})))))")
    public short readShortAt(int byteOffset) {
        return 0;
    }

    @GPUIntrinsic(code = "(*((__constant int*) ((({this}) + ({0})))))")
    public int readIntAt(int byteOffset) {
        return 0;
    }

    @GPUIntrinsic(code = "(*((__constant long*) ((({this}) + ({0})))))")
    public long readLongAt(int byteOffset) {
        return 0L;
    }

    @GPUIntrinsic(code = "(*((__constant float*) ((({this}) + ({0})))))")
    public float readFloatAt(int byteOffset) {
        return 0.0f;
    }

    @GPUIntrinsic(code = "(*((__constant double*) ((({this}) + ({0})))))")
    public double readDoubleAt(int byteOffset) {
        return 0.0d;
    }

    @GPUIntrinsic(code = "((__constant char*) ((({this}) + ({0}))))")
    public ConstantBytePtr bytePtrAt(int byteOffset) {
        return null;
    }

    @GPUIntrinsic(code = "((__constant char*) ((({this}) + ({0}))))")
    public ConstantCharPtr charPtrAt(int byteOffset) {
        return null;
    }

    @GPUIntrinsic(code = "((__constant short*) ((({this}) + ({0}))))")
    public ConstantShortPtr shortPtrAt(int byteOffset) {
        return null;
    }

    @GPUIntrinsic(code = "((__constant int*) ((({this}) + ({0}))))")
    public ConstantIntPtr intPtrAt(int byteOffset) {
        return null;
    }

    @GPUIntrinsic(code = "((__constant long*) ((({this}) + ({0}))))")
    public ConstantLongPtr longPtrAt(int byteOffset) {
        return null;
    }

    @GPUIntrinsic(code = "((__constant float*) ((({this}) + ({0}))))")
    public ConstantFloatPtr floatPtrAt(int byteOffset) {
        return null;
    }

    @GPUIntrinsic(code = "((__constant double*) ((({this}) + ({0}))))")
    public ConstantDoublePtr doublePtrAt(int byteOffset) {
        return null;
    }

    @GPUIntrinsic(code = "((__constant char*) ({this}))")
    public ConstantCharPtr asCharPtr() {
        return null;
    }

    @GPUIntrinsic(code = "((__constant short*) ({this}))")
    public ConstantShortPtr asShortPtr() {
        return null;
    }

    @GPUIntrinsic(code = "((__constant int*) ({this}))")
    public ConstantIntPtr asIntPtr() {
        return null;
    }

    @GPUIntrinsic(code = "((__constant long*) ({this}))")
    public ConstantLongPtr asLongPtr() {
        return null;
    }

    @GPUIntrinsic(code = "((__constant float*) ({this}))")
    public ConstantFloatPtr asFloatPtr() {
        return null;
    }

    @GPUIntrinsic(code = "((__constant double*) ({this}))")
    public ConstantDoublePtr asDoublePtr() {
        return null;
    }
}
