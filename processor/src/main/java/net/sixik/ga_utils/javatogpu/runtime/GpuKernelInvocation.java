package net.sixik.ga_utils.javatogpu.runtime;

public record GpuKernelInvocation(
        GpuKernelDescriptor descriptor,
        Object[] arguments,
        GpuExecutionConfig executionConfig,
        GpuRuntimeCompileOptions compileOptions
) {

    public GpuKernelInvocation(GpuKernelDescriptor descriptor, Object[] arguments) {
        this(descriptor, arguments, null, null);
    }

    public GpuKernelInvocation(GpuKernelDescriptor descriptor, Object[] arguments, long globalWorkSize) {
        this(descriptor, arguments, GpuExecutionConfig.oneDimensional(globalWorkSize), null);
    }

    public GpuKernelInvocation(
            GpuKernelDescriptor descriptor,
            Object[] arguments,
            GpuExecutionConfig executionConfig
    ) {
        this(descriptor, arguments, executionConfig, null);
    }

    public GpuKernelInvocation(
            GpuKernelDescriptor descriptor,
            Object[] arguments,
            GpuRuntimeCompileOptions compileOptions
    ) {
        this(descriptor, arguments, null, compileOptions);
    }

    public GpuKernelInvocation(
            GpuKernelDescriptor descriptor,
            Object[] arguments,
            long globalWorkSize,
            GpuRuntimeCompileOptions compileOptions
    ) {
        this(descriptor, arguments, GpuExecutionConfig.oneDimensional(globalWorkSize), compileOptions);
    }

    public Long globalWorkSize() {
        return executionConfig == null ? null : executionConfig.globalWorkSize();
    }
}
