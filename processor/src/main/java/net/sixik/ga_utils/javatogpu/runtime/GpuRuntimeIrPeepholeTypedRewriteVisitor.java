package net.sixik.ga_utils.javatogpu.runtime;

/**
 * Compatibility facade for the typed peephole rewrite visitor preflight.
 */
public final class GpuRuntimeIrPeepholeTypedRewriteVisitor {

    private final net.sixik.ga_utils.javatogpu.runtime.optimization.GpuRuntimeIrPeepholeTypedRewriteVisitor delegate;

    private GpuRuntimeIrPeepholeTypedRewriteVisitor(
            net.sixik.ga_utils.javatogpu.runtime.optimization.GpuRuntimeIrPeepholeTypedRewriteVisitor delegate
    ) {
        this.delegate = delegate;
    }

    public static GpuRuntimeIrPeepholeTypedRewriteVisitor forGraph(GpuRuntimeIrTypedNodeGraph graph) {
        return new GpuRuntimeIrPeepholeTypedRewriteVisitor(
                net.sixik.ga_utils.javatogpu.runtime.optimization.GpuRuntimeIrPeepholeTypedRewriteVisitor.forGraph(graph)
        );
    }

    public GpuRuntimeIrPeepholeRewriteVisitPreflight preflight(
            GpuRuntimeIrPeepholeReplacementPlan plan,
            GpuRuntimeIrPeepholeReplacementPlanValidation validation
    ) {
        return delegate.preflight(plan, validation);
    }
}
