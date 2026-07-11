package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifact;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuOptimizerPolicyMetadata;

import java.util.Objects;
import java.util.Optional;

public record GpuRuntimeIrOptimizationRequest(
        GpuRuntimeCompileRequest compileRequest,
        Optional<IrGpuArtifact> artifact,
        GpuOptimizationStrategyDecision strategyDecision
) {

    public GpuRuntimeIrOptimizationRequest(
            GpuRuntimeCompileRequest compileRequest,
            Optional<IrGpuArtifact> artifact
    ) {
        this(compileRequest, artifact, GpuOptimizationStrategyDecision.none(compileRequest));
    }

    public GpuRuntimeIrOptimizationRequest {
        compileRequest = Objects.requireNonNull(compileRequest, "compileRequest");
        artifact = artifact == null ? compileRequest.irGpuArtifact() : artifact;
        strategyDecision = strategyDecision == null
                ? GpuOptimizationStrategyDecision.none(compileRequest)
                : strategyDecision;
    }

    public static GpuRuntimeIrOptimizationRequest withoutArtifact(GpuRuntimeCompileRequest compileRequest) {
        return new GpuRuntimeIrOptimizationRequest(compileRequest, compileRequest.irGpuArtifact());
    }

    public IrGpuOptimizerPolicyMetadata optimizerPolicy() {
        return artifact
                .map(IrGpuArtifact::optimizerPolicyMetadata)
                .orElseGet(IrGpuOptimizerPolicyMetadata::defaultStrict);
    }

    public boolean fastMathEnabled() {
        return optimizerPolicy().fastMath();
    }
}
