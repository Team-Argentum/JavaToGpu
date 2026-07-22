package net.sixik.ga_utils.javatogpu.api.pointers.local;

import net.sixik.ga_utils.javatogpu.api.annotations.GPUPointerAddressSpace;
import net.sixik.ga_utils.javatogpu.api.annotations.GPUIntrinsic;
import net.sixik.ga_utils.javatogpu.api.annotations.GPUPointerType;

@GPUPointerType(valueType = "byte", addressSpace = GPUPointerAddressSpace.LOCAL)
public final class LocalBytePtr {

    public byte value;

    public LocalBytePtr() {
    }

    public LocalBytePtr(byte value) {
        this.value = value;
    }

    @GPUIntrinsic(code = "(({this}) + ({0}))")
    public LocalBytePtr add(int bytes) {
        return this;
    }

    @GPUIntrinsic(code = "(({this}) - ({0}))")
    public LocalBytePtr sub(int bytes) {
        return this;
    }

    @GPUIntrinsic(code = "(*((__local char*) ((({this}) + ({0})))))")
    public byte readByteAt(int byteOffset) {
        return 0;
    }

    @GPUIntrinsic(code = "(*((__local short*) ((({this}) + ({0})))))")
    public short readShortAt(int byteOffset) {
        return 0;
    }

    @GPUIntrinsic(code = "(*((__local int*) ((({this}) + ({0})))))")
    public int readIntAt(int byteOffset) {
        return 0;
    }

    @GPUIntrinsic(code = "(*((__local long*) ((({this}) + ({0})))))")
    public long readLongAt(int byteOffset) {
        return 0L;
    }

    @GPUIntrinsic(code = "(*((__local float*) ((({this}) + ({0})))))")
    public float readFloatAt(int byteOffset) {
        return 0.0f;
    }

    @GPUIntrinsic(code = "(*((__local double*) ((({this}) + ({0})))))")
    public double readDoubleAt(int byteOffset) {
        return 0.0d;
    }

    @GPUIntrinsic(code = "((__local char*) ((({this}) + ({0}))))")
    public LocalBytePtr bytePtrAt(int byteOffset) {
        return null;
    }

    @GPUIntrinsic(code = "((__local char*) ((({this}) + ({0}))))")
    public LocalCharPtr charPtrAt(int byteOffset) {
        return null;
    }

    @GPUIntrinsic(code = "((__local short*) ((({this}) + ({0}))))")
    public LocalShortPtr shortPtrAt(int byteOffset) {
        return null;
    }

    @GPUIntrinsic(code = "((__local int*) ((({this}) + ({0}))))")
    public LocalIntPtr intPtrAt(int byteOffset) {
        return null;
    }

    @GPUIntrinsic(code = "((__local long*) ((({this}) + ({0}))))")
    public LocalLongPtr longPtrAt(int byteOffset) {
        return null;
    }

    @GPUIntrinsic(code = "((__local float*) ((({this}) + ({0}))))")
    public LocalFloatPtr floatPtrAt(int byteOffset) {
        return null;
    }

    @GPUIntrinsic(code = "((__local double*) ((({this}) + ({0}))))")
    public LocalDoublePtr doublePtrAt(int byteOffset) {
        return null;
    }

    @GPUIntrinsic(code = "((__local char*) ({this}))")
    public LocalCharPtr asCharPtr() {
        return null;
    }

    @GPUIntrinsic(code = "((__local short*) ({this}))")
    public LocalShortPtr asShortPtr() {
        return null;
    }

    @GPUIntrinsic(code = "((__local int*) ({this}))")
    public LocalIntPtr asIntPtr() {
        return null;
    }

    @GPUIntrinsic(code = "((__local long*) ({this}))")
    public LocalLongPtr asLongPtr() {
        return null;
    }

    @GPUIntrinsic(code = "((__local float*) ({this}))")
    public LocalFloatPtr asFloatPtr() {
        return null;
    }

    @GPUIntrinsic(code = "((__local double*) ({this}))")
    public LocalDoublePtr asDoublePtr() {
        return null;
    }
}
