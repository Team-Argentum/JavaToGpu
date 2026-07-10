package net.sixik.ga_utils.javatogpu.runtime;

import java.util.Objects;

/**
 * Immutable binary payload emitted by one runtime compilation.
 */
public record GpuRuntimeBinaryArtifact(String name, String mediaType, byte[] content) {

    public GpuRuntimeBinaryArtifact {
        name = normalizeName(name);
        mediaType = mediaType == null || mediaType.isBlank()
                ? "application/octet-stream"
                : mediaType.trim();
        content = content == null ? new byte[0] : content.clone();
    }

    @Override
    public byte[] content() {
        return content.clone();
    }

    public int size() {
        return content.length;
    }

    private static String normalizeName(String value) {
        String name = Objects.requireNonNull(value, "name").trim();
        if (name.isEmpty() || !name.matches("[A-Za-z0-9._-]+")) {
            throw new IllegalArgumentException("Binary artifact name must be a simple file name: " + name);
        }
        return name;
    }
}
