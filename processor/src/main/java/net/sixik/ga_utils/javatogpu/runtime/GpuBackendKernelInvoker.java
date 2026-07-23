package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;

import java.util.List;

/**
 * SPI boundary for launching a prepared backend kernel.
 *
 * <p>This stage should submit the native kernel and perform any required synchronous readback promised by the prepared
 * handle. Unsupported launch shapes should fail closed with a structured runtime exception rather than over-launching or
 * silently ignoring local-size constraints.</p>
 */
public interface GpuBackendKernelInvoker<P extends GpuPreparedKernel> {

    /**
     * Backend family invoked by this stage.
     */
    GpuBackendTarget backendTarget();

    /**
     * Launches the prepared kernel with the effective execution configuration.
     */
    void invoke(P preparedKernel, GpuExecutionConfig executionConfig);

    /**
     * Builds the portable invocation/readback receipt after a successful launch.
     */
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
