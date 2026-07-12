package net.sixik.ga_utils.javatogpu.iroptimizer;

import net.sixik.ga_utils.javatogpu.extension.GpuExtension;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionCapability;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionPermission;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionPhase;

import java.util.Set;

/**
 * Optional backend-neutral provider that proposes a new IR artifact without mutating the original.
 */
@FunctionalInterface
public interface GpuIrOptimizationProposalProvider extends GpuExtension {

    GpuIrOptimizationProposal propose(GpuIrOptimizationProposalRequest request);

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
}
