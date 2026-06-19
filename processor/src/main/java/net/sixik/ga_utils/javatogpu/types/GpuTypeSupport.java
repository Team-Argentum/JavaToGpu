package net.sixik.ga_utils.javatogpu.types;

import java.util.List;
import java.util.Map;
import java.util.Set;

public final class GpuTypeSupport {

    private static final String POINTER_REFERENCE_SUFFIX = "&";

    private static final Set<String> SUPPORTED_SCALAR_TYPES = Set.of(
            "byte", "short", "int", "long", "float", "double", "boolean"
    );

    private static final Set<String> SUPPORTED_PARAMETER_SCALAR_TYPES = Set.of(
            "byte", "short", "int", "long", "float", "double"
    );

    private static final Map<String, String> SUPPORTED_POINTER_TYPES = Map.of(
            "BytePtr", "byte",
            "ShortPtr", "short",
            "IntPtr", "int",
            "LongPtr", "long",
            "FloatPtr", "float",
            "DoublePtr", "double"
    );

    private static final Map<String, VectorDescriptor> SUPPORTED_VECTOR_TYPES = Map.ofEntries(
            Map.entry("Float2", new VectorDescriptor("float2", "float", List.of("x", "y"))),
            Map.entry("Float3", new VectorDescriptor("float3", "float", List.of("x", "y", "z"))),
            Map.entry("Float4", new VectorDescriptor("float4", "float", List.of("x", "y", "z", "w"))),
            Map.entry("Int2", new VectorDescriptor("int2", "int", List.of("x", "y"))),
            Map.entry("Int3", new VectorDescriptor("int3", "int", List.of("x", "y", "z"))),
            Map.entry("Int4", new VectorDescriptor("int4", "int", List.of("x", "y", "z", "w"))),
            Map.entry("Long2", new VectorDescriptor("long2", "long", List.of("x", "y"))),
            Map.entry("Long3", new VectorDescriptor("long3", "long", List.of("x", "y", "z"))),
            Map.entry("Long4", new VectorDescriptor("long4", "long", List.of("x", "y", "z", "w"))),
            Map.entry("Double2", new VectorDescriptor("double2", "double", List.of("x", "y"))),
            Map.entry("Double3", new VectorDescriptor("double3", "double", List.of("x", "y", "z"))),
            Map.entry("Double4", new VectorDescriptor("double4", "double", List.of("x", "y", "z", "w")))
    );

    private static final Map<String, String> SUPPORTED_IMAGE_AND_SAMPLER_TYPES = Map.of(
            "Image2DReadOnly", "read_only image2d_t",
            "Image2DWriteOnly", "write_only image2d_t",
            "Sampler", "sampler_t"
    );

    private GpuTypeSupport() {
    }

    public static boolean isSupportedScalarType(String javaType) {
        return SUPPORTED_SCALAR_TYPES.contains(declaredType(javaType));
    }

    public static boolean isIntegralScalarType(String javaType) {
        return "byte".equals(javaType)
                || "short".equals(javaType)
                || "int".equals(javaType)
                || "long".equals(javaType);
    }

    public static boolean isFloatingScalarType(String javaType) {
        return "float".equals(javaType) || "double".equals(javaType);
    }

    public static boolean isSupportedArrayType(String javaType) {
        String declaredType = declaredType(javaType);
        return declaredType.endsWith("[]") && SUPPORTED_PARAMETER_SCALAR_TYPES.contains(componentType(declaredType));
    }

    public static boolean isArrayType(String javaType) {
        String declaredType = declaredType(javaType);
        return declaredType != null && declaredType.endsWith("[]");
    }

    public static boolean isSupportedKernelParameterType(String javaType) {
        return SUPPORTED_PARAMETER_SCALAR_TYPES.contains(javaType)
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
        return SUPPORTED_POINTER_TYPES.containsKey(simpleTypeName(declaredType(javaType)));
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

    public static int vectorWidth(String javaType) {
        VectorDescriptor descriptor = vectorDescriptor(javaType);
        if (descriptor == null) {
            throw new IllegalArgumentException("Unsupported vector type: " + javaType);
        }
        return descriptor.fieldNames().size();
    }

    public static int vectorStorageWidth(String javaType) {
        int width = vectorWidth(javaType);
        return width == 3 ? 4 : width;
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
        return vectorStorageWidth(javaType) * scalarByteSize(vectorComponentType(javaType, "x"));
    }

    public static String pointerValueType(String javaType) {
        String valueType = SUPPORTED_POINTER_TYPES.get(simpleTypeName(declaredType(javaType)));
        if (valueType == null) {
            throw new IllegalArgumentException("Unsupported pointer type: " + javaType);
        }
        return valueType;
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
        return switch (declaredType(javaType)) {
            case "byte", "boolean" -> Byte.BYTES;
            case "short" -> Short.BYTES;
            case "int", "float" -> Integer.BYTES;
            case "long", "double" -> Long.BYTES;
            default -> throw new IllegalArgumentException("Unsupported scalar type size lookup: " + javaType);
        };
    }

    private static boolean isPointerReferenceStorageSuffix(String javaType) {
        return javaType.endsWith(POINTER_REFERENCE_SUFFIX);
    }

    private static VectorDescriptor vectorDescriptor(String javaType) {
        String declaredType = declaredType(javaType);
        if (declaredType == null) {
            return null;
        }
        return SUPPORTED_VECTOR_TYPES.get(simpleTypeName(declaredType));
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
            case "short" -> 1;
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
            List<String> fieldNames
    ) {
    }
}
