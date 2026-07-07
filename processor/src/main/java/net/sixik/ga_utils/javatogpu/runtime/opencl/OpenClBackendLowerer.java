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
        OpenClIrGpuParityResult parityResult = OpenClIrGpuParityChecker.check(compileRequest);
        if (parityResult.checked() && !parityResult.compatible()) {
            throw new IllegalStateException(
                    "OpenCL IrGpu parity check failed: "
                            + parityResult.toLine()
                            + "; regenerate both kernel.cl and kernel.irgpu.properties from the same frontend output"
            );
        }
        return GpuBackendModuleArtifact.openClSource(
                compileRequest.descriptor().kernelSource(),
                compileRequest.descriptor().kernelResource(),
                VERSION
        );
    }
}
