package net.sixik.ga_utils.javatogpu.extension;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Validated, serializable metadata for one loaded extension.
 */
public record GpuExtensionDescriptor(
        String id,
        String version,
        List<GpuExtensionCapability> capabilities,
        GpuExtensionPhase phase,
        GpuExtensionPermission permission,
        int order,
        String implementationClass
) {

    public GpuExtensionDescriptor {
        id = requireToken(id, "extension id");
        version = requireToken(version, "extension version");
        Objects.requireNonNull(capabilities, "capabilities");
        if (capabilities.isEmpty()) {
            throw new IllegalArgumentException("Extension " + id + " must declare at least one capability");
        }
        if (capabilities.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Extension " + id + " capabilities must not contain null");
        }
        capabilities = capabilities.stream()
                .distinct()
                .sorted(Comparator.comparingInt(Enum::ordinal))
                .toList();
        phase = Objects.requireNonNull(phase, "phase");
        permission = Objects.requireNonNull(permission, "permission");
        implementationClass = requireToken(implementationClass, "extension implementation class");
    }

    public static GpuExtensionDescriptor from(GpuExtension extension) {
        Objects.requireNonNull(extension, "extension");
        return new GpuExtensionDescriptor(
                extension.extensionId(),
                extension.extensionVersion(),
                List.copyOf(extension.extensionCapabilities()),
                extension.extensionPhase(),
                extension.extensionPermission(),
                extension.extensionOrder(),
                extension.getClass().getName()
        );
    }

    private static String requireToken(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
        String normalized = value.trim();
        if (normalized.chars().anyMatch(character -> Character.isWhitespace(character) || Character.isISOControl(character))) {
            throw new IllegalArgumentException(label + " must not contain whitespace or control characters: " + value);
        }
        return normalized;
    }
}
