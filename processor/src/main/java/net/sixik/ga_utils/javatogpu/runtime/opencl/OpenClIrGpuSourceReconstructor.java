package net.sixik.ga_utils.javatogpu.runtime.opencl;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifact;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendSourceReconstructionResult;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendSourceReconstructor;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileRequest;

/**
 * OpenCL source reconstruction entrypoint.
 *
 * <p>This is intentionally non-mutating for now: it reports the same reconstruction decision used by
 * diagnostics, but does not synthesize OpenCL source until the IrGpu body payload is fully reconstructable.
 */
public final class OpenClIrGpuSourceReconstructor implements GpuBackendSourceReconstructor {

    public static final OpenClIrGpuSourceReconstructor INSTANCE = new OpenClIrGpuSourceReconstructor();
    public static final String VERSION = "opencl-irgpu-source-reconstructor-preview-v1";

    private OpenClIrGpuSourceReconstructor() {
    }

    @Override
    public GpuBackendTarget backendTarget() {
        return GpuBackendTarget.OPENCL;
    }

    @Override
    public String version() {
        return VERSION;
    }

    @Override
    public GpuBackendSourceReconstructionResult reconstruct(GpuRuntimeCompileRequest compileRequest) {
        if (compileRequest == null || compileRequest.irGpuArtifact().isEmpty()) {
            return OpenClIrGpuReconstructionPreview.inspect(compileRequest).toSourceReconstructionResult();
        }
        return reconstruct(compileRequest.irGpuArtifact().orElseThrow(), compileRequest.descriptor().kernelResource());
    }

    public GpuBackendSourceReconstructionResult reconstruct(IrGpuArtifact artifact, String descriptorOpenClResource) {
        OpenClIrGpuReconstructionPreview preview = OpenClIrGpuReconstructionPreview.inspect(
                artifact,
                descriptorOpenClResource
        );
        if (!preview.reconstructable() || artifact == null) {
            return preview.toSourceReconstructionResult();
        }

        OpenClIrGpuSourceEmission emission = OpenClIrGpuSourceEmission.inspect(artifact);
        if (!emission.sourceGenerated()) {
            java.util.ArrayList<String> diagnostics = new java.util.ArrayList<>(preview.diagnostics());
            diagnostics.addAll(emission.diagnostics());
            return GpuBackendSourceReconstructionResult.blocked(
                    GpuBackendTarget.OPENCL,
                    preview.selectedSource(),
                    preview.payloadFormat(),
                    "opencl-irgpu-source-compile",
                    emission.blockers(),
                    diagnostics
            );
        }

        java.util.ArrayList<String> diagnostics = new java.util.ArrayList<>(preview.diagnostics());
        diagnostics.addAll(emission.diagnostics());
        return GpuBackendSourceReconstructionResult.reconstructedSource(
                    GpuBackendTarget.OPENCL,
                    emission.source(),
                    preview.selectedSource(),
                    preview.payloadFormat(),
                    "opencl-irgpu-source-compile",
                    diagnostics
        );
    }
}
