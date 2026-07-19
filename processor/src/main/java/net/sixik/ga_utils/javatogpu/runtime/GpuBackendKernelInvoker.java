package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;

import java.util.List;

/**
 * SPI boundary for launching a prepared backend kernel.
 */
public interface GpuBackendKernelInvoker<P extends GpuPreparedKernel> {

    GpuBackendTarget backendTarget();

    void invoke(P preparedKernel, GpuExecutionConfig executionConfig);

    default GpuBackendInvocationResult invocationResult(
            P preparedKernel,
            GpuBackendPreparationResult preparationResult,
            GpuExecutionConfig executionConfig,
            int readbackCompletedCount
    ) {
        int readbackRequiredCount = preparedKernel == null ? 0 : preparedKernel.readbackRequiredCount();
        return GpuBackendInvocationResult.invoked(
                preparationResult,
                executionConfig,
                readbackRequiredCount,
                readbackCompletedCount,
                List.of("backend invoker completed a prepared kernel launch")
        );
    }
}
