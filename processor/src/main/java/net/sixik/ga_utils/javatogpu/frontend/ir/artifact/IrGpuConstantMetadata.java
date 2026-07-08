package net.sixik.ga_utils.javatogpu.frontend.ir.artifact;

/**
 * Compile-time constant metadata stored in an IrGpu artifact.
 */
public record IrGpuConstantMetadata(
        String ownerQualifiedName,
        String ownerSimpleName,
        String name,
        String javaType,
        String sourceText
) {

    public IrGpuConstantMetadata {
        ownerQualifiedName = normalize(ownerQualifiedName, "");
        ownerSimpleName = normalize(ownerSimpleName, ownerQualifiedName);
        name = normalize(name, "");
        javaType = normalize(javaType, "unknown");
        sourceText = sourceText == null ? "" : sourceText;
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
