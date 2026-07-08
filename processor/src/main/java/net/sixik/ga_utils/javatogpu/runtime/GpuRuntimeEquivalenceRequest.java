package net.sixik.ga_utils.javatogpu.runtime;

import java.util.Objects;

/**
 * Captures the original and optimized runtime compile state needed for equivalence execution.
 */
public record GpuRuntimeEquivalenceRequest(
        GpuRuntimeCompileRequest originalCompileRequest,
        GpuRuntimeCompileRequest optimizedCompileRequest,
        GpuBackendModuleArtifact optimizedBackendModuleArtifact,
        GpuRuntimeIrOptimizationReport optimizationReport,
        Object[] invocationArguments,
        GpuExecutionConfig executionConfig
) {

    public GpuRuntimeEquivalenceRequest(
            GpuRuntimeCompileRequest originalCompileRequest,
            GpuRuntimeCompileRequest optimizedCompileRequest,
            GpuBackendModuleArtifact optimizedBackendModuleArtifact,
            GpuRuntimeIrOptimizationReport optimizationReport
    ) {
        this(
                originalCompileRequest,
                optimizedCompileRequest,
                optimizedBackendModuleArtifact,
                optimizationReport,
                null,
                null
        );
    }

    public GpuRuntimeEquivalenceRequest {
        originalCompileRequest = Objects.requireNonNull(originalCompileRequest, "originalCompileRequest");
        optimizedCompileRequest = Objects.requireNonNull(optimizedCompileRequest, "optimizedCompileRequest");
        optimizedBackendModuleArtifact = optimizedBackendModuleArtifact == null
                ? GpuBackendModuleArtifact.unknown()
                : optimizedBackendModuleArtifact;
        optimizationReport = optimizationReport == null
                ? GpuRuntimeIrOptimizationReport.empty(optimizedCompileRequest.irGpuArtifact())
                : optimizationReport;
        invocationArguments = invocationArguments == null ? null : invocationArguments.clone();
    }

    public boolean hasOptimizedTransform() {
        return optimizationReport.passReports().stream()
                .anyMatch(report -> report.outcome() == GpuRuntimeIrOptimizationOutcome.APPLIED);
    }

    public boolean hasInvocationContext() {
        return invocationArguments != null;
    }

    @Override
    public Object[] invocationArguments() {
        return invocationArguments == null ? null : invocationArguments.clone();
    }
}
