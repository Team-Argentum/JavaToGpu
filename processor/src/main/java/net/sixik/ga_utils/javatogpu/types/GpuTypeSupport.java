package net.sixik.ga_utils.javatogpu.types;

import net.sixik.ga_utils.javatogpu.api.GpuAnnotationSupport;
import net.sixik.ga_utils.javatogpu.api.annotations.GPUPointerType;
import net.sixik.ga_utils.javatogpu.api.annotations.GPUScalarAliasType;
import net.sixik.ga_utils.javatogpu.api.annotations.GPUVectorType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class GpuTypeSupport {

    private static final String API_PACKAGE_PREFIX = "net.sixik.ga_utils.javatogpu.api.";

    private static final String POINTER_REFERENCE_SUFFIX = "&";

    private static final Set<String> SUPPORTED_SCALAR_TYPES = Set.of(
            "byte", "short", "int", "long", "float", "double", "boolean", "char"
    );

    private static final Set<String> SUPPORTED_PARAMETER_SCALAR_TYPES = Set.of(
            "byte", "short", "int", "long", "float", "double", "char"
    );

    private static final Map<String, ScalarAliasDescriptor> SUPPORTED_SCALAR_ALIASES = new ConcurrentHashMap<>();

    private static final Map<String, PointerDescriptor> SUPPORTED_POINTER_TYPES = new ConcurrentHashMap<>();

    private static final Map<String, VectorDescriptor> SUPPORTED_VECTOR_TYPES = new ConcurrentHashMap<>();

    private static final Map<String, String> SUPPORTED_IMAGE_AND_SAMPLER_TYPES = Map.ofEntries(
            Map.entry("Image1DReadOnly", "read_only image1d_t"),
            Map.entry("Image1DWriteOnly", "write_only image1d_t"),
            Map.entry("Image1DArrayReadOnly", "read_only image1d_array_t"),
            Map.entry("Image1DArrayWriteOnly", "write_only image1d_array_t"),
            Map.entry("Image1DBufferReadOnly", "read_only image1d_buffer_t"),
            Map.entry("Image1DBufferWriteOnly", "write_only image1d_buffer_t"),
            Map.entry("Image2DReadOnly", "read_only image2d_t"),
            Map.entry("Image2DWriteOnly", "write_only image2d_t"),
            Map.entry("Image2DMipmappedReadOnly", "read_only image2d_t"),
            Map.entry("Image2DMipmappedWriteOnly", "write_only image2d_t"),
            Map.entry("Image2DMsaaReadOnly", "read_only image2d_msaa_t"),
            Map.entry("Image2DMsaaWriteOnly", "write_only image2d_msaa_t"),
            Map.entry("Image2DArrayReadOnly", "read_only image2d_array_t"),
            Map.entry("Image2DArrayWriteOnly", "write_only image2d_array_t"),
            Map.entry("Image3DReadOnly", "read_only image3d_t"),
            Map.entry("Image3DWriteOnly", "write_only image3d_t"),
            Map.entry("Sampler", "sampler_t")
    );

    private GpuTypeSupport() {
    }

    public static void registerAnnotatedPointerType(Class<?> pointerType) {
        if (pointerType == null) {
            throw new IllegalArgumentException("pointerType cannot be null");
        }
        GPUPointerType annotation = pointerType.getAnnotation(GPUPointerType.class);
        if (annotation == null) {
            throw new IllegalArgumentException("Type is not annotated with @GPUPointerType: " + pointerType.getName());
        }

        PointerDescriptor descriptor = new PointerDescriptor(annotation.valueType());
        registerPointerAlias(pointerType.getSimpleName(), descriptor);
        registerPointerAlias(pointerType.getName(), descriptor);
    }

    public static void registerAnnotatedScalarAliasType(Class<?> scalarAliasType) {
        if (scalarAliasType == null) {
            throw new IllegalArgumentException("scalarAliasType cannot be null");
        }
        GPUScalarAliasType annotation = scalarAliasType.getAnnotation(GPUScalarAliasType.class);
        if (annotation == null) {
            throw new IllegalArgumentException("Type is not annotated with @GPUScalarAliasType: " + scalarAliasType.getName());
        }

        ScalarAliasDescriptor descriptor = new ScalarAliasDescriptor(annotation.backendType(), annotation.valueType());
        registerScalarAlias(scalarAliasType.getSimpleName(), descriptor);
        registerScalarAlias(scalarAliasType.getName(), descriptor);
    }

    public static void registerAnnotatedVectorType(Class<?> vectorType) {
        if (vectorType == null) {
            throw new IllegalArgumentException("vectorType cannot be null");
        }
        GPUVectorType annotation = vectorType.getAnnotation(GPUVectorType.class);
        if (annotation == null) {
            throw new IllegalArgumentException("Type is not annotated with @GPUVectorType: " + vectorType.getName());
        }

        List<String> fieldNames = List.of(annotation.fields());
        if (fieldNames.isEmpty()) {
            throw new IllegalArgumentException("@GPUVectorType fields() must not be empty: " + vectorType.getName());
        }

        int storageWidth = annotation.storageWidth() > 0
                ? annotation.storageWidth()
                : defaultVectorStorageWidth(fieldNames.size());
        if (storageWidth < fieldNames.size()) {
            throw new IllegalArgumentException(
                    "@GPUVectorType storageWidth() cannot be smaller than the declared field count: " + vectorType.getName()
            );
        }

        VectorDescriptor descriptor = new VectorDescriptor(
                annotation.openClType(),
                annotation.componentType(),
                fieldNames,
                storageWidth
        );
        registerVectorAlias(vectorType.getSimpleName(), descriptor);
        registerVectorAlias(vectorType.getName(), descriptor);
    }

    public static boolean isSupportedVectorClassName(String className) {
        if (className == null || className.isBlank()) {
            return false;
        }
        return vectorDescriptor(className) != null;
    }

    public static boolean isSupportedPointerClassName(String className) {
        if (className == null || className.isBlank()) {
            return false;
        }
        return pointerDescriptor(className) != null;
    }

    public static boolean isSupportedScalarAliasClassName(String className) {
        if (className == null || className.isBlank()) {
            return false;
        }
        return scalarAliasDescriptor(className) != null;
    }

    public static boolean isSupportedScalarType(String javaType) {
        String declaredType = declaredType(javaType);
        return SUPPORTED_SCALAR_TYPES.contains(declaredType) || isSupportedScalarAliasType(declaredType);
    }

    public static boolean isIntegralScalarType(String javaType) {
        String declaredType = declaredType(javaType);
        if ("byte".equals(declaredType)
                || "char".equals(declaredType)
                || "short".equals(declaredType)
                || "int".equals(declaredType)
                || "long".equals(declaredType)) {
            return true;
        }
        return isSupportedScalarAliasType(declaredType)
                && isIntegralScalarType(scalarAliasValueType(declaredType));
    }

    public static boolean isSupportedScalarAliasType(String javaType) {
        return scalarAliasDescriptor(javaType) != null;
    }

    public static String scalarAliasValueType(String javaType) {
        ScalarAliasDescriptor descriptor = scalarAliasDescriptor(javaType);
        if (descriptor == null) {
            throw new IllegalArgumentException("Unsupported scalar alias type: " + javaType);
        }
        return descriptor.valueType();
    }

    public static String openClScalarAliasTypeName(String javaType) {
        ScalarAliasDescriptor descriptor = scalarAliasDescriptor(javaType);
        if (descriptor == null) {
            throw new IllegalArgumentException("Unsupported scalar alias type: " + javaType);
        }
        return descriptor.openClTypeName();
    }

    public static boolean isFloatingScalarType(String javaType) {
        String declaredType = declaredType(javaType);
        if ("float".equals(declaredType) || "double".equals(declaredType)) {
            return true;
        }
        return isSupportedScalarAliasType(declaredType)
                && isFloatingScalarType(scalarAliasValueType(declaredType));
    }

    public static boolean isSupportedArrayType(String javaType) {
        String declaredType = declaredType(javaType);
        return declaredType.endsWith("[]") && isSupportedParameterScalarType(componentType(declaredType));
    }

    public static boolean isArrayType(String javaType) {
        String declaredType = declaredType(javaType);
        return declaredType != null && declaredType.endsWith("[]");
    }

    public static boolean isSupportedKernelParameterType(String javaType) {
        return isSupportedParameterScalarType(javaType)
                || isSupportedArrayType(javaType)
                || isSupportedVectorType(javaType)
                || isSupportedImageOrSamplerType(javaType);
    }

    public static boolean isSupportedHelperParameterType(String javaType) {
        return isSupportedKernelParameterType(javaType)
                || isSupportedPointerType(javaType)
                || isSupportedVectorType(javaType)
                || isSupportedImageOrSamplerType(javaType);
    }

    public static boolean isHelperArgumentCompatible(String actualType, String parameterType) {
        actualType = declaredType(actualType);
        parameterType = declaredType(parameterType);

        if (actualType == null || parameterType == null) {
            return false;
        }
        if (actualType.equals(parameterType) || sameVectorType(actualType, parameterType)) {
            return true;
        }
        if (isSupportedScalarAliasType(actualType) || isSupportedScalarAliasType(parameterType)) {
            return false;
        }
        if (isSupportedVectorType(actualType) || isSupportedVectorType(parameterType)) {
            return false;
        }
        if (isSupportedPointerType(actualType) || isSupportedPointerType(parameterType)) {
            return false;
        }
        if (isSupportedArrayType(actualType) || isSupportedArrayType(parameterType)) {
            return false;
        }
        if ("boolean".equals(actualType) || "boolean".equals(parameterType)) {
            return false;
        }
        if (!isSupportedScalarType(actualType) || !isSupportedScalarType(parameterType)) {
            return false;
        }

        return helperWideningRank(actualType) <= helperWideningRank(parameterType);
    }

    public static int helperCompatibilityScore(String actualType, String parameterType) {
        actualType = declaredType(actualType);
        parameterType = declaredType(parameterType);
        if (actualType == null || parameterType == null) {
            return Integer.MAX_VALUE;
        }
        if (actualType.equals(parameterType) || sameVectorType(actualType, parameterType)) {
            return 0;
        }
        if (!isHelperArgumentCompatible(actualType, parameterType)) {
            return Integer.MAX_VALUE;
        }
        return helperWideningRank(parameterType) - helperWideningRank(actualType);
    }

    public static boolean isSupportedLocalType(String javaType) {
        return isSupportedScalarType(javaType) || isSupportedPointerType(javaType) || isSupportedVectorType(javaType);
    }

    public static boolean isGlobalParameterCompatible(String javaType) {
        return isSupportedArrayType(javaType);
    }

    public static boolean isSupportedImageOrSamplerType(String javaType) {
        return SUPPORTED_IMAGE_AND_SAMPLER_TYPES.containsKey(simpleTypeName(declaredType(javaType)));
    }

    public static String openClImageOrSamplerTypeName(String javaType) {
        String openClType = SUPPORTED_IMAGE_AND_SAMPLER_TYPES.get(simpleTypeName(declaredType(javaType)));
        if (openClType == null) {
            throw new IllegalArgumentException("Unsupported image/sampler type: " + javaType);
        }
        return openClType;
    }

    public static boolean isSupportedPointerType(String javaType) {
        return pointerDescriptor(javaType) != null;
    }

    public static boolean isSupportedVectorType(String javaType) {
        return vectorDescriptor(javaType) != null;
    }

    public static String vectorComponentType(String javaType, String componentName) {
        VectorDescriptor descriptor = vectorDescriptor(javaType);
        if (descriptor == null || !descriptor.fieldNames().contains(componentName)) {
            return null;
        }
        return descriptor.componentType();
    }

    public static String vectorComponentType(String javaType) {
        VectorDescriptor descriptor = vectorDescriptor(javaType);
        if (descriptor == null) {
            throw new IllegalArgumentException("Unsupported vector type: " + javaType);
        }
        return descriptor.componentType();
    }

    public static int vectorWidth(String javaType) {
        VectorDescriptor descriptor = vectorDescriptor(javaType);
        if (descriptor == null) {
            throw new IllegalArgumentException("Unsupported vector type: " + javaType);
        }
        return descriptor.fieldNames().size();
    }

    public static int vectorStorageWidth(String javaType) {
        VectorDescriptor descriptor = vectorDescriptor(javaType);
        if (descriptor == null) {
            throw new IllegalArgumentException("Unsupported vector type: " + javaType);
        }
        return descriptor.storageWidth();
    }

    public static String openClVectorTypeName(String javaType) {
        VectorDescriptor descriptor = vectorDescriptor(javaType);
        if (descriptor == null) {
            throw new IllegalArgumentException("Unsupported vector type: " + javaType);
        }
        return descriptor.openClTypeName();
    }

    public static List<String> vectorFieldNames(String javaType) {
        VectorDescriptor descriptor = vectorDescriptor(javaType);
        if (descriptor == null) {
            throw new IllegalArgumentException("Unsupported vector type: " + javaType);
        }
        return descriptor.fieldNames();
    }

    public static int vectorByteSize(String javaType) {
        return vectorStorageWidth(javaType) * scalarByteSize(vectorComponentType(javaType));
    }

    public static String pointerValueType(String javaType) {
        PointerDescriptor descriptor = pointerDescriptor(javaType);
        if (descriptor == null) {
            throw new IllegalArgumentException("Unsupported pointer type: " + javaType);
        }
        return descriptor.valueType();
    }

    public static String parameterStorageType(String javaType) {
        return isSupportedPointerType(javaType) ? declaredType(javaType) + POINTER_REFERENCE_SUFFIX : javaType;
    }

    public static boolean isPointerReferenceStorage(String javaType) {
        return javaType != null
                && javaType.endsWith(POINTER_REFERENCE_SUFFIX)
                && isSupportedPointerType(javaType.substring(0, javaType.length() - POINTER_REFERENCE_SUFFIX.length()));
    }

    public static String declaredType(String javaType) {
        if (javaType == null) {
            return null;
        }
        if (isPointerReferenceStorageSuffix(javaType)) {
            return javaType.substring(0, javaType.length() - POINTER_REFERENCE_SUFFIX.length());
        }
        return javaType;
    }

    public static String componentType(String arrayType) {
        if (!arrayType.endsWith("[]")) {
            throw new IllegalArgumentException("Not an array type: " + arrayType);
        }
        return arrayType.substring(0, arrayType.length() - 2);
    }

    public static String simpleTypeName(String javaType) {
        int separatorIndex = javaType.lastIndexOf('.');
        if (separatorIndex < 0) {
            return javaType;
        }
        return javaType.substring(separatorIndex + 1);
    }

    public static int scalarByteSize(String javaType) {
        String declaredType = declaredType(javaType);
        if (isSupportedScalarAliasType(declaredType)) {
            return scalarByteSize(scalarAliasValueType(declaredType));
        }
        return switch (declaredType) {
            case "byte", "boolean" -> Byte.BYTES;
            case "short", "char" -> Short.BYTES;
            case "int", "float" -> Integer.BYTES;
            case "long", "double" -> Long.BYTES;
            default -> throw new IllegalArgumentException("Unsupported scalar type size lookup: " + javaType);
        };
    }

    private static boolean isSupportedParameterScalarType(String javaType) {
        String declaredType = declaredType(javaType);
        return SUPPORTED_PARAMETER_SCALAR_TYPES.contains(declaredType) || isSupportedScalarAliasType(declaredType);
    }

    private static boolean isPointerReferenceStorageSuffix(String javaType) {
        return javaType.endsWith(POINTER_REFERENCE_SUFFIX);
    }

    private static VectorDescriptor vectorDescriptor(String javaType) {
        String declaredType = declaredType(javaType);
        if (declaredType == null) {
            return null;
        }
        VectorDescriptor descriptor = SUPPORTED_VECTOR_TYPES.get(declaredType);
        if (descriptor != null) {
            return descriptor;
        }

        descriptor = SUPPORTED_VECTOR_TYPES.get(simpleTypeName(declaredType));
        if (descriptor != null) {
            return descriptor;
        }

        tryRegisterAnnotatedVectorType(declaredType);
        descriptor = SUPPORTED_VECTOR_TYPES.get(declaredType);
        if (descriptor != null) {
            return descriptor;
        }
        return SUPPORTED_VECTOR_TYPES.get(simpleTypeName(declaredType));
    }

    private static PointerDescriptor pointerDescriptor(String javaType) {
        String declaredType = declaredType(javaType);
        if (declaredType == null) {
            return null;
        }
        PointerDescriptor descriptor = SUPPORTED_POINTER_TYPES.get(declaredType);
        if (descriptor != null) {
            return descriptor;
        }

        descriptor = SUPPORTED_POINTER_TYPES.get(simpleTypeName(declaredType));
        if (descriptor != null) {
            return descriptor;
        }

        tryRegisterAnnotatedPointerType(declaredType);
        descriptor = SUPPORTED_POINTER_TYPES.get(declaredType);
        if (descriptor != null) {
            return descriptor;
        }
        return SUPPORTED_POINTER_TYPES.get(simpleTypeName(declaredType));
    }

    private static ScalarAliasDescriptor scalarAliasDescriptor(String javaType) {
        String declaredType = declaredType(javaType);
        if (declaredType == null) {
            return null;
        }
        ScalarAliasDescriptor descriptor = SUPPORTED_SCALAR_ALIASES.get(declaredType);
        if (descriptor != null) {
            return descriptor;
        }

        descriptor = SUPPORTED_SCALAR_ALIASES.get(simpleTypeName(declaredType));
        if (descriptor != null) {
            return descriptor;
        }

        tryRegisterAnnotatedScalarAliasType(declaredType);
        descriptor = SUPPORTED_SCALAR_ALIASES.get(declaredType);
        if (descriptor != null) {
            return descriptor;
        }
        return SUPPORTED_SCALAR_ALIASES.get(simpleTypeName(declaredType));
    }

    private static void registerVectorAlias(String alias, VectorDescriptor descriptor) {
        if (alias == null || alias.isBlank()) {
            return;
        }
        SUPPORTED_VECTOR_TYPES.putIfAbsent(alias, descriptor);
    }

    private static void registerPointerAlias(String alias, PointerDescriptor descriptor) {
        if (alias == null || alias.isBlank()) {
            return;
        }
        SUPPORTED_POINTER_TYPES.putIfAbsent(alias, descriptor);
    }

    private static void registerScalarAlias(String alias, ScalarAliasDescriptor descriptor) {
        if (alias == null || alias.isBlank()) {
            return;
        }
        SUPPORTED_SCALAR_ALIASES.putIfAbsent(alias, descriptor);
    }

    private static void tryRegisterAnnotatedVectorType(String declaredType) {
        for (String candidate : candidateAnnotatedTypeClassNames(declaredType)) {
            try {
                Class<?> type = Class.forName(candidate, false, GpuTypeSupport.class.getClassLoader());
                if (GpuAnnotationSupport.hasAnnotation(type, GpuAnnotationSupport.GPU_VECTOR_TYPE_ANNOTATION_TYPES)) {
                    registerAnnotatedVectorType(type);
                }
            } catch (ClassNotFoundException ignored) {
                // Best effort lookup for optional vector extensions.
            }
        }
    }

    private static void tryRegisterAnnotatedPointerType(String declaredType) {
        for (String candidate : candidateAnnotatedTypeClassNames(declaredType)) {
            try {
                Class<?> type = Class.forName(candidate, false, GpuTypeSupport.class.getClassLoader());
                if (GpuAnnotationSupport.hasAnnotation(type, GpuAnnotationSupport.GPU_POINTER_TYPE_ANNOTATION_TYPES)) {
                    registerAnnotatedPointerType(type);
                }
            } catch (ClassNotFoundException ignored) {
                // Best effort lookup for optional pointer extensions.
            }
        }
    }

    private static void tryRegisterAnnotatedScalarAliasType(String declaredType) {
        for (String candidate : candidateAnnotatedTypeClassNames(declaredType)) {
            try {
                Class<?> type = Class.forName(candidate, false, GpuTypeSupport.class.getClassLoader());
                if (GpuAnnotationSupport.hasAnnotation(type, GpuAnnotationSupport.GPU_SCALAR_ALIAS_TYPE_ANNOTATION_TYPES)) {
                    registerAnnotatedScalarAliasType(type);
                }
            } catch (ClassNotFoundException ignored) {
                // Best effort lookup for optional scalar alias extensions.
            }
        }
    }

    private static List<String> candidateAnnotatedTypeClassNames(String declaredType) {
        List<String> candidates = new ArrayList<>();
        candidates.add(declaredType);
        if (!declaredType.contains(".")) {
            candidates.add(API_PACKAGE_PREFIX + declaredType);
        }
        return candidates;
    }

    private static int defaultVectorStorageWidth(int width) {
        return width == 3 ? 4 : width;
    }

    private static boolean sameVectorType(String leftType, String rightType) {
        if (!isSupportedVectorType(leftType) || !isSupportedVectorType(rightType)) {
            return false;
        }
        return openClVectorTypeName(leftType).equals(openClVectorTypeName(rightType));
    }

    private static int helperWideningRank(String javaType) {
        return switch (javaType) {
            case "byte" -> 0;
            case "char", "short" -> 1;
            case "int" -> 2;
            case "long" -> 3;
            case "float" -> 4;
            case "double" -> 5;
            default -> Integer.MAX_VALUE;
        };
    }

    private record VectorDescriptor(
            String openClTypeName,
            String componentType,
            List<String> fieldNames,
            int storageWidth
    ) {
    }

    private record PointerDescriptor(String valueType) {
    }

    private record ScalarAliasDescriptor(
            String openClTypeName,
            String valueType
    ) {
    }
}
