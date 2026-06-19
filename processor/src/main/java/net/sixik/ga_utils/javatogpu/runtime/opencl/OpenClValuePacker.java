package net.sixik.ga_utils.javatogpu.runtime.opencl;

import java.nio.ByteBuffer;

final class OpenClValuePacker {

    private OpenClValuePacker() {
    }

    static boolean isStructInstance(Object value) {
        return OpenClAbiSupport.isStructInstance(value);
    }

    static boolean isStructArrayInstance(Object value) {
        return OpenClAbiSupport.isStructArrayInstance(value);
    }

    static boolean isVectorArrayInstance(Object value) {
        return OpenClAbiSupport.isVectorArrayInstance(value);
    }

    static ByteBuffer packVector(String javaType, Object argument) {
        return OpenClAbiSupport.packVector(javaType, argument);
    }

    static ByteBuffer packStruct(Object argument) {
        return OpenClAbiSupport.packStruct(argument);
    }

    static ByteBuffer packStructArray(Object array) {
        return OpenClAbiSupport.packStructArray(array);
    }

    static int structArrayByteSize(Object array) {
        return OpenClAbiSupport.structArrayByteSize(array);
    }

    static void unpackStructArray(ByteBuffer buffer, Object array) {
        OpenClAbiSupport.unpackStructArray(buffer, array);
    }

    static ByteBuffer packVectorArray(Object array) {
        return OpenClAbiSupport.packVectorArray(array);
    }

    static int vectorArrayByteSize(Object array) {
        return OpenClAbiSupport.vectorArrayByteSize(array);
    }

    static void unpackVectorArray(ByteBuffer buffer, Object array) {
        OpenClAbiSupport.unpackVectorArray(buffer, array);
    }
}
