package net.sixik.ga_utils.javatogpu.runtime.cuda;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendCompilationResult;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendCompiledKernel;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendKernelPreparer;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendModuleFormat;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendPreparationResult;
import net.sixik.ga_utils.javatogpu.runtime.GpuPreparedKernel;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeInvocationBindingSummary;

import java.util.List;
import java.util.Objects;

/**
 * CUDA prepare-stage skeleton. Native memory binding starts after the compiler bridge exists.
 */
final class CudaKernelPreparer implements GpuBackendKernelPreparer<
        GpuBackendCompiledKernel,
        GpuPreparedKernel,
        CudaExecutionPlan> {

    private final CudaModuleLoaderBridgeRegistry moduleLoaders;
    private final CudaArgumentBinderBridgeRegistry argumentBinders;
    private CudaModuleLoadResult lastModuleLoadResult;
    private CudaArgumentBindingResult lastArgumentBindingResult;

    CudaKernelPreparer() {
        this(CudaModuleLoaderBridgeRegistry.loadWithBuiltIns(), CudaArgumentBinderBridgeRegistry.loadWithBuiltIns());
    }

    CudaKernelPreparer(CudaModuleLoaderBridgeRegistry moduleLoaders) {
        this(moduleLoaders, CudaArgumentBinderBridgeRegistry.loadWithBuiltIns());
    }

    CudaKernelPreparer(
            CudaModuleLoaderBridgeRegistry moduleLoaders,
            CudaArgumentBinderBridgeRegistry argumentBinders
    ) {
        this.moduleLoaders = Objects.requireNonNull(moduleLoaders, "moduleLoaders");
        this.argumentBinders = Objects.requireNonNull(argumentBinders, "argumentBinders");
    }

    @Override
    public GpuBackendTarget backendTarget() {
        return GpuBackendTarget.CUDA;
    }

    @Override
    public GpuPreparedKernel prepare(GpuBackendCompiledKernel compiledKernel, CudaExecutionPlan executionPlan) {
        lastModuleLoadResult = null;
        lastArgumentBindingResult = null;
        if (!(compiledKernel instanceof CudaCompiledKernel cudaCompiledKernel)) {
            return null;
        }
        CudaModuleLoadRequest request = CudaModuleLoadRequest.from(cudaCompiledKernel, executionPlan);
        if (!request.moduleLoaderRequested()) {
            return null;
        }
        GpuBackendModuleFormat moduleFormat = request.moduleArtifact().moduleFormat();
        if (moduleFormat == GpuBackendModuleFormat.CUBIN
                || moduleFormat == GpuBackendModuleFormat.FATBIN) {
            if (request.moduleBinaryArtifact().isEmpty()) {
                lastModuleLoadResult = CudaModuleLoadResult.unsupported(
                        request.loaderMode(),
                        List.of("cuda-module-loader-binary-payload-missing:" + moduleFormat.key()),
                        List.of("CUDA Driver module loading requires a binary payload for " + moduleFormat.key())
                );
                return null;
            }
        } else if (moduleFormat == GpuBackendModuleFormat.NATIVE_BINARY) {
            lastModuleLoadResult = CudaModuleLoadResult.unsupported(
                    request.loaderMode(),
                    List.of("cuda-module-loader-binary-format-unsupported:" + moduleFormat.key()),
                    List.of("CUDA Driver module loading needs a concrete CUDA binary format such as cubin or fatbin")
            );
            return null;
        }
        lastModuleLoadResult = moduleLoaders.load(request);
        if (lastModuleLoadResult.succeeded()) {
            CudaArgumentBindingRequest bindingRequest = CudaArgumentBindingRequest.from(
                    cudaCompiledKernel,
                    lastModuleLoadResult,
                    executionPlan
            );
            lastArgumentBindingResult = argumentBinders.bind(bindingRequest);
            if (bindingRequest.argumentBinderRequested() && !lastArgumentBindingResult.succeeded()) {
                return null;
            }
            return new CudaPreparedKernel(
                    cudaCompiledKernel,
                    lastModuleLoadResult,
                    lastArgumentBindingResult,
                    bindingSummary(executionPlan)
            );
        }
        return null;
    }

    @Override
    public GpuBackendPreparationResult preparationResult(
            GpuPreparedKernel preparedKernel,
            GpuBackendCompilationResult compilationResult
    ) {
        if (compilationResult == null || !compilationResult.compiled()) {
            return GpuBackendPreparationResult.skipped(
                    GpuBackendTarget.CUDA,
                    compilationResult,
                    List.of("cuda-compile-stage-not-available"),
                    List.of("CUDA argument binding is skipped until native compilation returns a kernel handle")
            );
        }
        if (preparedKernel instanceof CudaPreparedKernel cudaPreparedKernel) {
            List<String> diagnostics = cudaPreparedKernel.argumentBindingResult() != null
                    && cudaPreparedKernel.argumentBindingResult().succeeded()
                    ? List.of("CUDA module/function handle loaded; argument binder succeeded; kernel launch is a separate invoke stage")
                    : List.of("CUDA module/function handle loaded; kernel launch is a separate invoke stage");
            return GpuBackendPreparationResult.prepared(
                    compilationResult,
                    cudaPreparedKernel.preparedKernelKind(),
                    cudaPreparedKernel.bindingSummary(),
                    diagnostics
            );
        }
        if (lastArgumentBindingResult != null) {
            return GpuBackendPreparationResult.unsupported(
                    GpuBackendTarget.CUDA,
                    compilationResult,
                    lastArgumentBindingResult.blockers(),
                    lastArgumentBindingResult.diagnostics()
            );
        }
        if (lastModuleLoadResult != null) {
            return GpuBackendPreparationResult.unsupported(
                    GpuBackendTarget.CUDA,
                    compilationResult,
                    lastModuleLoadResult.blockers(),
                    lastModuleLoadResult.diagnostics()
            );
        }
        return GpuBackendPreparationResult.unsupported(
                GpuBackendTarget.CUDA,
                compilationResult,
                List.of("cuda-native-argument-binding-missing"),
                List.of("CUDA native memory binding is not implemented yet")
        );
    }

    static GpuRuntimeInvocationBindingSummary bindingSummary(CudaExecutionPlan executionPlan) {
        return executionPlan == null ? GpuRuntimeInvocationBindingSummary.empty() : executionPlan.bindingSummary();
    }
}
