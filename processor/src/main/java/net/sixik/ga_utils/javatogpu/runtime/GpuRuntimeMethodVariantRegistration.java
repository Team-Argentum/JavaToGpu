package net.sixik.ga_utils.javatogpu.runtime;

/**
 * One cross-module fallback variant exposed through {@link GpuRuntimeMethodVariantProvider}.
 */
public record GpuRuntimeMethodVariantRegistration(
        String groupId,
        String variantId,
        GpuKernelDescriptor descriptor
) {

    public GpuRuntimeMethodVariantRegistration {
        groupId = requireText(groupId, "groupId");
        variantId = requireText(variantId, "variantId");
        descriptor = java.util.Objects.requireNonNull(descriptor, "descriptor");
    }

    private static String requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Method variant " + label + " must not be blank");
        }
        return value.trim();
    }
}
