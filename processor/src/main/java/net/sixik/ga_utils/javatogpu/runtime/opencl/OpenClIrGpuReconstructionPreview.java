package net.sixik.ga_utils.javatogpu.runtime.opencl;

import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifact;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuMethodBody;
import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendIrGpuSourceSummary;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendModuleArtifact;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendSourceReconstructionResult;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileRequest;

import java.util.ArrayList;
import java.util.List;

/**
 * Non-mutating preview of the future IrGpu -> OpenCL reconstruction path.
 *
 * <p>The runtime still compiles the generated OpenCL source by default. This preview exists so tests,
 * diagnostics, and CI can prove when an IrGpu artifact has enough payload to become the backend source.
 */
public record OpenClIrGpuReconstructionPreview(
        boolean attempted,
        boolean reconstructable,
        String selectedSource,
        String payloadFormat,
        String entryEmittedName,
        int methodBodyCount,
        List<String> blockers,
        List<String> diagnostics
) {

    private static final String SOURCE_PAYLOAD_NOT_GENERATED = "backend-source-payload-not-yet-generated";

    public OpenClIrGpuReconstructionPreview {
        selectedSource = normalize(selectedSource, "descriptor-opencl-source");
        payloadFormat = normalize(payloadFormat, "unknown");
        entryEmittedName = normalize(entryEmittedName, "");
        blockers = blockers == null ? List.of() : List.copyOf(blockers);
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }

    public static OpenClIrGpuReconstructionPreview inspect(GpuRuntimeCompileRequest compileRequest) {
        if (compileRequest == null || compileRequest.irGpuArtifact().isEmpty()) {
            return new OpenClIrGpuReconstructionPreview(
                    false,
                    false,
                    "descriptor-opencl-source",
                    "unknown",
                    "",
                    0,
                    List.of("irgpu-artifact-missing"),
                    List.of("OpenCL reconstruction preview skipped because no IrGpu artifact was available")
            );
        }
        return inspect(
                compileRequest.irGpuArtifact().orElseThrow(),
                compileRequest.descriptor().kernelResource()
        );
    }

    public static OpenClIrGpuReconstructionPreview inspect(IrGpuArtifact artifact, String descriptorOpenClResource) {
        if (artifact == null) {
            return new OpenClIrGpuReconstructionPreview(
                    false,
                    false,
                    "descriptor-opencl-source",
                    "unknown",
                    "",
                    0,
                    List.of("irgpu-artifact-missing"),
                    List.of("OpenCL reconstruction preview skipped because no IrGpu artifact was available")
            );
        }

        OpenClIrGpuParityResult parityResult = OpenClIrGpuParityChecker.check(artifact, descriptorOpenClResource);
        OpenClIrGpuReconstructionPlan reconstructionPlan = OpenClIrGpuReconstructionPlan.from(parityResult);
        GpuBackendIrGpuSourceSummary sourceSummary = GpuBackendIrGpuSourceSummary.from(
                artifact,
                GpuBackendModuleArtifact.openClSource("", descriptorOpenClResource, OpenClBackendLowerer.VERSION)
        );
        List<IrGpuMethodBody> methodBodies = artifact.module().methodBodies();
        String entryEmittedName = methodBodies.stream()
                .filter(methodBody -> "entry".equals(methodBody.role()))
                .map(IrGpuMethodBody::emittedName)
                .filter(name -> !name.isBlank())
                .findFirst()
                .orElse("");

        ArrayList<String> blockers = new ArrayList<>(sourceSummary.blockers());
        if (!parityResult.compatible()) {
            addBlocker(blockers, "irgpu-opencl-resource-drift");
        }
        if (methodBodies.isEmpty()) {
            addBlocker(blockers, "irgpu-method-bodies-missing");
        }
        if (entryEmittedName.isBlank()) {
            addBlocker(blockers, "irgpu-entry-body-missing");
        }

        boolean reconstructable = reconstructionPlan.irGpuSourceSelected()
                && sourceSummary.irGpuSourceSelected()
                && parityResult.compatible()
                && !methodBodies.isEmpty()
                && !entryEmittedName.isBlank()
                && blockers.isEmpty();
        String selectedSource = reconstructable
                ? "irgpu-backend-neutral-source"
                : sourceSummary.selectedSource();
        ArrayList<String> diagnostics = new ArrayList<>(reconstructionPlan.diagnostics());
        diagnostics.add(reconstructable
                ? "OpenCL source can be reconstructed from IrGpu when the runtime source path is enabled"
                : "OpenCL source reconstruction remains blocked; runtime must use generated OpenCL fallback");

        return new OpenClIrGpuReconstructionPreview(
                true,
                reconstructable,
                selectedSource,
                sourceSummary.payloadFormat(),
                entryEmittedName,
                methodBodies.size(),
                blockers,
                diagnostics
        );
    }

    public String toLine() {
        return "attempted="
                + attempted
                + " reconstructable="
                + reconstructable
                + " selectedSource="
                + selectedSource
                + " payloadFormat="
                + payloadFormat
                + " entryEmittedName="
                + entryEmittedName
                + " methodBodyCount="
                + methodBodyCount
                + " blockers="
                + (blockers.isEmpty() ? "-" : String.join(",", blockers))
                + " diagnostics="
                + (diagnostics.isEmpty() ? "-" : String.join(" | ", diagnostics));
    }

    public String toPropertiesText() {
        StringBuilder builder = new StringBuilder();
        builder.append("attempted=").append(attempted).append('\n');
        builder.append("reconstructable=").append(reconstructable).append('\n');
        builder.append("selectedSource=").append(selectedSource).append('\n');
        builder.append("payloadFormat=").append(payloadFormat).append('\n');
        builder.append("entryEmittedName=").append(entryEmittedName).append('\n');
        builder.append("methodBody.count=").append(methodBodyCount).append('\n');
        builder.append("blocker.count=").append(blockers.size()).append('\n');
        for (int index = 0; index < blockers.size(); index++) {
            builder.append("blocker.").append(index).append('=').append(blockers.get(index)).append('\n');
        }
        builder.append("diagnostic.count=").append(diagnostics.size()).append('\n');
        for (int index = 0; index < diagnostics.size(); index++) {
            builder.append("diagnostic.").append(index).append('=').append(diagnostics.get(index)).append('\n');
        }
        return builder.toString();
    }

    public GpuBackendSourceReconstructionResult toSourceReconstructionResult() {
        if (!attempted) {
            return GpuBackendSourceReconstructionResult.notAttempted(
                    GpuBackendTarget.OPENCL,
                    selectedSource,
                    payloadFormat,
                    runtimeLoadMode(),
                    blockers,
                    diagnostics
            );
        }
        if (!reconstructable) {
            return GpuBackendSourceReconstructionResult.blocked(
                    GpuBackendTarget.OPENCL,
                    selectedSource,
                    payloadFormat,
                    runtimeLoadMode(),
                    blockers,
                    diagnostics
            );
        }
        ArrayList<String> sourceBlockers = new ArrayList<>(blockers);
        addBlocker(sourceBlockers, SOURCE_PAYLOAD_NOT_GENERATED);
        ArrayList<String> sourceDiagnostics = new ArrayList<>(diagnostics);
        sourceDiagnostics.add("OpenCL IrGpu metadata is reconstructable, but source payload emission is not implemented yet");
        return GpuBackendSourceReconstructionResult.blocked(
                GpuBackendTarget.OPENCL,
                selectedSource,
                payloadFormat,
                runtimeLoadMode(),
                sourceBlockers,
                sourceDiagnostics
        );
    }

    private String runtimeLoadMode() {
        if ("irgpu-backend-neutral-source".equals(selectedSource)) {
            return "opencl-irgpu-source-compile";
        }
        if ("descriptor-opencl-source".equals(selectedSource)) {
            return "opencl-descriptor-source-compile";
        }
        return "opencl-source-compile";
    }

    private static void addBlocker(List<String> blockers, String blocker) {
        if (!blockers.contains(blocker)) {
            blockers.add(blocker);
        }
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
