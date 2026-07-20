package net.sixik.ga_utils.javatogpu.runtime.cuda;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendLowerer;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendLoweringResult;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendModuleArtifact;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendSourceReconstructionResult;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendSourceSelectionPlan;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileRequest;

import java.util.List;
import java.util.Objects;

/**
 * Preview CUDA-C lowerer for the first backend-neutral IrGpu source slice.
 */
public final class CudaBackendLowerer implements GpuBackendLowerer {

    public static final String VERSION = "cuda-source-preview-v1";

    @Override
    public GpuBackendTarget backendTarget() {
        return GpuBackendTarget.CUDA;
    }

    @Override
    public String lowererVersion() {
        return VERSION;
    }

    @Override
    public GpuBackendSourceSelectionPlan sourceSelectionPlan(GpuRuntimeCompileRequest compileRequest) {
        GpuBackendSourceReconstructionResult reconstruction = CudaIrGpuSourceReconstructor.INSTANCE.reconstruct(compileRequest);
        return new GpuBackendSourceSelectionPlan(
                GpuBackendTarget.CUDA,
                reconstruction.reconstructed(),
                reconstruction.selectedSource(),
                reconstruction.payloadFormat(),
                reconstruction.runtimeLoadMode(),
                reconstruction.blockers(),
                reconstruction.diagnostics()
        );
    }

    @Override
    public GpuBackendLoweringResult lowerWithStageResult(GpuRuntimeCompileRequest compileRequest) {
        GpuBackendSourceReconstructionResult reconstruction = CudaIrGpuSourceReconstructor.INSTANCE.reconstruct(compileRequest);
        GpuBackendSourceSelectionPlan plan = new GpuBackendSourceSelectionPlan(
                GpuBackendTarget.CUDA,
                reconstruction.reconstructed(),
                reconstruction.selectedSource(),
                reconstruction.payloadFormat(),
                reconstruction.runtimeLoadMode(),
                reconstruction.blockers(),
                reconstruction.diagnostics()
        );
        if (!reconstruction.reconstructed()) {
            return GpuBackendLoweringResult.unsupported(
                    GpuBackendTarget.CUDA,
                    plan,
                    reconstruction.blockers().isEmpty() ? List.of("cuda-source-reconstruction-unavailable") : reconstruction.blockers(),
                    reconstruction.diagnostics()
            );
        }
        return GpuBackendLoweringResult.succeeded(
                moduleArtifact(compileRequest, reconstruction),
                plan,
                reconstruction.diagnostics()
        );
    }

    @Override
    public GpuBackendModuleArtifact lower(GpuRuntimeCompileRequest compileRequest) {
        Objects.requireNonNull(compileRequest, "compileRequest");
        GpuBackendSourceReconstructionResult reconstruction = CudaIrGpuSourceReconstructor.INSTANCE.reconstruct(compileRequest);
        if (!reconstruction.reconstructed()) {
            throw new IllegalStateException(
                    "CUDA source reconstruction is not available: " + reconstruction.toLine()
            );
        }
        return moduleArtifact(compileRequest, reconstruction);
    }

    private static GpuBackendModuleArtifact moduleArtifact(
            GpuRuntimeCompileRequest compileRequest,
            GpuBackendSourceReconstructionResult reconstruction
    ) {
        String resource = compileRequest == null || compileRequest.descriptor() == null
                ? "irgpu-cuda-source-preview.cu"
                : compileRequest.descriptor().kernelResource() + "#irgpu-cuda-preview";
        return GpuBackendModuleArtifact.cudaSource(
                reconstruction.source(),
                resource,
                VERSION
        );
    }
}
