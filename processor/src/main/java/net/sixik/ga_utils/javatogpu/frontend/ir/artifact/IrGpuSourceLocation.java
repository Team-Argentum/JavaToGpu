package net.sixik.ga_utils.javatogpu.frontend.ir.artifact;

/**
 * Source anchor preserved through IrGpu artifacts for runtime diagnostics.
 */
public record IrGpuSourceLocation(
        String sourceKind,
        String ownerQualifiedName,
        String methodName,
        int beginLine,
        int beginColumn,
        int endLine,
        int endColumn
) {

    public static IrGpuSourceLocation unknown(String methodName) {
        return new IrGpuSourceLocation("unknown", "", methodName, -1, -1, -1, -1);
    }

    public IrGpuSourceLocation {
        sourceKind = normalize(sourceKind, "unknown");
        ownerQualifiedName = ownerQualifiedName == null ? "" : ownerQualifiedName;
        methodName = methodName == null ? "" : methodName;
    }

    public boolean knownRange() {
        return beginLine > 0 && beginColumn > 0;
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
