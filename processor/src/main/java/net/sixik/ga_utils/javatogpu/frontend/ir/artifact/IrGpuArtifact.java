package net.sixik.ga_utils.javatogpu.frontend.ir.artifact;

import java.util.List;

public record IrGpuArtifact(
        IrGpuArtifactHeader header,
        IrGpuModule module,
        List<IrGpuEntryParameter> entryParameters,
        IrGpuLaunchMetadata launchMetadata,
        IrGpuValidationMetadata validationMetadata,
        IrGpuFeatureMetadata featureMetadata,
        IrGpuRegenerationMetadata regenerationMetadata,
        List<IrGpuStructMetadata> structMetadata,
        List<IrGpuConstantMetadata> constants,
        List<IrGpuConstantDataMetadata> constantData,
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
        this(
                header,
                module,
                List.of(),
                IrGpuLaunchMetadata.defaultOneDimensional(),
                IrGpuValidationMetadata.frontendSubset(),
                IrGpuFeatureMetadata.none(),
                IrGpuRegenerationMetadata.transitionalIrText(),
                List.of(),
                List.of(),
                List.of(),
                backendOutputs,
                runtimeDefaultBackend,
                runtimeOptimizationProfile
        );
    }

    public IrGpuArtifact(
            IrGpuArtifactHeader header,
            IrGpuModule module,
            List<IrGpuEntryParameter> entryParameters,
            List<IrGpuBackendOutput> backendOutputs,
            String runtimeDefaultBackend,
            String runtimeOptimizationProfile
    ) {
        this(
                header,
                module,
                entryParameters,
                IrGpuLaunchMetadata.defaultOneDimensional(),
                IrGpuValidationMetadata.frontendSubset(),
                IrGpuFeatureMetadata.none(),
                IrGpuRegenerationMetadata.transitionalIrText(),
                List.of(),
                List.of(),
                List.of(),
                backendOutputs,
                runtimeDefaultBackend,
                runtimeOptimizationProfile
        );
    }

    public IrGpuArtifact(
            IrGpuArtifactHeader header,
            IrGpuModule module,
            List<IrGpuEntryParameter> entryParameters,
            IrGpuLaunchMetadata launchMetadata,
            IrGpuValidationMetadata validationMetadata,
            IrGpuFeatureMetadata featureMetadata,
            IrGpuRegenerationMetadata regenerationMetadata,
            List<IrGpuBackendOutput> backendOutputs,
            String runtimeDefaultBackend,
            String runtimeOptimizationProfile
    ) {
        this(
                header,
                module,
                entryParameters,
                launchMetadata,
                validationMetadata,
                featureMetadata,
                regenerationMetadata,
                List.of(),
                List.of(),
                List.of(),
                backendOutputs,
                runtimeDefaultBackend,
                runtimeOptimizationProfile
        );
    }

    public IrGpuArtifact {
        entryParameters = entryParameters == null ? List.of() : List.copyOf(entryParameters);
        launchMetadata = launchMetadata == null ? IrGpuLaunchMetadata.defaultOneDimensional() : launchMetadata;
        validationMetadata = validationMetadata == null ? IrGpuValidationMetadata.frontendSubset() : validationMetadata;
        featureMetadata = featureMetadata == null ? IrGpuFeatureMetadata.none() : featureMetadata;
        regenerationMetadata = regenerationMetadata == null
                ? IrGpuRegenerationMetadata.transitionalIrText()
                : regenerationMetadata;
        structMetadata = structMetadata == null ? List.of() : List.copyOf(structMetadata);
        constants = constants == null ? List.of() : List.copyOf(constants);
        constantData = constantData == null ? List.of() : List.copyOf(constantData);
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
