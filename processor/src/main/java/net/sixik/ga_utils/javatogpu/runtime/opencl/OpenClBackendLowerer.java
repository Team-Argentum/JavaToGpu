package net.sixik.ga_utils.javatogpu.runtime.opencl;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendLowerer;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendModuleArtifact;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendSourceReconstructionResult;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendSourceSelectionPlan;
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
    public GpuBackendSourceSelectionPlan sourceSelectionPlan(GpuRuntimeCompileRequest compileRequest) {
        Objects.requireNonNull(compileRequest, "compileRequest");
        OpenClIrGpuReconstructionPlan reconstructionPlan = OpenClIrGpuReconstructionPlan.from(
                OpenClIrGpuParityChecker.check(compileRequest)
        );
        return toSourceSelectionPlan(reconstructionPlan);
    }

    @Override
    public GpuBackendModuleArtifact lower(GpuRuntimeCompileRequest compileRequest) {
        Objects.requireNonNull(compileRequest, "compileRequest");
        OpenClIrGpuParityResult parityResult = OpenClIrGpuParityChecker.check(compileRequest);
        OpenClIrGpuReconstructionPlan reconstructionPlan = OpenClIrGpuReconstructionPlan.from(parityResult);
        if (parityResult.checked() && !parityResult.compatible()) {
            throw new IllegalStateException(
                    "OpenCL IrGpu parity check failed: "
                            + parityResult.toLine()
                            + "; reconstructionPlan="
                            + reconstructionPlan.toLine()
                            + "; regenerate both kernel.cl and kernel.irgpu.properties from the same frontend output"
            );
        }
        GpuBackendSourceReconstructionResult sourceReconstructionResult = OpenClIrGpuSourceReconstructor.INSTANCE
                .reconstruct(compileRequest);
        return GpuBackendModuleArtifact.openClSource(
                compileRequest.descriptor().kernelSource(),
                compileRequest.descriptor().kernelResource(),
                VERSION,
                sourceReconstructionResult.sourceOrigin(),
                sourceReconstructionResult.runtimeLoadMode()
        );
    }

    private GpuBackendSourceSelectionPlan toSourceSelectionPlan(OpenClIrGpuReconstructionPlan reconstructionPlan) {
        Objects.requireNonNull(reconstructionPlan, "reconstructionPlan");
        return new GpuBackendSourceSelectionPlan(
                backendTarget(),
                reconstructionPlan.irGpuSourceSelected(),
                reconstructionPlan.selectedSource(),
                reconstructionPlan.payloadFormat(),
                runtimeLoadMode(reconstructionPlan),
                reconstructionPlan.blockers(),
                reconstructionPlan.diagnostics()
        );
    }

    private String runtimeLoadMode(OpenClIrGpuReconstructionPlan reconstructionPlan) {
        if (reconstructionPlan.irGpuSourceSelected()) {
            return "opencl-irgpu-source-compile";
        }
        if ("descriptor-opencl-source".equals(reconstructionPlan.selectedSource())) {
            return "opencl-descriptor-source-compile";
        }
        return "opencl-source-compile";
    }
}
