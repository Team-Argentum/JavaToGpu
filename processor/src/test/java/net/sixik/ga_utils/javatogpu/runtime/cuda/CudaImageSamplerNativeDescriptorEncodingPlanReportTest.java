package net.sixik.ga_utils.javatogpu.runtime.cuda;

import net.sixik.ga_utils.javatogpu.api.Image1DBufferWriteOnly;
import net.sixik.ga_utils.javatogpu.api.Image2DReadOnly;
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

class CudaImageSamplerNativeDescriptorEncodingPlanReportTest {

    @Test
    void nativeDescriptorEncodingPlanReportPinsJavaOnlyFieldWrites() {
        CudaImageSamplerNativeDescriptorEncodingPlanReport report = CudaImageSamplerNativeDescriptorEncodingPlanReport.inspectBuiltIns();
        Map<String, CudaImageSamplerNativeDescriptorEncodingPlanReport.Case> cases = report.cases().stream()
                .collect(Collectors.toMap(CudaImageSamplerNativeDescriptorEncodingPlanReport.Case::key, testCase -> testCase));
        Map<String, String> fields = report.artifactFields("test.cuda.imageSamplerNativeDescriptorEncodingPlanReport");
        String rendered = CudaImageSamplerNativeDescriptorEncodingPlanCli.render(report);

        assertEquals("ready", report.status());
        assertTrue(report.ready());
        assertEquals("none", report.firstBlocker());
        assertEquals(3, report.cases().size());
        assertEquals(3, report.caseReadyCount());
        assertEquals(0, report.caseBlockedCount());
        assertEquals(1, report.planReadyCount());
        assertEquals(2, report.planBlockedCount());
        assertEquals(19, report.entryCount());
        assertEquals(35, report.resourceFieldWriteCount());
        assertEquals(54, report.textureFieldWriteCount());
        assertEquals(89, report.fieldWriteCount());
        assertEquals(6, report.samplerTextureFieldWriteCount());
        assertEquals(0, report.nativeWriteEnabledCount());
        assertEquals(0, report.sdkStructByteEncodingEnabledCount());
        assertEquals(0, report.activeNativeDescriptorCount());

        assertCase(cases, "all-builtins-valid", "ready", "none", 35, 54);
        assertCase(
                cases,
                "image2d-handle-missing",
                "blocked",
                "cuda-image-descriptor-handle-missing:0:" + Image2DReadOnly.class.getName(),
                0,
                0
        );
        assertCase(
                cases,
                "sampler-closed",
                "blocked",
                "cuda-sampler-descriptor-handle-closed:0:" + Sampler.class.getName(),
                0,
                0
        );

        assertEquals("ready", fields.get("runtime.cuda.imageSamplerNativeDescriptorEncodingPlanReport.status"));
        assertEquals("3", fields.get("runtime.cuda.imageSamplerNativeDescriptorEncodingPlanReport.case.ready.count"));
        assertEquals("1", fields.get("runtime.cuda.imageSamplerNativeDescriptorEncodingPlanReport.plan.ready.count"));
        assertEquals("2", fields.get("runtime.cuda.imageSamplerNativeDescriptorEncodingPlanReport.plan.blocked.count"));
        assertEquals("19", fields.get("runtime.cuda.imageSamplerNativeDescriptorEncodingPlanReport.entry.count"));
        assertEquals("35", fields.get("runtime.cuda.imageSamplerNativeDescriptorEncodingPlanReport.resourceFieldWrite.count"));
        assertEquals("54", fields.get("runtime.cuda.imageSamplerNativeDescriptorEncodingPlanReport.textureFieldWrite.count"));
        assertEquals("89", fields.get("runtime.cuda.imageSamplerNativeDescriptorEncodingPlanReport.fieldWrite.count"));
        assertEquals("6", fields.get("runtime.cuda.imageSamplerNativeDescriptorEncodingPlanReport.samplerTextureFieldWrite.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerNativeDescriptorEncodingPlanReport.nativeWrite.enabled.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerNativeDescriptorEncodingPlanReport.sdkStructByteEncoding.enabled.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerNativeDescriptorEncodingPlanReport.activeNativeDescriptor.count"));
        assertEquals("false", fields.get("runtime.cuda.imageSamplerNativeDescriptorEncodingPlanReport.nativeDescriptorMemoryAllocation.enabled"));
        assertEquals("false", fields.get("runtime.cuda.imageSamplerNativeDescriptorEncodingPlanReport.objectCreation.enabled"));
        assertEquals("false", fields.get("runtime.cuda.imageSamplerNativeDescriptorEncodingPlanReport.runtimeBinding.enabled"));
        assertEquals("none", fields.get("runtime.cuda.imageSamplerNativeDescriptorEncodingPlanReport.firstBlocker"));

        assertTrue(report.toMarkdown().contains("CUDA image/sampler native descriptor encoding plan: ready"));
        assertTrue(report.toMarkdown().contains("Field writes: resource=35, texture=54, total=89"));
        assertTrue(rendered.contains("caseReady=3/3"));
        assertTrue(rendered.contains("fieldWrites=89"));
        assertTrue(rendered.contains("rule=native descriptor field encoding is planned only"));
    }

    @Test
    void nativeDescriptorEncodingPlanRecordsFieldWriteShapeWithoutNativeWrites() {
        CudaImageSamplerNativeDescriptorEncodingPlan plan = CudaImageSamplerNativeDescriptorEncodingPlan.from(
                CudaImageSamplerDescriptorPayloadModel.from(
                        CudaImageSamplerDescriptorBuildPlan.from(
                                List.of(
                                        new GpuKernelParameterDescriptor("inputImage", Image2DReadOnly.class.getName(), GpuKernelParameterAccess.READ_ONLY),
                                        new GpuKernelParameterDescriptor("outputBufferImage", Image1DBufferWriteOnly.class.getName(), GpuKernelParameterAccess.READ_WRITE),
                                        new GpuKernelParameterDescriptor("sampler", Sampler.class.getName(), GpuKernelParameterAccess.VALUE)
                                ),
                                new Object[]{
                                        Image2DReadOnly.borrowed(0xCAFE_7201L, 8, 4),
                                        new Image1DBufferWriteOnly(0xCAFE_7202L, 8, 0xCAFE_8202L),
                                        Sampler.borrowed(0xCAFE_7203L)
                                }
                        )
                )
        );
        Map<String, String> fields = plan.artifactFields("test.cuda.imageSamplerNativeDescriptorEncodingPlan");

        assertEquals("ready", plan.status());
        assertTrue(plan.ready());
        assertEquals(3, plan.entries().size());
        assertEquals(4, plan.resourceFieldWriteCount());
        assertEquals(12, plan.textureFieldWriteCount());
        assertEquals(16, plan.fieldWriteCount());
        assertEquals(6, plan.samplerTextureFieldWriteCount());
        assertEquals(0, plan.nativeWriteEnabledCount());
        assertEquals(0, plan.sdkStructByteEncodingEnabledCount());
        assertFalse(plan.nativeDescriptorMemoryAllocationEnabled());
        assertFalse(plan.sdkStructByteEncodingEnabled());
        assertFalse(plan.objectCreationEnabled());
        assertFalse(plan.runtimeBindingEnabled());
        assertEquals(0, plan.activeNativeDescriptorCount());

        assertEquals("CUDA_RESOURCE_DESC", fields.get("runtime.cuda.imageSamplerNativeDescriptorEncodingPlan.entry.0.resourceEncoding.struct"));
        assertEquals("2", fields.get("runtime.cuda.imageSamplerNativeDescriptorEncodingPlan.entry.0.resourceFieldWrite.count"));
        assertEquals("resType", fields.get("runtime.cuda.imageSamplerNativeDescriptorEncodingPlan.entry.0.resourceFieldWrite.0.fieldPath"));
        assertEquals("resourceDescriptor.kind", fields.get("runtime.cuda.imageSamplerNativeDescriptorEncodingPlan.entry.0.resourceFieldWrite.0.valueSource"));
        assertEquals("literal-planned", fields.get("runtime.cuda.imageSamplerNativeDescriptorEncodingPlan.entry.0.resourceFieldWrite.0.valueStatus"));
        assertEquals("res.array.hArray", fields.get("runtime.cuda.imageSamplerNativeDescriptorEncodingPlan.entry.0.resourceFieldWrite.1.fieldPath"));
        assertEquals("java-wrapper-handle", fields.get("runtime.cuda.imageSamplerNativeDescriptorEncodingPlan.entry.0.resourceFieldWrite.1.valueSource"));
        assertEquals("not-native-bound", fields.get("runtime.cuda.imageSamplerNativeDescriptorEncodingPlan.entry.0.resourceFieldWrite.1.valueStatus"));
        assertEquals("CUDA_TEXTURE_DESC", fields.get("runtime.cuda.imageSamplerNativeDescriptorEncodingPlan.entry.0.textureEncoding.struct"));
        assertEquals("addressMode[0]", fields.get("runtime.cuda.imageSamplerNativeDescriptorEncodingPlan.entry.0.textureFieldWrite.0.fieldPath"));
        assertEquals("flags", fields.get("runtime.cuda.imageSamplerNativeDescriptorEncodingPlan.entry.0.textureFieldWrite.4.fieldPath"));
        assertEquals("unnormalized-coordinates", fields.get("runtime.cuda.imageSamplerNativeDescriptorEncodingPlan.entry.0.textureFieldWrite.4.valueStatus"));

        assertEquals("CUDA_RESOURCE_DESC", fields.get("runtime.cuda.imageSamplerNativeDescriptorEncodingPlan.entry.1.resourceEncoding.struct"));
        assertEquals("res.array.hArray", fields.get("runtime.cuda.imageSamplerNativeDescriptorEncodingPlan.entry.1.resourceFieldWrite.1.fieldPath"));
        assertEquals("staged-array-from-backing-buffer", fields.get("runtime.cuda.imageSamplerNativeDescriptorEncodingPlan.entry.1.resourceFieldWrite.1.valueSource"));
        assertEquals("staged-array-resource-handle-pending", fields.get("runtime.cuda.imageSamplerNativeDescriptorEncodingPlan.entry.1.resourceFieldWrite.1.valueStatus"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerNativeDescriptorEncodingPlan.entry.1.textureFieldWrite.count"));

        assertEquals("CUDA_TEXTURE_DESC", fields.get("runtime.cuda.imageSamplerNativeDescriptorEncodingPlan.entry.2.textureEncoding.struct"));
        assertEquals("6", fields.get("runtime.cuda.imageSamplerNativeDescriptorEncodingPlan.entry.2.textureFieldWrite.count"));
        assertEquals("readMode", fields.get("runtime.cuda.imageSamplerNativeDescriptorEncodingPlan.entry.2.textureFieldWrite.5.fieldPath"));
        assertEquals("element-type", fields.get("runtime.cuda.imageSamplerNativeDescriptorEncodingPlan.entry.2.textureFieldWrite.5.valueStatus"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerNativeDescriptorEncodingPlan.nativeWrite.enabled.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerNativeDescriptorEncodingPlan.sdkStructByteEncoding.enabled.count"));
    }

    private static void assertCase(
            Map<String, CudaImageSamplerNativeDescriptorEncodingPlanReport.Case> cases,
            String key,
            String expectedPlanStatus,
            String expectedFirstBlocker,
            int expectedResourceFieldWrites,
            int expectedTextureFieldWrites
    ) {
        CudaImageSamplerNativeDescriptorEncodingPlanReport.Case testCase = cases.get(key);
        assertNotNull(testCase, "missing native descriptor encoding-plan case " + key);
        assertTrue(testCase.ready(), key);
        assertEquals(expectedPlanStatus, testCase.plan().status());
        assertEquals(expectedFirstBlocker, testCase.plan().firstBlocker());
        assertEquals(expectedResourceFieldWrites, testCase.plan().resourceFieldWriteCount());
        assertEquals(expectedTextureFieldWrites, testCase.plan().textureFieldWriteCount());
    }
}
