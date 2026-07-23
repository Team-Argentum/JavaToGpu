package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.extension.GpuExtensionCapability;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionPermission;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionPhase;

import java.util.Set;

/**
 * Read-only hook for contributing explainable backend-selection score adjustments.
 *
 * <p>Use this for preference logic that should affect backend ordering without directly installing or mutating a
 * backend. Contributors should be deterministic for the same context and should include a concise reason in returned
 * score contributions so selection reports stay understandable.</p>
 */
public interface GpuRuntimeBackendScoreContributor extends GpuBackendHook {

    /**
     * Returns a score adjustment for one backend candidate, or {@link GpuRuntimeBackendScoreContribution#none()}.
     */
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
