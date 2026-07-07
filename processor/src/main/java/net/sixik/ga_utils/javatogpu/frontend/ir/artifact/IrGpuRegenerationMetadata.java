package net.sixik.ga_utils.javatogpu.frontend.ir.artifact;

import java.util.List;

/**
 * Describes whether an IrGpu artifact is ready to regenerate backend source without using derived build outputs.
 */
public record IrGpuRegenerationMetadata(
        boolean backendNeutralSourceReady,
        String payloadFormat,
        String fallbackSource,
        List<String> blockers
) {

    public IrGpuRegenerationMetadata {
        payloadFormat = normalize(payloadFormat, "ir-text-v1");
        fallbackSource = normalize(fallbackSource, "derived-opencl-source");
        blockers = blockers == null ? List.of() : List.copyOf(blockers);
    }

    public static IrGpuRegenerationMetadata transitionalIrText() {
        return new IrGpuRegenerationMetadata(
                false,
                "ir-text-v1",
                "derived-opencl-source",
                List.of("typed-body-regeneration-not-yet-available")
        );
    }

    public static IrGpuRegenerationMetadata backendNeutralReady() {
        return new IrGpuRegenerationMetadata(
                true,
                "ir-text-v1",
                "irgpu-backend-neutral-source",
                List.of()
        );
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
