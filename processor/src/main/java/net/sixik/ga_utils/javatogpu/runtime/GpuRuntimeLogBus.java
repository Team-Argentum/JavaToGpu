package net.sixik.ga_utils.javatogpu.runtime;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Compatibility facade for the runtime logging bus.
 */
public final class GpuRuntimeLogBus {

    private static final GpuRuntimeLogBus EMPTY = new GpuRuntimeLogBus(
            net.sixik.ga_utils.javatogpu.runtime.observability.GpuRuntimeLogBus.empty()
    );

    private final net.sixik.ga_utils.javatogpu.runtime.observability.GpuRuntimeLogBus delegate;

    private GpuRuntimeLogBus(net.sixik.ga_utils.javatogpu.runtime.observability.GpuRuntimeLogBus delegate) {
        this.delegate = delegate;
    }

    public static GpuRuntimeLogBus empty() {
        return EMPTY;
    }

    public static GpuRuntimeLogBus of(Collection<? extends GpuRuntimeLogService> services) {
        if (services == null || services.isEmpty()) {
            return empty();
        }
        return new GpuRuntimeLogBus(
                net.sixik.ga_utils.javatogpu.runtime.observability.GpuRuntimeLogBus.of(services)
        );
    }

    public static GpuRuntimeLogBus loadFromServiceLoader() {
        return new GpuRuntimeLogBus(
                net.sixik.ga_utils.javatogpu.runtime.observability.GpuRuntimeLogBus.loadFromServiceLoader()
        );
    }

    public static GpuRuntimeLogBus loadDefault() {
        return new GpuRuntimeLogBus(
                net.sixik.ga_utils.javatogpu.runtime.observability.GpuRuntimeLogBus.loadDefault()
        );
    }

    public int serviceCount() {
        return delegate.serviceCount();
    }

    public List<GpuRuntimeLogService> services() {
        return delegate.services();
    }

    public Map<String, String> extensionArtifactFields(String prefix) {
        return delegate.extensionArtifactFields(prefix);
    }

    public GpuRuntimeLogDispatchReport publish(GpuRuntimeLogRecord record) {
        return delegate.publish(record);
    }
}
