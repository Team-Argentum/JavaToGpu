package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.runtime.validation.GpuBackendSourceLoweringContractCli;
import net.sixik.ga_utils.javatogpu.runtime.validation.GpuBackendSourceLoweringContractReport;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuBackendSourceLoweringContractReportTest {

    @Test
    void builtInsExposeSharedSourceLoweringContractWithoutNativeRuntime() {
        GpuBackendSourceLoweringContractReport report = GpuBackendSourceLoweringContractReport.inspectBuiltIns();
        Map<String, String> fields = report.artifactFields("test.sourceLowering");
        String rendered = GpuBackendSourceLoweringContractCli.render(report);

        assertEquals("ready", report.status());
        assertTrue(report.ready());
        assertEquals("none", report.firstBlocker());
        assertEquals(4, report.entries().size());
        assertEquals(4, report.readyEntryCount());
        assertEquals(2, report.plannedUnsupportedCount());
        assertEquals(2, report.plannedEntryCount());
        assertEquals(1, report.previewLoweringCount());
        assertEquals("ready", fields.get("runtime.backend.sourceLoweringContract.status"));
        assertEquals("4", fields.get("runtime.backend.sourceLoweringContract.entry.count"));
        assertEquals("4", fields.get("runtime.backend.sourceLoweringContract.entry.ready.count"));
        assertEquals("2", fields.get("runtime.backend.sourceLoweringContract.planned.unsupported.count"));
        assertEquals("1", fields.get("runtime.backend.sourceLoweringContract.preview.lowering.count"));
        assertEquals("none", fields.get("runtime.backend.sourceLoweringContract.firstBlocker"));
        assertTrue(rendered.contains("Backend source/lowering contract:"), rendered);
        assertTrue(rendered.contains("readyEntries=4/4"), rendered);
        assertTrue(rendered.contains("plannedUnsupported=2/2"), rendered);
        assertTrue(rendered.contains("previewLowering=1"), rendered);
        assertTrue(rendered.contains("entry.OPENCL=status:ready,stage:SUCCEEDED,lowered:true"), rendered);
        assertTrue(rendered.contains("entry.CUDA=status:ready,stage:SUCCEEDED,lowered:true"), rendered);
    }

    @Test
    void openClEntryRequiresSourceLikeOpenClArtifact() {
        GpuBackendSourceLoweringContractReport.Entry openCl = GpuBackendSourceLoweringContractReport.inspectBuiltIns()
                .entries()
                .stream()
                .filter(entry -> entry.backendTarget() == GpuBackendTarget.OPENCL)
                .findFirst()
                .orElseThrow();
        Map<String, String> fields = openCl.artifactFields("entry.opencl");

        assertEquals("ready", openCl.status());
        assertEquals(GpuBackendStageStatus.SUCCEEDED, openCl.loweringResult().stageResult().status());
        assertTrue(openCl.loweringResult().lowered());
        assertEquals("descriptor-opencl-source", openCl.sourceSelectionPlan().selectedSource());
        assertEquals("opencl-c", openCl.loweringResult().moduleArtifact().moduleFormat().key());
        assertEquals("opencl-c", fields.get("entry.opencl.module.format.canonical"));
        assertEquals("true", fields.get("entry.opencl.module.sourceAvailable"));
    }

    @Test
    void cudaEntryRequiresPreviewCudaSourceArtifact() {
        GpuBackendSourceLoweringContractReport.Entry cuda = GpuBackendSourceLoweringContractReport.inspectBuiltIns()
                .entries()
                .stream()
                .filter(entry -> entry.backendTarget() == GpuBackendTarget.CUDA)
                .findFirst()
                .orElseThrow();
        Map<String, String> fields = cuda.artifactFields("entry.cuda");

        assertEquals("ready", cuda.status());
        assertEquals(GpuBackendStageStatus.SUCCEEDED, cuda.loweringResult().stageResult().status());
        assertTrue(cuda.loweringResult().lowered());
        assertTrue(cuda.previewLoweringExpected());
        assertTrue(!cuda.productionLoweringExpected());
        assertEquals("irgpu-cuda-source", cuda.sourceSelectionPlan().selectedSource());
        assertEquals("ir-text-v1", cuda.sourceSelectionPlan().payloadFormat());
        assertEquals("cuda-c-source-preview", cuda.sourceSelectionPlan().runtimeLoadMode());
        assertEquals("cuda-c", cuda.loweringResult().moduleArtifact().moduleFormat().key());
        assertEquals("cuda-c", fields.get("entry.cuda.module.format.canonical"));
        assertEquals("true", fields.get("entry.cuda.module.sourceAvailable"));
    }

    @Test
    void plannedEntriesRequireStructuredUnsupportedLowering() {
        GpuBackendSourceLoweringContractReport.Entry vulkan = GpuBackendSourceLoweringContractReport.inspectBuiltIns()
                .entries()
                .stream()
                .filter(entry -> entry.backendTarget() == GpuBackendTarget.VULKAN)
                .findFirst()
                .orElseThrow();

        assertEquals("ready", vulkan.status());
        assertEquals(GpuBackendStageStatus.UNSUPPORTED, vulkan.loweringResult().stageResult().status());
        assertTrue(!vulkan.loweringResult().lowered());
        assertEquals("vulkan-lowerer-unavailable", vulkan.sourceSelectionPlan().selectedSource());
        assertEquals("irgpu-unlowered", vulkan.sourceSelectionPlan().payloadFormat());
        assertEquals("vulkan-unsupported", vulkan.sourceSelectionPlan().runtimeLoadMode());
        assertTrue(vulkan.sourceSelectionPlan().blockers().contains("vulkan-lowerer-not-implemented"));
    }

    @Test
    void blocksIfPlannedBackendLowersBeforeContractGateChanges() {
        GpuBackendSourceSelectionPlan plan = GpuBackendSourceSelectionPlan.descriptorSource(
                GpuBackendTarget.CUDA,
                GpuBackendModuleFormat.CUDA_C.key(),
                "test premature CUDA source"
        );
        GpuBackendLoweringResult loweringResult = GpuBackendLoweringResult.succeeded(
                GpuBackendModuleArtifact.cudaSource(
                        "extern \"C\" __global__ void kernel(int* output) { output[0] = 1; }",
                        "test/kernel.cu",
                        "test"
                ),
                plan,
                List.of("test premature CUDA lowering")
        );
        GpuBackendSourceLoweringContractReport.Entry entry = new GpuBackendSourceLoweringContractReport.Entry(
                GpuBackendTarget.CUDA,
                "backend-lowerer:cuda",
                "test",
                false,
                plan,
                loweringResult
        );
        GpuBackendSourceLoweringContractReport report = new GpuBackendSourceLoweringContractReport(List.of(entry));

        assertEquals("blocked", report.status());
        assertTrue(report.blockers().contains("CUDA:planned-lowering-stage-not-unsupported:SUCCEEDED"));
        assertTrue(report.blockers().contains("CUDA:planned-backend-lowered-before-contract-update"));
    }

    @Test
    void sourceSelectionPlanExposesPortableArtifactFields() {
        GpuBackendSourceSelectionPlan plan = new GpuBackendSourceSelectionPlan(
                GpuBackendTarget.CUDA,
                false,
                "cuda-lowerer-unavailable",
                "irgpu-unlowered",
                "cuda-unsupported",
                List.of("cuda-lowerer-not-implemented"),
                List.of("planned backend")
        );
        Map<String, String> fields = plan.artifactFields("test.plan");

        assertEquals("true", fields.get("runtime.backend.sourceSelection.present"));
        assertEquals("CUDA", fields.get("runtime.backend.sourceSelection.backendTarget"));
        assertEquals("false", fields.get("runtime.backend.sourceSelection.irGpuSourceSelected"));
        assertEquals("cuda-lowerer-unavailable", fields.get("runtime.backend.sourceSelection.selectedSource"));
        assertEquals("irgpu-unlowered", fields.get("runtime.backend.sourceSelection.payloadFormat"));
        assertEquals("cuda-unsupported", fields.get("runtime.backend.sourceSelection.runtimeLoadMode"));
        assertEquals("1", fields.get("runtime.backend.sourceSelection.blocker.count"));
        assertEquals("CUDA", fields.get("runtime.backend.target"));
    }
}
