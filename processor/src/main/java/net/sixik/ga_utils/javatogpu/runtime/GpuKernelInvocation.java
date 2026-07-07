package net.sixik.ga_utils.javatogpu.runtime;

public record GpuKernelInvocation(
        GpuKernelDescriptor descriptor,
        Object[] arguments,
        GpuExecutionConfig executionConfig,
        GpuRuntimeCompileOptions compileOptions,
        ClassLoader artifactClassLoader
) {

    public GpuKernelInvocation(GpuKernelDescriptor descriptor, Object[] arguments) {
        this(descriptor, arguments, null, null, null);
    }

    public GpuKernelInvocation(GpuKernelDescriptor descriptor, Object[] arguments, long globalWorkSize) {
        this(descriptor, arguments, GpuExecutionConfig.oneDimensional(globalWorkSize), null, null);
    }

    public GpuKernelInvocation(
            GpuKernelDescriptor descriptor,
            Object[] arguments,
            GpuExecutionConfig executionConfig
    ) {
        this(descriptor, arguments, executionConfig, null, null);
    }

    public GpuKernelInvocation(
            GpuKernelDescriptor descriptor,
            Object[] arguments,
            GpuRuntimeCompileOptions compileOptions
    ) {
        this(descriptor, arguments, null, compileOptions, null);
    }

    public GpuKernelInvocation(
            GpuKernelDescriptor descriptor,
            Object[] arguments,
            GpuExecutionConfig executionConfig,
            GpuRuntimeCompileOptions compileOptions
    ) {
        this(descriptor, arguments, executionConfig, compileOptions, null);
    }

    public GpuKernelInvocation(
            GpuKernelDescriptor descriptor,
            Object[] arguments,
            long globalWorkSize,
            GpuRuntimeCompileOptions compileOptions
    ) {
        this(descriptor, arguments, GpuExecutionConfig.oneDimensional(globalWorkSize), compileOptions, null);
    }

    public GpuKernelInvocation withArtifactClassLoader(ClassLoader artifactClassLoader) {
        return new GpuKernelInvocation(
                descriptor,
                arguments,
                executionConfig,
                compileOptions,
                artifactClassLoader
        );
    }

    public Long globalWorkSize() {
        return executionConfig == null ? null : executionConfig.globalWorkSize();
    }
}
