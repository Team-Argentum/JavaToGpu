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

class CudaImageSamplerRuntimeObjectBindingPlanReportTest {

    @Test
    void runtimeObjectBindingPlanReportPinsFailClosedKernelSlotCounts() {
        CudaImageSamplerRuntimeObjectBindingPlanReport report = CudaImageSamplerRuntimeObjectBindingPlanReport.inspectBuiltIns();
        Map<String, CudaImageSamplerRuntimeObjectBindingPlanReport.Case> cases = report.cases().stream()
                .collect(Collectors.toMap(CudaImageSamplerRuntimeObjectBindingPlanReport.Case::key, testCase -> testCase));
        Map<String, String> fields = report.artifactFields("test.cuda.imageSamplerRuntimeObjectBindingPlanReport");
        String rendered = CudaImageSamplerRuntimeObjectBindingPlanCli.render(report);

        assertEquals("ready", report.status());
        assertTrue(report.ready());
        assertEquals("none", report.firstBlocker());
        assertEquals(3, report.cases().size());
        assertEquals(3, report.caseReadyCount());
        assertEquals(0, report.caseBlockedCount());
        assertEquals(1, report.planReadyCount());
        assertEquals(2, report.planBlockedCount());
        assertEquals(19, report.entryCount());
        assertEquals(16, report.objectBindingCount());
        assertEquals(8, report.textureObjectBindingCount());
        assertEquals(8, report.surfaceObjectBindingCount());
        assertEquals(1, report.foldedSamplerBindingCount());
        assertEquals(16, report.plannedObjectKernelParameterSlotCount());
        assertEquals(28, report.plannedMetadataKernelParameterSlotCount());
        assertEquals(44, report.plannedKernelParameterSlotCount());
        assertEquals(0, report.runtimeBindingKernelParameterSlotCount());
        assertEquals(0, report.objectCreationCallEnabledCount());
        assertEquals(0, report.activeObjectCount());

        assertCase(cases, "all-builtins-valid", "ready", "none", 16, 8, 8, 1, 16, 28, 44);
        assertCase(
                cases,
                "image2d-handle-missing",
                "blocked",
                "cuda-image-descriptor-handle-missing:0:" + Image2DReadOnly.class.getName(),
                0,
                0,
                0,
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
                0,
                0,
                0,
                0
        );

        assertEquals("ready", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingPlanReport.status"));
        assertEquals("3", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingPlanReport.case.ready.count"));
        assertEquals("1", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingPlanReport.plan.ready.count"));
        assertEquals("2", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingPlanReport.plan.blocked.count"));
        assertEquals("19", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingPlanReport.entry.count"));
        assertEquals("16", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingPlanReport.objectBinding.count"));
        assertEquals("8", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingPlanReport.textureObjectBinding.count"));
        assertEquals("8", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingPlanReport.surfaceObjectBinding.count"));
        assertEquals("1", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingPlanReport.foldedSamplerBinding.count"));
        assertEquals("16", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingPlanReport.plannedObjectKernelParameterSlot.count"));
        assertEquals("28", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingPlanReport.plannedMetadataKernelParameterSlot.count"));
        assertEquals("44", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingPlanReport.plannedKernelParameterSlot.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingPlanReport.runtimeBinding.kernelParameterSlot.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingPlanReport.objectCreationCall.enabled.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingPlanReport.activeObject.count"));
        assertEquals("false", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingPlanReport.objectCreationCall.enabled"));
        assertEquals("false", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingPlanReport.runtimeBinding.enabled"));
        assertEquals("none", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingPlanReport.firstBlocker"));

        assertTrue(report.toMarkdown().contains("CUDA image/sampler runtime object binding plan: ready"));
        assertTrue(report.toMarkdown().contains("Object bindings: texture=8, surface=8, total=16"));
        assertTrue(rendered.contains("caseReady=3/3"));
        assertTrue(rendered.contains("plannedKernelParameterSlots=44"));
        assertTrue(rendered.contains("rule=runtime object bindings are planned only"));
    }

    @Test
    void runtimeObjectBindingPlanConnectsFutureObjectsToKernelSlotsWithoutBindingThem() {
        CudaImageSamplerRuntimeObjectBindingPlan plan = CudaImageSamplerRuntimeObjectBindingPlan.from(
                CudaImageSamplerObjectCreationRequestPlan.from(
                        CudaImageSamplerNativeDescriptorEncodingPlan.from(
                                CudaImageSamplerDescriptorPayloadModel.from(
                                        CudaImageSamplerDescriptorBuildPlan.from(
                                                List.of(
                                                        new GpuKernelParameterDescriptor("inputImage", Image2DReadOnly.class.getName(), GpuKernelParameterAccess.READ_ONLY),
                                                        new GpuKernelParameterDescriptor("outputImage", Image2DWriteOnly.class.getName(), GpuKernelParameterAccess.READ_WRITE),
                                                        new GpuKernelParameterDescriptor("sampler", Sampler.class.getName(), GpuKernelParameterAccess.VALUE)
                                                ),
                                                new Object[]{
                                                        Image2DReadOnly.borrowed(0xCAFE_7401L, 8, 4),
                                                        Image2DWriteOnly.borrowed(0xCAFE_7402L, 8, 4),
                                                        Sampler.borrowed(0xCAFE_7403L)
                                                }
                                        )
                                )
                        )
                )
        );
        Map<String, String> fields = plan.artifactFields("test.cuda.imageSamplerRuntimeObjectBindingPlan");

        assertEquals("ready", plan.status());
        assertTrue(plan.ready());
        assertEquals(3, plan.entries().size());
        assertEquals(2, plan.objectBindingCount());
        assertEquals(1, plan.textureObjectBindingCount());
        assertEquals(1, plan.surfaceObjectBindingCount());
        assertEquals(1, plan.foldedSamplerBindingCount());
        assertEquals(2, plan.plannedObjectKernelParameterSlotCount());
        assertEquals(4, plan.plannedMetadataKernelParameterSlotCount());
        assertEquals(6, plan.plannedKernelParameterSlotCount());
        assertEquals(0, plan.runtimeBindingKernelParameterSlotCount());
        assertFalse(plan.objectCreationCallEnabled());
        assertFalse(plan.objectOwnershipEnabled());
        assertFalse(plan.runtimeBindingEnabled());
        assertEquals(0, plan.objectCreationCallEnabledCount());
        assertEquals(0, plan.activeObjectCount());

        CudaImageSamplerRuntimeObjectBindingPlan.Entry texture = plan.entries().get(0);
        assertTrue(texture.textureObjectBinding());
        assertEquals("texture", texture.objectKind());
        assertEquals("CUtexObject", texture.parameterCarrier());
        assertEquals("future-cuTexObjectCreate-result", texture.objectHandleSource());
        assertEquals(1, texture.plannedObjectKernelParameterSlotCount());
        assertEquals(2, texture.plannedMetadataKernelParameterSlotCount());
        assertEquals(3, texture.plannedKernelParameterSlotCount());
        assertEquals(0, texture.runtimeBindingKernelParameterSlotCount());

        CudaImageSamplerRuntimeObjectBindingPlan.Entry surface = plan.entries().get(1);
        assertTrue(surface.surfaceObjectBinding());
        assertEquals("surface", surface.objectKind());
        assertEquals("CUsurfObject", surface.parameterCarrier());
        assertEquals("future-cuSurfObjectCreate-result", surface.objectHandleSource());
        assertEquals(1, surface.plannedObjectKernelParameterSlotCount());
        assertEquals(2, surface.plannedMetadataKernelParameterSlotCount());
        assertEquals(3, surface.plannedKernelParameterSlotCount());
        assertEquals(0, surface.runtimeBindingKernelParameterSlotCount());

        CudaImageSamplerRuntimeObjectBindingPlan.Entry sampler = plan.entries().get(2);
        assertFalse(sampler.objectBindingRequired());
        assertTrue(sampler.foldedSamplerBinding());
        assertEquals("not-required", sampler.objectKind());
        assertEquals("folded-sampler-descriptor-state", sampler.objectHandleSource());

        assertEquals("true", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingPlan.present"));
        assertEquals("ready", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingPlan.status"));
        assertEquals("2", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingPlan.objectBinding.count"));
        assertEquals("1", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingPlan.textureObjectBinding.count"));
        assertEquals("1", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingPlan.surfaceObjectBinding.count"));
        assertEquals("1", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingPlan.foldedSamplerBinding.count"));
        assertEquals("2", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingPlan.plannedObjectKernelParameterSlot.count"));
        assertEquals("4", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingPlan.plannedMetadataKernelParameterSlot.count"));
        assertEquals("6", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingPlan.plannedKernelParameterSlot.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingPlan.runtimeBinding.kernelParameterSlot.count"));
        assertEquals("false", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingPlan.objectCreationCall.enabled"));
        assertEquals("false", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingPlan.runtimeBinding.enabled"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingPlan.activeObject.count"));
        assertEquals("texture", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingPlan.entry.0.object.kind"));
        assertEquals("CUtexObject", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingPlan.entry.0.parameter.carrier"));
        assertEquals("future-cuTexObjectCreate-result", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingPlan.entry.0.objectHandle.source"));
        assertEquals("surface", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingPlan.entry.1.object.kind"));
        assertEquals("CUsurfObject", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingPlan.entry.1.parameter.carrier"));
        assertEquals("future-cuSurfObjectCreate-result", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingPlan.entry.1.objectHandle.source"));
        assertEquals("folded-sampler-descriptor-state", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingPlan.entry.2.objectHandle.source"));
    }

    private static void assertCase(
            Map<String, CudaImageSamplerRuntimeObjectBindingPlanReport.Case> cases,
            String key,
            String expectedPlanStatus,
            String expectedFirstBlocker,
            int expectedObjectBindings,
            int expectedTextureBindings,
            int expectedSurfaceBindings,
            int expectedFoldedSamplers,
            int expectedObjectSlots,
            int expectedMetadataSlots,
            int expectedKernelSlots
    ) {
        CudaImageSamplerRuntimeObjectBindingPlanReport.Case testCase = cases.get(key);
        assertNotNull(testCase, "missing runtime object-binding plan case " + key);
        assertTrue(testCase.ready(), key);
        assertEquals(expectedPlanStatus, testCase.plan().status());
        assertEquals(expectedFirstBlocker, testCase.plan().firstBlocker());
        assertEquals(expectedObjectBindings, testCase.plan().objectBindingCount());
        assertEquals(expectedTextureBindings, testCase.plan().textureObjectBindingCount());
        assertEquals(expectedSurfaceBindings, testCase.plan().surfaceObjectBindingCount());
        assertEquals(expectedFoldedSamplers, testCase.plan().foldedSamplerBindingCount());
        assertEquals(expectedObjectSlots, testCase.plan().plannedObjectKernelParameterSlotCount());
        assertEquals(expectedMetadataSlots, testCase.plan().plannedMetadataKernelParameterSlotCount());
        assertEquals(expectedKernelSlots, testCase.plan().plannedKernelParameterSlotCount());
        assertEquals(0, testCase.plan().runtimeBindingKernelParameterSlotCount());
        assertEquals(0, testCase.plan().objectCreationCallEnabledCount());
        assertEquals(0, testCase.plan().activeObjectCount());
    }
}
