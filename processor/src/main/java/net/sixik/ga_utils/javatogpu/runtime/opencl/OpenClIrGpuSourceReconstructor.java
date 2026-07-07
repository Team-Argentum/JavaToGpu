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
        return reconstruct(
                compileRequest.irGpuArtifact().orElseThrow(),
                compileRequest.descriptor().kernelResource(),
                compileRequest.descriptor().kernelSource()
        );
    }

    public GpuBackendSourceReconstructionResult reconstruct(IrGpuArtifact artifact, String descriptorOpenClResource) {
        return reconstruct(artifact, descriptorOpenClResource, "");
    }

    public GpuBackendSourceReconstructionResult reconstruct(
            IrGpuArtifact artifact,
            String descriptorOpenClResource,
            String descriptorSource
    ) {
        OpenClIrGpuReconstructionPreview preview = OpenClIrGpuReconstructionPreview.inspect(
                artifact,
                descriptorOpenClResource
        );
        if (artifact == null || !canAttemptDiagnosticEmission(preview)) {
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
                    runtimeLoadMode(preview),
                    emission.blockers(),
                    diagnostics
            );
        }

        java.util.ArrayList<String> diagnostics = new java.util.ArrayList<>(preview.diagnostics());
        diagnostics.addAll(emission.diagnostics());
        diagnostics.addAll(OpenClIrGpuSourceParityComparison.compare(emission.source(), descriptorSource).diagnostics());
        return GpuBackendSourceReconstructionResult.reconstructedSource(
                    GpuBackendTarget.OPENCL,
                    emission.source(),
                    preview.selectedSource(),
                    preview.payloadFormat(),
                    runtimeLoadMode(preview),
                    diagnostics
        );
    }

    private static boolean canAttemptDiagnosticEmission(OpenClIrGpuReconstructionPreview preview) {
        return preview != null
                && preview.attempted()
                && "ir-text-v1".equals(preview.payloadFormat())
                && !preview.entryEmittedName().isBlank()
                && preview.methodBodyCount() > 0
                && !preview.blockers().contains("irgpu-artifact-missing")
                && !preview.blockers().contains("irgpu-opencl-resource-drift")
                && !preview.blockers().contains("irgpu-method-bodies-missing")
                && !preview.blockers().contains("irgpu-entry-body-missing");
    }

    private static String runtimeLoadMode(OpenClIrGpuReconstructionPreview preview) {
        return "irgpu-backend-neutral-source".equals(preview.selectedSource())
                ? "opencl-irgpu-source-compile"
                : "opencl-source-compile";
    }
}
