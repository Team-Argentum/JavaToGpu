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

class CudaImageSamplerDescriptorPayloadModelReportTest {

    @Test
    void descriptorPayloadModelReportPinsJavaOnlyPayloadCounts() {
        CudaImageSamplerDescriptorPayloadModelReport report = CudaImageSamplerDescriptorPayloadModelReport.inspectBuiltIns();
        Map<String, CudaImageSamplerDescriptorPayloadModelReport.Case> cases = report.cases().stream()
                .collect(Collectors.toMap(CudaImageSamplerDescriptorPayloadModelReport.Case::key, testCase -> testCase));
        Map<String, String> fields = report.artifactFields("test.cuda.imageSamplerDescriptorPayloadModelReport");
        String rendered = CudaImageSamplerDescriptorPayloadModelCli.render(report);

        assertEquals("ready", report.status());
        assertTrue(report.ready());
        assertEquals("none", report.firstBlocker());
        assertEquals(3, report.cases().size());
        assertEquals(3, report.caseReadyCount());
        assertEquals(0, report.caseBlockedCount());
        assertEquals(1, report.modelReadyCount());
        assertEquals(2, report.modelBlockedCount());
        assertEquals(19, report.entryCount());
        assertEquals(16, report.resourcePayloadBuiltCount());
        assertEquals(9, report.texturePayloadBuiltCount());
        assertEquals(1, report.samplerPayloadBuiltCount());
        assertEquals(0, report.activeNativeDescriptorCount());

        assertCase(cases, "all-builtins-valid", "ready", "none", 16, 9);
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

        assertEquals("ready", fields.get("runtime.cuda.imageSamplerDescriptorPayloadModelReport.status"));
        assertEquals("3", fields.get("runtime.cuda.imageSamplerDescriptorPayloadModelReport.case.ready.count"));
        assertEquals("1", fields.get("runtime.cuda.imageSamplerDescriptorPayloadModelReport.model.ready.count"));
        assertEquals("2", fields.get("runtime.cuda.imageSamplerDescriptorPayloadModelReport.model.blocked.count"));
        assertEquals("19", fields.get("runtime.cuda.imageSamplerDescriptorPayloadModelReport.entry.count"));
        assertEquals("16", fields.get("runtime.cuda.imageSamplerDescriptorPayloadModelReport.resourcePayload.built.count"));
        assertEquals("9", fields.get("runtime.cuda.imageSamplerDescriptorPayloadModelReport.texturePayload.built.count"));
        assertEquals("1", fields.get("runtime.cuda.imageSamplerDescriptorPayloadModelReport.samplerPayload.built.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerDescriptorPayloadModelReport.activeNativeDescriptor.count"));
        assertEquals("false", fields.get("runtime.cuda.imageSamplerDescriptorPayloadModelReport.nativeDescriptorAllocation.enabled"));
        assertEquals("false", fields.get("runtime.cuda.imageSamplerDescriptorPayloadModelReport.objectCreation.enabled"));
        assertEquals("false", fields.get("runtime.cuda.imageSamplerDescriptorPayloadModelReport.runtimeBinding.enabled"));
        assertEquals("none", fields.get("runtime.cuda.imageSamplerDescriptorPayloadModelReport.firstBlocker"));

        assertTrue(report.toMarkdown().contains("CUDA image/sampler descriptor payload model: ready"));
        assertTrue(report.toMarkdown().contains("Java payloads: resource=16, texture=9, sampler=1"));
        assertTrue(rendered.contains("caseReady=3/3"));
        assertTrue(rendered.contains("resourcePayloads=16"));
        assertTrue(rendered.contains("rule=Java descriptor payloads are modeled only"));
    }

    @Test
    void descriptorPayloadModelRecordsResourceTextureAndStagedBufferFieldsWithoutNativeBinding() {
        CudaImageSamplerDescriptorPayloadModel model = CudaImageSamplerDescriptorPayloadModel.from(
                CudaImageSamplerDescriptorBuildPlan.from(
                        List.of(
                                new GpuKernelParameterDescriptor("inputImage", Image2DReadOnly.class.getName(), GpuKernelParameterAccess.READ_ONLY),
                                new GpuKernelParameterDescriptor("outputBufferImage", Image1DBufferWriteOnly.class.getName(), GpuKernelParameterAccess.READ_WRITE),
                                new GpuKernelParameterDescriptor("sampler", Sampler.class.getName(), GpuKernelParameterAccess.VALUE)
                        ),
                        new Object[]{
                                Image2DReadOnly.borrowed(0xCAFE_7101L, 8, 4),
                                new Image1DBufferWriteOnly(0xCAFE_7102L, 8, 0xCAFE_8102L),
                                Sampler.borrowed(0xCAFE_7103L)
                        }
                )
        );
        Map<String, String> fields = model.artifactFields("test.cuda.imageSamplerDescriptorPayloadModel");

        assertEquals("ready", model.status());
        assertTrue(model.ready());
        assertEquals(3, model.entries().size());
        assertEquals(2, model.resourcePayloadBuiltCount());
        assertEquals(2, model.texturePayloadBuiltCount());
        assertEquals(1, model.samplerPayloadCount());
        assertFalse(model.nativeDescriptorAllocationEnabled());
        assertFalse(model.objectCreationEnabled());
        assertFalse(model.runtimeBindingEnabled());
        assertEquals(0, model.activeNativeDescriptorCount());

        assertEquals("CUDA_RESOURCE_DESC", fields.get("runtime.cuda.imageSamplerDescriptorPayloadModel.entry.0.resourcePayload.struct"));
        assertEquals("CUDA_RESOURCE_TYPE_ARRAY", fields.get("runtime.cuda.imageSamplerDescriptorPayloadModel.entry.0.resourcePayload.resourceDescriptor.kind"));
        assertEquals("2d", fields.get("runtime.cuda.imageSamplerDescriptorPayloadModel.entry.0.resourcePayload.resourceDescriptor.dimension"));
        assertEquals("resType+res.array.hArray", fields.get("runtime.cuda.imageSamplerDescriptorPayloadModel.entry.0.resourcePayload.field.policy"));
        assertEquals("not-native-bound", fields.get("runtime.cuda.imageSamplerDescriptorPayloadModel.entry.0.resourcePayload.cudaHandleBinding.status"));
        assertEquals("CUDA_TEXTURE_DESC", fields.get("runtime.cuda.imageSamplerDescriptorPayloadModel.entry.0.texturePayload.struct"));
        assertEquals("nearest-clamp-to-edge-default-until-sampler-metadata-exists", fields.get("runtime.cuda.imageSamplerDescriptorPayloadModel.entry.0.texturePayload.samplerState.source"));

        assertEquals("CUDA_RESOURCE_TYPE_ARRAY_STAGING_PENDING", fields.get("runtime.cuda.imageSamplerDescriptorPayloadModel.entry.1.resourcePayload.resourceDescriptor.kind"));
        assertEquals("resType+staged-array-resource-handle-pending", fields.get("runtime.cuda.imageSamplerDescriptorPayloadModel.entry.1.resourcePayload.field.policy"));
        assertEquals("staged-array-resource-handle-pending", fields.get("runtime.cuda.imageSamplerDescriptorPayloadModel.entry.1.resourcePayload.cudaHandleBinding.status"));
        assertEquals("true", fields.get("runtime.cuda.imageSamplerDescriptorPayloadModel.entry.1.resourcePayload.backingBufferHandle.present"));
        assertEquals("false", fields.get("runtime.cuda.imageSamplerDescriptorPayloadModel.entry.1.texturePayload.required"));
        assertEquals("false", fields.get("runtime.cuda.imageSamplerDescriptorPayloadModel.entry.1.texturePayload.present"));

        assertEquals("CUDA_TEXTURE_DESC", fields.get("runtime.cuda.imageSamplerDescriptorPayloadModel.entry.2.texturePayload.struct"));
        assertEquals("folded-sampler-parameter-default", fields.get("runtime.cuda.imageSamplerDescriptorPayloadModel.entry.2.texturePayload.samplerState.source"));
        assertEquals("not-native-bound", fields.get("runtime.cuda.imageSamplerDescriptorPayloadModel.entry.2.texturePayload.cudaHandleBinding.status"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerDescriptorPayloadModel.activeNativeDescriptor.count"));
    }

    private static void assertCase(
            Map<String, CudaImageSamplerDescriptorPayloadModelReport.Case> cases,
            String key,
            String expectedModelStatus,
            String expectedFirstBlocker,
            int expectedResourcePayloads,
            int expectedTexturePayloads
    ) {
        CudaImageSamplerDescriptorPayloadModelReport.Case testCase = cases.get(key);
        assertNotNull(testCase, "missing descriptor payload model case " + key);
        assertTrue(testCase.ready(), key);
        assertEquals(expectedModelStatus, testCase.model().status());
        assertEquals(expectedFirstBlocker, testCase.model().firstBlocker());
        assertEquals(expectedResourcePayloads, testCase.model().resourcePayloadBuiltCount());
        assertEquals(expectedTexturePayloads, testCase.model().texturePayloadBuiltCount());
    }
}
