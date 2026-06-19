package net.sixik.ga_utils.javatogpu.runtime.opencl;

import java.nio.ByteBuffer;
import java.util.List;

record OpenClScalarLayout(String javaType, int size) implements OpenClValueLayout {

    @Override
    public int alignment() {
        return size;
    }

    @Override
    public void write(Object value, ByteBuffer buffer, int offset) {
        OpenClAbiSupport.writeScalar(buffer, offset, javaType, value);
    }

    @Override
    public Object read(ByteBuffer buffer, int offset, Class<?> targetType) {
        return OpenClAbiSupport.readScalar(buffer, offset, javaType);
    }

    @Override
    public OpenClAbiDescriptor describe(String typeName) {
        return new OpenClAbiDescriptor(
                typeName,
                OpenClAbiKind.SCALAR,
                size,
                alignment(),
                List.of()
        );
    }
}
