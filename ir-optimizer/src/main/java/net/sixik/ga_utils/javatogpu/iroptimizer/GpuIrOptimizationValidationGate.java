package net.sixik.ga_utils.javatogpu.iroptimizer;

/**
 * Pluggable validation gate used before and after optimizer proposals.
 */
@FunctionalInterface
public interface GpuIrOptimizationValidationGate {

    GpuIrOptimizationValidationResult validate(GpuIrOptimizationValidationRequest request);

    static GpuIrOptimizationValidationGate alwaysValid() {
        return request -> GpuIrOptimizationValidationResult.valid(request.stage());
    }
}
