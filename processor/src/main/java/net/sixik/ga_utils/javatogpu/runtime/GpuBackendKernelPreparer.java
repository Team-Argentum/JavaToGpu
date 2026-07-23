package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;

import java.util.List;

/**
 * SPI boundary for binding backend resources before a kernel invocation.
 *
 * <p>This stage turns a compiled handle plus a backend-specific execution plan into a prepared handle with native
 * argument storage, buffers, local/shared-memory layout, and readback bookkeeping.</p>
 */
public interface GpuBackendKernelPreparer<
        C extends GpuBackendCompiledKernel,
        P extends GpuPreparedKernel,
        PLAN> {

    /**
     * Backend family prepared by this stage.
     */
    GpuBackendTarget backendTarget();

    /**
     * Prepares arguments/resources and returns a closeable prepared handle.
     */
    P prepare(C compiledKernel, PLAN executionPlan);

    /**
     * Builds the portable prepare/module-load receipt for a returned prepared handle.
     */
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
