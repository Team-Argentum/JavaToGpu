package net.sixik.ga_utils.javatogpu.runtime.cuda;

import net.sixik.ga_utils.javatogpu.runtime.GpuBackendCompileOptions;
import net.sixik.ga_utils.javatogpu.runtime.GpuExecutionConfig;

import java.util.Objects;

/**
 * Input for an optional CUDA readback bridge after a kernel launch was submitted.
 */
public record CudaKernelReadbackRequest(
        CudaPreparedKernel preparedKernel,
        CudaKernelLaunchResult launchResult,
        GpuExecutionConfig executionConfig,
        GpuBackendCompileOptions backendOptions
) {

    public CudaKernelReadbackRequest {
        preparedKernel = Objects.requireNonNull(preparedKernel, "preparedKernel");
        launchResult = Objects.requireNonNull(launchResult, "launchResult");
        backendOptions = backendOptions == null
                ? preparedKernel.compiledKernel().backendOptions()
                : backendOptions;
    }

    public static CudaKernelReadbackRequest from(
            CudaPreparedKernel preparedKernel,
            CudaKernelLaunchResult launchResult,
            GpuExecutionConfig executionConfig
    ) {
        return new CudaKernelReadbackRequest(preparedKernel, launchResult, executionConfig, null);
    }

    public String readbackMode() {
        return backendOptions.cudaReadbackMode();
    }

    public boolean readbackRequested() {
        return backendOptions.requestsCudaNativeReadback();
    }

    public int readbackRequiredCount() {
        return launchResult.readbackRequiredCount();
    }

    public int readbackAlreadyCompletedCount() {
        return launchResult.readbackCompletedCount();
    }

    public String kernelName() {
        return preparedKernel.compiledKernel().descriptor().kernelName();
    }
}
