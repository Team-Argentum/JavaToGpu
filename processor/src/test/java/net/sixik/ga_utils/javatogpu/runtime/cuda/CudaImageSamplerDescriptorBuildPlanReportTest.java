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

class CudaImageSamplerDescriptorBuildPlanReportTest {

    @Test
    void descriptorBuildPlanReportPinsValidAndBlockedPreflightCases() {
        CudaImageSamplerDescriptorBuildPlanReport report = CudaImageSamplerDescriptorBuildPlanReport.inspectBuiltIns();
        Map<String, CudaImageSamplerDescriptorBuildPlanReport.Case> cases = report.cases().stream()
                .collect(Collectors.toMap(CudaImageSamplerDescriptorBuildPlanReport.Case::key, testCase -> testCase));
        Map<String, String> fields = report.artifactFields("test.cuda.imageSamplerDescriptorBuildPlanReport");
        String rendered = CudaImageSamplerDescriptorBuildPlanCli.render(report);

        assertEquals("ready", report.status());
        assertTrue(report.ready());
        assertEquals("none", report.firstBlocker());
        assertEquals(7, report.cases().size());
        assertEquals(7, report.caseReadyCount());
        assertEquals(0, report.caseBlockedCount());
        assertEquals(4, report.planReadyCount());
        assertEquals(3, report.planBlockedCount());
        assertEquals(9, report.entryCount());
        assertEquals(6, report.resourceDescriptorPayloadPlannedCount());
        assertEquals(7, report.textureDescriptorPayloadPlannedCount());
        assertEquals(0, report.activeDescriptorPayloadCount());
        assertEquals(0, report.activeNativeDescriptorCount());
        assertEquals("ready", report.nativeDescriptorLayoutStatus());
        assertTrue(report.nativeDescriptorLayoutReady());

        assertCase(cases, "image2d-read-only", "ready", "none");
        assertCase(cases, "image2d-write-only", "ready", "none");
        assertCase(cases, "sampler-value", "ready", "none");
        assertCase(cases, "image-sampler-mixed", "ready", "none");
        assertCase(
                cases,
                "image2d-handle-missing",
                "blocked",
                "cuda-image-descriptor-handle-missing:0:" + Image2DReadOnly.class.getName()
        );
        assertCase(
                cases,
                "sampler-closed",
                "blocked",
                "cuda-sampler-descriptor-handle-closed:0:" + Sampler.class.getName()
        );
        assertCase(
                cases,
                "image2d-height-missing",
                "blocked",
                "cuda-image-descriptor-height-missing:0:" + Image2DReadOnly.class.getName()
        );

        assertEquals("ready", fields.get("runtime.cuda.imageSamplerDescriptorBuildPlanReport.status"));
        assertEquals("7", fields.get("runtime.cuda.imageSamplerDescriptorBuildPlanReport.case.ready.count"));
        assertEquals("4", fields.get("runtime.cuda.imageSamplerDescriptorBuildPlanReport.plan.ready.count"));
        assertEquals("3", fields.get("runtime.cuda.imageSamplerDescriptorBuildPlanReport.plan.blocked.count"));
        assertEquals("9", fields.get("runtime.cuda.imageSamplerDescriptorBuildPlanReport.entry.count"));
        assertEquals("6", fields.get("runtime.cuda.imageSamplerDescriptorBuildPlanReport.resourceDescriptorPayload.planned.count"));
        assertEquals("7", fields.get("runtime.cuda.imageSamplerDescriptorBuildPlanReport.textureDescriptorPayload.planned.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerDescriptorBuildPlanReport.activeDescriptorPayload.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerDescriptorBuildPlanReport.activeNativeDescriptor.count"));
        assertEquals("ready", fields.get("runtime.cuda.imageSamplerDescriptorBuildPlanReport.nativeDescriptorLayout.status"));
        assertEquals("true", fields.get("runtime.cuda.imageSamplerDescriptorBuildPlanReport.nativeDescriptorLayout.ready"));
        assertEquals("none", fields.get("runtime.cuda.imageSamplerDescriptorBuildPlanReport.firstBlocker"));

        assertTrue(report.toMarkdown().contains("CUDA image/sampler descriptor build plan: ready"));
        assertTrue(report.toMarkdown().contains("Plans: ready=4, blocked=3"));
        assertTrue(rendered.contains("caseReady=7/7"));
        assertTrue(rendered.contains("planBlocked=3"));
        assertTrue(rendered.contains("rule=descriptor build planning validates Java image/sampler arguments"));
    }

    @Test
    void invocationBuildPlanRecordsDescriptorPayloadShapeWithoutNativeAllocation() {
        CudaImageSamplerDescriptorBuildPlan plan = CudaImageSamplerDescriptorBuildPlan.from(
                List.of(
                        new GpuKernelParameterDescriptor("inputImage", Image2DReadOnly.class.getName(), GpuKernelParameterAccess.READ_ONLY),
                        new GpuKernelParameterDescriptor("sampler", Sampler.class.getName(), GpuKernelParameterAccess.VALUE),
                        new GpuKernelParameterDescriptor("outputImage", Image2DWriteOnly.class.getName(), GpuKernelParameterAccess.READ_WRITE)
                ),
                new Object[]{
                        Image2DReadOnly.borrowed(0xCAFE_5101L, 8, 4),
                        Sampler.borrowed(0xCAFE_5102L),
                        Image2DWriteOnly.borrowed(0xCAFE_5103L, 8, 4)
                }
        );
        Map<String, String> fields = plan.artifactFields("test.cuda.imageSamplerDescriptorBuildPlan");

        assertEquals("ready", plan.status());
        assertTrue(plan.ready());
        assertEquals("none", plan.firstBlocker());
        assertEquals(3, plan.entries().size());
        assertEquals(2, plan.imageEntryCount());
        assertEquals(1, plan.samplerEntryCount());
        assertEquals(2, plan.resourceDescriptorPayloadPlannedCount());
        assertEquals(2, plan.textureDescriptorPayloadPlannedCount());
        assertEquals(3, plan.compatibleArgumentCount());
        assertEquals(3, plan.validHandleCount());
        assertEquals(3, plan.metadataAvailableCount());
        assertFalse(plan.descriptorPayloadBuildEnabled());
        assertFalse(plan.nativeDescriptorAllocationEnabled());
        assertFalse(plan.objectCreationEnabled());
        assertEquals(0, plan.activeDescriptorPayloadCount());
        assertEquals(0, plan.activeNativeDescriptorCount());

        assertEquals("ready", fields.get("runtime.cuda.imageSamplerDescriptorBuildPlan.status"));
        assertEquals("true", fields.get("runtime.cuda.imageSamplerDescriptorBuildPlan.javaDescriptorPlan.enabled"));
        assertEquals("false", fields.get("runtime.cuda.imageSamplerDescriptorBuildPlan.descriptorPayloadBuild.enabled"));
        assertEquals("false", fields.get("runtime.cuda.imageSamplerDescriptorBuildPlan.nativeDescriptorAllocation.enabled"));
        assertEquals("false", fields.get("runtime.cuda.imageSamplerDescriptorBuildPlan.objectCreation.enabled"));
        assertEquals("3", fields.get("runtime.cuda.imageSamplerDescriptorBuildPlan.entry.count"));
        assertEquals("2", fields.get("runtime.cuda.imageSamplerDescriptorBuildPlan.resourceDescriptorPayload.planned.count"));
        assertEquals("2", fields.get("runtime.cuda.imageSamplerDescriptorBuildPlan.textureDescriptorPayload.planned.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerDescriptorBuildPlan.activeDescriptorPayload.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerDescriptorBuildPlan.activeNativeDescriptor.count"));
        assertEquals("8", fields.get("runtime.cuda.imageSamplerDescriptorBuildPlan.entry.0.argument.metadata.width"));
        assertEquals("4", fields.get("runtime.cuda.imageSamplerDescriptorBuildPlan.entry.0.argument.metadata.height"));
        assertEquals("true", fields.get("runtime.cuda.imageSamplerDescriptorBuildPlan.entry.0.argument.handle.valid"));
        assertEquals("planned", fields.get("runtime.cuda.imageSamplerDescriptorBuildPlan.entry.0.descriptorPayload.status"));
        assertEquals("disabled", fields.get("runtime.cuda.imageSamplerDescriptorBuildPlan.entry.0.nativeDescriptorAllocation.status"));
    }

    private static void assertCase(
            Map<String, CudaImageSamplerDescriptorBuildPlanReport.Case> cases,
            String key,
            String expectedPlanStatus,
            String expectedFirstBlocker
    ) {
        CudaImageSamplerDescriptorBuildPlanReport.Case testCase = cases.get(key);
        assertNotNull(testCase, "missing descriptor build-plan case " + key);
        assertTrue(testCase.ready(), key);
        assertEquals(expectedPlanStatus, testCase.plan().status());
        assertEquals(expectedFirstBlocker, testCase.plan().firstBlocker());
    }
}
