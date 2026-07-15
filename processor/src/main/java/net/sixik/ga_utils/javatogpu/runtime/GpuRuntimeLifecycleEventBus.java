package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.extension.GpuExtensionCapability;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionExecutionReport;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionFailurePolicy;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionPermission;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionPhase;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionRegistry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;

/**
 * Failure-isolated dispatcher for read-only runtime lifecycle listeners.
 */
public final class GpuRuntimeLifecycleEventBus {

    private static final GpuRuntimeLifecycleEventBus EMPTY = new GpuRuntimeLifecycleEventBus(List.of());

    private final List<GpuRuntimeLifecycleEventListener> listeners;
    private final GpuExtensionRegistry extensionRegistry;

    private GpuRuntimeLifecycleEventBus(Collection<? extends GpuRuntimeLifecycleEventListener> listeners) {
        List<GpuRuntimeLifecycleEventListener> rawListeners = listeners == null ? List.of() : List.copyOf(listeners);
        this.extensionRegistry = GpuExtensionRegistry.of(rawListeners);
        this.extensionRegistry.requirePipelineContract(
                "runtime lifecycle event bus",
                GpuExtensionPhase.RUNTIME_LIFECYCLE,
                GpuExtensionPermission.READ_ONLY,
                GpuExtensionCapability.RUNTIME_LIFECYCLE_EVENT_LISTENER
        );
        LinkedHashMap<String, GpuRuntimeLifecycleEventListener> listenersById = new LinkedHashMap<>();
        for (GpuRuntimeLifecycleEventListener listener : rawListeners) {
            listenersById.put(listener.extensionId().trim(), listener);
        }
        this.listeners = this.extensionRegistry.descriptors().stream()
                .map(descriptor -> listenersById.get(descriptor.id()))
                .toList();
    }

    public static GpuRuntimeLifecycleEventBus empty() {
        return EMPTY;
    }

    public static GpuRuntimeLifecycleEventBus of(Collection<? extends GpuRuntimeLifecycleEventListener> listeners) {
        if (listeners == null || listeners.isEmpty()) {
            return empty();
        }
        return new GpuRuntimeLifecycleEventBus(listeners);
    }

    public static GpuRuntimeLifecycleEventBus loadFromServiceLoader() {
        LinkedHashMap<String, GpuRuntimeLifecycleEventListener> listeners = new LinkedHashMap<>();
        ServiceLoader.load(GpuRuntimeLifecycleService.class)
                .forEach(listener -> addLoadedListener(listeners, listener));
        ServiceLoader.load(GpuRuntimeLifecycleEventListener.class)
                .forEach(listener -> addLoadedListener(listeners, listener));
        return of(listeners.values());
    }

    private static void addLoadedListener(
            LinkedHashMap<String, GpuRuntimeLifecycleEventListener> listeners,
            GpuRuntimeLifecycleEventListener listener
    ) {
        Objects.requireNonNull(listeners, "listeners");
        Objects.requireNonNull(listener, "listener");
        listeners.putIfAbsent(listener.getClass().getName(), listener);
    }

    public int listenerCount() {
        return listeners.size();
    }

    public List<GpuRuntimeLifecycleEventListener> listeners() {
        return listeners;
    }

    public Map<String, String> extensionArtifactFields(String prefix) {
        return extensionRegistry.artifactFields(prefix == null || prefix.isBlank()
                ? "runtimeLifecycleExtension"
                : prefix.trim());
    }

    public GpuRuntimeLifecycleEventReport publish(GpuRuntimeLifecycleEvent event) {
        GpuRuntimeLifecycleEvent value = Objects.requireNonNull(event, "event");
        ArrayList<GpuExtensionExecutionReport> reports = new ArrayList<>();
        for (GpuRuntimeLifecycleEventListener listener : listeners) {
            try {
                listener.onRuntimeLifecycleEvent(value);
                reports.add(GpuExtensionExecutionReport.succeeded(listener, "runtime lifecycle event " + value.kind()));
            } catch (RuntimeException failure) {
                reports.add(GpuExtensionExecutionReport.failed(
                        listener,
                        "runtime lifecycle event " + value.kind(),
                        GpuExtensionFailurePolicy.CONTINUE,
                        failure
                ));
            }
        }
        return new GpuRuntimeLifecycleEventReport(value, reports);
    }
}
