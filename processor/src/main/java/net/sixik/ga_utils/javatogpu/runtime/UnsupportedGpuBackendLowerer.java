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
    public GpuBackendSourceSelectionPlan sourceSelectionPlan(GpuRuntimeCompileRequest compileRequest) {
        Objects.requireNonNull(compileRequest, "compileRequest");
        String backendName = backendTarget.name().toLowerCase(java.util.Locale.ROOT);
        return new GpuBackendSourceSelectionPlan(
                backendTarget,
                false,
                backendName + "-lowerer-unavailable",
                "irgpu-unlowered",
                backendName + "-unsupported",
                java.util.List.of(backendName + "-lowerer-not-implemented"),
                java.util.List.of(
                        "GPU backend lowerer is not implemented for " + backendTarget,
                        "IrGpu remains available for future " + backendTarget + " lowering, but runtime must not compile this backend yet"
                )
        );
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
