package net.sixik.ga_utils.javatogpu.runtime.opencl;

import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeInvocationBindingSummary;

import java.util.List;

public record OpenClPreparedExecution(
        OpenClCompiledKernel compiledKernel,
        List<OpenClPreparedBufferBinding> bufferBindings,
        List<OpenClLocalBinding> localBindings,
        List<OpenClScalarBinding> scalarBindings,
        List<OpenClPreparedArgumentBinding> argumentBindings,
        net.sixik.ga_utils.javatogpu.runtime.GpuExecutionConfig explicitExecutionConfig
) {

    public GpuRuntimeInvocationBindingSummary bindingSummary() {
        return new GpuRuntimeInvocationBindingSummary(
                sizeOf(bufferBindings),
                sizeOf(localBindings),
                sizeOf(scalarBindings),
                sizeOf(argumentBindings)
        );
    }

    private static int sizeOf(List<?> values) {
        return values == null ? 0 : values.size();
    }
}
