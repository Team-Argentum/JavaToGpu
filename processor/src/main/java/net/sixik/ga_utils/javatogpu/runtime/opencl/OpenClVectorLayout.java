package net.sixik.ga_utils.javatogpu.runtime.opencl;

import net.sixik.ga_utils.javatogpu.types.GpuTypeSupport;

import java.nio.ByteBuffer;
import java.util.List;

final class OpenClVectorLayout implements OpenClValueLayout {

    private final String javaType;
    private final String componentType;
    private final List<String> fieldNames;
    private final int componentSize;
    private final int size;

    OpenClVectorLayout(String javaType) {
        this.javaType = javaType;
        this.componentType = GpuTypeSupport.vectorComponentType(javaType);
        this.fieldNames = GpuTypeSupport.vectorFieldNames(javaType);
        this.componentSize = GpuTypeSupport.scalarByteSize(componentType);
        this.size = GpuTypeSupport.vectorByteSize(javaType);
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public int alignment() {
        return size;
    }

    @Override
    public void write(Object value, ByteBuffer buffer, int offset) {
        int cursor = offset;
        for (String fieldName : fieldNames) {
            OpenClAbiSupport.writeScalar(
                    buffer,
                    cursor,
                    componentType,
                    OpenClAbiSupport.readFieldValue(value, OpenClAbiSupport.requireField(value.getClass(), fieldName))
            );
            cursor += componentSize;
        }
        for (int i = fieldNames.size(); i < GpuTypeSupport.vectorStorageWidth(javaType); i++) {
            OpenClAbiSupport.writeZeroScalar(buffer, cursor, componentType);
            cursor += componentSize;
        }
    }

    @Override
    public Object read(ByteBuffer buffer, int offset, Class<?> targetType) {
        Object instance = OpenClAbiSupport.instantiateType(targetType);
        int cursor = offset;
        for (String fieldName : fieldNames) {
            OpenClAbiSupport.writeFieldValue(
                    instance,
                    OpenClAbiSupport.requireField(targetType, fieldName),
                    OpenClAbiSupport.readScalar(buffer, cursor, componentType)
            );
            cursor += componentSize;
        }
        return instance;
    }

    @Override
    public OpenClAbiDescriptor describe(String typeName) {
        java.util.List<OpenClAbiFieldDescriptor> descriptors = new java.util.ArrayList<>(fieldNames.size());
        int cursor = 0;
        OpenClAbiDescriptor componentDescriptor = new OpenClScalarLayout(componentType, componentSize).describe(componentType);
        for (String fieldName : fieldNames) {
            descriptors.add(new OpenClAbiFieldDescriptor(
                    fieldName,
                    componentType,
                    cursor,
                    componentSize,
                    componentSize,
                    componentDescriptor
            ));
            cursor += componentSize;
        }
        return new OpenClAbiDescriptor(typeName, OpenClAbiKind.VECTOR, size, alignment(), List.copyOf(descriptors));
    }
}
