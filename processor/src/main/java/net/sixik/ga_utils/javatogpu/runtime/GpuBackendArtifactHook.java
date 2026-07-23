package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.extension.GpuExtensionCapability;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionPermission;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionPhase;

import java.util.Map;
import java.util.Set;

/**
 * Hook for backend-specific artifact-field contributions.
 */
public interface GpuBackendArtifactHook extends GpuBackendHook {

    default Map<String, String> contributeArtifactFields(
            GpuRuntimeCompileRequest compileRequest,
            Map<String, String> currentFields
    ) {
        return Map.of();
    }

    @Override
    default Set<GpuExtensionCapability> extensionCapabilities() {
        return Set.of(GpuExtensionCapability.BACKEND_ARTIFACT_HOOK);
    }

    @Override
    default GpuExtensionPhase extensionPhase() {
        return GpuExtensionPhase.ARTIFACT_EMISSION;
    }

    @Override
    default GpuExtensionPermission extensionPermission() {
        return GpuExtensionPermission.READ_ONLY;
    }
}
