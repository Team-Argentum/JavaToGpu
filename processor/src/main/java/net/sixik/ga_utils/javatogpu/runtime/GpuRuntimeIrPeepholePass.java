package net.sixik.ga_utils.javatogpu.runtime;

/**
 * Compatibility facade for the built-in typed-IR peephole optimization pass.
 */
public final class GpuRuntimeIrPeepholePass implements GpuRuntimeIrOptimizationPass {

    public static final String VERSION =
            net.sixik.ga_utils.javatogpu.runtime.optimization.GpuRuntimeIrPeepholePass.VERSION;
    public static final String TYPED_BODY_FORMAT =
            net.sixik.ga_utils.javatogpu.runtime.optimization.GpuRuntimeIrPeepholePass.TYPED_BODY_FORMAT;

    private final net.sixik.ga_utils.javatogpu.runtime.optimization.GpuRuntimeIrPeepholePass delegate;

    public GpuRuntimeIrPeepholePass() {
        this.delegate = new net.sixik.ga_utils.javatogpu.runtime.optimization.GpuRuntimeIrPeepholePass();
    }

    public GpuRuntimeIrPeepholePass(GpuRuntimeIrPeepholeRuleRegistry ruleRegistry) {
        this.delegate = new net.sixik.ga_utils.javatogpu.runtime.optimization.GpuRuntimeIrPeepholePass(
                java.util.Objects.requireNonNull(ruleRegistry, "ruleRegistry").unwrap()
        );
    }

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
    public boolean requiresFastMath() {
        return delegate.requiresFastMath();
    }
}
