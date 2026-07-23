package net.sixik.ga_utils.javatogpu.runtime.opencl;

import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeInvocationBindingSummary;
import net.sixik.ga_utils.javatogpu.runtime.GpuPreparedKernel;

import java.util.List;

public record OpenClPreparedExecution(
        OpenClCompiledKernel compiledKernel,
        List<OpenClPreparedBufferBinding> bufferBindings,
        List<OpenClLocalBinding> localBindings,
        List<OpenClScalarBinding> scalarBindings,
        List<OpenClPreparedArgumentBinding> argumentBindings,
        net.sixik.ga_utils.javatogpu.runtime.GpuExecutionConfig explicitExecutionConfig
) implements GpuPreparedKernel {

    @Override
    public String preparedKernelKind() {
        return "opencl-kernel";
    }

    @Override
    public GpuRuntimeInvocationBindingSummary bindingSummary() {
        return new GpuRuntimeInvocationBindingSummary(
                sizeOf(bufferBindings),
                sizeOf(localBindings),
                sizeOf(scalarBindings),
                sizeOf(argumentBindings)
        );
    }

    @Override
    public int readbackRequiredCount() {
        if (bufferBindings == null) {
            return 0;
        }
        return (int) bufferBindings.stream()
                .filter(binding -> binding != null && binding.binding() != null && binding.binding().readbackRequired())
                .count();
    }

    private static int sizeOf(List<?> values) {
        return values == null ? 0 : values.size();
    }
}
