package net.sixik.ga_utils.javatogpu.frontend.ir.artifact;

/**
 * Method-level optimizer policy stored in an IrGpu artifact.
 */
public record IrGpuOptimizerPolicyMetadata(
        boolean fastMath,
        String source
) {

    public IrGpuOptimizerPolicyMetadata {
        source = source == null || source.isBlank() ? "default-strict" : source;
    }

    public static IrGpuOptimizerPolicyMetadata defaultStrict() {
        return new IrGpuOptimizerPolicyMetadata(false, "default-strict");
    }

    public static IrGpuOptimizerPolicyMetadata fromGpuOptimize(boolean fastMath) {
        return new IrGpuOptimizerPolicyMetadata(fastMath, "GPUOptimize");
    }
}
