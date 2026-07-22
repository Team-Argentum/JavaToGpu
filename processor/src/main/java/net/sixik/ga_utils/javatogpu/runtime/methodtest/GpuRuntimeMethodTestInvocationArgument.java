package net.sixik.ga_utils.javatogpu.runtime.methodtest;

import net.sixik.ga_utils.javatogpu.runtime.*;

import net.sixik.ga_utils.javatogpu.api.GpuAnnotationSupport;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Materialized Java argument preview for one future {@code @GPUTest} invocation.
 */
public record GpuRuntimeMethodTestInvocationArgument(
        String testId,
        int parameterIndex,
        String parameterName,
        String javaType,
        GpuKernelParameterAccess access,
        boolean argumentReady,
        String argumentKind,
        int itemCount,
        Object argumentValue,
        List<String> argumentNumericValues,
        boolean expectedOutputReady,
        String expectedOutputKind,
        int expectedOutputItemCount,
        Object expectedOutputValue,
        List<String> expectedOutputNumericValues,
        String blocker
) {

    public GpuRuntimeMethodTestInvocationArgument {
        testId = normalize(testId, "unknown");
        parameterName = normalize(parameterName, "none");
        javaType = normalize(javaType, "none");
        access = access == null ? GpuKernelParameterAccess.VALUE : access;
        argumentKind = normalize(argumentKind, argumentReady ? "java-value" : "none");
        itemCount = argumentReady ? Math.max(itemCount, 0) : -1;
        argumentValue = cloneValue(argumentValue);
        argumentNumericValues = argumentReady && argumentNumericValues != null ? List.copyOf(argumentNumericValues) : List.of();
        expectedOutputKind = normalize(expectedOutputKind, expectedOutputReady ? "java-value" : "none");
        expectedOutputItemCount = expectedOutputReady ? Math.max(expectedOutputItemCount, 0) : -1;
        expectedOutputValue = cloneValue(expectedOutputValue);
        expectedOutputNumericValues = expectedOutputReady && expectedOutputNumericValues != null
                ? List.copyOf(expectedOutputNumericValues)
                : List.of();
        blocker = normalize(blocker, argumentReady ? "none" : "fixture-invocation-argument-not-ready");
    }

    @Override
    public Object argumentValue() {
        return cloneValue(argumentValue);
    }

    @Override
    public Object expectedOutputValue() {
        return cloneValue(expectedOutputValue);
    }

    public Map<String, String> artifactFields(String prefix) {
        String normalizedPrefix = prefix == null || prefix.isBlank() ? "methodTestInvocationArgument" : prefix.trim();
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        fields.put(normalizedPrefix + ".testId", testId);
        fields.put(normalizedPrefix + ".parameter.index", Integer.toString(parameterIndex));
        fields.put(normalizedPrefix + ".parameter.name", parameterName);
        fields.put(normalizedPrefix + ".javaType", javaType);
        fields.put(normalizedPrefix + ".access", access.name());
        fields.put(normalizedPrefix + ".argument.ready", Boolean.toString(argumentReady));
        fields.put(normalizedPrefix + ".argument.kind", argumentKind);
        fields.put(normalizedPrefix + ".argument.item.count", Integer.toString(itemCount));
        fields.put(normalizedPrefix + ".argument.valueClass", valueClass(argumentValue));
        writePreview(fields, normalizedPrefix + ".argument.numericValue", argumentNumericValues);
        fields.put(normalizedPrefix + ".expectedOutput.ready", Boolean.toString(expectedOutputReady));
        fields.put(normalizedPrefix + ".expectedOutput.kind", expectedOutputKind);
        fields.put(normalizedPrefix + ".expectedOutput.item.count", Integer.toString(expectedOutputItemCount));
        fields.put(normalizedPrefix + ".expectedOutput.valueClass", valueClass(expectedOutputValue));
        writePreview(fields, normalizedPrefix + ".expectedOutput.numericValue", expectedOutputNumericValues);
        fields.put(normalizedPrefix + ".blocker", blocker);
        return Collections.unmodifiableMap(fields);
    }

    static Object cloneValue(Object value) {
        if (value instanceof float[] values) {
            return values.clone();
        }
        if (value instanceof double[] values) {
            return values.clone();
        }
        if (value instanceof int[] values) {
            return values.clone();
        }
        if (value instanceof long[] values) {
            return values.clone();
        }
        if (value instanceof short[] values) {
            return values.clone();
        }
        if (value instanceof byte[] values) {
            return values.clone();
        }
        if (isGpuStructArray(value)) {
            return cloneStructArray(value);
        }
        if (isGpuStructValue(value)) {
            return cloneStruct(value);
        }
        return value;
    }

    private static boolean isGpuStructArray(Object value) {
        if (value == null) {
            return false;
        }
        Class<?> type = value.getClass();
        return type.isArray()
                && type.getComponentType() != null
                && isGpuStructType(type.getComponentType());
    }

    private static boolean isGpuStructValue(Object value) {
        return value != null && isGpuStructType(value.getClass());
    }

    private static boolean isGpuStructType(Class<?> type) {
        return type != null && GpuAnnotationSupport.hasAnnotation(type, GpuAnnotationSupport.GPU_STRUCT_ANNOTATION_TYPES);
    }

    private static Object cloneStructArray(Object value) {
        Class<?> componentType = value.getClass().getComponentType();
        int length = Array.getLength(value);
        Object copy = Array.newInstance(componentType, length);
        for (int index = 0; index < length; index++) {
            Object item = Array.get(value, index);
            Array.set(copy, index, item == null ? null : cloneStruct(item));
        }
        return copy;
    }

    private static Object cloneStruct(Object value) {
        Object copy = newStructInstance(value.getClass());
        for (Field field : fixtureStructFields(value.getClass())) {
            Object fieldValue = readField(value, field);
            if (isGpuStructValue(fieldValue)) {
                fieldValue = cloneStruct(fieldValue);
            }
            writeField(copy, field, fieldValue);
        }
        return copy;
    }

    private static Object newStructInstance(Class<?> structType) {
        try {
            Constructor<?> constructor = structType.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException exception) {
            throw new IllegalArgumentException("Fixture struct type requires an accessible no-arg constructor: "
                    + structType.getName(), exception);
        }
    }

    private static List<Field> fixtureStructFields(Class<?> structType) {
        ArrayList<Field> fields = new ArrayList<>();
        for (Field field : structType.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) {
                continue;
            }
            field.setAccessible(true);
            fields.add(field);
        }
        return List.copyOf(fields);
    }

    private static Object readField(Object source, Field field) {
        try {
            field.setAccessible(true);
            return field.get(source);
        } catch (IllegalAccessException exception) {
            throw new IllegalArgumentException("Fixture struct field is not readable: " + field.getName(), exception);
        }
    }

    private static void writeField(Object target, Field field, Object value) {
        try {
            field.setAccessible(true);
            field.set(target, value);
        } catch (IllegalAccessException exception) {
            throw new IllegalArgumentException("Fixture struct field is not writable: " + field.getName(), exception);
        }
    }

    private static void writePreview(LinkedHashMap<String, String> fields, String prefix, List<String> values) {
        fields.put(prefix + ".count", Integer.toString(values.size()));
        int previewCount = Math.min(values.size(), 4);
        fields.put(prefix + ".preview.count", Integer.toString(previewCount));
        for (int index = 0; index < previewCount; index++) {
            fields.put(prefix + ".preview." + index, values.get(index));
        }
    }

    private static String valueClass(Object value) {
        return value == null ? "none" : value.getClass().getTypeName();
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
