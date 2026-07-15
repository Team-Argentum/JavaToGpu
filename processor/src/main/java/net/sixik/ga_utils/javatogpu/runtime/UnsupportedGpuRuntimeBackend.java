package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;

/**
 * Runtime backend placeholder for backend families that are part of the public roadmap but not implemented yet.
 */
public final class UnsupportedGpuRuntimeBackend implements GpuRuntimeBackend {

    private final GpuBackendTarget backendTarget;
    private final String backendName;
    private final String diagnostic;

    public UnsupportedGpuRuntimeBackend(GpuBackendTarget backendTarget, String backendName, String diagnostic) {
        this.backendTarget = backendTarget == null ? GpuBackendTarget.UNKNOWN : backendTarget;
        this.backendName = backendName == null || backendName.isBlank() ? this.backendTarget.name() : backendName;
        this.diagnostic = diagnostic == null || diagnostic.isBlank()
                ? "Runtime backend adapter is not implemented for " + this.backendTarget
                : diagnostic;
    }

    @Override
    public GpuBackendTarget backendTarget() {
        return backendTarget;
    }

    @Override
    public GpuRuntimeBackendReport describeCapabilities() {
        return GpuRuntimeBackendReport.unavailable(backendTarget, backendName, diagnostic);
    }

    @Override
    public void invoke(GpuKernelInvocation invocation) {
        throw new UnsupportedOperationException(diagnostic);
    }
}
