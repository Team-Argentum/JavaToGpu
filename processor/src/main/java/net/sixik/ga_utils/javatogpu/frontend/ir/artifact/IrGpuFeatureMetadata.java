package net.sixik.ga_utils.javatogpu.frontend.ir.artifact;

import java.util.List;

/**
 * Backend-neutral feature flags required by an IrGpu artifact.
 */
public record IrGpuFeatureMetadata(
        List<String> requiredFeatures,
        List<String> optionalFeatures
) {

    public IrGpuFeatureMetadata {
        requiredFeatures = requiredFeatures == null ? List.of() : List.copyOf(requiredFeatures);
        optionalFeatures = optionalFeatures == null ? List.of() : List.copyOf(optionalFeatures);
    }

    public static IrGpuFeatureMetadata none() {
        return new IrGpuFeatureMetadata(List.of(), List.of());
    }
}
