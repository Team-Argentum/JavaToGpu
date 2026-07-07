package net.sixik.ga_utils.javatogpu.runtime;

/**
 * One explicit runtime IR optimization pass inside a staged production pipeline.
 */
@FunctionalInterface
public interface GpuRuntimeIrOptimizationPass {

    GpuRuntimeIrOptimizationReport run(GpuRuntimeIrOptimizationRequest request);

    default GpuRuntimeIrOptimizationStage stage() {
        return GpuRuntimeIrOptimizationStage.TRANSFORM;
    }

    default String passName() {
        return getClass().getName();
    }

    default String passVersion() {
        return passName();
    }
}
