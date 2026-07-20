package net.sixik.ga_utils.javatogpu.runtime.cuda;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendCompilationResult;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendCompiledKernel;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendKernelCompiler;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendLoweringResult;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendModuleArtifact;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendModuleFormat;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileRequest;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeBackendCompilationSummary;

import java.util.List;
import java.util.Objects;

/**
 * CUDA compile-stage skeleton. It proves the shared pipeline route while failing closed before native compilation.
 */
final class CudaKernelCompiler implements GpuBackendKernelCompiler<GpuBackendCompiledKernel> {

    private final CudaNativeCompilerBridgeRegistry nativeCompilerBridges;
    private CudaNativeCompilationResult lastNativeCompilationResult;

    CudaKernelCompiler() {
        this(CudaNativeCompilerBridgeRegistry.loadWithBuiltIns());
    }

    CudaKernelCompiler(CudaNativeCompilerBridgeRegistry nativeCompilerBridges) {
        this.nativeCompilerBridges = Objects.requireNonNull(nativeCompilerBridges, "nativeCompilerBridges");
    }

    @Override
    public GpuBackendTarget backendTarget() {
        return GpuBackendTarget.CUDA;
    }

    @Override
    public GpuBackendCompiledKernel compile(
            GpuRuntimeCompileRequest compileRequest,
            GpuBackendModuleArtifact moduleArtifact
    ) {
        lastNativeCompilationResult = null;
        if (!canCompilePreview(moduleArtifact)) {
            return null;
        }
        CudaNativeCompilationRequest nativeRequest = CudaNativeCompilationRequest.from(compileRequest, moduleArtifact);
        if (nativeRequest.nativeCompilerRequested()) {
            lastNativeCompilationResult = nativeCompilerBridges.compile(nativeRequest);
            if (lastNativeCompilationResult.succeeded()) {
                return CudaCompiledKernel.nativeCompiled(compileRequest, moduleArtifact, lastNativeCompilationResult);
            }
            return null;
        }
        return CudaCompiledKernel.preview(compileRequest, moduleArtifact);
    }

    @Override
    public GpuBackendCompilationResult compilationResult(
            GpuBackendCompiledKernel compiledKernel,
            GpuBackendLoweringResult loweringResult
    ) {
        if (compiledKernel == null && lastNativeCompilationResult != null) {
            return GpuBackendCompilationResult.unsupported(
                    GpuBackendTarget.CUDA,
                    loweringResult,
                    lastNativeCompilationResult.blockers(),
                    lastNativeCompilationResult.diagnostics()
            );
        }
        if (compiledKernel instanceof CudaCompiledKernel cudaCompiledKernel) {
            return GpuBackendCompilationResult.succeeded(
                    loweringResult,
                    GpuRuntimeBackendCompilationSummary.from(
                            cudaCompiledKernel.moduleArtifact(),
                            cudaCompiledKernel.artifactSnapshot(),
                            cudaCompiledKernel.cacheKey()
                    ),
                    cudaCompiledKernel.cacheKey(),
                    List.of("CUDA compile-preview bridge accepted the lowered module; native handle is not available yet")
            );
        }
        return GpuBackendCompilationResult.unsupported(
                GpuBackendTarget.CUDA,
                loweringResult,
                List.of("cuda-compile-preview-artifact-missing"),
                List.of("CUDA compile preview requires a CUDA target module in cuda-c or ptx format")
        );
    }

    private static boolean canCompilePreview(GpuBackendModuleArtifact moduleArtifact) {
        if (moduleArtifact == null || moduleArtifact.backendTarget() != GpuBackendTarget.CUDA) {
            return false;
        }
        GpuBackendModuleFormat format = moduleArtifact.moduleFormat();
        return (format == GpuBackendModuleFormat.CUDA_C || format == GpuBackendModuleFormat.PTX)
                && moduleArtifact.sourceAvailable();
    }
}
