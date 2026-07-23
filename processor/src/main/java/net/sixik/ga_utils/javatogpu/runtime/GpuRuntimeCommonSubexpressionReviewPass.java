package net.sixik.ga_utils.javatogpu.runtime;

/**
 * Compatibility facade for the review-only common-subexpression optimizer-family lane.
 */
public final class GpuRuntimeCommonSubexpressionReviewPass implements GpuRuntimeIrOptimizationPass {

    public static final String PASS_ID =
            net.sixik.ga_utils.javatogpu.runtime.optimization.GpuRuntimeCommonSubexpressionReviewPass.PASS_ID;
    public static final String PASS_VERSION =
            net.sixik.ga_utils.javatogpu.runtime.optimization.GpuRuntimeCommonSubexpressionReviewPass.PASS_VERSION;
    public static final String OPT_IN_PROPERTY =
            net.sixik.ga_utils.javatogpu.runtime.optimization.GpuRuntimeCommonSubexpressionReviewPass.OPT_IN_PROPERTY;

    private final net.sixik.ga_utils.javatogpu.runtime.optimization.GpuRuntimeCommonSubexpressionReviewPass delegate =
            new net.sixik.ga_utils.javatogpu.runtime.optimization.GpuRuntimeCommonSubexpressionReviewPass();

    @Override
    public GpuRuntimeIrOptimizationReport run(GpuRuntimeIrOptimizationRequest request) {
        return delegate.run(request);
    }

    @Override
    public String passName() {
        return delegate.passName();
    }

    @Override
    public String passVersion() {
        return delegate.passVersion();
    }

    @Override
    public int extensionOrder() {
        return delegate.extensionOrder();
    }

    public static boolean optInEnabled() {
        return net.sixik.ga_utils.javatogpu.runtime.optimization.GpuRuntimeCommonSubexpressionReviewPass.optInEnabled();
    }
}
