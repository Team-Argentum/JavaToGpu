package net.sixik.ga_utils.javatogpu.runtime.cuda;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CudaImageSamplerContractReportTest {

    @Test
    void imageAndSamplerContractIsRecognizedButFailClosed() {
        CudaImageSamplerContractReport report = CudaImageSamplerContractReport.inspectBuiltIns();
        Map<String, CudaImageSamplerContractReport.Case> cases = report.cases().stream()
                .collect(Collectors.toMap(CudaImageSamplerContractReport.Case::key, testCase -> testCase));
        Map<String, String> fields = report.artifactFields("test.cuda.imageSamplerContract");

        assertEquals("ready", report.status());
        assertTrue(report.ready());
        assertEquals("none", report.firstBlocker());
        assertEquals(4, report.cases().size());
        assertEquals(4, report.readyCount());
        assertEquals(0, report.blockedCount());
        assertCase(cases, "image2d-read-only");
        assertCase(cases, "image2d-write-only");
        assertCase(cases, "sampler-value");
        assertCase(cases, "image-sampler-mixed");
        assertEquals("ready", fields.get("runtime.cuda.imageSamplerContract.status"));
        assertEquals("4", fields.get("runtime.cuda.imageSamplerContract.case.count"));
        assertEquals("4", fields.get("runtime.cuda.imageSamplerContract.case.ready.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerContract.case.blocked.count"));
        assertEquals("false", fields.get("runtime.cuda.imageSamplerContract.productionSupport.enabled"));
        assertEquals("6", fields.get("runtime.cuda.imageSamplerContract.runtimeBindingPlan.entry.count"));
        assertEquals("12", fields.get("runtime.cuda.imageSamplerContract.runtimeBindingPlan.sourcePreviewKernelParameterSlot.count"));
        assertEquals("12", fields.get("runtime.cuda.imageSamplerContract.runtimeBindingPlan.plannedKernelParameterSlot.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerContract.runtimeBindingPlan.kernelParameterSlot.count"));
        assertEquals("true", fields.get("test.cuda.imageSamplerContract.case.0.binding.imageSamplerRuntimeBindingPlan.present"));
        assertEquals("fail-closed", fields.get("test.cuda.imageSamplerContract.case.0.binding.imageSamplerRuntimeBindingPlan.status"));
        assertEquals("1", fields.get("test.cuda.imageSamplerContract.case.0.binding.imageSamplerRuntimeBindingPlan.entry.count"));
        assertEquals("3", fields.get("test.cuda.imageSamplerContract.case.0.binding.imageSamplerRuntimeBindingPlan.runtimeBinding.plannedKernelParameterSlot.count"));
        assertEquals("0", fields.get("test.cuda.imageSamplerContract.case.0.binding.imageSamplerRuntimeBindingPlan.runtimeBinding.kernelParameterSlot.count"));
        assertTrue(report.toMarkdown().contains("CUDA image/sampler contract: ready"));
        assertTrue(report.toMarkdown().contains("Runtime binding plan: 6 entries, 12 planned slots, 0 active slots"));
        assertTrue(CudaImageSamplerContractCli.render(report).contains("caseReady=4/4"));
        assertTrue(CudaImageSamplerContractCli.render(report).contains("productionSupportEnabled=false"));
        assertTrue(CudaImageSamplerContractCli.render(report).contains("runtimeBindingPlanEntries=6"));
        assertTrue(CudaImageSamplerContractCli.render(report).contains("runtimeBindingPlanActiveSlots=0"));
    }

    private static void assertCase(Map<String, CudaImageSamplerContractReport.Case> cases, String key) {
        CudaImageSamplerContractReport.Case testCase = cases.get(key);
        assertNotNull(testCase, "missing case " + key);
        assertTrue(testCase.ready(), key);
        assertEquals("unsupported", testCase.bindingResult().status());
        assertTrue(testCase.bindingResult().argumentFrame() == null);
        assertTrue(testCase.bindingResult().blockers().containsAll(testCase.expectedBlockers()), key);
    }
}
