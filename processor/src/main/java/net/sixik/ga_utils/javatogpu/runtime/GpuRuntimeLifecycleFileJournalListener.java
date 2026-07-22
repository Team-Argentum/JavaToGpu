package net.sixik.ga_utils.javatogpu.runtime;

/**
 * Compatibility facade for the built-in file-backed lifecycle journal service.
 *
 * @deprecated use {@link net.sixik.ga_utils.javatogpu.runtime.observability.GpuRuntimeLifecycleFileJournalListener}.
 */
@Deprecated
public final class GpuRuntimeLifecycleFileJournalListener implements GpuRuntimeLifecycleService {

    public static final String JOURNAL_FILE_PROPERTY =
            net.sixik.ga_utils.javatogpu.runtime.observability.GpuRuntimeLifecycleFileJournalListener
                    .JOURNAL_FILE_PROPERTY;
    public static final String JOURNAL_FORMAT_PROPERTY =
            net.sixik.ga_utils.javatogpu.runtime.observability.GpuRuntimeLifecycleFileJournalListener
                    .JOURNAL_FORMAT_PROPERTY;

    private final net.sixik.ga_utils.javatogpu.runtime.observability.GpuRuntimeLifecycleFileJournalListener delegate =
            new net.sixik.ga_utils.javatogpu.runtime.observability.GpuRuntimeLifecycleFileJournalListener();

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
