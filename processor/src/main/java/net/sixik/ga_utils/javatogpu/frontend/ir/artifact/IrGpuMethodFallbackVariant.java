package net.sixik.ga_utils.javatogpu.frontend.ir.artifact;

/**
 * Backend-neutral identity and ordering metadata for one runtime fallback method variant.
 */
public record IrGpuMethodFallbackVariant(
        String methodName,
        String emittedName,
        String groupId,
        String variantId,
        int priority,
        String compatibilityNote,
        String source
) {

    public IrGpuMethodFallbackVariant {
        methodName = normalize(methodName, "unknown");
        emittedName = normalize(emittedName, methodName);
        groupId = normalize(groupId, "");
        variantId = normalize(variantId, methodName);
        compatibilityNote = normalize(compatibilityNote, "");
        source = normalize(source, "GPUFallbackVariant");
    }

    public boolean active() {
        return !groupId.isBlank();
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
