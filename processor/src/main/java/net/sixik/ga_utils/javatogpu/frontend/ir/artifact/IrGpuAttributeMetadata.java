package net.sixik.ga_utils.javatogpu.frontend.ir.artifact;

/**
 * Backend-neutral attribute metadata persisted in IrGpu artifacts.
 */
public record IrGpuAttributeMetadata(
        String kind,
        String value,
        String source
) {

    public IrGpuAttributeMetadata {
        kind = normalize(kind, "unknown");
        value = value == null ? "" : value;
        source = normalize(source, "unknown");
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
