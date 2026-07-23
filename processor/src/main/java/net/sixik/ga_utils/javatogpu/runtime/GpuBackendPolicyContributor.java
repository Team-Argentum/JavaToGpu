package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.extension.GpuExtensionCapability;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionPermission;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionPhase;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Read-only hook for contributing backend-selection policy facts or requirements.
 */
public interface GpuBackendPolicyContributor extends GpuBackendHook {

    default List<GpuRuntimeRequirement> backendRequirements(GpuRuntimeCompileOptions compileOptions) {
        return List.of();
    }

    default Map<String, String> policyFacts(GpuRuntimeCompileOptions compileOptions) {
        return Map.of();
    }

    @Override
    default Set<GpuExtensionCapability> extensionCapabilities() {
        return Set.of(GpuExtensionCapability.BACKEND_POLICY_CONTRIBUTION);
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
