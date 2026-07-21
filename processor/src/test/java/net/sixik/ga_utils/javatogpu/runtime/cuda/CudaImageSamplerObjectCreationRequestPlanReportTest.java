package net.sixik.ga_utils.javatogpu.runtime.cuda;

import net.sixik.ga_utils.javatogpu.api.Image2DReadOnly;
import net.sixik.ga_utils.javatogpu.api.Image2DWriteOnly;
import net.sixik.ga_utils.javatogpu.api.Sampler;
import net.sixik.ga_utils.javatogpu.runtime.GpuKernelParameterAccess;
import net.sixik.ga_utils.javatogpu.runtime.GpuKernelParameterDescriptor;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CudaImageSamplerObjectCreationRequestPlanReportTest {

    @Test
    void objectCreationRequestPlanReportPinsFailClosedRequestCounts() {
        CudaImageSamplerObjectCreationRequestPlanReport report = CudaImageSamplerObjectCreationRequestPlanReport.inspectBuiltIns();
        Map<String, CudaImageSamplerObjectCreationRequestPlanReport.Case> cases = report.cases().stream()
                .collect(Collectors.toMap(CudaImageSamplerObjectCreationRequestPlanReport.Case::key, testCase -> testCase));
        Map<String, String> fields = report.artifactFields("test.cuda.imageSamplerObjectCreationRequestPlanReport");
        String rendered = CudaImageSamplerObjectCreationRequestPlanCli.render(report);

        assertEquals("ready", report.status());
        assertTrue(report.ready());
        assertEquals("none", report.firstBlocker());
        assertEquals(3, report.cases().size());
        assertEquals(3, report.caseReadyCount());
        assertEquals(0, report.caseBlockedCount());
        assertEquals(1, report.planReadyCount());
        assertEquals(2, report.planBlockedCount());
        assertEquals(19, report.entryCount());
        assertEquals(16, report.objectCreationRequestCount());
        assertEquals(8, report.textureObjectRequestCount());
        assertEquals(8, report.surfaceObjectRequestCount());
        assertEquals(1, report.foldedSamplerCount());
        assertEquals(0, report.objectCreationCallEnabledCount());
        assertEquals(0, report.activeObjectCount());

        assertCase(cases, "all-builtins-valid", "ready", "none", 16, 8, 8, 1);
        assertCase(
                cases,
                "image2d-handle-missing",
                "blocked",
                "cuda-image-descriptor-handle-missing:0:" + Image2DReadOnly.class.getName(),
                0,
                0,
                0,
                0
        );
        assertCase(
                cases,
                "sampler-closed",
                "blocked",
                "cuda-sampler-descriptor-handle-closed:0:" + Sampler.class.getName(),
                0,
                0,
                0,
                0
        );

        assertEquals("ready", fields.get("runtime.cuda.imageSamplerObjectCreationRequestPlanReport.status"));
        assertEquals("3", fields.get("runtime.cuda.imageSamplerObjectCreationRequestPlanReport.case.ready.count"));
        assertEquals("1", fields.get("runtime.cuda.imageSamplerObjectCreationRequestPlanReport.plan.ready.count"));
        assertEquals("2", fields.get("runtime.cuda.imageSamplerObjectCreationRequestPlanReport.plan.blocked.count"));
        assertEquals("19", fields.get("runtime.cuda.imageSamplerObjectCreationRequestPlanReport.entry.count"));
        assertEquals("16", fields.get("runtime.cuda.imageSamplerObjectCreationRequestPlanReport.objectRequest.count"));
        assertEquals("8", fields.get("runtime.cuda.imageSamplerObjectCreationRequestPlanReport.textureObjectRequest.count"));
        assertEquals("8", fields.get("runtime.cuda.imageSamplerObjectCreationRequestPlanReport.surfaceObjectRequest.count"));
        assertEquals("1", fields.get("runtime.cuda.imageSamplerObjectCreationRequestPlanReport.foldedSampler.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerObjectCreationRequestPlanReport.objectCreationCall.enabled.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerObjectCreationRequestPlanReport.activeObject.count"));
        assertEquals("false", fields.get("runtime.cuda.imageSamplerObjectCreationRequestPlanReport.objectCreationCall.enabled"));
        assertEquals("false", fields.get("runtime.cuda.imageSamplerObjectCreationRequestPlanReport.runtimeBinding.enabled"));
        assertEquals("none", fields.get("runtime.cuda.imageSamplerObjectCreationRequestPlanReport.firstBlocker"));

        assertTrue(report.toMarkdown().contains("CUDA image/sampler object creation request plan: ready"));
        assertTrue(report.toMarkdown().contains("Object requests: texture=8, surface=8, total=16"));
        assertTrue(rendered.contains("caseReady=3/3"));
        assertTrue(rendered.contains("objectRequests=16"));
        assertTrue(rendered.contains("rule=object creation requests are planned only"));
    }

    @Test
    void objectCreationRequestPlanRecordsFutureTextureAndSurfaceCallsWithoutEnablingThem() {
        CudaImageSamplerObjectCreationRequestPlan plan = CudaImageSamplerObjectCreationRequestPlan.from(
                CudaImageSamplerNativeDescriptorEncodingPlan.from(
                        CudaImageSamplerDescriptorPayloadModel.from(
                                CudaImageSamplerDescriptorBuildPlan.from(
                                        List.of(
                                                new GpuKernelParameterDescriptor("inputImage", Image2DReadOnly.class.getName(), GpuKernelParameterAccess.READ_ONLY),
                                                new GpuKernelParameterDescriptor("outputImage", Image2DWriteOnly.class.getName(), GpuKernelParameterAccess.READ_WRITE),
                                                new GpuKernelParameterDescriptor("sampler", Sampler.class.getName(), GpuKernelParameterAccess.VALUE)
                                        ),
                                        new Object[]{
                                                Image2DReadOnly.borrowed(0xCAFE_7301L, 8, 4),
                                                Image2DWriteOnly.borrowed(0xCAFE_7302L, 8, 4),
                                                Sampler.borrowed(0xCAFE_7303L)
                                        }
                                )
                        )
                )
        );
        Map<String, String> fields = plan.artifactFields("test.cuda.imageSamplerObjectCreationRequestPlan");

        assertEquals("ready", plan.status());
        assertTrue(plan.ready());
        assertEquals(3, plan.entries().size());
        assertEquals(2, plan.objectCreationRequestCount());
        assertEquals(1, plan.textureObjectRequestCount());
        assertEquals(1, plan.surfaceObjectRequestCount());
        assertEquals(1, plan.foldedSamplerCount());
        assertFalse(plan.objectCreationCallEnabled());
        assertFalse(plan.objectOwnershipEnabled());
        assertFalse(plan.runtimeBindingEnabled());
        assertEquals(0, plan.objectCreationCallEnabledCount());
        assertEquals(0, plan.activeObjectCount());

        CudaImageSamplerObjectCreationRequestPlan.Entry texture = plan.entries().get(0);
        assertTrue(texture.textureRequest());
        assertEquals("texture", texture.objectKind());
        assertEquals("CUtexObject", texture.parameterCarrier());
        assertEquals("cuTexObjectCreate", texture.createFunctionSymbol());
        assertEquals("cuTexObjectDestroy", texture.destroyFunctionSymbol());
        assertEquals("disabled", texture.objectCreationCallStatus());
        assertEquals("fail-closed", texture.runtimeBindingStatus());

        CudaImageSamplerObjectCreationRequestPlan.Entry surface = plan.entries().get(1);
        assertTrue(surface.surfaceRequest());
        assertEquals("surface", surface.objectKind());
        assertEquals("CUsurfObject", surface.parameterCarrier());
        assertEquals("cuSurfObjectCreate", surface.createFunctionSymbol());
        assertEquals("cuSurfObjectDestroy", surface.destroyFunctionSymbol());
        assertEquals("disabled", surface.objectCreationCallStatus());
        assertEquals("fail-closed", surface.runtimeBindingStatus());

        CudaImageSamplerObjectCreationRequestPlan.Entry sampler = plan.entries().get(2);
        assertFalse(sampler.objectCreationRequestRequired());
        assertTrue(sampler.samplerFolded());
        assertEquals("not-required", sampler.objectKind());
        assertEquals("not-required", sampler.createFunctionSymbol());

        assertEquals("true", fields.get("runtime.cuda.imageSamplerObjectCreationRequestPlan.present"));
        assertEquals("ready", fields.get("runtime.cuda.imageSamplerObjectCreationRequestPlan.status"));
        assertEquals("2", fields.get("runtime.cuda.imageSamplerObjectCreationRequestPlan.request.count"));
        assertEquals("1", fields.get("runtime.cuda.imageSamplerObjectCreationRequestPlan.textureObjectRequest.count"));
        assertEquals("1", fields.get("runtime.cuda.imageSamplerObjectCreationRequestPlan.surfaceObjectRequest.count"));
        assertEquals("1", fields.get("runtime.cuda.imageSamplerObjectCreationRequestPlan.foldedSampler.count"));
        assertEquals("false", fields.get("runtime.cuda.imageSamplerObjectCreationRequestPlan.objectCreationCall.enabled"));
        assertEquals("false", fields.get("runtime.cuda.imageSamplerObjectCreationRequestPlan.runtimeBinding.enabled"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerObjectCreationRequestPlan.activeObject.count"));
        assertEquals("texture", fields.get("runtime.cuda.imageSamplerObjectCreationRequestPlan.entry.0.object.kind"));
        assertEquals("CUtexObject", fields.get("runtime.cuda.imageSamplerObjectCreationRequestPlan.entry.0.parameter.carrier"));
        assertEquals("cuTexObjectCreate", fields.get("runtime.cuda.imageSamplerObjectCreationRequestPlan.entry.0.createFunction.symbol"));
        assertEquals("surface", fields.get("runtime.cuda.imageSamplerObjectCreationRequestPlan.entry.1.object.kind"));
        assertEquals("CUsurfObject", fields.get("runtime.cuda.imageSamplerObjectCreationRequestPlan.entry.1.parameter.carrier"));
        assertEquals("cuSurfObjectCreate", fields.get("runtime.cuda.imageSamplerObjectCreationRequestPlan.entry.1.createFunction.symbol"));
        assertEquals("not-required", fields.get("runtime.cuda.imageSamplerObjectCreationRequestPlan.entry.2.object.kind"));
    }

    private static void assertCase(
            Map<String, CudaImageSamplerObjectCreationRequestPlanReport.Case> cases,
            String key,
            String expectedPlanStatus,
            String expectedFirstBlocker,
            int expectedObjectRequests,
            int expectedTextureRequests,
            int expectedSurfaceRequests,
            int expectedFoldedSamplers
    ) {
        CudaImageSamplerObjectCreationRequestPlanReport.Case testCase = cases.get(key);
        assertNotNull(testCase, "missing object-creation request-plan case " + key);
        assertTrue(testCase.ready(), key);
        assertEquals(expectedPlanStatus, testCase.plan().status());
        assertEquals(expectedFirstBlocker, testCase.plan().firstBlocker());
        assertEquals(expectedObjectRequests, testCase.plan().objectCreationRequestCount());
        assertEquals(expectedTextureRequests, testCase.plan().textureObjectRequestCount());
        assertEquals(expectedSurfaceRequests, testCase.plan().surfaceObjectRequestCount());
        assertEquals(expectedFoldedSamplers, testCase.plan().foldedSamplerCount());
        assertEquals(0, testCase.plan().objectCreationCallEnabledCount());
        assertEquals(0, testCase.plan().activeObjectCount());
    }
}
