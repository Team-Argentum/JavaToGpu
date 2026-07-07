package net.sixik.ga_utils.javatogpu.runtime.opencl;

import java.util.List;

/**
 * Describes which source path OpenCL lowering can use for the current IrGpu artifact.
 */
public record OpenClIrGpuReconstructionPlan(
        boolean irGpuSourceSelected,
        String selectedSource,
        String payloadFormat,
        List<String> blockers,
        List<String> diagnostics
) {

    public OpenClIrGpuReconstructionPlan {
        selectedSource = normalize(selectedSource, "derived-opencl-source");
        payloadFormat = normalize(payloadFormat, "ir-text-v1");
        blockers = blockers == null ? List.of() : List.copyOf(blockers);
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }

    public static OpenClIrGpuReconstructionPlan from(OpenClIrGpuParityResult parityResult) {
        if (parityResult == null || !parityResult.checked()) {
            return new OpenClIrGpuReconstructionPlan(
                    false,
                    "descriptor-opencl-source",
                    "unknown",
                    List.of("irgpu-artifact-missing"),
                    List.of("OpenCL lowering uses descriptor source because no IrGpu artifact was available")
            );
        }
        if (!parityResult.compatible()) {
            return new OpenClIrGpuReconstructionPlan(
                    false,
                    "blocked",
                    parityResult.regenerationPayloadFormat(),
                    parityResult.regenerationBlockers(),
                    List.of("OpenCL lowering is blocked because IrGpu derived resource drifted from descriptor source")
            );
        }
        if (parityResult.backendNeutralSourceReady()) {
            return new OpenClIrGpuReconstructionPlan(
                    true,
                    "irgpu-backend-neutral-source",
                    parityResult.regenerationPayloadFormat(),
                    parityResult.regenerationBlockers(),
                    List.of("IrGpu backend-neutral source is ready for OpenCL reconstruction")
            );
        }
        return new OpenClIrGpuReconstructionPlan(
                false,
                parityResult.regenerationFallbackSource(),
                parityResult.regenerationPayloadFormat(),
                parityResult.regenerationBlockers(),
                List.of("OpenCL lowering stays on derived source fallback until IrGpu typed-body regeneration is ready")
        );
    }

    public String toLine() {
        return "irGpuSourceSelected="
                + irGpuSourceSelected
                + " selectedSource="
                + selectedSource
                + " payloadFormat="
                + payloadFormat
                + " blockers="
                + (blockers.isEmpty() ? "-" : String.join(",", blockers))
                + " diagnostics="
                + (diagnostics.isEmpty() ? "-" : String.join(" | ", diagnostics));
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
