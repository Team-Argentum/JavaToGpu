package net.sixik.ga_utils.javatogpu.runtime.opencl;

import java.nio.ByteBuffer;
import java.util.List;

record OpenClStructLayout(
        int size,
        int alignment,
        List<OpenClFieldLayout> fields
) implements OpenClValueLayout {

    @Override
    public void write(Object value, ByteBuffer buffer, int offset) {
        for (OpenClFieldLayout field : fields) {
            Object fieldValue = OpenClAbiSupport.readFieldValue(value, field.field());
            if (fieldValue == null) {
                throw new IllegalArgumentException("Null @GPUStruct field is not supported for OpenCL marshalling: " + field.field().getName());
            }
            field.layout().write(fieldValue, buffer, offset + field.offset());
        }
    }

    @Override
    public Object read(ByteBuffer buffer, int offset, Class<?> targetType) {
        Object instance = OpenClAbiSupport.instantiateType(targetType);
        for (OpenClFieldLayout field : fields) {
            Object fieldValue = field.layout().read(buffer, offset + field.offset(), field.field().getType());
            OpenClAbiSupport.writeFieldValue(instance, field.field(), fieldValue);
        }
        return instance;
    }

    @Override
    public OpenClAbiDescriptor describe(String typeName) {
        java.util.List<OpenClAbiFieldDescriptor> descriptors = new java.util.ArrayList<>(fields.size());
        for (OpenClFieldLayout field : fields) {
            descriptors.add(new OpenClAbiFieldDescriptor(
                    field.field().getName(),
                    field.field().getType().getName(),
                    field.offset(),
                    field.storageSize(),
                    field.alignment(),
                    field.layout().describe(field.field().getType().getName())
            ));
        }
        return new OpenClAbiDescriptor(typeName, OpenClAbiKind.STRUCT, size, alignment, List.copyOf(descriptors));
    }
}
