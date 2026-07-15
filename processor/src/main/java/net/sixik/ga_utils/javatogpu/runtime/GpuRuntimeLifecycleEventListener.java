package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.extension.GpuExtension;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionCapability;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionPermission;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionPhase;

import java.util.Set;

/**
 * Read-only observer for compile/runtime lifecycle events.
 *
 * <p>Listeners are intended for diagnostics, journaling, metrics, and test instrumentation. They must not mutate
 * runtime state or use exceptions as control flow; the lifecycle event bus isolates listener failures.</p>
 */
@FunctionalInterface
public interface GpuRuntimeLifecycleEventListener extends GpuExtension {

    void onRuntimeLifecycleEvent(GpuRuntimeLifecycleEvent event);

    @Override
    default Set<GpuExtensionCapability> extensionCapabilities() {
        return Set.of(GpuExtensionCapability.RUNTIME_LIFECYCLE_EVENT_LISTENER);
    }

    @Override
    default GpuExtensionPhase extensionPhase() {
        return GpuExtensionPhase.RUNTIME_LIFECYCLE;
    }

    @Override
    default GpuExtensionPermission extensionPermission() {
        return GpuExtensionPermission.READ_ONLY;
    }
}
