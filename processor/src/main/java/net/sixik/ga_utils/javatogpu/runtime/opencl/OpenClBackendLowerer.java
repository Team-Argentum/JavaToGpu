package net.sixik.ga_utils.javatogpu.runtime.opencl;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendCompileOptions;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendLowerer;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendModuleArtifact;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendSourceReconstructionResult;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendSourceSelectionPlan;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendSourceSwitchingDecision;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendSourceSwitchingPolicy;
import net.sixik.ga_utils.javatogpu.runtime.GpuProductionIrAcceptanceGate;
import net.sixik.ga_utils.javatogpu.runtime.GpuProductionPromotionOperatorAcceptance;
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
        GpuBackendSourceSwitchingPolicy sourceSwitchingPolicy = GpuBackendSourceSwitchingPolicy.from(
                compileRequest.options().backendOptions()
        );
        if (sourceSwitchingPolicy.irGpuSourceRequested()) {
            validateProductionSourceSwitching(compileRequest, sourceSwitchingPolicy);
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

    private void validateProductionSourceSwitching(
            GpuRuntimeCompileRequest compileRequest,
            GpuBackendSourceSwitchingPolicy sourceSwitchingPolicy
    ) {
        GpuProductionPromotionOperatorAcceptance.Result operatorAcceptance =
                GpuProductionPromotionOperatorAcceptance.evaluate(compileRequest);
        GpuProductionIrAcceptanceGate.evaluate(
                "OpenCL",
                "IrGpu source",
                compileRequest.options().optimizationProfile(),
                GpuRuntimeProductionProfiles.isProductionProfile(compileRequest.options().optimizationProfile()),
                sourceSwitchingPolicy.productionSourceSwitchingEnabled(),
                sourceSwitchingPolicy.productionPromotionDecisionMode(),
                operatorAcceptance.accepted()
        ).throwIfRejected("pass backend option "
                + GpuBackendCompileOptions.OPENCL_PRODUCTION_SOURCE_SWITCHING_PROPERTY
                + "="
                + GpuBackendCompileOptions.OPENCL_PRODUCTION_SOURCE_SWITCHING_ENABLED
                + " only after accepted production-promotion evidence is loaded; "
                + operatorAcceptance.diagnostic());
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
        if (!sourceReconstructionResult.diagnostics().contains("sourceParity.checked=true")
                || !sourceReconstructionResult.diagnostics().contains("sourceParity.matched=true")) {
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
