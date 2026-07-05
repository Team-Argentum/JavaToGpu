package net.sixik.ga_utils.javatogpu.irvalidation;

import net.sixik.ga_utils.javatogpu.frontend.ir.model.GpuIrMethod;

import java.util.List;
import java.util.Objects;

/**
 * Opt-in runner for auto-vectorization prototype pre/post runtime-equivalence artifacts.
 *
 * <p>The runner delegates to the existing prototype artifact runner and only adds a stable wrapper
 * surface for CI exports. It is intentionally not used by the normal validation pipeline.</p>
 */
public final class GpuIrAutoVectorizationPrototypePrePostRuntimeEquivalenceRunner {
    private final GpuIrAutoVectorizationPrototypeArtifactRunner artifactRunner;

    public GpuIrAutoVectorizationPrototypePrePostRuntimeEquivalenceRunner() {
        this(new GpuIrAutoVectorizationPrototypeArtifactRunner());
    }

    public GpuIrAutoVectorizationPrototypePrePostRuntimeEquivalenceRunner(
            GpuIrAutoVectorizationPrototypeArtifactRunner artifactRunner
    ) {
        this.artifactRunner = Objects.requireNonNull(artifactRunner, "artifactRunner");
    }

    public GpuIrAutoVectorizationPrototypePrePostRuntimeEquivalenceReport run(
            GpuIrMethod method,
            GpuIrAutoVectorizationPreview preview,
            List<GpuIrAutoVectorizationPrototypeInputCase> inputCases,
            List<String> comparedOutputs
    ) {
        return new GpuIrAutoVectorizationPrototypePrePostRuntimeEquivalenceReport(
                artifactRunner.run(method, preview, inputCases, comparedOutputs)
        );
    }
}
