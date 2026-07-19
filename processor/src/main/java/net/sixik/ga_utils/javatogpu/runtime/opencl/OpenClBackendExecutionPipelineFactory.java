package net.sixik.ga_utils.javatogpu.runtime.opencl;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendExecutionPipeline;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendExecutionPipelineFactory;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeBackend;

/**
 * OpenCL factory for the backend-neutral compile -> prepare -> invoke runner.
 */
public final class OpenClBackendExecutionPipelineFactory implements GpuBackendExecutionPipelineFactory<
        OpenClCompiledKernel,
        OpenClPreparedExecution,
        OpenClExecutionPlan> {

    @Override
    public GpuBackendTarget backendTarget() {
        return GpuBackendTarget.OPENCL;
    }

    @Override
    public String factoryId() {
        return "backend-execution-pipeline:opencl";
    }

    @Override
    public String factoryVersion() {
        return "1";
    }

    @Override
    public Class<? extends GpuRuntimeBackend> backendType() {
        return OpenClGpuRuntimeBackend.class;
    }

    @Override
    public GpuBackendExecutionPipeline<OpenClCompiledKernel, OpenClPreparedExecution, OpenClExecutionPlan> createPipeline(
            GpuRuntimeBackend backend
    ) {
        requireSupportedBackend(backend);
        OpenClGpuRuntimeBackend openClBackend = (OpenClGpuRuntimeBackend) backend;
        return new GpuBackendExecutionPipeline<>(
                new OpenClKernelCompiler(openClBackend),
                new OpenClExecutionPreparer(new OpenClDeviceBufferRegistry()),
                new OpenClKernelInvoker(openClBackend)
        );
    }
}
