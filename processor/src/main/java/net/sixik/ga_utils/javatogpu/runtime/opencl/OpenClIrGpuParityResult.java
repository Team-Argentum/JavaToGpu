package net.sixik.ga_utils.javatogpu.runtime.opencl;

import java.util.List;

/**
 * Records whether a packaged IrGpu artifact agrees with the transitional OpenCL source artifact.
 */
public record OpenClIrGpuParityResult(
        boolean checked,
        boolean compatible,
        String derivedOpenClResource,
        String descriptorOpenClResource,
        String reason,
        List<String> diagnostics
) {

    public OpenClIrGpuParityResult {
        derivedOpenClResource = normalize(derivedOpenClResource);
        descriptorOpenClResource = normalize(descriptorOpenClResource);
        reason = reason == null || reason.isBlank() ? "not checked" : reason;
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }

    public static OpenClIrGpuParityResult missingIrGpu(String descriptorOpenClResource) {
        return new OpenClIrGpuParityResult(
                false,
                false,
                "",
                descriptorOpenClResource,
                "compile request has no IrGpu artifact",
                List.of("OpenCL lowering remains descriptor-source based")
        );
    }

    public static OpenClIrGpuParityResult compatible(String derivedOpenClResource, String descriptorOpenClResource) {
        return new OpenClIrGpuParityResult(
                true,
                true,
                derivedOpenClResource,
                descriptorOpenClResource,
                "IrGpu derived OpenCL resource matches descriptor OpenCL resource",
                List.of()
        );
    }

    public static OpenClIrGpuParityResult incompatible(String derivedOpenClResource, String descriptorOpenClResource) {
        return new OpenClIrGpuParityResult(
                true,
                false,
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
