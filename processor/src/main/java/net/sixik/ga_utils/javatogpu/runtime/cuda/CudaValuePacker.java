package net.sixik.ga_utils.javatogpu.runtime.cuda;

import net.sixik.ga_utils.javatogpu.api.GpuAnnotationSupport;
import net.sixik.ga_utils.javatogpu.types.GpuTypeSupport;
import org.lwjgl.system.MemoryUtil;

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

/**
 * CUDA-side host packing helpers for backend-neutral value wrappers.
 */
final class CudaValuePacker {

    private static final Pattern ALIGNED_PATTERN = Pattern.compile("aligned\\((\\d+)\\)");
    private static final Map<Class<?>, StructLayout> STRUCT_LAYOUT_CACHE = new ConcurrentHashMap<>();

    private CudaValuePacker() {
    }

    static boolean isStructArrayInstance(Object value) {
        if (value == null) {
            return false;
        }
        Class<?> type = value.getClass();
        return type.isArray()
                && type.getComponentType() != null
                && GpuAnnotationSupport.hasAnnotation(type.getComponentType(), GpuAnnotationSupport.GPU_STRUCT_ANNOTATION_TYPES);
    }

    static boolean isStructValueInstance(Object value) {
        if (value == null) {
            return false;
        }
        Class<?> type = value.getClass();
        return !type.isArray()
                && GpuAnnotationSupport.hasAnnotation(type, GpuAnnotationSupport.GPU_STRUCT_ANNOTATION_TYPES);
    }

    static boolean structValueCompatible(String declaredType, Object value) {
        if (!isStructValueInstance(value) || declaredType == null || declaredType.endsWith("[]")) {
            return false;
        }
        Class<?> actualType = value.getClass();
        return declaredType.equals(actualType.getName())
                || declaredType.equals(actualType.getSimpleName());
    }

    static long structValueByteSize(Object value) {
        if (!isStructValueInstance(value)) {
            return 0L;
        }
        return resolveStructLayout(value.getClass()).size();
    }

    static ByteBuffer packStructValue(Object value) {
        if (!isStructValueInstance(value)) {
            String typeName = value == null ? "null" : value.getClass().getName();
            throw new IllegalArgumentException("Unsupported CUDA struct VALUE type: " + typeName);
        }
        StructLayout layout = resolveStructLayout(value.getClass());
        ByteBuffer buffer = MemoryUtil.memCalloc(layout.size()).order(ByteOrder.nativeOrder());
        layout.write(value, buffer, 0);
        buffer.limit(buffer.capacity());
        buffer.position(0);
        return buffer;
    }

    static boolean structArrayCompatible(String declaredArrayType, Object value) {
        if (!isStructArrayInstance(value) || declaredArrayType == null || !declaredArrayType.endsWith("[]")) {
            return false;
        }
        String declaredComponent = GpuTypeSupport.componentType(declaredArrayType);
        Class<?> actualComponent = value.getClass().getComponentType();
        return declaredComponent.equals(actualComponent.getName())
                || declaredComponent.equals(actualComponent.getSimpleName());
    }

    static int structArrayLength(Object value) {
        return isStructArrayInstance(value) ? Array.getLength(value) : 0;
    }

    static long structArrayByteSize(Object value) {
        if (!isStructArrayInstance(value)) {
            return 0L;
        }
        return structArrayByteSize(value, structArrayLength(value));
    }

    static long structArrayByteSize(Object value, int elementCount) {
        if (!isStructArrayInstance(value)) {
            return 0L;
        }
        StructLayout layout = resolveStructLayout(value.getClass().getComponentType());
        return (long) layout.size() * Math.max(0, elementCount);
    }

    static int structArrayElementByteSize(Object value) {
        if (!isStructArrayInstance(value)) {
            return 0;
        }
        return resolveStructLayout(value.getClass().getComponentType()).size();
    }

    static int structArrayElementAlignment(Object value) {
        if (!isStructArrayInstance(value)) {
            return 0;
        }
        return resolveStructLayout(value.getClass().getComponentType()).alignment();
    }

    static ByteBuffer packStructArray(Object value) {
        return packStructArray(value, 0, structArrayLength(value));
    }

    static ByteBuffer packStructArray(Object value, int offset, int elementCount) {
        if (!isStructArrayInstance(value)) {
            throw new IllegalArgumentException("Unsupported CUDA struct array type: " + value.getClass().getName());
        }
        StructLayout layout = resolveStructLayout(value.getClass().getComponentType());
        long byteSize = (long) layout.size() * elementCount;
        if (byteSize > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("CUDA struct array is too large to pack on host: " + byteSize);
        }
        ByteBuffer buffer = MemoryUtil.memCalloc((int) byteSize).order(ByteOrder.nativeOrder());
        for (int index = 0; index < elementCount; index++) {
            Object element = Array.get(value, offset + index);
            if (element != null) {
                layout.write(element, buffer, index * layout.size());
            }
        }
        buffer.limit(buffer.capacity());
        buffer.position(0);
        return buffer;
    }

    static void unpackStructArray(ByteBuffer buffer, Object targetArray) {
        unpackStructArray(buffer, targetArray, 0, structArrayLength(targetArray));
    }

    static void unpackStructArray(ByteBuffer buffer, Object targetArray, int offset, int elementCount) {
        if (!isStructArrayInstance(targetArray)) {
            throw new IllegalArgumentException("Unsupported CUDA struct array readback type: " + targetArray.getClass().getName());
        }
        Class<?> targetType = targetArray.getClass().getComponentType();
        StructLayout layout = resolveStructLayout(targetType);
        for (int index = 0; index < elementCount; index++) {
            Array.set(targetArray, offset + index, layout.read(buffer, index * layout.size(), targetType));
        }
    }

    static boolean isVectorArrayInstance(Object value) {
        if (value == null) {
            return false;
        }
        Class<?> type = value.getClass();
        return type.isArray()
                && type.getComponentType() != null
                && GpuTypeSupport.isSupportedVectorType(type.getComponentType().getName());
    }

    static boolean vectorArrayCompatible(String declaredArrayType, Object value) {
        if (!isVectorArrayInstance(value) || declaredArrayType == null || !declaredArrayType.endsWith("[]")) {
            return false;
        }
        String declaredComponent = GpuTypeSupport.componentType(declaredArrayType);
        String actualComponent = value.getClass().getComponentType().getName();
        if (!GpuTypeSupport.isSupportedVectorType(declaredComponent)
                || !GpuTypeSupport.isSupportedVectorType(actualComponent)) {
            return false;
        }
        return GpuTypeSupport.openClVectorTypeName(declaredComponent)
                .equals(GpuTypeSupport.openClVectorTypeName(actualComponent));
    }

    static int vectorArrayLength(Object value) {
        return isVectorArrayInstance(value) ? Array.getLength(value) : 0;
    }

    static long vectorArrayByteSize(String declaredArrayType, int elementCount) {
        String componentType = GpuTypeSupport.componentType(declaredArrayType);
        return (long) Math.max(0, elementCount) * GpuTypeSupport.vectorByteSize(componentType);
    }

    static ByteBuffer packVectorArray(String declaredArrayType, Object value) {
        return packVectorArray(declaredArrayType, value, 0, vectorArrayLength(value));
    }

    static ByteBuffer packVectorArray(String declaredArrayType, Object value, int offset, int elementCount) {
        long byteSize = vectorArrayByteSize(declaredArrayType, elementCount);
        if (byteSize > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("CUDA vector array is too large to pack on host: " + byteSize);
        }
        ByteBuffer buffer = MemoryUtil.memCalloc((int) byteSize).order(ByteOrder.nativeOrder());
        String componentType = GpuTypeSupport.componentType(declaredArrayType);
        int stride = GpuTypeSupport.vectorByteSize(componentType);
        for (int index = 0; index < elementCount; index++) {
            Object element = Array.get(value, offset + index);
            if (element != null) {
                writeVector(componentType, element, buffer, index * stride);
            }
        }
        buffer.limit(buffer.capacity());
        buffer.position(0);
        return buffer;
    }

    static void unpackVectorArray(ByteBuffer buffer, String declaredArrayType, Object targetArray) {
        unpackVectorArray(buffer, declaredArrayType, targetArray, 0, vectorArrayLength(targetArray));
    }

    static void unpackVectorArray(
            ByteBuffer buffer,
            String declaredArrayType,
            Object targetArray,
            int offset,
            int elementCount
    ) {
        String componentType = GpuTypeSupport.componentType(declaredArrayType);
        int stride = GpuTypeSupport.vectorByteSize(componentType);
        Class<?> targetType = targetArray.getClass().getComponentType();
        for (int index = 0; index < elementCount; index++) {
            Object element = instantiate(targetType);
            readVector(componentType, buffer, index * stride, element);
            Array.set(targetArray, offset + index, element);
        }
    }

    private static void writeVector(String vectorType, Object value, ByteBuffer buffer, int offset) {
        String scalarType = GpuTypeSupport.vectorComponentType(vectorType);
        int scalarSize = GpuTypeSupport.scalarByteSize(scalarType);
        int cursor = offset;
        for (String fieldName : GpuTypeSupport.vectorFieldNames(vectorType)) {
            writeScalar(buffer, cursor, scalarType, readField(value, fieldName));
            cursor += scalarSize;
        }
        for (int i = GpuTypeSupport.vectorWidth(vectorType); i < GpuTypeSupport.vectorStorageWidth(vectorType); i++) {
            writeZeroScalar(buffer, cursor, scalarType);
            cursor += scalarSize;
        }
    }

    private static void readVector(String vectorType, ByteBuffer buffer, int offset, Object target) {
        String scalarType = GpuTypeSupport.vectorComponentType(vectorType);
        int scalarSize = GpuTypeSupport.scalarByteSize(scalarType);
        int cursor = offset;
        for (String fieldName : GpuTypeSupport.vectorFieldNames(vectorType)) {
            writeField(target, fieldName, readScalar(buffer, cursor, scalarType));
            cursor += scalarSize;
        }
    }

    private static StructLayout resolveStructLayout(Class<?> type) {
        StructLayout cached = STRUCT_LAYOUT_CACHE.get(type);
        if (cached != null) {
            return cached;
        }
        StructLayout created = createStructLayout(type);
        StructLayout existing = STRUCT_LAYOUT_CACHE.putIfAbsent(type, created);
        return existing == null ? created : existing;
    }

    private static StructLayout createStructLayout(Class<?> type) {
        if (!GpuAnnotationSupport.hasAnnotation(type, GpuAnnotationSupport.GPU_STRUCT_ANNOTATION_TYPES)) {
            throw new IllegalArgumentException(
                    "Unsupported CUDA struct argument type: "
                            + type.getName()
                            + "; mark the type with @GPUStruct before using it in packed CUDA ABI marshalling"
            );
        }

        List<String> structAttributes = openClAttributes(type);
        boolean packed = hasPacked(structAttributes);
        int structAlignment = 1;
        int offset = 0;
        ArrayList<FieldLayout> fields = new ArrayList<>();

        for (Field field : type.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) {
                continue;
            }
            field.setAccessible(true);
            ValueLayout valueLayout = resolveLayoutForType(field.getType());
            int fieldAlignment = packed ? 1 : valueLayout.alignment();
            int explicitFieldAlignment = explicitAlignment(openClAttributes(field));
            if (explicitFieldAlignment > 0) {
                fieldAlignment = Math.max(fieldAlignment, explicitFieldAlignment);
            }
            offset = align(offset, fieldAlignment);
            fields.add(new FieldLayout(field, offset, fieldAlignment, valueLayout));
            offset += valueLayout.size();
            structAlignment = Math.max(structAlignment, fieldAlignment);
        }

        int explicitStructAlignment = explicitAlignment(structAttributes);
        if (explicitStructAlignment > 0) {
            structAlignment = Math.max(structAlignment, explicitStructAlignment);
        }
        return new StructLayout(align(offset, structAlignment), structAlignment, List.copyOf(fields));
    }

    private static ValueLayout resolveLayoutForType(Class<?> type) {
        if (type.isPrimitive()) {
            return new ScalarLayout(type.getName(), GpuTypeSupport.scalarByteSize(type.getName()));
        }
        if (type.isArray()) {
            throw new IllegalArgumentException(
                    "Unsupported CUDA field type for ABI marshalling: "
                            + type.getName()
                            + "; array fields inside @GPUStruct are not supported in the current CUDA ABI"
            );
        }
        if (GpuTypeSupport.isSupportedVectorType(type.getName())) {
            return new VectorLayout(type.getName());
        }
        if (GpuAnnotationSupport.hasAnnotation(type, GpuAnnotationSupport.GPU_STRUCT_ANNOTATION_TYPES)) {
            return resolveStructLayout(type);
        }
        throw new IllegalArgumentException(
                "Unsupported CUDA field type for ABI marshalling: "
                        + type.getName()
                        + "; use primitive fields, supported vector fields, or nested @GPUStruct values"
        );
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

    private static int align(int value, int alignment) {
        if (alignment <= 1) {
            return value;
        }
        int remainder = value % alignment;
        return remainder == 0 ? value : value + (alignment - remainder);
    }

    private interface ValueLayout {
        int size();

        int alignment();

        void write(Object value, ByteBuffer buffer, int offset);

        Object read(ByteBuffer buffer, int offset, Class<?> targetType);
    }

    private record ScalarLayout(String javaType, int size) implements ValueLayout {
        @Override
        public int alignment() {
            return size;
        }

        @Override
        public void write(Object value, ByteBuffer buffer, int offset) {
            writeScalar(buffer, offset, javaType, value);
        }

        @Override
        public Object read(ByteBuffer buffer, int offset, Class<?> targetType) {
            return readScalar(buffer, offset, javaType);
        }
    }

    private static final class VectorLayout implements ValueLayout {
        private final String javaType;

        private VectorLayout(String javaType) {
            this.javaType = javaType;
        }

        @Override
        public int size() {
            return GpuTypeSupport.vectorByteSize(javaType);
        }

        @Override
        public int alignment() {
            return size();
        }

        @Override
        public void write(Object value, ByteBuffer buffer, int offset) {
            writeVector(javaType, value, buffer, offset);
        }

        @Override
        public Object read(ByteBuffer buffer, int offset, Class<?> targetType) {
            Object instance = instantiate(targetType);
            readVector(javaType, buffer, offset, instance);
            return instance;
        }
    }

    private record StructLayout(int size, int alignment, List<FieldLayout> fields) implements ValueLayout {
        @Override
        public void write(Object value, ByteBuffer buffer, int offset) {
            for (FieldLayout field : fields) {
                Object fieldValue = readFieldValue(value, field.field());
                if (fieldValue == null) {
                    throw new IllegalArgumentException(
                            "Null @GPUStruct field is not supported for CUDA marshalling: "
                                    + field.field().getName()
                                    + "; initialize nested structs before launching the kernel"
                    );
                }
                field.layout().write(fieldValue, buffer, offset + field.offset());
            }
        }

        @Override
        public Object read(ByteBuffer buffer, int offset, Class<?> targetType) {
            Object instance = instantiate(targetType);
            for (FieldLayout field : fields) {
                Object fieldValue = field.layout().read(buffer, offset + field.offset(), field.field().getType());
                writeFieldValue(instance, field.field(), fieldValue);
            }
            return instance;
        }
    }

    private record FieldLayout(Field field, int offset, int alignment, ValueLayout layout) {
    }

    private static Object readField(Object value, String fieldName) {
        try {
            return field(value.getClass(), fieldName).get(value);
        } catch (IllegalAccessException exception) {
            throw new IllegalArgumentException("Failed to read CUDA vector field " + fieldName, exception);
        }
    }

    private static Object readFieldValue(Object value, Field field) {
        try {
            return field.get(value);
        } catch (IllegalAccessException exception) {
            throw new IllegalArgumentException("Failed to read CUDA struct field " + field.getName(), exception);
        }
    }

    private static void writeFieldValue(Object target, Field field, Object value) {
        try {
            field.set(target, value);
        } catch (IllegalAccessException exception) {
            throw new IllegalArgumentException("Failed to write CUDA struct field " + field.getName(), exception);
        }
    }

    private static void writeField(Object target, String fieldName, Object value) {
        try {
            field(target.getClass(), fieldName).set(target, value);
        } catch (IllegalAccessException exception) {
            throw new IllegalArgumentException("Failed to write CUDA vector field " + fieldName, exception);
        }
    }

    private static Field field(Class<?> type, String fieldName) {
        Class<?> current = type;
        while (current != null) {
            try {
                Field field = current.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new IllegalArgumentException("CUDA vector field not found: " + type.getName() + "." + fieldName);
    }

    private static Object instantiate(Class<?> type) {
        try {
            Constructor<?> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException exception) {
            throw new IllegalArgumentException("CUDA vector type needs a no-arg constructor for readback: " + type.getName(), exception);
        }
    }

    private static void writeScalar(ByteBuffer buffer, int offset, String scalarType, Object value) {
        switch (scalarType) {
            case "byte" -> buffer.put(offset, ((Number) value).byteValue());
            case "boolean" -> buffer.put(offset, Boolean.TRUE.equals(value) ? (byte) 1 : (byte) 0);
            case "char" -> buffer.putChar(offset, value instanceof Character character ? character : (char) ((Number) value).intValue());
            case "short" -> buffer.putShort(offset, ((Number) value).shortValue());
            case "int" -> buffer.putInt(offset, ((Number) value).intValue());
            case "long" -> buffer.putLong(offset, ((Number) value).longValue());
            case "float" -> buffer.putFloat(offset, ((Number) value).floatValue());
            case "double" -> buffer.putDouble(offset, ((Number) value).doubleValue());
            default -> throw new IllegalArgumentException("Unsupported CUDA vector scalar type: " + scalarType);
        }
    }

    private static void writeZeroScalar(ByteBuffer buffer, int offset, String scalarType) {
        switch (scalarType) {
            case "byte", "boolean" -> buffer.put(offset, (byte) 0);
            case "char" -> buffer.putChar(offset, (char) 0);
            case "short" -> buffer.putShort(offset, (short) 0);
            case "int" -> buffer.putInt(offset, 0);
            case "long" -> buffer.putLong(offset, 0L);
            case "float" -> buffer.putFloat(offset, 0.0f);
            case "double" -> buffer.putDouble(offset, 0.0d);
            default -> throw new IllegalArgumentException("Unsupported CUDA vector scalar type: " + scalarType);
        }
    }

    private static Object readScalar(ByteBuffer buffer, int offset, String scalarType) {
        return switch (scalarType) {
            case "byte" -> buffer.get(offset);
            case "boolean" -> buffer.get(offset) != 0;
            case "char" -> buffer.getChar(offset);
            case "short" -> buffer.getShort(offset);
            case "int" -> buffer.getInt(offset);
            case "long" -> buffer.getLong(offset);
            case "float" -> buffer.getFloat(offset);
            case "double" -> buffer.getDouble(offset);
            default -> throw new IllegalArgumentException("Unsupported CUDA vector scalar type: " + scalarType);
        };
    }
}
