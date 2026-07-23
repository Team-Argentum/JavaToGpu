package net.sixik.ga_utils.javatogpu.runtime;

/**
 * Compatibility facade for the built-in lifecycle-to-log bridge.
 *
 * @deprecated use {@link net.sixik.ga_utils.javatogpu.runtime.observability.GpuRuntimeLifecycleLoggingService}.
 */
@Deprecated
public final class GpuRuntimeLifecycleLoggingService implements GpuRuntimeLifecycleService {

    private final net.sixik.ga_utils.javatogpu.runtime.observability.GpuRuntimeLifecycleLoggingService delegate =
            new net.sixik.ga_utils.javatogpu.runtime.observability.GpuRuntimeLifecycleLoggingService();

    @Override
    public void onRuntimeLifecycleEvent(GpuRuntimeLifecycleEvent event) {
        delegate.onRuntimeLifecycleEvent(event);
    }

    @Override
    public String extensionId() {
        return delegate.extensionId();
    }

    @Override
    public String extensionVersion() {
        return delegate.extensionVersion();
    }

    @Override
    public int extensionOrder() {
        return delegate.extensionOrder();
    }
}
