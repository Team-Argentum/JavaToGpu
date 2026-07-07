package net.sixik.ga_utils.javatogpu.runtime.opencl;

import dev.denismasterherobrine.packager.opencl.core.OpenClKernel;
import dev.denismasterherobrine.packager.opencl.core.OpenClProgram;
import net.sixik.ga_utils.javatogpu.runtime.GpuKernelDescriptor;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileArtifactSnapshot;

public final class OpenClCompiledKernel implements AutoCloseable {

    private final GpuKernelDescriptor descriptor;
    private final String cacheKey;
    private final OpenClProgram program;
    private final OpenClKernel kernel;
    private GpuRuntimeCompileArtifactSnapshot artifactSnapshot;

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
