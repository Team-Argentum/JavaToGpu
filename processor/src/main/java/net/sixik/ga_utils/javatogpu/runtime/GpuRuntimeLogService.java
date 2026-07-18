package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.extension.GpuExtension;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionCapability;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionPermission;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionPhase;

import java.util.Set;

/**
 * ServiceLoader-facing runtime logging sink.
 *
 * <p>Applications can route JavaToGpu logs into Log4J, SLF4J, java.util.logging, files, metrics, or any other logging
 * backend by implementing this service and registering it in
 * {@code META-INF/services/net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeLogService}. The core runtime only emits
 * immutable log records; it does not depend on a concrete logging framework.</p>
 */
@FunctionalInterface
public interface GpuRuntimeLogService extends GpuExtension {

    void log(GpuRuntimeLogRecord record);

    @Override
    default Set<GpuExtensionCapability> extensionCapabilities() {
        return Set.of(GpuExtensionCapability.RUNTIME_LOGGING_SERVICE);
    }

    @Override
    default GpuExtensionPhase extensionPhase() {
        return GpuExtensionPhase.DIAGNOSTICS;
    }

    @Override
    default GpuExtensionPermission extensionPermission() {
        return GpuExtensionPermission.READ_ONLY;
    }
}
