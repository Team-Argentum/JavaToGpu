package net.sixik.ga_utils.javatogpu.runtime.opencl;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendKernelPreparer;

import java.util.List;

public final class OpenClExecutionPreparer implements GpuBackendKernelPreparer<
        OpenClCompiledKernel,
        OpenClPreparedExecution,
        OpenClExecutionPlan> {

    private final OpenClDeviceBufferRegistry registry;

    public OpenClExecutionPreparer(OpenClDeviceBufferRegistry registry) {
        this.registry = registry;
    }

    @Override
    public GpuBackendTarget backendTarget() {
        return GpuBackendTarget.OPENCL;
    }

    @Override
    public OpenClPreparedExecution prepare(OpenClCompiledKernel compiledKernel, OpenClExecutionPlan plan) {
        List<OpenClPreparedBufferBinding> preparedBuffers = plan.bufferBindings().stream()
                .map(binding -> new OpenClPreparedBufferBinding(binding, registry.acquire(binding)))
                .toList();
        java.util.Map<String, OpenClPreparedBufferBinding> preparedBufferByHandle = preparedBuffers.stream()
                .collect(java.util.stream.Collectors.toMap(binding -> binding.handle().handleId(), binding -> binding));
        List<OpenClPreparedArgumentBinding> preparedArguments = plan.argumentBindings().stream()
                .map(binding -> {
                    if (binding.bufferBinding() != null) {
                        String handleId = registry.acquire(binding.bufferBinding()).handleId();
                        OpenClPreparedBufferBinding preparedBuffer = preparedBufferByHandle.get(handleId);
                        return OpenClPreparedArgumentBinding.forBuffer(binding.parameterIndex(), preparedBuffer);
                    }
                    if (binding.localBinding() != null) {
                        return OpenClPreparedArgumentBinding.forLocal(binding.parameterIndex(), binding.localBinding());
                    }

                    return OpenClPreparedArgumentBinding.forScalar(binding.parameterIndex(), binding.scalarBinding());
                })
                .toList();

        return new OpenClPreparedExecution(
                compiledKernel,
                preparedBuffers,
                plan.localBindings(),
                plan.scalarBindings(),
                preparedArguments,
                null
        );
    }
}
