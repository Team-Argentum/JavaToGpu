package net.sixik.ga_utils.javatogpu.runtime;

/**
 * Compatibility facade for compiler-feedback backend scoring.
 */
public final class GpuBackendCompilerFeedbackScoreContributor implements GpuRuntimeBackendScoreContributor {

    public static final String CONTRIBUTOR_ID =
            net.sixik.ga_utils.javatogpu.runtime.selection.GpuBackendCompilerFeedbackScoreContributor.CONTRIBUTOR_ID;
    public static final String CONTRIBUTOR_VERSION =
            net.sixik.ga_utils.javatogpu.runtime.selection.GpuBackendCompilerFeedbackScoreContributor.CONTRIBUTOR_VERSION;

    private final net.sixik.ga_utils.javatogpu.runtime.selection.GpuBackendCompilerFeedbackScoreContributor delegate;

    private GpuBackendCompilerFeedbackScoreContributor(
            net.sixik.ga_utils.javatogpu.runtime.selection.GpuBackendCompilerFeedbackScoreContributor delegate
    ) {
        this.delegate = delegate;
    }

    public static GpuBackendCompilerFeedbackScoreContributor fromContext() {
        return new GpuBackendCompilerFeedbackScoreContributor(
                net.sixik.ga_utils.javatogpu.runtime.selection.GpuBackendCompilerFeedbackScoreContributor.fromContext()
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
