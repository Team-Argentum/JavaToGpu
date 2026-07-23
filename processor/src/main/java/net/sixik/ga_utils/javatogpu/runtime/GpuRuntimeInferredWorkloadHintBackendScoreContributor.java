package net.sixik.ga_utils.javatogpu.runtime;

/**
 * Compatibility facade for inferred-workload backend scoring.
 */
public final class GpuRuntimeInferredWorkloadHintBackendScoreContributor implements GpuRuntimeBackendScoreContributor {

    public static final String CONTRIBUTOR_ID =
            net.sixik.ga_utils.javatogpu.runtime.selection.GpuRuntimeInferredWorkloadHintBackendScoreContributor.CONTRIBUTOR_ID;
    public static final String CONTRIBUTOR_VERSION =
            net.sixik.ga_utils.javatogpu.runtime.selection.GpuRuntimeInferredWorkloadHintBackendScoreContributor.CONTRIBUTOR_VERSION;

    private final net.sixik.ga_utils.javatogpu.runtime.selection.GpuRuntimeInferredWorkloadHintBackendScoreContributor delegate;

    private GpuRuntimeInferredWorkloadHintBackendScoreContributor(
            net.sixik.ga_utils.javatogpu.runtime.selection.GpuRuntimeInferredWorkloadHintBackendScoreContributor delegate
    ) {
        this.delegate = delegate;
    }

    public static GpuRuntimeInferredWorkloadHintBackendScoreContributor fromContext() {
        return new GpuRuntimeInferredWorkloadHintBackendScoreContributor(
                net.sixik.ga_utils.javatogpu.runtime.selection.GpuRuntimeInferredWorkloadHintBackendScoreContributor.fromContext()
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
