package net.sixik.ga_utils.javatogpu.runtime.opencl;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendCompileOptions;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendLowerer;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendModuleArtifact;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendSourceReconstructionResult;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendSourceSelectionPlan;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileRequest;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeProductionProfiles;

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
        if (compileRequest.options().backendOptions().requestsOpenClIrGpuSource()) {
            validateProductionSourceSwitching(compileRequest);
            return lowerIrGpuSource(compileRequest, sourceReconstructionResult);
        }
        return GpuBackendModuleArtifact.openClSource(
                compileRequest.descriptor().kernelSource(),
                compileRequest.descriptor().kernelResource(),
                VERSION,
                sourceReconstructionResult.sourceOrigin(),
                sourceReconstructionResult.runtimeLoadMode()
        );
    }

    private void validateProductionSourceSwitching(GpuRuntimeCompileRequest compileRequest) {
        if (!GpuRuntimeProductionProfiles.isProductionProfile(compileRequest.options().optimizationProfile())) {
            return;
        }
        if (compileRequest.options().backendOptions().enablesOpenClProductionSourceSwitching()) {
            return;
        }
        throw new IllegalStateException(
                "OpenCL IrGpu source compilation was requested for production-like optimization profile '"
                        + compileRequest.options().optimizationProfile()
                        + "', but production source switching is disabled; pass backend option "
                        + GpuBackendCompileOptions.OPENCL_PRODUCTION_SOURCE_SWITCHING_PROPERTY
                        + "="
                        + GpuBackendCompileOptions.OPENCL_PRODUCTION_SOURCE_SWITCHING_ENABLED
                        + " only after A1/A2 promotion evidence is accepted"
        );
    }

    private GpuBackendModuleArtifact lowerIrGpuSource(
            GpuRuntimeCompileRequest compileRequest,
            GpuBackendSourceReconstructionResult sourceReconstructionResult
    ) {
        if (!sourceReconstructionResult.reconstructed() || !sourceReconstructionResult.sourceAvailable()) {
            throw new IllegalStateException(
                    "OpenCL IrGpu source compilation was requested with backend option "
                            + GpuBackendCompileOptions.OPENCL_SOURCE_SELECTION_PROPERTY
                            + "="
                            + GpuBackendCompileOptions.OPENCL_SOURCE_SELECTION_IRGPU
                            + ", but reconstructed source is not available: "
                            + sourceReconstructionResult.toLine()
            );
        }
        if (!sourceReconstructionResult.diagnostics().contains("sourceParity.matched=true")) {
            throw new IllegalStateException(
                    "OpenCL IrGpu source compilation was requested with backend option "
                            + GpuBackendCompileOptions.OPENCL_SOURCE_SELECTION_PROPERTY
                            + "="
                            + GpuBackendCompileOptions.OPENCL_SOURCE_SELECTION_IRGPU
                            + ", but reconstructed source parity has not matched descriptor source: "
                            + sourceReconstructionResult.toLine()
            );
        }
        return GpuBackendModuleArtifact.openClSource(
                sourceReconstructionResult.source(),
                compileRequest.descriptor().kernelResource() + "#irgpu-reconstructed",
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
