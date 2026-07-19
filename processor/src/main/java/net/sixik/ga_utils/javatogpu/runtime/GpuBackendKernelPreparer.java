package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;

import java.util.List;

/**
 * SPI boundary for binding backend resources before a kernel invocation.
 */
public interface GpuBackendKernelPreparer<
        C extends GpuBackendCompiledKernel,
        P extends GpuPreparedKernel,
        PLAN> {

    GpuBackendTarget backendTarget();

    P prepare(C compiledKernel, PLAN executionPlan);

    default GpuBackendPreparationResult preparationResult(
            P preparedKernel,
            GpuBackendCompilationResult compilationResult
    ) {
        return GpuBackendPreparationResult.prepared(
                compilationResult,
                preparedKernel == null ? "unknown" : preparedKernel.preparedKernelKind(),
                preparedKernel == null ? GpuRuntimeInvocationBindingSummary.empty() : preparedKernel.bindingSummary(),
                List.of("backend preparer returned a prepared kernel handle")
        );
    }
}
