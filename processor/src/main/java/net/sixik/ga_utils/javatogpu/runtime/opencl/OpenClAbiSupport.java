package net.sixik.ga_utils.javatogpu.runtime.opencl;

import net.sixik.ga_utils.javatogpu.api.GpuAnnotationSupport;
import net.sixik.ga_utils.javatogpu.types.GpuTypeSupport;

import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class OpenClAbiSupport {

    private static final Pattern ALIGNED_PATTERN = Pattern.compile("aligned\\((\\d+)\\)");
    private static final Map<Class<?>, OpenClStructLayout> STRUCT_LAYOUT_CACHE = new ConcurrentHashMap<>();

    private OpenClAbiSupport() {
    }

    static boolean isStructInstance(Object value) {
        return GpuAnnotationSupport.hasAnnotation(value.getClass(), GpuAnnotationSupport.GPU_STRUCT_ANNOTATION_TYPES);
    }

    static boolean isStructArrayInstance(Object value) {
        Class<?> type = value.getClass();
        return type.isArray()
                && type.getComponentType() != null
                && GpuAnnotationSupport.hasAnnotation(type.getComponentType(), GpuAnnotationSupport.GPU_STRUCT_ANNOTATION_TYPES);
    }

    static boolean isVectorArrayInstance(Object value) {
        Class<?> type = value.getClass();
        return type.isArray()
                && type.getComponentType() != null
                && GpuTypeSupport.isSupportedVectorType(type.getComponentType().getName());
    }

    static ByteBuffer packVector(String javaType, Object argument) {
        OpenClValueLayout layout = new OpenClVectorLayout(javaType);
        ByteBuffer buffer = zeroedBuffer(layout.size());
        layout.write(argument, buffer, 0);
        buffer.limit(buffer.capacity());
        buffer.position(0);
        return buffer;
    }

    static OpenClAbiDescriptor describeVectorType(String javaType) {
        return new OpenClVectorLayout(javaType).describe(javaType);
    }

    static String debugVectorType(String javaType) {
        return debugDescriptor(describeVectorType(javaType));
    }

    static ByteBuffer packStruct(Object argument) {
        OpenClStructLayout layout = resolveStructLayout(argument.getClass());
        ByteBuffer buffer = zeroedBuffer(layout.size());
        layout.write(argument, buffer, 0);
        buffer.limit(layout.size());
        buffer.position(0);
        return buffer;
    }

    static OpenClAbiDescriptor describeStructType(Class<?> type) {
        return resolveStructLayout(type).describe(type.getName());
    }

    static String debugStructType(Class<?> type) {
        return debugDescriptor(describeStructType(type));
    }

    static String debugDescriptor(OpenClAbiDescriptor descriptor) {
        StringBuilder builder = new StringBuilder();
        appendDescriptor(builder, descriptor, 0, descriptor.typeName());
        return builder.toString();
    }

    static ByteBuffer packStructArray(Object array) {
        return packPackedArray(array, PackedArrayKind.STRUCT);
    }

    static int structArrayByteSize(Object array) {
        return packedArrayByteSize(array, PackedArrayKind.STRUCT);
    }

    static void unpackStructArray(ByteBuffer buffer, Object array) {
        unpackPackedArray(buffer, array, PackedArrayKind.STRUCT);
    }

    static ByteBuffer packVectorArray(Object array) {
        return packPackedArray(array, PackedArrayKind.VECTOR);
    }

    static int vectorArrayByteSize(Object array) {
        return packedArrayByteSize(array, PackedArrayKind.VECTOR);
    }

    static void unpackVectorArray(ByteBuffer buffer, Object array) {
        unpackPackedArray(buffer, array, PackedArrayKind.VECTOR);
    }

    static OpenClStructLayout resolveStructLayout(Class<?> type) {
        OpenClStructLayout cached = STRUCT_LAYOUT_CACHE.get(type);
        if (cached != null) {
            return cached;
        }

        OpenClStructLayout created = createStructLayout(type);
        OpenClStructLayout existing = STRUCT_LAYOUT_CACHE.putIfAbsent(type, created);
        return existing != null ? existing : created;
    }

    private static OpenClStructLayout createStructLayout(Class<?> type) {
        if (!GpuAnnotationSupport.hasAnnotation(type, GpuAnnotationSupport.GPU_STRUCT_ANNOTATION_TYPES)) {
            throw new IllegalArgumentException(
                    "Unsupported OpenCL struct argument type: "
                            + type.getName()
                            + "; mark the type with @GPUStruct before using it in packed OpenCL ABI marshalling"
            );
        }

        List<String> structAttributes = openClAttributes(type);
        boolean packed = hasPacked(structAttributes);
        int structAlignment = 1;
        int offset = 0;
        List<OpenClFieldLayout> fields = new ArrayList<>();

        for (Field field : type.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) {
                continue;
            }

            field.setAccessible(true);
            OpenClValueLayout valueLayout = resolveLayoutForType(field.getType());
            int fieldAlignment = packed ? 1 : valueLayout.alignment();
            int explicitFieldAlignment = explicitAlignment(openClAttributes(field));
            if (explicitFieldAlignment > 0) {
                fieldAlignment = Math.max(fieldAlignment, explicitFieldAlignment);
            }

            offset = align(offset, fieldAlignment);
            fields.add(new OpenClFieldLayout(field, offset, valueLayout.size(), fieldAlignment, valueLayout));
            offset += valueLayout.size();
            structAlignment = Math.max(structAlignment, fieldAlignment);
        }

        int explicitStructAlignment = explicitAlignment(structAttributes);
        if (explicitStructAlignment > 0) {
            structAlignment = Math.max(structAlignment, explicitStructAlignment);
        }

        return new OpenClStructLayout(align(offset, structAlignment), structAlignment, List.copyOf(fields));
    }

    private static OpenClValueLayout resolveLayoutForType(Class<?> type) {
        if (type.isPrimitive()) {
            return new OpenClScalarLayout(type.getName(), GpuTypeSupport.scalarByteSize(type.getName()));
        }
        if (type.isArray()) {
            throw new IllegalArgumentException(
                    "Unsupported OpenCL field type for ABI marshalling: " + type.getName()
                            + "; array fields inside @GPUStruct are not supported in the current OpenCL ABI"
                            + "; move the array to a kernel parameter or flatten it into scalar/vector fields"
            );
        }
        if (GpuTypeSupport.isSupportedVectorType(type.getName())) {
            return new OpenClVectorLayout(type.getName());
        }
        if (GpuAnnotationSupport.hasAnnotation(type, GpuAnnotationSupport.GPU_STRUCT_ANNOTATION_TYPES)) {
            return resolveStructLayout(type);
        }
        throw new IllegalArgumentException(
                "Unsupported OpenCL field type for ABI marshalling: "
                        + type.getName()
                        + "; use primitive fields, supported vector fields, or nested @GPUStruct values"
        );
    }

    private static ByteBuffer packPackedArray(Object array, PackedArrayKind kind) {
        Class<?> componentType = requirePackedArrayComponentType(array.getClass(), kind);
        OpenClValueLayout layout = resolvePackedArrayLayout(componentType, kind);
        int length = Array.getLength(array);
        ByteBuffer buffer = zeroedBuffer(layout.size() * length);
        for (int index = 0; index < length; index++) {
            Object element = Array.get(array, index);
            if (element != null) {
                layout.write(element, buffer, index * layout.size());
            }
        }
        buffer.limit(layout.size() * length);
        buffer.position(0);
        return buffer;
    }

    private static int packedArrayByteSize(Object array, PackedArrayKind kind) {
        Class<?> componentType = requirePackedArrayComponentType(array.getClass(), kind);
        return resolvePackedArrayLayout(componentType, kind).size() * Array.getLength(array);
    }

    private static void unpackPackedArray(ByteBuffer buffer, Object array, PackedArrayKind kind) {
        Class<?> componentType = requirePackedArrayComponentType(array.getClass(), kind);
        OpenClValueLayout layout = resolvePackedArrayLayout(componentType, kind);
        int length = Array.getLength(array);
        for (int index = 0; index < length; index++) {
            Array.set(array, index, layout.read(buffer, index * layout.size(), componentType));
        }
    }

    private static Class<?> requirePackedArrayComponentType(Class<?> arrayType, PackedArrayKind kind) {
        return switch (kind) {
            case STRUCT -> requireStructArrayComponentType(arrayType);
            case VECTOR -> requireVectorArrayComponentType(arrayType);
        };
    }

    private static Class<?> requireStructArrayComponentType(Class<?> arrayType) {
        Class<?> componentType = arrayType.getComponentType();
        if (componentType == null || !GpuAnnotationSupport.hasAnnotation(componentType, GpuAnnotationSupport.GPU_STRUCT_ANNOTATION_TYPES)) {
            throw new IllegalArgumentException(
                    "Unsupported OpenCL struct array type: "
                            + arrayType.getName()
                            + "; use @GPUStruct[] arrays when marshalling packed struct buffers"
            );
        }
        return componentType;
    }

    private static Class<?> requireVectorArrayComponentType(Class<?> arrayType) {
        Class<?> componentType = arrayType.getComponentType();
        if (componentType == null || !GpuTypeSupport.isSupportedVectorType(componentType.getName())) {
            throw new IllegalArgumentException(
                    "Unsupported OpenCL vector array type: "
                            + arrayType.getName()
                            + "; use arrays of supported GPU vector wrappers such as Float2, Int4, or UInt16"
            );
        }
        return componentType;
    }

    private static OpenClValueLayout resolvePackedArrayLayout(Class<?> componentType, PackedArrayKind kind) {
        return switch (kind) {
            case STRUCT -> resolveStructLayout(componentType);
            case VECTOR -> new OpenClVectorLayout(componentType.getName());
        };
    }

    private static boolean hasPacked(List<String> attributes) {
        for (String attribute : attributes) {
            if ("packed".equals(attribute.strip())) {
                return true;
            }
        }
        return false;
    }

    private static int explicitAlignment(List<String> attributes) {
        int alignment = 0;
        for (String attribute : attributes) {
            Matcher matcher = ALIGNED_PATTERN.matcher(attribute.strip());
            if (matcher.matches()) {
                alignment = Math.max(alignment, Integer.parseInt(matcher.group(1)));
            }
        }
        return alignment;
    }

    private static List<String> openClAttributes(java.lang.reflect.AnnotatedElement element) {
        for (Annotation annotation : element.getAnnotations()) {
            if (!GpuAnnotationSupport.OPENCL_ATTRIBUTES_ANNOTATION_TYPES.contains(annotation.annotationType().getName())) {
                continue;
            }
            try {
                Object value = annotation.annotationType().getMethod("value").invoke(annotation);
                if (value instanceof String[] array) {
                    return List.of(array);
                }
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Failed to read OpenCL attribute metadata from " + element, exception);
            }
        }
        return List.of();
    }

    static int align(int value, int alignment) {
        if (alignment <= 1) {
            return value;
        }
        int remainder = value % alignment;
        return remainder == 0 ? value : value + (alignment - remainder);
    }

    static ByteBuffer zeroedBuffer(int size) {
        ByteBuffer buffer = ByteBuffer.allocateDirect(size).order(ByteOrder.nativeOrder());
        for (int i = 0; i < size; i++) {
            buffer.put((byte) 0);
        }
        buffer.clear();
        return buffer;
    }

    static Field requireField(Class<?> ownerType, String fieldName) {
        Class<?> current = ownerType;
        while (current != null) {
            try {
                Field field = current.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new IllegalArgumentException("Failed to read field '" + fieldName + "' from " + ownerType.getName());
    }

    static Object readFieldValue(Object owner, Field field) {
        try {
            return field.get(owner);
        } catch (IllegalAccessException exception) {
            throw new IllegalArgumentException("Failed to read field '" + field.getName() + "' from " + owner.getClass().getName(), exception);
        }
    }

    static void writeFieldValue(Object owner, Field field, Object value) {
        try {
            field.set(owner, value);
        } catch (IllegalAccessException exception) {
            throw new IllegalArgumentException("Failed to write field '" + field.getName() + "' on " + owner.getClass().getName(), exception);
        }
    }

    static void writeScalar(ByteBuffer buffer, int offset, String scalarType, Object value) {
        switch (scalarType) {
            case "byte" -> buffer.put(offset, ((Number) value).byteValue());
            case "char" -> buffer.putChar(offset, value instanceof Character character ? character : (char) ((Number) value).intValue());
            case "short" -> buffer.putShort(offset, ((Number) value).shortValue());
            case "int" -> buffer.putInt(offset, ((Number) value).intValue());
            case "long" -> buffer.putLong(offset, ((Number) value).longValue());
            case "float" -> buffer.putFloat(offset, ((Number) value).floatValue());
            case "double" -> buffer.putDouble(offset, ((Number) value).doubleValue());
            case "boolean" -> buffer.put(offset, Boolean.TRUE.equals(value) ? (byte) 1 : (byte) 0);
            default -> throw new IllegalArgumentException("Unsupported OpenCL scalar type for marshalling: " + scalarType);
        }
    }

    static void writeZeroScalar(ByteBuffer buffer, int offset, String scalarType) {
        switch (scalarType) {
            case "byte", "boolean" -> buffer.put(offset, (byte) 0);
            case "char" -> buffer.putChar(offset, (char) 0);
            case "short" -> buffer.putShort(offset, (short) 0);
            case "int" -> buffer.putInt(offset, 0);
            case "long" -> buffer.putLong(offset, 0L);
            case "float" -> buffer.putFloat(offset, 0.0f);
            case "double" -> buffer.putDouble(offset, 0.0d);
            default -> throw new IllegalArgumentException("Unsupported OpenCL scalar type for marshalling: " + scalarType);
        }
    }

    static Object readScalar(ByteBuffer buffer, int offset, String scalarType) {
        return switch (scalarType) {
            case "byte" -> buffer.get(offset);
            case "char" -> buffer.getChar(offset);
            case "short" -> buffer.getShort(offset);
            case "int" -> buffer.getInt(offset);
            case "long" -> buffer.getLong(offset);
            case "float" -> buffer.getFloat(offset);
            case "double" -> buffer.getDouble(offset);
            case "boolean" -> buffer.get(offset) != 0;
            default -> throw new IllegalArgumentException("Unsupported OpenCL scalar type for readback: " + scalarType);
        };
    }

    static Object instantiateType(Class<?> type) {
        try {
            Constructor<?> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException exception) {
            throw new IllegalArgumentException(
                    "Type requires an accessible no-arg constructor for OpenCL readback: "
                            + type.getName()
                            + "; add a default constructor so packed struct/vector values can be reconstructed during readback",
                    exception
            );
        }
    }

    private enum PackedArrayKind {
        STRUCT,
        VECTOR
    }

    private static void appendDescriptor(StringBuilder builder, OpenClAbiDescriptor descriptor, int indent, String label) {
        String prefix = "  ".repeat(indent);
        builder.append(prefix)
                .append(label)
                .append(" [")
                .append(descriptor.kind())
                .append("] size=")
                .append(descriptor.size())
                .append(", align=")
                .append(descriptor.alignment())
                .append("\n");
        for (OpenClAbiFieldDescriptor field : descriptor.fields()) {
            builder.append(prefix)
                    .append("  ")
                    .append(field.name())
                    .append(" @")
                    .append(field.offset())
                    .append(" size=")
                    .append(field.size())
                    .append(", align=")
                    .append(field.alignment())
                    .append(", type=")
                    .append(field.typeName())
                    .append("\n");
            if (field.nestedDescriptor() != null && field.nestedDescriptor().fields().size() > 0) {
                appendDescriptor(builder, field.nestedDescriptor(), indent + 2, field.typeName());
            }
        }
    }
}
