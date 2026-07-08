package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifact;

import java.util.Optional;

@FunctionalInterface
public interface GpuRuntimeIrOptimizer {

    String NO_OP_VERSION = "optimizer:no-op";

    Optional<IrGpuArtifact> optimize(GpuRuntimeIrOptimizationRequest request);

    default GpuRuntimeIrOptimizationReport optimizeWithReport(GpuRuntimeIrOptimizationRequest request) {
        Optional<IrGpuArtifact> originalArtifact = request == null ? Optional.empty() : request.artifact();
        String originalIdentity = GpuRuntimeIrOptimizerRegistry.identityOf(originalArtifact);
        Optional<IrGpuArtifact> optimizedArtifact = optimize(request);
        String optimizedIdentity = GpuRuntimeIrOptimizerRegistry.identityOf(optimizedArtifact);
        GpuRuntimeIrOptimizationPassReport passReport = originalIdentity.equals(optimizedIdentity)
                ? GpuRuntimeIrOptimizationPassReport.skipped(optimizerVersion(), originalIdentity, "optimizer returned unchanged IR")
                : GpuRuntimeIrOptimizationPassReport.applied(
                optimizerVersion(),
                originalIdentity,
                optimizedIdentity,
                "legacy-optimizer-result",
                java.util.List.of("optimizer used legacy Optional<IrGpuArtifact> API")
        );
        return new GpuRuntimeIrOptimizationReport(optimizedArtifact, java.util.List.of(passReport));
    }

    default String optimizerVersion() {
        return getClass().getName();
    }

    static GpuRuntimeIrOptimizer noOp() {
        return new GpuRuntimeIrOptimizer() {
            @Override
            public Optional<IrGpuArtifact> optimize(GpuRuntimeIrOptimizationRequest request) {
                return request.artifact();
            }

            @Override
            public GpuRuntimeIrOptimizationReport optimizeWithReport(GpuRuntimeIrOptimizationRequest request) {
                return GpuRuntimeIrOptimizationReport.empty(request.artifact());
            }

            @Override
            public String optimizerVersion() {
                return NO_OP_VERSION;
            }
        };
    }
}
