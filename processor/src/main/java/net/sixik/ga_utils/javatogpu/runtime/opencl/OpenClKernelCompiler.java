package net.sixik.ga_utils.javatogpu.runtime.opencl;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendKernelCompiler;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendModuleArtifact;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileRequest;

import java.util.Objects;

final class OpenClKernelCompiler implements GpuBackendKernelCompiler<OpenClCompiledKernel> {

    private final OpenClGpuRuntimeBackend backend;

    OpenClKernelCompiler(OpenClGpuRuntimeBackend backend) {
        this.backend = Objects.requireNonNull(backend, "backend");
    }

    @Override
    public GpuBackendTarget backendTarget() {
        return GpuBackendTarget.OPENCL;
    }

    @Override
    public OpenClCompiledKernel compile(
            GpuRuntimeCompileRequest compileRequest,
            GpuBackendModuleArtifact moduleArtifact
    ) {
        return backend.compileKernel(compileRequest, moduleArtifact);
    }
}
