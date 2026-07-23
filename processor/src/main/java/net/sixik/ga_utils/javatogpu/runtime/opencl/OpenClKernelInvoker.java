package net.sixik.ga_utils.javatogpu.runtime.opencl;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendKernelInvoker;
import net.sixik.ga_utils.javatogpu.runtime.GpuExecutionConfig;

import java.util.Objects;

final class OpenClKernelInvoker implements GpuBackendKernelInvoker<OpenClPreparedExecution> {

    private final OpenClGpuRuntimeBackend backend;

    OpenClKernelInvoker(OpenClGpuRuntimeBackend backend) {
        this.backend = Objects.requireNonNull(backend, "backend");
    }

    @Override
    public GpuBackendTarget backendTarget() {
        return GpuBackendTarget.OPENCL;
    }

    @Override
    public void invoke(OpenClPreparedExecution preparedKernel, GpuExecutionConfig executionConfig) {
        OpenClPreparedExecution execution = executionConfig == null || preparedKernel.explicitExecutionConfig() != null
                ? preparedKernel
                : new OpenClPreparedExecution(
                        preparedKernel.compiledKernel(),
                        preparedKernel.bufferBindings(),
                        preparedKernel.localBindings(),
                        preparedKernel.scalarBindings(),
                        preparedKernel.argumentBindings(),
                        executionConfig
                );
        backend.executeKernel(execution);
    }
}
