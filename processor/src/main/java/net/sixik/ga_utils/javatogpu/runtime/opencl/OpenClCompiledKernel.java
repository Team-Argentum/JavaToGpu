package net.sixik.ga_utils.javatogpu.runtime.opencl;

import dev.denismasterherobrine.packager.opencl.core.OpenClKernel;
import dev.denismasterherobrine.packager.opencl.core.OpenClProgram;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendCompiledKernel;
import net.sixik.ga_utils.javatogpu.runtime.GpuKernelDescriptor;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileArtifactSnapshot;

public final class OpenClCompiledKernel implements GpuBackendCompiledKernel {

    private final GpuKernelDescriptor descriptor;
    private final String cacheKey;
    private final OpenClProgram program;
    private final OpenClKernel kernel;
    private GpuRuntimeCompileArtifactSnapshot artifactSnapshot;
    private OpenClKernelResourceInfo kernelResourceInfo;

    public OpenClCompiledKernel(GpuKernelDescriptor descriptor, String cacheKey) {
        this(descriptor, cacheKey, GpuRuntimeCompileArtifactSnapshot.legacy(descriptor), null, null);
    }

    public OpenClCompiledKernel(
            GpuKernelDescriptor descriptor,
            String cacheKey,
            OpenClProgram program,
            OpenClKernel kernel
    ) {
        this(descriptor, cacheKey, GpuRuntimeCompileArtifactSnapshot.legacy(descriptor), program, kernel);
    }

    public OpenClCompiledKernel(
            GpuKernelDescriptor descriptor,
            String cacheKey,
            GpuRuntimeCompileArtifactSnapshot artifactSnapshot,
            OpenClProgram program,
            OpenClKernel kernel
    ) {
        this.descriptor = descriptor;
        this.cacheKey = cacheKey;
        this.program = program;
        this.kernel = kernel;
        this.artifactSnapshot = artifactSnapshot == null
                ? GpuRuntimeCompileArtifactSnapshot.legacy(descriptor)
                : artifactSnapshot;
        this.kernelResourceInfo = OpenClKernelResourceInfo.unavailable();
    }

    public GpuKernelDescriptor descriptor() {
        return descriptor;
    }

    public String cacheKey() {
        return cacheKey;
    }

    public GpuRuntimeCompileArtifactSnapshot artifactSnapshot() {
        return artifactSnapshot;
    }

    @Override
    public String compiledKernelKind() {
        return "opencl-kernel";
    }

    public OpenClProgram program() {
        return program;
    }

    public OpenClKernel kernel() {
        return kernel;
    }

    public OpenClCompiledKernel withArtifactSnapshot(GpuRuntimeCompileArtifactSnapshot artifactSnapshot) {
        this.artifactSnapshot = artifactSnapshot == null
                ? GpuRuntimeCompileArtifactSnapshot.legacy(descriptor)
                : artifactSnapshot;
        return this;
    }

    OpenClCompiledKernel withKernelResourceInfo(OpenClKernelResourceInfo kernelResourceInfo) {
        this.kernelResourceInfo = kernelResourceInfo == null
                ? OpenClKernelResourceInfo.unavailable()
                : kernelResourceInfo;
        return this;
    }

    public long kernelMaxWorkGroupSize() {
        return kernelResourceInfo.maxWorkGroupSize();
    }

    public long kernelPreferredWorkGroupSizeMultiple() {
        return kernelResourceInfo.preferredWorkGroupSizeMultiple();
    }

    public long kernelLocalMemoryBytes() {
        return kernelResourceInfo.localMemoryBytes();
    }

    public long kernelPrivateMemoryBytes() {
        return kernelResourceInfo.privateMemoryBytes();
    }

    @Override
    public void close() {
        Throwable failure = null;

        if (kernel != null) {
            try {
                kernel.close();
            } catch (Throwable throwable) {
                failure = throwable;
            }
        }

        if (program != null) {
            try {
                program.close();
            } catch (Throwable throwable) {
                if (failure != null) {
                    failure.addSuppressed(throwable);
                } else {
                    failure = throwable;
                }
            }
        }

        if (failure instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        if (failure != null) {
            throw new RuntimeException("Failed to close OpenCL compiled kernel", failure);
        }
    }
}
