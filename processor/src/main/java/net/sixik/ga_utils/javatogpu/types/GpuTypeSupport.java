package net.sixik.ga_utils.javatogpu.types;

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

    public static boolean isSupportedKernelParameterType(String javaType) {
        return SUPPORTED_PARAMETER_SCALAR_TYPES.contains(javaType) || isSupportedArrayType(javaType);
    }

    public static boolean isSupportedHelperParameterType(String javaType) {
        return isSupportedKernelParameterType(javaType) || isSupportedPointerType(javaType);
    }

    public static boolean isHelperArgumentCompatible(String actualType, String parameterType) {
        actualType = declaredType(actualType);
        parameterType = declaredType(parameterType);

        if (actualType == null || parameterType == null) {
            return false;
        }
        if (actualType.equals(parameterType)) {
            return true;
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
        if (actualType.equals(parameterType)) {
            return 0;
        }
        if (!isHelperArgumentCompatible(actualType, parameterType)) {
            return Integer.MAX_VALUE;
        }
        return helperWideningRank(parameterType) - helperWideningRank(actualType);
    }

    public static boolean isSupportedLocalType(String javaType) {
        return isSupportedScalarType(javaType) || isSupportedPointerType(javaType);
    }

    public static boolean isGlobalParameterCompatible(String javaType) {
        return isSupportedArrayType(javaType);
    }

    public static boolean isSupportedPointerType(String javaType) {
        return SUPPORTED_POINTER_TYPES.containsKey(simpleTypeName(declaredType(javaType)));
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

    private static boolean isPointerReferenceStorageSuffix(String javaType) {
        return javaType.endsWith(POINTER_REFERENCE_SUFFIX);
    }

    private static String simpleTypeName(String javaType) {
        int separatorIndex = javaType.lastIndexOf('.');
        if (separatorIndex < 0) {
            return javaType;
        }
        return javaType.substring(separatorIndex + 1);
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
}
