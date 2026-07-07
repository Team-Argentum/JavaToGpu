package net.sixik.ga_utils.javatogpu.frontend.ir.artifact;

import java.util.List;

public record IrGpuArtifact(
        IrGpuArtifactHeader header,
        IrGpuModule module,
        List<IrGpuEntryParameter> entryParameters,
        List<IrGpuBackendOutput> backendOutputs,
        String runtimeDefaultBackend,
        String runtimeOptimizationProfile
) {

    public IrGpuArtifact(
            IrGpuArtifactHeader header,
            IrGpuModule module,
            List<IrGpuBackendOutput> backendOutputs,
            String runtimeDefaultBackend,
            String runtimeOptimizationProfile
    ) {
        this(header, module, List.of(), backendOutputs, runtimeDefaultBackend, runtimeOptimizationProfile);
    }

    public IrGpuArtifact {
        entryParameters = entryParameters == null ? List.of() : List.copyOf(entryParameters);
        backendOutputs = backendOutputs == null ? List.of() : List.copyOf(backendOutputs);
        runtimeDefaultBackend = runtimeDefaultBackend == null || runtimeDefaultBackend.isBlank()
                ? "opencl"
                : runtimeDefaultBackend;
        runtimeOptimizationProfile = runtimeOptimizationProfile == null || runtimeOptimizationProfile.isBlank()
                ? "off"
                : runtimeOptimizationProfile;
    }

    public String derivedOpenClResource() {
        return backendOutputs.stream()
                .filter(output -> "opencl".equals(output.backend()))
                .filter(output -> "source".equals(output.kind()))
                .map(IrGpuBackendOutput::resource)
                .findFirst()
                .orElse("");
    }
}
