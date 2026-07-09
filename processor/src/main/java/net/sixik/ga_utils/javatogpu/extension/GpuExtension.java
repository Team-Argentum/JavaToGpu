package net.sixik.ga_utils.javatogpu.extension;

import java.util.Set;

/**
 * Common identity and capability contract shared by public extension points.
 */
public interface GpuExtension {

    default String extensionId() {
        return getClass().getName();
    }

    default String extensionVersion() {
        return "1";
    }

    default Set<GpuExtensionCapability> extensionCapabilities() {
        return Set.of();
    }

    GpuExtensionPhase extensionPhase();

    GpuExtensionPermission extensionPermission();

    /**
     * Provides deterministic ordering for extensions loaded through ServiceLoader.
     */
    default int extensionOrder() {
        return 0;
    }
}
