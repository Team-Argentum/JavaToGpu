package net.sixik.ga_utils.javatogpu.runtime.cuda;

import net.sixik.ga_utils.javatogpu.runtime.GpuBackendCompileOptions;
import net.sixik.ga_utils.javatogpu.runtime.GpuKernelDescriptor;
import net.sixik.ga_utils.javatogpu.runtime.GpuKernelParameterAccess;
import net.sixik.ga_utils.javatogpu.runtime.GpuKernelParameterDescriptor;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeInvocationBindingSummary;

import java.util.List;
import java.util.Objects;

/**
 * Input for an optional CUDA argument binder bridge after module/function loading.
 */
public record CudaArgumentBindingRequest(
        CudaCompiledKernel compiledKernel,
        CudaModuleLoadResult moduleLoadResult,
        CudaExecutionPlan executionPlan,
        GpuBackendCompileOptions backendOptions
) {

    public CudaArgumentBindingRequest {
        compiledKernel = Objects.requireNonNull(compiledKernel, "compiledKernel");
        moduleLoadResult = Objects.requireNonNull(moduleLoadResult, "moduleLoadResult");
        executionPlan = executionPlan == null ? CudaExecutionPlan.empty() : executionPlan;
        backendOptions = backendOptions == null ? compiledKernel.backendOptions() : backendOptions;
    }

    public static CudaArgumentBindingRequest from(
            CudaCompiledKernel compiledKernel,
            CudaModuleLoadResult moduleLoadResult,
            CudaExecutionPlan executionPlan
    ) {
        return new CudaArgumentBindingRequest(compiledKernel, moduleLoadResult, executionPlan, null);
    }

    public String binderMode() {
        return backendOptions.cudaArgumentBinderMode();
    }

    public boolean argumentBinderRequested() {
        return backendOptions.requestsCudaNativeArgumentBinder();
    }

    public GpuKernelDescriptor descriptor() {
        return compiledKernel.descriptor();
    }

    public GpuRuntimeInvocationBindingSummary descriptorBindingSummary() {
        List<GpuKernelParameterDescriptor> parameters = descriptor() == null
                ? List.of()
                : descriptor().parameterDescriptors();
        int buffers = 0;
        int locals = 0;
        int scalars = 0;
        int arguments = 0;
        for (GpuKernelParameterDescriptor parameter : parameters == null ? List.<GpuKernelParameterDescriptor>of() : parameters) {
            if (parameter == null) {
                continue;
            }
            arguments++;
            GpuKernelParameterAccess access = parameter.access();
            if (access == GpuKernelParameterAccess.LOCAL) {
                locals++;
            } else if (access == GpuKernelParameterAccess.VALUE) {
                scalars++;
            } else {
                buffers++;
            }
        }
        return new GpuRuntimeInvocationBindingSummary(buffers, locals, scalars, arguments);
    }

    public int descriptorArgumentCount() {
        return descriptorBindingSummary().argumentBindingCount();
    }

    public boolean invocationArgumentsPresent() {
        return executionPlan.invocationArgumentsPresent();
    }

    public int invocationArgumentCount() {
        return executionPlan.invocationArgumentCount();
    }

    public boolean invocationArgumentCountMatchesDescriptor() {
        return invocationArgumentsPresent() && invocationArgumentCount() == descriptorArgumentCount();
    }

    public Object[] invocationArguments() {
        return executionPlan.invocationArguments();
    }

    public Object invocationArgumentAt(int index) {
        return executionPlan.invocationArgumentAt(index);
    }
}
