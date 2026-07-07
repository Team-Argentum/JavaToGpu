package net.sixik.ga_utils.javatogpu.frontend.ir.artifact;

/**
 * Constant data reference metadata stored in an IrGpu artifact.
 */
public record IrGpuConstantDataMetadata(
        String ownerQualifiedName,
        String ownerSimpleName,
        String name,
        String javaType,
        String initializerSource,
        String kind
) {

    public IrGpuConstantDataMetadata {
        ownerQualifiedName = normalize(ownerQualifiedName, "");
        ownerSimpleName = normalize(ownerSimpleName, ownerQualifiedName);
        name = normalize(name, "");
        javaType = normalize(javaType, "unknown");
        initializerSource = initializerSource == null ? "" : initializerSource;
        kind = normalize(kind, "EMBEDDED");
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
