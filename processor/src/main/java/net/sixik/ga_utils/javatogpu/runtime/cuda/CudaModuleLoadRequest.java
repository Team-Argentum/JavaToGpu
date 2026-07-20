package net.sixik.ga_utils.javatogpu.runtime.cuda;

import net.sixik.ga_utils.javatogpu.runtime.GpuBackendCompileOptions;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendModuleArtifact;

import java.util.Objects;

/**
 * Input for an optional CUDA module/function loader bridge.
 */
public record CudaModuleLoadRequest(
        CudaCompiledKernel compiledKernel,
        CudaExecutionPlan executionPlan,
        GpuBackendCompileOptions backendOptions
) {

    public CudaModuleLoadRequest {
        compiledKernel = Objects.requireNonNull(compiledKernel, "compiledKernel");
        executionPlan = executionPlan == null ? CudaExecutionPlan.empty() : executionPlan;
        backendOptions = backendOptions == null ? compiledKernel.backendOptions() : backendOptions;
    }

    public static CudaModuleLoadRequest from(CudaCompiledKernel compiledKernel, CudaExecutionPlan executionPlan) {
        return new CudaModuleLoadRequest(compiledKernel, executionPlan, null);
    }

    public String loaderMode() {
        return backendOptions.cudaModuleLoaderMode();
    }

    public boolean moduleLoaderRequested() {
        return backendOptions.requestsCudaNativeModuleLoader();
    }

    public GpuBackendModuleArtifact moduleArtifact() {
        return compiledKernel.moduleArtifact();
    }

    public String kernelName() {
        return compiledKernel.descriptor().kernelName();
    }
}
