package net.sixik.ga_utils.javatogpu.runtime.cuda;

import net.sixik.ga_utils.javatogpu.runtime.GpuBackendCompileOptions;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendModuleArtifact;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendModuleFormat;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeBinaryArtifact;

import java.util.Objects;
import java.util.Optional;

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

    public Optional<GpuRuntimeBinaryArtifact> moduleBinaryArtifact() {
        GpuBackendModuleFormat moduleFormat = moduleArtifact().moduleFormat();
        if (moduleFormat != GpuBackendModuleFormat.CUBIN && moduleFormat != GpuBackendModuleFormat.FATBIN) {
            return Optional.empty();
        }
        String extension = "." + moduleFormat.key();
        String resource = moduleArtifact().resource();
        return compiledKernel.artifactSnapshot().binaryArtifacts().stream()
                .filter(artifact -> artifact != null && artifact.size() > 0)
                .filter(artifact -> artifact.name().endsWith(extension)
                        || (!resource.isBlank() && resource.endsWith(artifact.name())))
                .findFirst();
    }

    public String kernelName() {
        return compiledKernel.descriptor().kernelName();
    }
}
