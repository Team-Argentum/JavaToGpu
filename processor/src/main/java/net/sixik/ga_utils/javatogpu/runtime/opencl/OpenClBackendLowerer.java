package net.sixik.ga_utils.javatogpu.runtime.opencl;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendLowerer;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendModuleArtifact;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileRequest;

import java.util.Objects;

public final class OpenClBackendLowerer implements GpuBackendLowerer {

    public static final String VERSION = "opencl-source-v1";

    @Override
    public GpuBackendTarget backendTarget() {
        return GpuBackendTarget.OPENCL;
    }

    @Override
    public String lowererVersion() {
        return VERSION;
    }

    @Override
    public GpuBackendModuleArtifact lower(GpuRuntimeCompileRequest compileRequest) {
        Objects.requireNonNull(compileRequest, "compileRequest");
        compileRequest.irGpuArtifact().ifPresent(artifact -> {
            if (!artifact.module().methodBodies().isEmpty()) {
                // The current OpenCL production path still uses generated OpenCL source.
                // Keeping this branch explicit proves the lowerer receives IrGpu payloads
                // and gives future IrGpu-to-OpenCL lowering a stable entry point.
            }
        });
        return GpuBackendModuleArtifact.openClSource(
                compileRequest.descriptor().kernelSource(),
                compileRequest.descriptor().kernelResource(),
                VERSION
        );
    }
}
