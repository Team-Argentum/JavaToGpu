package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.extension.GpuExtension;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionCapability;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionPermission;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionPhase;

import java.util.Optional;
import java.util.Set;

/**
 * Read-only extension point for parsing resource usage from backend compiler diagnostics.
 */
public interface GpuBackendCompilerFeedbackProvider extends GpuExtension {

    Optional<GpuBackendCompilerFeedback> inspect(GpuBackendCompilerFeedbackRequest request);

    @Override
    default Set<GpuExtensionCapability> extensionCapabilities() {
        return Set.of(GpuExtensionCapability.COMPILER_FEEDBACK);
    }

    @Override
    default GpuExtensionPhase extensionPhase() {
        return GpuExtensionPhase.BACKEND_COMPILER_FEEDBACK;
    }

    @Override
    default GpuExtensionPermission extensionPermission() {
        return GpuExtensionPermission.READ_ONLY;
    }
}
