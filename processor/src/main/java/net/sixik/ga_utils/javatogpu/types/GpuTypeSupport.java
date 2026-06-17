package net.sixik.ga_utils.javatogpu.types;

import java.util.Set;

public final class GpuTypeSupport {

    private static final Set<String> SUPPORTED_SCALAR_TYPES = Set.of(
            "byte", "short", "int", "long", "float", "double", "boolean"
    );

    private static final Set<String> SUPPORTED_PARAMETER_SCALAR_TYPES = Set.of(
            "byte", "short", "int", "long", "float", "double"
    );

    private GpuTypeSupport() {
    }

    public static boolean isSupportedScalarType(String javaType) {
        return SUPPORTED_SCALAR_TYPES.contains(javaType);
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
        return javaType.endsWith("[]") && SUPPORTED_PARAMETER_SCALAR_TYPES.contains(componentType(javaType));
    }

    public static boolean isSupportedParameterType(String javaType) {
        return SUPPORTED_PARAMETER_SCALAR_TYPES.contains(javaType) || isSupportedArrayType(javaType);
    }

    public static boolean isSupportedLocalType(String javaType) {
        return isSupportedScalarType(javaType);
    }

    public static boolean isGlobalParameterCompatible(String javaType) {
        return isSupportedArrayType(javaType);
    }

    public static String componentType(String arrayType) {
        if (!arrayType.endsWith("[]")) {
            throw new IllegalArgumentException("Not an array type: " + arrayType);
        }
        return arrayType.substring(0, arrayType.length() - 2);
    }
}
