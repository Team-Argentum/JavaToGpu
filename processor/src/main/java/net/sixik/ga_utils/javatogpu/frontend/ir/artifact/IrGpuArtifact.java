package net.sixik.ga_utils.javatogpu.frontend.ir.artifact;

import java.util.List;

public record IrGpuArtifact(
        IrGpuArtifactHeader header,
        IrGpuModule module,
        List<IrGpuEntryParameter> entryParameters,
        IrGpuLaunchMetadata launchMetadata,
        IrGpuValidationMetadata validationMetadata,
        IrGpuFeatureMetadata featureMetadata,
        IrGpuOptimizerPolicyMetadata optimizerPolicyMetadata,
        IrGpuRegenerationMetadata regenerationMetadata,
        List<IrGpuStructMetadata> structMetadata,
        List<IrGpuConstantMetadata> constants,
        List<IrGpuConstantDataMetadata> constantData,
        List<IrGpuBackendOutput> backendOutputs,
        String runtimeDefaultBackend,
        String runtimeOptimizationProfile,
        List<IrGpuMethodDeviceConstraint> methodDeviceConstraints,
        List<IrGpuMethodFallbackVariant> methodFallbackVariants,
        List<IrGpuExtensionParticipationMetadata> extensionParticipationMetadata,
        List<IrGpuMethodTestVectorMetadata> methodTestVectors
) {

    public IrGpuArtifact(
            IrGpuArtifactHeader header,
            IrGpuModule module,
            List<IrGpuEntryParameter> entryParameters,
            IrGpuLaunchMetadata launchMetadata,
            IrGpuValidationMetadata validationMetadata,
            IrGpuFeatureMetadata featureMetadata,
            IrGpuOptimizerPolicyMetadata optimizerPolicyMetadata,
            IrGpuRegenerationMetadata regenerationMetadata,
            List<IrGpuStructMetadata> structMetadata,
            List<IrGpuConstantMetadata> constants,
            List<IrGpuConstantDataMetadata> constantData,
            List<IrGpuBackendOutput> backendOutputs,
            String runtimeDefaultBackend,
            String runtimeOptimizationProfile,
            List<IrGpuMethodDeviceConstraint> methodDeviceConstraints,
            List<IrGpuMethodFallbackVariant> methodFallbackVariants,
            List<IrGpuExtensionParticipationMetadata> extensionParticipationMetadata
    ) {
        this(
                header,
                module,
                entryParameters,
                launchMetadata,
                validationMetadata,
                featureMetadata,
                optimizerPolicyMetadata,
                regenerationMetadata,
                structMetadata,
                constants,
                constantData,
                backendOutputs,
                runtimeDefaultBackend,
                runtimeOptimizationProfile,
                methodDeviceConstraints,
                methodFallbackVariants,
                extensionParticipationMetadata,
                List.of()
        );
    }

    public IrGpuArtifact(
            IrGpuArtifactHeader header,
            IrGpuModule module,
            List<IrGpuEntryParameter> entryParameters,
            IrGpuLaunchMetadata launchMetadata,
            IrGpuValidationMetadata validationMetadata,
            IrGpuFeatureMetadata featureMetadata,
            IrGpuOptimizerPolicyMetadata optimizerPolicyMetadata,
            IrGpuRegenerationMetadata regenerationMetadata,
            List<IrGpuStructMetadata> structMetadata,
            List<IrGpuConstantMetadata> constants,
            List<IrGpuConstantDataMetadata> constantData,
            List<IrGpuBackendOutput> backendOutputs,
            String runtimeDefaultBackend,
            String runtimeOptimizationProfile,
            List<IrGpuMethodDeviceConstraint> methodDeviceConstraints,
            List<IrGpuMethodFallbackVariant> methodFallbackVariants
    ) {
        this(
                header,
                module,
                entryParameters,
                launchMetadata,
                validationMetadata,
                featureMetadata,
                optimizerPolicyMetadata,
                regenerationMetadata,
                structMetadata,
                constants,
                constantData,
                backendOutputs,
                runtimeDefaultBackend,
                runtimeOptimizationProfile,
                methodDeviceConstraints,
                methodFallbackVariants,
                List.of(),
                List.of()
        );
    }

    public IrGpuArtifact(
            IrGpuArtifactHeader header,
            IrGpuModule module,
            List<IrGpuEntryParameter> entryParameters,
            IrGpuLaunchMetadata launchMetadata,
            IrGpuValidationMetadata validationMetadata,
            IrGpuFeatureMetadata featureMetadata,
            IrGpuOptimizerPolicyMetadata optimizerPolicyMetadata,
            IrGpuRegenerationMetadata regenerationMetadata,
            List<IrGpuStructMetadata> structMetadata,
            List<IrGpuConstantMetadata> constants,
            List<IrGpuConstantDataMetadata> constantData,
            List<IrGpuBackendOutput> backendOutputs,
            String runtimeDefaultBackend,
            String runtimeOptimizationProfile,
            List<IrGpuMethodDeviceConstraint> methodDeviceConstraints
    ) {
        this(
                header,
                module,
                entryParameters,
                launchMetadata,
                validationMetadata,
                featureMetadata,
                optimizerPolicyMetadata,
                regenerationMetadata,
                structMetadata,
                constants,
                constantData,
                backendOutputs,
                runtimeDefaultBackend,
                runtimeOptimizationProfile,
                methodDeviceConstraints,
                List.of(),
                List.of(),
                List.of()
        );
    }

    public IrGpuArtifact(
            IrGpuArtifactHeader header,
            IrGpuModule module,
            List<IrGpuEntryParameter> entryParameters,
            IrGpuLaunchMetadata launchMetadata,
            IrGpuValidationMetadata validationMetadata,
            IrGpuFeatureMetadata featureMetadata,
            IrGpuOptimizerPolicyMetadata optimizerPolicyMetadata,
            IrGpuRegenerationMetadata regenerationMetadata,
            List<IrGpuStructMetadata> structMetadata,
            List<IrGpuConstantMetadata> constants,
            List<IrGpuConstantDataMetadata> constantData,
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
                optimizerPolicyMetadata,
                regenerationMetadata,
                structMetadata,
                constants,
                constantData,
                backendOutputs,
                runtimeDefaultBackend,
                runtimeOptimizationProfile,
                List.of()
        );
    }

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
                IrGpuOptimizerPolicyMetadata.defaultStrict(),
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
                IrGpuOptimizerPolicyMetadata.defaultStrict(),
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
                IrGpuOptimizerPolicyMetadata.defaultStrict(),
                regenerationMetadata,
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
            IrGpuOptimizerPolicyMetadata optimizerPolicyMetadata,
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
                optimizerPolicyMetadata,
                regenerationMetadata,
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
            List<IrGpuStructMetadata> structMetadata,
            List<IrGpuConstantMetadata> constants,
            List<IrGpuConstantDataMetadata> constantData,
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
                IrGpuOptimizerPolicyMetadata.defaultStrict(),
                regenerationMetadata,
                structMetadata,
                constants,
                constantData,
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
        optimizerPolicyMetadata = optimizerPolicyMetadata == null
                ? IrGpuOptimizerPolicyMetadata.defaultStrict()
                : optimizerPolicyMetadata;
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
        methodDeviceConstraints = methodDeviceConstraints == null ? List.of() : List.copyOf(methodDeviceConstraints);
        methodFallbackVariants = methodFallbackVariants == null ? List.of() : List.copyOf(methodFallbackVariants);
        extensionParticipationMetadata = extensionParticipationMetadata == null
                ? List.of()
                : List.copyOf(extensionParticipationMetadata);
        methodTestVectors = methodTestVectors == null ? List.of() : List.copyOf(methodTestVectors);
    }

    public String derivedOpenClResource() {
        return backendOutputs.stream()
                .filter(output -> "opencl".equals(output.backend()))
                .filter(output -> "source".equals(output.kind()))
                .map(IrGpuBackendOutput::resource)
                .findFirst()
                .orElse("");
    }

    public java.util.Optional<IrGpuMethodDeviceConstraint> entryDeviceConstraint() {
        return methodDeviceConstraints.stream()
                .filter(constraint -> constraint.methodName().equals(module.entryMethod())
                        || constraint.emittedName().equals(module.entryEmittedName()))
                .findFirst();
    }

    public java.util.Optional<IrGpuMethodFallbackVariant> entryFallbackVariant() {
        return methodFallbackVariants.stream()
                .filter(variant -> variant.methodName().equals(module.entryMethod())
                        || variant.emittedName().equals(module.entryEmittedName()))
                .findFirst();
    }

    public List<IrGpuMethodTestVectorMetadata> entryTestVectors() {
        return methodTestVectors.stream()
                .filter(testVector -> testVector.methodName().equals(module.entryMethod())
                        || testVector.emittedName().equals(module.entryEmittedName()))
                .toList();
    }

    public IrGpuArtifact withMethodDeviceConstraints(List<IrGpuMethodDeviceConstraint> constraints) {
        return new IrGpuArtifact(
                header,
                module,
                entryParameters,
                launchMetadata,
                validationMetadata,
                featureMetadata,
                optimizerPolicyMetadata,
                regenerationMetadata,
                structMetadata,
                constants,
                constantData,
                backendOutputs,
                runtimeDefaultBackend,
                runtimeOptimizationProfile,
                constraints,
                methodFallbackVariants,
                extensionParticipationMetadata,
                methodTestVectors
        );
    }

    public IrGpuArtifact withMethodFallbackVariants(List<IrGpuMethodFallbackVariant> variants) {
        return new IrGpuArtifact(
                header,
                module,
                entryParameters,
                launchMetadata,
                validationMetadata,
                featureMetadata,
                optimizerPolicyMetadata,
                regenerationMetadata,
                structMetadata,
                constants,
                constantData,
                backendOutputs,
                runtimeDefaultBackend,
                runtimeOptimizationProfile,
                methodDeviceConstraints,
                variants,
                extensionParticipationMetadata,
                methodTestVectors
        );
    }

    public IrGpuArtifact withMethodTestVectors(List<IrGpuMethodTestVectorMetadata> testVectors) {
        return new IrGpuArtifact(
                header,
                module,
                entryParameters,
                launchMetadata,
                validationMetadata,
                featureMetadata,
                optimizerPolicyMetadata,
                regenerationMetadata,
                structMetadata,
                constants,
                constantData,
                backendOutputs,
                runtimeDefaultBackend,
                runtimeOptimizationProfile,
                methodDeviceConstraints,
                methodFallbackVariants,
                extensionParticipationMetadata,
                testVectors
        );
    }

    public IrGpuArtifact withExtensionParticipationMetadata(
            List<IrGpuExtensionParticipationMetadata> participationMetadata
    ) {
        return new IrGpuArtifact(
                header,
                module,
                entryParameters,
                launchMetadata,
                validationMetadata,
                featureMetadata,
                optimizerPolicyMetadata,
                regenerationMetadata,
                structMetadata,
                constants,
                constantData,
                backendOutputs,
                runtimeDefaultBackend,
                runtimeOptimizationProfile,
                methodDeviceConstraints,
                methodFallbackVariants,
                participationMetadata,
                methodTestVectors
        );
    }
}
