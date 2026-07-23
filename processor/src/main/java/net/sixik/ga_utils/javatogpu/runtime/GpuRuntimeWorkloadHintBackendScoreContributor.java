package net.sixik.ga_utils.javatogpu.runtime;

/**
 * Compatibility facade for workload-hint backend scoring.
 */
public final class GpuRuntimeWorkloadHintBackendScoreContributor implements GpuRuntimeBackendScoreContributor {

    public static final String CONTRIBUTOR_ID =
            net.sixik.ga_utils.javatogpu.runtime.selection.GpuRuntimeWorkloadHintBackendScoreContributor.CONTRIBUTOR_ID;
    public static final String CONTRIBUTOR_VERSION =
            net.sixik.ga_utils.javatogpu.runtime.selection.GpuRuntimeWorkloadHintBackendScoreContributor.CONTRIBUTOR_VERSION;

    private final net.sixik.ga_utils.javatogpu.runtime.selection.GpuRuntimeWorkloadHintBackendScoreContributor delegate;

    private GpuRuntimeWorkloadHintBackendScoreContributor(
            net.sixik.ga_utils.javatogpu.runtime.selection.GpuRuntimeWorkloadHintBackendScoreContributor delegate
    ) {
        this.delegate = delegate;
    }

    public static GpuRuntimeWorkloadHintBackendScoreContributor fromContext() {
        return new GpuRuntimeWorkloadHintBackendScoreContributor(
                net.sixik.ga_utils.javatogpu.runtime.selection.GpuRuntimeWorkloadHintBackendScoreContributor.fromContext()
        );
    }

    @Override
    public GpuRuntimeBackendScoreContribution scoreCandidate(GpuRuntimeBackendScoreContext context) {
        return delegate.scoreCandidate(context);
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
