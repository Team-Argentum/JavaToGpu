package net.sixik.ga_utils.javatogpu.runtime.cuda;

import net.sixik.ga_utils.javatogpu.api.Image1DReadOnly;
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

class CudaImageSamplerNativeDescriptorEncodingTransactionPlanReportTest {

    @Test
    void nativeDescriptorEncodingTransactionReportPinsFailClosedOwnerWriteMapping() {
        CudaImageSamplerNativeDescriptorEncodingTransactionPlanReport report =
                CudaImageSamplerNativeDescriptorEncodingTransactionPlanReport.inspectBuiltIns();
        Map<String, CudaImageSamplerNativeDescriptorEncodingTransactionPlanReport.Case> cases = report.cases().stream()
                .collect(Collectors.toMap(CudaImageSamplerNativeDescriptorEncodingTransactionPlanReport.Case::key, testCase -> testCase));
        Map<String, String> fields = report.artifactFields("test.cuda.imageSamplerNativeDescriptorEncodingTransactionPlanReport");
        String rendered = CudaImageSamplerNativeDescriptorEncodingTransactionPlanCli.render(report);

        assertEquals("ready", report.status());
        assertTrue(report.ready());
        assertEquals("none", report.firstBlocker());
        assertEquals(3, report.cases().size());
        assertEquals(3, report.caseReadyCount());
        assertEquals(0, report.caseBlockedCount());
        assertEquals(1, report.encodingPlanReadyCount());
        assertEquals(2, report.encodingPlanBlockedCount());
        assertEquals(0, report.allocationTransactionReadyCount());
        assertEquals(3, report.allocationTransactionBlockedCount());
        assertEquals(0, report.transactionReadyCount());
        assertEquals(3, report.transactionBlockedCount());
        assertEquals(19, report.entryCount());
        assertEquals(25, report.descriptorWriteCount());
        assertEquals(16, report.resourceDescriptorWriteCount());
        assertEquals(9, report.textureDescriptorWriteCount());
        assertEquals(35, report.resourceFieldWriteCount());
        assertEquals(54, report.textureFieldWriteCount());
        assertEquals(89, report.fieldWriteCount());
        assertEquals(25, report.ownerPresentCount());
        assertEquals(0, report.ownerActiveCount());
        assertEquals(0, report.nativeAddressPresentCount());
        assertEquals(0, report.nativeWriteEnabledCount());
        assertEquals(0, report.sdkStructByteEncodingEnabledCount());
        assertEquals(0, report.activeNativeDescriptorCount());

        assertCase(
                cases,
                "all-builtins-valid",
                "blocked",
                "cuda-image-sampler-native-descriptor-encoding-transaction-disabled:0:" + Image1DReadOnly.class.getName(),
                16,
                9,
                35,
                54
        );
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

        assertEquals("ready", fields.get("runtime.cuda.imageSamplerNativeDescriptorEncodingTransactionPlanReport.status"));
        assertEquals("3", fields.get("runtime.cuda.imageSamplerNativeDescriptorEncodingTransactionPlanReport.case.ready.count"));
        assertEquals("1", fields.get("runtime.cuda.imageSamplerNativeDescriptorEncodingTransactionPlanReport.encodingPlan.ready.count"));
        assertEquals("2", fields.get("runtime.cuda.imageSamplerNativeDescriptorEncodingTransactionPlanReport.encodingPlan.blocked.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerNativeDescriptorEncodingTransactionPlanReport.allocationTransaction.ready.count"));
        assertEquals("3", fields.get("runtime.cuda.imageSamplerNativeDescriptorEncodingTransactionPlanReport.allocationTransaction.blocked.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerNativeDescriptorEncodingTransactionPlanReport.transaction.ready.count"));
        assertEquals("3", fields.get("runtime.cuda.imageSamplerNativeDescriptorEncodingTransactionPlanReport.transaction.blocked.count"));
        assertEquals("19", fields.get("runtime.cuda.imageSamplerNativeDescriptorEncodingTransactionPlanReport.entry.count"));
        assertEquals("25", fields.get("runtime.cuda.imageSamplerNativeDescriptorEncodingTransactionPlanReport.descriptorWrite.count"));
        assertEquals("16", fields.get("runtime.cuda.imageSamplerNativeDescriptorEncodingTransactionPlanReport.resourceDescriptorWrite.count"));
        assertEquals("9", fields.get("runtime.cuda.imageSamplerNativeDescriptorEncodingTransactionPlanReport.textureDescriptorWrite.count"));
        assertEquals("35", fields.get("runtime.cuda.imageSamplerNativeDescriptorEncodingTransactionPlanReport.resourceFieldWrite.count"));
        assertEquals("54", fields.get("runtime.cuda.imageSamplerNativeDescriptorEncodingTransactionPlanReport.textureFieldWrite.count"));
        assertEquals("89", fields.get("runtime.cuda.imageSamplerNativeDescriptorEncodingTransactionPlanReport.fieldWrite.count"));
        assertEquals("25", fields.get("runtime.cuda.imageSamplerNativeDescriptorEncodingTransactionPlanReport.owner.present.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerNativeDescriptorEncodingTransactionPlanReport.owner.active.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerNativeDescriptorEncodingTransactionPlanReport.nativeAddress.present.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerNativeDescriptorEncodingTransactionPlanReport.nativeWrite.enabled.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerNativeDescriptorEncodingTransactionPlanReport.sdkStructByteEncoding.enabled.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerNativeDescriptorEncodingTransactionPlanReport.activeNativeDescriptor.count"));
        assertEquals("false", fields.get("runtime.cuda.imageSamplerNativeDescriptorEncodingTransactionPlanReport.writeTransactionApply.enabled"));
        assertEquals("false", fields.get("runtime.cuda.imageSamplerNativeDescriptorEncodingTransactionPlanReport.nativeMemoryAllocation.enabled"));
        assertEquals("false", fields.get("runtime.cuda.imageSamplerNativeDescriptorEncodingTransactionPlanReport.sdkStructByteEncoding.enabled"));
        assertEquals("false", fields.get("runtime.cuda.imageSamplerNativeDescriptorEncodingTransactionPlanReport.objectCreation.enabled"));
        assertEquals("false", fields.get("runtime.cuda.imageSamplerNativeDescriptorEncodingTransactionPlanReport.runtimeBinding.enabled"));
        assertEquals("none", fields.get("runtime.cuda.imageSamplerNativeDescriptorEncodingTransactionPlanReport.firstBlocker"));

        assertTrue(report.toMarkdown().contains("CUDA image/sampler native descriptor encoding transaction plan: ready"));
        assertTrue(report.toMarkdown().contains("Descriptor writes: resource=16, texture=9, total=25"));
        assertTrue(report.toMarkdown().contains("Field writes: resource=35, texture=54, total=89"));
        assertTrue(rendered.contains("caseReady=3/3"));
        assertTrue(rendered.contains("fieldWrites=89"));
        assertTrue(rendered.contains("rule=native descriptor field writes are mapped to owner slots only"));
    }

    @Test
    void nativeDescriptorEncodingTransactionMapsFieldWritesToPlannedOwnersWithoutNativeWrites() {
        CudaImageSamplerNativeDescriptorEncodingPlan encodingPlan = CudaImageSamplerNativeDescriptorEncodingPlan.from(
                CudaImageSamplerDescriptorPayloadModel.from(
                        CudaImageSamplerDescriptorBuildPlan.from(
                                List.of(
                                        new GpuKernelParameterDescriptor("inputImage", Image2DReadOnly.class.getName(), GpuKernelParameterAccess.READ_ONLY),
                                        new GpuKernelParameterDescriptor("outputImage", Image2DWriteOnly.class.getName(), GpuKernelParameterAccess.READ_WRITE),
                                        new GpuKernelParameterDescriptor("sampler", Sampler.class.getName(), GpuKernelParameterAccess.VALUE)
                                ),
                                new Object[]{
                                        Image2DReadOnly.borrowed(0xCAFE_7A01L, 8, 4),
                                        Image2DWriteOnly.borrowed(0xCAFE_7A02L, 8, 4),
                                        Sampler.borrowed(0xCAFE_7A03L)
                                }
                        )
                )
        );
        CudaImageSamplerNativeDescriptorEncodingTransactionPlan plan =
                CudaImageSamplerNativeDescriptorEncodingTransactionPlan.from(
                        encodingPlan,
                        CudaImageSamplerNativeDescriptorAllocationTransactionPlan.from(
                                CudaImageSamplerNativeDescriptorAllocationPreflight.from(encodingPlan)
                        )
                );
        Map<String, String> fields = plan.artifactFields("test.cuda.imageSamplerNativeDescriptorEncodingTransactionPlan");

        assertEquals("blocked", plan.status());
        assertFalse(plan.ready());
        assertEquals("cuda-image-sampler-native-descriptor-encoding-transaction-disabled:0:" + Image2DReadOnly.class.getName(), plan.firstBlocker());
        assertEquals(3, plan.entries().size());
        assertEquals(0, plan.entryReadyCount());
        assertEquals(3, plan.entryBlockedCount());
        assertEquals(4, plan.descriptorWriteCount());
        assertEquals(2, plan.resourceDescriptorWriteCount());
        assertEquals(2, plan.textureDescriptorWriteCount());
        assertEquals(4, plan.resourceFieldWriteCount());
        assertEquals(12, plan.textureFieldWriteCount());
        assertEquals(16, plan.fieldWriteCount());
        assertEquals(4, plan.ownerPresentCount());
        assertEquals(0, plan.ownerActiveCount());
        assertEquals(0, plan.nativeAddressPresentCount());
        assertEquals(0, plan.nativeWriteEnabledCount());
        assertEquals(0, plan.sdkStructByteEncodingEnabledCount());
        assertFalse(plan.writeTransactionApplyEnabled());
        assertFalse(plan.nativeMemoryAllocationEnabled());
        assertFalse(plan.sdkStructByteEncodingEnabled());
        assertFalse(plan.objectCreationEnabled());
        assertFalse(plan.runtimeBindingEnabled());
        assertEquals(0, plan.activeNativeDescriptorCount());

        CudaImageSamplerNativeDescriptorEncodingTransactionPlan.Entry texture = plan.entries().get(0);
        assertEquals(2, texture.descriptorWrites().size());
        assertEquals(1, texture.resourceDescriptorWriteCount());
        assertEquals(1, texture.textureDescriptorWriteCount());
        assertEquals(2, texture.resourceFieldWriteCount());
        assertEquals(6, texture.textureFieldWriteCount());
        assertEquals("resource", texture.descriptorWrites().get(0).descriptorKind());
        assertEquals("CUDA_RESOURCE_DESC", texture.descriptorWrites().get(0).targetStruct());
        assertTrue(texture.descriptorWrites().get(0).ownerPresent());
        assertFalse(texture.descriptorWrites().get(0).nativeAddressPresent());
        assertEquals("res.array.hArray", texture.descriptorWrites().get(0).fieldWrites().get(1).fieldPath());
        assertEquals("texture", texture.descriptorWrites().get(1).descriptorKind());
        assertEquals("CUDA_TEXTURE_DESC", texture.descriptorWrites().get(1).targetStruct());
        assertEquals("addressMode[0]", texture.descriptorWrites().get(1).fieldWrites().get(0).fieldPath());

        CudaImageSamplerNativeDescriptorEncodingTransactionPlan.Entry surface = plan.entries().get(1);
        assertEquals(1, surface.descriptorWrites().size());
        assertEquals("resource", surface.descriptorWrites().get(0).descriptorKind());
        assertEquals(2, surface.descriptorWrites().get(0).fieldWriteCount());

        CudaImageSamplerNativeDescriptorEncodingTransactionPlan.Entry sampler = plan.entries().get(2);
        assertEquals(1, sampler.descriptorWrites().size());
        assertEquals("texture", sampler.descriptorWrites().get(0).descriptorKind());
        assertEquals(6, sampler.descriptorWrites().get(0).fieldWriteCount());
        assertEquals("readMode", sampler.descriptorWrites().get(0).fieldWrites().get(5).fieldPath());

        assertEquals("true", fields.get("runtime.cuda.imageSamplerNativeDescriptorEncodingTransactionPlan.present"));
        assertEquals("blocked", fields.get("runtime.cuda.imageSamplerNativeDescriptorEncodingTransactionPlan.status"));
        assertEquals("3", fields.get("runtime.cuda.imageSamplerNativeDescriptorEncodingTransactionPlan.entry.count"));
        assertEquals("4", fields.get("runtime.cuda.imageSamplerNativeDescriptorEncodingTransactionPlan.descriptorWrite.count"));
        assertEquals("2", fields.get("runtime.cuda.imageSamplerNativeDescriptorEncodingTransactionPlan.resourceDescriptorWrite.count"));
        assertEquals("2", fields.get("runtime.cuda.imageSamplerNativeDescriptorEncodingTransactionPlan.textureDescriptorWrite.count"));
        assertEquals("4", fields.get("runtime.cuda.imageSamplerNativeDescriptorEncodingTransactionPlan.resourceFieldWrite.count"));
        assertEquals("12", fields.get("runtime.cuda.imageSamplerNativeDescriptorEncodingTransactionPlan.textureFieldWrite.count"));
        assertEquals("16", fields.get("runtime.cuda.imageSamplerNativeDescriptorEncodingTransactionPlan.fieldWrite.count"));
        assertEquals("4", fields.get("runtime.cuda.imageSamplerNativeDescriptorEncodingTransactionPlan.owner.present.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerNativeDescriptorEncodingTransactionPlan.owner.active.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerNativeDescriptorEncodingTransactionPlan.nativeAddress.present.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerNativeDescriptorEncodingTransactionPlan.nativeWrite.enabled.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerNativeDescriptorEncodingTransactionPlan.sdkStructByteEncoding.enabled.count"));
        assertEquals("false", fields.get("runtime.cuda.imageSamplerNativeDescriptorEncodingTransactionPlan.writeTransactionApply.enabled"));
        assertEquals("false", fields.get("runtime.cuda.imageSamplerNativeDescriptorEncodingTransactionPlan.nativeMemoryAllocation.enabled"));
        assertEquals("false", fields.get("runtime.cuda.imageSamplerNativeDescriptorEncodingTransactionPlan.sdkStructByteEncoding.enabled"));
        assertEquals("cuda-image-sampler-native-descriptor-encoding-transaction-disabled:0:" + Image2DReadOnly.class.getName(), fields.get("runtime.cuda.imageSamplerNativeDescriptorEncodingTransactionPlan.firstBlocker"));
        assertEquals("resource", fields.get("runtime.cuda.imageSamplerNativeDescriptorEncodingTransactionPlan.entry.0.descriptorWrite.0.descriptor.kind"));
        assertEquals("CUDA_RESOURCE_DESC", fields.get("runtime.cuda.imageSamplerNativeDescriptorEncodingTransactionPlan.entry.0.descriptorWrite.0.targetStruct"));
        assertEquals("true", fields.get("runtime.cuda.imageSamplerNativeDescriptorEncodingTransactionPlan.entry.0.descriptorWrite.0.owner.present"));
        assertEquals("false", fields.get("runtime.cuda.imageSamplerNativeDescriptorEncodingTransactionPlan.entry.0.descriptorWrite.0.nativeAddress.present"));
        assertEquals("res.array.hArray", fields.get("runtime.cuda.imageSamplerNativeDescriptorEncodingTransactionPlan.entry.0.descriptorWrite.0.fieldWrite.1.fieldPath"));
        assertEquals("texture", fields.get("runtime.cuda.imageSamplerNativeDescriptorEncodingTransactionPlan.entry.2.descriptorWrite.0.descriptor.kind"));
        assertEquals("readMode", fields.get("runtime.cuda.imageSamplerNativeDescriptorEncodingTransactionPlan.entry.2.descriptorWrite.0.fieldWrite.5.fieldPath"));
    }

    private static void assertCase(
            Map<String, CudaImageSamplerNativeDescriptorEncodingTransactionPlanReport.Case> cases,
            String key,
            String expectedTransactionStatus,
            String expectedFirstBlocker,
            int expectedResourceDescriptorWrites,
            int expectedTextureDescriptorWrites,
            int expectedResourceFieldWrites,
            int expectedTextureFieldWrites
    ) {
        CudaImageSamplerNativeDescriptorEncodingTransactionPlanReport.Case testCase = cases.get(key);
        assertNotNull(testCase, "missing native descriptor encoding-transaction case " + key);
        assertTrue(testCase.ready(), key);
        assertEquals(expectedTransactionStatus, testCase.plan().status());
        assertEquals(expectedFirstBlocker, testCase.plan().firstBlocker());
        assertEquals(expectedResourceDescriptorWrites, testCase.plan().resourceDescriptorWriteCount());
        assertEquals(expectedTextureDescriptorWrites, testCase.plan().textureDescriptorWriteCount());
        assertEquals(expectedResourceDescriptorWrites + expectedTextureDescriptorWrites, testCase.plan().descriptorWriteCount());
        assertEquals(expectedResourceFieldWrites, testCase.plan().resourceFieldWriteCount());
        assertEquals(expectedTextureFieldWrites, testCase.plan().textureFieldWriteCount());
        assertEquals(expectedResourceFieldWrites + expectedTextureFieldWrites, testCase.plan().fieldWriteCount());
        assertEquals(expectedResourceDescriptorWrites + expectedTextureDescriptorWrites, testCase.plan().ownerPresentCount());
        assertEquals(0, testCase.plan().ownerActiveCount());
        assertEquals(0, testCase.plan().nativeAddressPresentCount());
        assertEquals(0, testCase.plan().nativeWriteEnabledCount());
        assertEquals(0, testCase.plan().sdkStructByteEncodingEnabledCount());
        assertEquals(0, testCase.plan().activeNativeDescriptorCount());
    }
}
