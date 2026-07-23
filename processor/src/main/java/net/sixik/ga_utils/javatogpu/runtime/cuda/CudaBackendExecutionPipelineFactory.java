package net.sixik.ga_utils.javatogpu.runtime.cuda;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendCompiledKernel;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendExecutionPipeline;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendExecutionPipelineFactory;
import net.sixik.ga_utils.javatogpu.runtime.GpuPreparedKernel;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeBackend;

/**
 * CUDA factory for the shared compile -> prepare -> invoke runner skeleton.
 */
public final class CudaBackendExecutionPipelineFactory implements GpuBackendExecutionPipelineFactory<
        GpuBackendCompiledKernel,
        GpuPreparedKernel,
        CudaExecutionPlan> {

    @Override
    public GpuBackendTarget backendTarget() {
        return GpuBackendTarget.CUDA;
    }

    @Override
    public String factoryId() {
        return "backend-execution-pipeline:cuda-preview";
    }

    @Override
    public String factoryVersion() {
        return "1";
    }

    @Override
    public Class<? extends GpuRuntimeBackend> backendType() {
        return CudaGpuRuntimeBackend.class;
    }

    @Override
    public GpuBackendExecutionPipeline<GpuBackendCompiledKernel, GpuPreparedKernel, CudaExecutionPlan> createPipeline(
            GpuRuntimeBackend backend
    ) {
        requireSupportedBackend(backend);
        return new GpuBackendExecutionPipeline<>(
                new CudaKernelCompiler(),
                new CudaKernelPreparer(),
                new CudaKernelInvoker()
        );
    }
}
