package net.sixik.ga_utils.javatogpu.runtime.opencl;

import java.util.List;

/**
 * Records whether a packaged IrGpu artifact agrees with the transitional OpenCL source artifact.
 */
public record OpenClIrGpuParityResult(
        boolean checked,
        boolean compatible,
        boolean backendNeutralSourceReady,
        String regenerationPayloadFormat,
        String regenerationFallbackSource,
        List<String> regenerationBlockers,
        String derivedOpenClResource,
        String descriptorOpenClResource,
        String reason,
        List<String> diagnostics
) {

    public OpenClIrGpuParityResult {
        regenerationPayloadFormat = normalize(regenerationPayloadFormat);
        regenerationFallbackSource = normalize(regenerationFallbackSource);
        regenerationBlockers = regenerationBlockers == null ? List.of() : List.copyOf(regenerationBlockers);
        derivedOpenClResource = normalize(derivedOpenClResource);
        descriptorOpenClResource = normalize(descriptorOpenClResource);
        reason = reason == null || reason.isBlank() ? "not checked" : reason;
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }

    public static OpenClIrGpuParityResult missingIrGpu(String descriptorOpenClResource) {
        return new OpenClIrGpuParityResult(
                false,
                false,
                false,
                "",
                "descriptor-opencl-source",
                List.of("irgpu-artifact-missing"),
                "",
                descriptorOpenClResource,
                "compile request has no IrGpu artifact",
                List.of("OpenCL lowering remains descriptor-source based")
        );
    }

    public static OpenClIrGpuParityResult compatible(
            boolean backendNeutralSourceReady,
            String regenerationPayloadFormat,
            String regenerationFallbackSource,
            List<String> regenerationBlockers,
            String derivedOpenClResource,
            String descriptorOpenClResource
    ) {
        return new OpenClIrGpuParityResult(
                true,
                true,
                backendNeutralSourceReady,
                regenerationPayloadFormat,
                regenerationFallbackSource,
                regenerationBlockers,
                derivedOpenClResource,
                descriptorOpenClResource,
                "IrGpu derived OpenCL resource matches descriptor OpenCL resource",
                backendNeutralSourceReady
                        ? List.of("OpenCL lowering can use backend-neutral IrGpu source once enabled")
                        : List.of("OpenCL lowering remains on transitional derived OpenCL source fallback")
        );
    }

    public static OpenClIrGpuParityResult incompatible(
            boolean backendNeutralSourceReady,
            String regenerationPayloadFormat,
            String regenerationFallbackSource,
            List<String> regenerationBlockers,
            String derivedOpenClResource,
            String descriptorOpenClResource
    ) {
        return new OpenClIrGpuParityResult(
                true,
                false,
                backendNeutralSourceReady,
                regenerationPayloadFormat,
                regenerationFallbackSource,
                regenerationBlockers,
                derivedOpenClResource,
                descriptorOpenClResource,
                "IrGpu derived OpenCL resource does not match descriptor OpenCL resource",
                List.of(
                        "derivedOpenClResource=" + normalize(derivedOpenClResource),
                        "descriptorOpenClResource=" + normalize(descriptorOpenClResource)
                )
        );
    }

    public String toLine() {
        String suffix = diagnostics.isEmpty() ? "" : " diagnostics=" + String.join(" | ", diagnostics);
        return "checked="
                + checked
                + " compatible="
                + compatible
                + " backendNeutralSourceReady="
                + backendNeutralSourceReady
                + " regenerationPayloadFormat="
                + regenerationPayloadFormat
                + " regenerationFallbackSource="
                + regenerationFallbackSource
                + " regenerationBlockers="
                + (regenerationBlockers.isEmpty() ? "-" : String.join(",", regenerationBlockers))
                + " derivedOpenClResource="
                + derivedOpenClResource
                + " descriptorOpenClResource="
                + descriptorOpenClResource
                + " reason="
                + reason
                + suffix;
    }

    private static String normalize(String value) {
        return value == null ? "" : value;
    }
}
