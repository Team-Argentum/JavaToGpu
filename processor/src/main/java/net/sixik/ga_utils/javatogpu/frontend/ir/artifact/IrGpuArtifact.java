package net.sixik.ga_utils.javatogpu.frontend.ir.artifact;

import java.util.List;

public record IrGpuArtifact(
        IrGpuArtifactHeader header,
        IrGpuModule module,
        List<IrGpuBackendOutput> backendOutputs,
        String runtimeDefaultBackend,
        String runtimeOptimizationProfile
) {

    public String derivedOpenClResource() {
        return backendOutputs.stream()
                .filter(output -> "opencl".equals(output.backend()))
                .filter(output -> "source".equals(output.kind()))
                .map(IrGpuBackendOutput::resource)
                .findFirst()
                .orElse("");
    }
}
