package net.sixik.ga_utils.javatogpu.irvalidation;

import net.sixik.ga_utils.javatogpu.frontend.ir.model.GpuIrCompiledMethod;

import java.util.List;
import java.util.Objects;

/**
 * Opt-in runner for pre/post CSE runtime-equivalence artifacts.
 *
 * <p>The runner delegates to the existing CSE artifact runner and only adds a stable pre/post
 * wrapper surface for CI exports. It is intentionally not used by the normal validation pipeline.</p>
 */
public final class GpuIrCommonSubexpressionPrePostRuntimeEquivalenceRunner {
    private final GpuIrCommonSubexpressionArtifactRunner artifactRunner;

    public GpuIrCommonSubexpressionPrePostRuntimeEquivalenceRunner() {
        this(new GpuIrCommonSubexpressionArtifactRunner());
    }

    public GpuIrCommonSubexpressionPrePostRuntimeEquivalenceRunner(
            GpuIrCommonSubexpressionArtifactRunner artifactRunner
    ) {
        this.artifactRunner = Objects.requireNonNull(artifactRunner, "artifactRunner");
    }

    public GpuIrCommonSubexpressionPrePostRuntimeEquivalenceReport run(
            GpuIrCompiledMethod method,
            List<GpuIrCommonSubexpressionInputCase> inputCases,
            List<String> comparedOutputs
    ) {
        return new GpuIrCommonSubexpressionPrePostRuntimeEquivalenceReport(
                artifactRunner.run(method, inputCases, comparedOutputs)
        );
    }
}
