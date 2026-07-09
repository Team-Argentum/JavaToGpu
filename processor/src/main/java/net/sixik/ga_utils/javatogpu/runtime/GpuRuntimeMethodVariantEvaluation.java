package net.sixik.ga_utils.javatogpu.runtime;

import java.util.List;

/**
 * Auditable compatibility result for one method variant considered at runtime.
 */
public record GpuRuntimeMethodVariantEvaluation(
        String groupId,
        String variantId,
        int priority,
        GpuKernelDescriptor descriptor,
        boolean accepted,
        int selectedDeviceScore,
        String selectedDeviceKey,
        List<String> diagnostics
) {

    public GpuRuntimeMethodVariantEvaluation {
        groupId = normalize(groupId, "none");
        variantId = normalize(variantId, descriptor == null ? "unknown" : descriptor.kernelName());
        descriptor = java.util.Objects.requireNonNull(descriptor, "descriptor");
        selectedDeviceKey = normalize(selectedDeviceKey, "none");
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
