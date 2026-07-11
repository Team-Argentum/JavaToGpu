package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.extension.GpuExtension;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionCapability;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionPermission;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionPhase;

import java.util.Set;

/**
 * One explicit runtime IR optimization pass inside a staged production pipeline.
 */
@FunctionalInterface
public interface GpuRuntimeIrOptimizationPass extends GpuExtension {

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

    @Override
    default String extensionId() {
        return passName();
    }

    @Override
    default String extensionVersion() {
        return passVersion();
    }

    @Override
    default Set<GpuExtensionCapability> extensionCapabilities() {
        return Set.of(GpuExtensionCapability.IR_OPTIMIZATION_PROPOSAL);
    }

    @Override
    default GpuExtensionPhase extensionPhase() {
        return GpuExtensionPhase.RUNTIME_IR_OPTIMIZATION;
    }

    @Override
    default GpuExtensionPermission extensionPermission() {
        return GpuExtensionPermission.MUTATION_PROPOSAL;
    }

    /**
     * Returns whether this pass may change strict floating-point behavior.
     */
    default boolean requiresFastMath() {
        return false;
    }
}
