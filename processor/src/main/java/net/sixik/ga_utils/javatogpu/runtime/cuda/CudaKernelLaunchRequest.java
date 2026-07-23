package net.sixik.ga_utils.javatogpu.runtime.cuda;

import net.sixik.ga_utils.javatogpu.runtime.GpuBackendCompileOptions;
import net.sixik.ga_utils.javatogpu.runtime.GpuExecutionConfig;
import net.sixik.ga_utils.javatogpu.runtime.GpuKernelParameterAccess;
import net.sixik.ga_utils.javatogpu.runtime.GpuKernelParameterDescriptor;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeInvocationBindingSummary;

import java.util.List;
import java.util.Objects;

/**
 * Input for an optional CUDA kernel launch bridge after argument binding.
 */
public record CudaKernelLaunchRequest(
        CudaPreparedKernel preparedKernel,
        GpuExecutionConfig executionConfig,
        GpuBackendCompileOptions backendOptions
) {

    public CudaKernelLaunchRequest {
        preparedKernel = Objects.requireNonNull(preparedKernel, "preparedKernel");
        backendOptions = backendOptions == null
                ? preparedKernel.compiledKernel().backendOptions()
                : backendOptions;
    }

    public static CudaKernelLaunchRequest from(
            CudaPreparedKernel preparedKernel,
            GpuExecutionConfig executionConfig
    ) {
        return new CudaKernelLaunchRequest(preparedKernel, executionConfig, null);
    }

    public String launcherMode() {
        return backendOptions.cudaKernelLauncherMode();
    }

    public boolean kernelLauncherRequested() {
        return backendOptions.requestsCudaNativeKernelLauncher();
    }

    public boolean argumentBindingSucceeded() {
        return preparedKernel.argumentBindingResult() != null
                && preparedKernel.argumentBindingResult().succeeded();
    }

    public GpuRuntimeInvocationBindingSummary bindingSummary() {
        return preparedKernel.bindingSummary();
    }

    public String kernelName() {
        return preparedKernel.compiledKernel().descriptor().kernelName();
    }

    public int readbackRequiredCount() {
        if (preparedKernel.argumentBindingResult() != null
                && preparedKernel.argumentBindingResult().argumentFrame() != null) {
            return preparedKernel.argumentBindingResult().argumentFrame().readbackRequiredCount();
        }
        List<GpuKernelParameterDescriptor> parameters = preparedKernel.compiledKernel().descriptor().parameterDescriptors();
        if (parameters == null || parameters.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (GpuKernelParameterDescriptor parameter : parameters) {
            if (parameter != null && parameter.access() == GpuKernelParameterAccess.READ_WRITE) {
                count++;
            }
        }
        return count;
    }

    public int sharedMemoryByteSize() {
        CudaKernelArgumentFrame frame = argumentFrame();
        if (frame == null) {
            return 0;
        }
        long byteSize = frame.localSharedMemoryByteSize();
        if (byteSize > Integer.MAX_VALUE) {
            throw new IllegalStateException("CUDA shared-memory byte size exceeds Driver API int range: " + byteSize);
        }
        return (int) byteSize;
    }

    public boolean kernelParameterTableRequired() {
        CudaKernelArgumentFrame frame = argumentFrame();
        return frame != null && frame.kernelParameterSlotCount() > 0;
    }

    private CudaKernelArgumentFrame argumentFrame() {
        CudaArgumentBindingResult bindingResult = preparedKernel.argumentBindingResult();
        return bindingResult == null ? null : bindingResult.argumentFrame();
    }
}
