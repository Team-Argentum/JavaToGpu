package net.sixik.ga_utils.javatogpu.runtime;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Compatibility facade for the runtime lifecycle event bus.
 */
public final class GpuRuntimeLifecycleEventBus {

    private static final GpuRuntimeLifecycleEventBus EMPTY = new GpuRuntimeLifecycleEventBus(
            net.sixik.ga_utils.javatogpu.runtime.observability.GpuRuntimeLifecycleEventBus.empty()
    );

    private final net.sixik.ga_utils.javatogpu.runtime.observability.GpuRuntimeLifecycleEventBus delegate;

    private GpuRuntimeLifecycleEventBus(
            net.sixik.ga_utils.javatogpu.runtime.observability.GpuRuntimeLifecycleEventBus delegate
    ) {
        this.delegate = delegate;
    }

    public static GpuRuntimeLifecycleEventBus empty() {
        return EMPTY;
    }

    public static GpuRuntimeLifecycleEventBus of(
            Collection<? extends GpuRuntimeLifecycleEventListener> listeners
    ) {
        if (listeners == null || listeners.isEmpty()) {
            return empty();
        }
        return new GpuRuntimeLifecycleEventBus(
                net.sixik.ga_utils.javatogpu.runtime.observability.GpuRuntimeLifecycleEventBus.of(listeners)
        );
    }

    public static GpuRuntimeLifecycleEventBus loadFromServiceLoader() {
        return new GpuRuntimeLifecycleEventBus(
                net.sixik.ga_utils.javatogpu.runtime.observability.GpuRuntimeLifecycleEventBus.loadFromServiceLoader()
        );
    }

    public int listenerCount() {
        return delegate.listenerCount();
    }

    public List<GpuRuntimeLifecycleEventListener> listeners() {
        return delegate.listeners();
    }

    public Map<String, String> extensionArtifactFields(String prefix) {
        return delegate.extensionArtifactFields(prefix);
    }

    public GpuRuntimeLifecycleEventReport publish(GpuRuntimeLifecycleEvent event) {
        return delegate.publish(event);
    }
}
