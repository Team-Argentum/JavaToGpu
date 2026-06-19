package net.sixik.ga_utils.javatogpu.runtime.opencl;

import java.nio.ByteBuffer;

interface OpenClValueLayout {

    int size();

    int alignment();

    void write(Object value, ByteBuffer buffer, int offset);

    Object read(ByteBuffer buffer, int offset, Class<?> targetType);

    OpenClAbiDescriptor describe(String typeName);
}
