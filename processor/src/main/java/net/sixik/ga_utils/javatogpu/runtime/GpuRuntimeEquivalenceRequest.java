package net.sixik.ga_utils.javatogpu.runtime;

import java.util.Objects;

/**
 * Captures the original and optimized runtime compile state needed for equivalence execution.
 */
public record GpuRuntimeEquivalenceRequest(
        GpuRuntimeCompileRequest originalCompileRequest,
        GpuRuntimeCompileRequest optimizedCompileRequest,
        GpuBackendModuleArtifact optimizedBackendModuleArtifact,
        GpuRuntimeIrOptimizationReport optimizationReport
) {

    public GpuRuntimeEquivalenceRequest {
        originalCompileRequest = Objects.requireNonNull(originalCompileRequest, "originalCompileRequest");
        optimizedCompileRequest = Objects.requireNonNull(optimizedCompileRequest, "optimizedCompileRequest");
        optimizedBackendModuleArtifact = optimizedBackendModuleArtifact == null
                ? GpuBackendModuleArtifact.unknown()
                : optimizedBackendModuleArtifact;
        optimizationReport = optimizationReport == null
                ? GpuRuntimeIrOptimizationReport.empty(optimizedCompileRequest.irGpuArtifact())
                : optimizationReport;
    }

    public boolean hasOptimizedTransform() {
        return optimizationReport.passReports().stream()
                .anyMatch(report -> report.outcome() == GpuRuntimeIrOptimizationOutcome.APPLIED);
    }
}
