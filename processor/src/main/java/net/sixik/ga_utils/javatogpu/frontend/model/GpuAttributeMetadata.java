package net.sixik.ga_utils.javatogpu.frontend.model;

/**
 * Backend-neutral representation of source metadata that may lower to backend attributes.
 */
public record GpuAttributeMetadata(
        String kind,
        String value,
        String source
) {

    public GpuAttributeMetadata {
        kind = normalize(kind, "unknown");
        value = value == null ? "" : value;
        source = normalize(source, "unknown");
    }

    public static GpuAttributeMetadata packed(String source) {
        return new GpuAttributeMetadata("packed", "true", source);
    }

    public static GpuAttributeMetadata aligned(int bytes, String source) {
        return new GpuAttributeMetadata("aligned", Integer.toString(bytes), source);
    }

    public static GpuAttributeMetadata requiredWorkGroupSize(int x, int y, int z, String source) {
        return new GpuAttributeMetadata("required-work-group-size", x + "," + y + "," + z, source);
    }

    public static GpuAttributeMetadata workGroupSizeHint(int x, int y, int z, String source) {
        return new GpuAttributeMetadata("work-group-size-hint", x + "," + y + "," + z, source);
    }

    public static GpuAttributeMetadata vectorTypeHint(String value, String source) {
        return new GpuAttributeMetadata("vector-type-hint", value, source);
    }

    public static GpuAttributeMetadata alwaysInline(String source) {
        return new GpuAttributeMetadata("always-inline", "true", source);
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
