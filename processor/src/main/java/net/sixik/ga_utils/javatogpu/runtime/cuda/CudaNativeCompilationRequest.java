package net.sixik.ga_utils.javatogpu.runtime.cuda;

import net.sixik.ga_utils.javatogpu.runtime.GpuBackendCompileOptions;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendModuleArtifact;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendModuleFormat;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileRequest;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Input for an optional CUDA-native compiler bridge such as nvcc or a future NVRTC adapter.
 */
public record CudaNativeCompilationRequest(
        GpuRuntimeCompileRequest compileRequest,
        GpuBackendModuleArtifact moduleArtifact,
        GpuBackendCompileOptions backendOptions
) {

    public CudaNativeCompilationRequest {
        compileRequest = Objects.requireNonNull(compileRequest, "compileRequest");
        moduleArtifact = moduleArtifact == null ? GpuBackendModuleArtifact.unknown() : moduleArtifact;
        backendOptions = backendOptions == null
                ? compileRequest.options().backendOptions()
                : backendOptions;
    }

    public static CudaNativeCompilationRequest from(
            GpuRuntimeCompileRequest compileRequest,
            GpuBackendModuleArtifact moduleArtifact
    ) {
        return new CudaNativeCompilationRequest(
                compileRequest,
                moduleArtifact,
                compileRequest == null ? null : compileRequest.options().backendOptions()
        );
    }

    public String bridgeMode() {
        return backendOptions.cudaCompilerBridgeMode();
    }

    public boolean nativeCompilerRequested() {
        return backendOptions.requestsCudaNativeCompilerBridge();
    }

    public List<String> compilerFlags() {
        return backendOptions.flags();
    }

    public Optional<String> nvccPath() {
        return backendOptions.cudaNvccPath();
    }

    public String nvccOutputFormat() {
        return backendOptions.cudaNvccOutputFormat();
    }

    public GpuBackendModuleFormat nvccOutputModuleFormat() {
        return backendOptions.cudaNvccOutputModuleFormat();
    }

    public Optional<String> nvccOutputFormatBlocker() {
        return backendOptions.cudaNvccOutputFormatBlocker();
    }

    public Duration timeout() {
        return backendOptions.cudaCompilerTimeout();
    }

    public String source() {
        return moduleArtifact.source();
    }

    public String kernelName() {
        return compileRequest.descriptor().kernelName();
    }
}
