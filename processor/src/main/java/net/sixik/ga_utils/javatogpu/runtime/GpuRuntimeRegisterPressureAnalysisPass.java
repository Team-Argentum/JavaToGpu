package net.sixik.ga_utils.javatogpu.runtime;

/**
 * Compatibility facade for the built-in register-pressure analysis pass.
 */
public final class GpuRuntimeRegisterPressureAnalysisPass implements GpuRuntimeIrOptimizationPass {

    public static final String PASS_ID =
            net.sixik.ga_utils.javatogpu.runtime.optimization.GpuRuntimeRegisterPressureAnalysisPass.PASS_ID;
    public static final String PASS_VERSION =
            net.sixik.ga_utils.javatogpu.runtime.optimization.GpuRuntimeRegisterPressureAnalysisPass.PASS_VERSION;

    private final net.sixik.ga_utils.javatogpu.runtime.optimization.GpuRuntimeRegisterPressureAnalysisPass delegate =
            new net.sixik.ga_utils.javatogpu.runtime.optimization.GpuRuntimeRegisterPressureAnalysisPass();

    @Override
    public GpuRuntimeIrOptimizationReport run(GpuRuntimeIrOptimizationRequest request) {
        return delegate.run(request);
    }

    @Override
    public GpuRuntimeIrOptimizationStage stage() {
        return delegate.stage();
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
}
