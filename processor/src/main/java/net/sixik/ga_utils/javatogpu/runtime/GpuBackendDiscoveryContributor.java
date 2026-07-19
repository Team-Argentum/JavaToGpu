package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.extension.GpuExtensionCapability;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionPermission;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionPhase;

import java.util.Map;
import java.util.Set;

/**
 * Hook for backend/device discovery facts.
 *
 * <p>The default implementation is observational. Implementations that add or replace discovery results should
 * override {@link #extensionPermission()} with an explicit production-affecting permission before a future registry
 * allows them to run in a production path.</p>
 */
public interface GpuBackendDiscoveryContributor extends GpuBackendHook {

    default GpuRuntimeDeviceDiscoveryResult afterDiscovery(
            GpuRuntimeCompileOptions compileOptions,
            GpuRuntimeDeviceDiscoveryResult discoveryResult
    ) {
        return discoveryResult;
    }

    default Map<String, String> discoveryFacts(GpuRuntimeDeviceDiscoveryResult discoveryResult) {
        return Map.of();
    }

    @Override
    default Set<GpuExtensionCapability> extensionCapabilities() {
        return Set.of(GpuExtensionCapability.BACKEND_DISCOVERY_CONTRIBUTION);
    }

    @Override
    default GpuExtensionPhase extensionPhase() {
        return GpuExtensionPhase.BACKEND_DISCOVERY;
    }

    @Override
    default GpuExtensionPermission extensionPermission() {
        return GpuExtensionPermission.READ_ONLY;
    }
}
