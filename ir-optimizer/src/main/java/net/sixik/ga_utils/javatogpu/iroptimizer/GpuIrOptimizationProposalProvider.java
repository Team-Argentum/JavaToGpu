package net.sixik.ga_utils.javatogpu.iroptimizer;

import net.sixik.ga_utils.javatogpu.extension.GpuExtension;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionCapability;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionPermission;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionPhase;

import java.util.Set;
import java.util.Locale;

/**
 * Optional backend-neutral provider that proposes a new IR artifact without mutating the original.
 */
@FunctionalInterface
public interface GpuIrOptimizationProposalProvider extends GpuExtension {

    GpuIrOptimizationProposal propose(GpuIrOptimizationProposalRequest request);

    /**
     * Stable optimizer-family id used by method-level policy gates such as {@code @GPUOptimize(enabledFamilies = ...)}.
     */
    default String optimizerFamily() {
        String family = extensionId() == null ? "unknown" : extensionId().trim().toLowerCase(Locale.ROOT);
        String prefix = GpuIrOptimizerModule.MODULE_ID + ".";
        if (family.startsWith(prefix)) {
            family = family.substring(prefix.length());
        }
        for (String suffix : java.util.List.of("-materialization", "-preview")) {
            if (family.endsWith(suffix)) {
                family = family.substring(0, family.length() - suffix.length());
            }
        }
        return family.isBlank() ? "unknown" : family;
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
}
