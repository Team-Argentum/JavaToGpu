package net.sixik.ga_utils.javatogpu.runtime;

public record GpuKernelInvocation(
        GpuKernelDescriptor descriptor,
        Object[] arguments,
        GpuExecutionConfig executionConfig,
        GpuRuntimeCompileOptions compileOptions,
        ClassLoader artifactClassLoader,
        java.util.List<GpuKernelDescriptor> fallbackDescriptors
) {

    public GpuKernelInvocation(
            GpuKernelDescriptor descriptor,
            Object[] arguments,
            GpuExecutionConfig executionConfig,
            GpuRuntimeCompileOptions compileOptions,
            ClassLoader artifactClassLoader
    ) {
        this(descriptor, arguments, executionConfig, compileOptions, artifactClassLoader, java.util.List.of());
    }

    public GpuKernelInvocation {
        descriptor = java.util.Objects.requireNonNull(descriptor, "descriptor");
        arguments = arguments == null ? new Object[0] : arguments;
        fallbackDescriptors = fallbackDescriptors == null ? java.util.List.of() : java.util.List.copyOf(fallbackDescriptors);
    }

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
                artifactClassLoader,
                fallbackDescriptors
        );
    }

    public java.util.List<GpuKernelDescriptor> descriptorVariants() {
        java.util.ArrayList<GpuKernelDescriptor> variants = new java.util.ArrayList<>(fallbackDescriptors.size() + 1);
        variants.add(descriptor);
        variants.addAll(fallbackDescriptors);
        return java.util.List.copyOf(variants);
    }

    public GpuKernelInvocation withSelectedDescriptor(GpuKernelDescriptor selectedDescriptor) {
        return new GpuKernelInvocation(
                selectedDescriptor,
                arguments,
                executionConfig,
                compileOptions,
                artifactClassLoader,
                java.util.List.of()
        );
    }

    public Long globalWorkSize() {
        return executionConfig == null ? null : executionConfig.globalWorkSize();
    }
}
