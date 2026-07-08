package net.sixik.ga_utils.javatogpu.frontend.ir.artifact;

/**
 * Validation contract metadata for an IrGpu artifact.
 */
public record IrGpuValidationMetadata(
        String contractVersion,
        String safetyMode,
        boolean optimizerEvidenceRequired
) {

    public IrGpuValidationMetadata {
        contractVersion = contractVersion == null || contractVersion.isBlank() ? "ir-validation-v1" : contractVersion;
        safetyMode = safetyMode == null || safetyMode.isBlank() ? "frontend-subset" : safetyMode;
    }

    public static IrGpuValidationMetadata frontendSubset() {
        return new IrGpuValidationMetadata("ir-validation-v1", "frontend-subset", false);
    }
}
