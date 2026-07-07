package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;

import java.util.Objects;

public final class UnsupportedGpuBackendLowerer implements GpuBackendLowerer {

    private final GpuBackendTarget backendTarget;

    public UnsupportedGpuBackendLowerer(GpuBackendTarget backendTarget) {
        this.backendTarget = backendTarget == null ? GpuBackendTarget.UNKNOWN : backendTarget;
    }

    @Override
    public GpuBackendTarget backendTarget() {
        return backendTarget;
    }

    @Override
    public String lowererVersion() {
        return "unsupported";
    }

    @Override
    public GpuBackendModuleArtifact lower(GpuRuntimeCompileRequest compileRequest) {
        Objects.requireNonNull(compileRequest, "compileRequest");
        throw new UnsupportedOperationException(
                "GPU backend lowerer is not implemented for "
                        + backendTarget
                        + "; keep using OPENCL or provide a backend-specific lowerer implementation"
        );
    }
}
