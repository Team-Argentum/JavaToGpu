package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.extension.GpuExtensionCapability;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionPermission;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionPhase;

import java.util.Set;

/**
 * Read-only hook for contributing explainable backend-selection score adjustments.
 */
public interface GpuRuntimeBackendScoreContributor extends GpuBackendHook {

    default GpuRuntimeBackendScoreContribution scoreCandidate(GpuRuntimeBackendScoreContext context) {
        return GpuRuntimeBackendScoreContribution.none();
    }

    @Override
    default Set<GpuExtensionCapability> extensionCapabilities() {
        return Set.of(GpuExtensionCapability.BACKEND_SCORE_CONTRIBUTION);
    }

    @Override
    default GpuExtensionPhase extensionPhase() {
        return GpuExtensionPhase.BACKEND_POLICY;
    }

    @Override
    default GpuExtensionPermission extensionPermission() {
        return GpuExtensionPermission.READ_ONLY;
    }
}
