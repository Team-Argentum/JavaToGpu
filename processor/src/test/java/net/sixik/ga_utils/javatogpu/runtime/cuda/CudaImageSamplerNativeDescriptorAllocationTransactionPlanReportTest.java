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

class CudaImageSamplerNativeDescriptorAllocationTransactionPlanReportTest {

    @Test
    void nativeDescriptorAllocationTransactionReportPinsFailClosedOwnerLifecycle() {
        CudaImageSamplerNativeDescriptorAllocationTransactionPlanReport report =
                CudaImageSamplerNativeDescriptorAllocationTransactionPlanReport.inspectBuiltIns();
        Map<String, CudaImageSamplerNativeDescriptorAllocationTransactionPlanReport.Case> cases = report.cases().stream()
                .collect(Collectors.toMap(CudaImageSamplerNativeDescriptorAllocationTransactionPlanReport.Case::key, testCase -> testCase));
        Map<String, String> fields = report.artifactFields("test.cuda.imageSamplerNativeDescriptorAllocationTransactionPlanReport");
        String rendered = CudaImageSamplerNativeDescriptorAllocationTransactionPlanCli.render(report);

        assertEquals("ready", report.status());
        assertTrue(report.ready());
        assertEquals("none", report.firstBlocker());
        assertEquals(3, report.cases().size());
        assertEquals(3, report.caseReadyCount());
        assertEquals(0, report.caseBlockedCount());
        assertEquals(0, report.preflightReadyCount());
        assertEquals(3, report.preflightBlockedCount());
        assertEquals(0, report.transactionReadyCount());
        assertEquals(3, report.transactionBlockedCount());
        assertEquals(19, report.entryCount());
        assertEquals(25, report.descriptorOwnerCount());
        assertEquals(16, report.resourceDescriptorOwnerCount());
        assertEquals(9, report.textureDescriptorOwnerCount());
        assertEquals(0, report.activeDescriptorOwnerCount());
        assertEquals(0, report.nativeAddressPresentCount());
        assertEquals(0, report.allocationEnabledCount());
        assertEquals(25, report.cleanupPlannedCount());
        assertEquals(0, report.cleanupActiveCount());
        assertEquals(25, report.rollbackPlannedCount());
        assertEquals(0, report.rollbackActiveCount());
        assertEquals(0, report.activeNativeDescriptorCount());

        assertCase(
                cases,
                "all-builtins-valid",
                "blocked",
                "cuda-image-sampler-native-descriptor-allocation-transaction-disabled:0:" + Image1DReadOnly.class.getName(),
                16,
                9
        );
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

        assertEquals("ready", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationTransactionPlanReport.status"));
        assertEquals("3", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationTransactionPlanReport.case.ready.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationTransactionPlanReport.preflight.ready.count"));
        assertEquals("3", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationTransactionPlanReport.preflight.blocked.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationTransactionPlanReport.transaction.ready.count"));
        assertEquals("3", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationTransactionPlanReport.transaction.blocked.count"));
        assertEquals("19", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationTransactionPlanReport.entry.count"));
        assertEquals("25", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationTransactionPlanReport.descriptorOwner.count"));
        assertEquals("16", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationTransactionPlanReport.resourceDescriptorOwner.count"));
        assertEquals("9", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationTransactionPlanReport.textureDescriptorOwner.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationTransactionPlanReport.activeDescriptorOwner.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationTransactionPlanReport.nativeAddress.present.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationTransactionPlanReport.allocation.enabled.count"));
        assertEquals("25", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationTransactionPlanReport.cleanup.planned.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationTransactionPlanReport.cleanup.active.count"));
        assertEquals("25", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationTransactionPlanReport.rollback.planned.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationTransactionPlanReport.rollback.active.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationTransactionPlanReport.activeNativeDescriptor.count"));
        assertEquals("false", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationTransactionPlanReport.allocationApply.enabled"));
        assertEquals("false", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationTransactionPlanReport.nativeMemoryAllocation.enabled"));
        assertEquals("false", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationTransactionPlanReport.sdkStructByteEncoding.enabled"));
        assertEquals("false", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationTransactionPlanReport.cleanupApply.enabled"));
        assertEquals("false", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationTransactionPlanReport.rollbackApply.enabled"));
        assertEquals("false", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationTransactionPlanReport.objectCreation.enabled"));
        assertEquals("false", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationTransactionPlanReport.runtimeBinding.enabled"));
        assertEquals("none", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationTransactionPlanReport.firstBlocker"));

        assertTrue(report.toMarkdown().contains("CUDA image/sampler native descriptor allocation transaction plan: ready"));
        assertTrue(report.toMarkdown().contains("Descriptor owners: resource=16, texture=9, total=25, active=0"));
        assertTrue(rendered.contains("caseReady=3/3"));
        assertTrue(rendered.contains("descriptorOwners=25"));
        assertTrue(rendered.contains("rule=native descriptor allocation owners and cleanup/rollback order are planned only"));
    }

    @Test
    void nativeDescriptorAllocationTransactionRecordsOwnerSkeletonsWithoutNativeAddresses() {
        CudaImageSamplerNativeDescriptorAllocationTransactionPlan plan =
                CudaImageSamplerNativeDescriptorAllocationTransactionPlan.from(
                        CudaImageSamplerNativeDescriptorAllocationPreflight.from(
                                CudaImageSamplerNativeDescriptorEncodingPlan.from(
                                        CudaImageSamplerDescriptorPayloadModel.from(
                                                CudaImageSamplerDescriptorBuildPlan.from(
                                                        List.of(
                                                                new GpuKernelParameterDescriptor("inputImage", Image2DReadOnly.class.getName(), GpuKernelParameterAccess.READ_ONLY),
                                                                new GpuKernelParameterDescriptor("outputImage", Image2DWriteOnly.class.getName(), GpuKernelParameterAccess.READ_WRITE),
                                                                new GpuKernelParameterDescriptor("sampler", Sampler.class.getName(), GpuKernelParameterAccess.VALUE)
                                                        ),
                                                        new Object[]{
                                                                Image2DReadOnly.borrowed(0xCAFE_7901L, 8, 4),
                                                                Image2DWriteOnly.borrowed(0xCAFE_7902L, 8, 4),
                                                                Sampler.borrowed(0xCAFE_7903L)
                                                        }
                                                )
                                        )
                                )
                        )
                );
        Map<String, String> fields = plan.artifactFields("test.cuda.imageSamplerNativeDescriptorAllocationTransactionPlan");

        assertEquals("blocked", plan.status());
        assertFalse(plan.ready());
        assertEquals("cuda-image-sampler-native-descriptor-allocation-transaction-disabled:0:" + Image2DReadOnly.class.getName(), plan.firstBlocker());
        assertEquals(3, plan.entries().size());
        assertEquals(0, plan.entryReadyCount());
        assertEquals(3, plan.entryBlockedCount());
        assertEquals(4, plan.descriptorOwnerCount());
        assertEquals(2, plan.resourceDescriptorOwnerCount());
        assertEquals(2, plan.textureDescriptorOwnerCount());
        assertEquals(0, plan.activeDescriptorOwnerCount());
        assertEquals(0, plan.nativeAddressPresentCount());
        assertEquals(0, plan.allocationEnabledCount());
        assertEquals(4, plan.cleanupPlannedCount());
        assertEquals(0, plan.cleanupActiveCount());
        assertEquals(4, plan.rollbackPlannedCount());
        assertEquals(0, plan.rollbackActiveCount());
        assertFalse(plan.allocationApplyEnabled());
        assertFalse(plan.nativeMemoryAllocationEnabled());
        assertFalse(plan.sdkStructByteEncodingEnabled());
        assertFalse(plan.cleanupApplyEnabled());
        assertFalse(plan.rollbackApplyEnabled());
        assertFalse(plan.objectCreationEnabled());
        assertFalse(plan.runtimeBindingEnabled());
        assertEquals(0, plan.activeNativeDescriptorCount());

        CudaImageSamplerNativeDescriptorAllocationTransactionPlan.Entry texture = plan.entries().get(0);
        assertEquals(2, texture.descriptorOwners().size());
        assertEquals(1, texture.resourceDescriptorOwnerCount());
        assertEquals(1, texture.textureDescriptorOwnerCount());
        assertEquals("resource", texture.descriptorOwners().get(0).descriptorKind());
        assertEquals("CUDA_RESOURCE_DESC", texture.descriptorOwners().get(0).structName());
        assertEquals(0, texture.descriptorOwners().get(0).cleanupOrder());
        assertEquals(0, texture.descriptorOwners().get(0).rollbackOrder());
        assertFalse(texture.descriptorOwners().get(0).nativeAddressPresent());
        assertFalse(texture.descriptorOwners().get(0).allocationEnabled());
        assertEquals("texture", texture.descriptorOwners().get(1).descriptorKind());
        assertEquals("CUDA_TEXTURE_DESC", texture.descriptorOwners().get(1).structName());
        assertEquals(1, texture.descriptorOwners().get(1).cleanupOrder());

        CudaImageSamplerNativeDescriptorAllocationTransactionPlan.Entry surface = plan.entries().get(1);
        assertEquals(1, surface.descriptorOwners().size());
        assertEquals("resource", surface.descriptorOwners().get(0).descriptorKind());
        assertEquals(2, surface.descriptorOwners().get(0).cleanupOrder());

        CudaImageSamplerNativeDescriptorAllocationTransactionPlan.Entry sampler = plan.entries().get(2);
        assertEquals(1, sampler.descriptorOwners().size());
        assertEquals("texture", sampler.descriptorOwners().get(0).descriptorKind());
        assertEquals(3, sampler.descriptorOwners().get(0).cleanupOrder());
        sampler.descriptorOwners().get(0).close();
        assertTrue(sampler.descriptorOwners().get(0).closed());
        assertFalse(sampler.descriptorOwners().get(0).active());

        assertEquals("true", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationTransactionPlan.present"));
        assertEquals("blocked", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationTransactionPlan.status"));
        assertEquals("3", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationTransactionPlan.entry.count"));
        assertEquals("4", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationTransactionPlan.descriptorOwner.count"));
        assertEquals("2", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationTransactionPlan.resourceDescriptorOwner.count"));
        assertEquals("2", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationTransactionPlan.textureDescriptorOwner.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationTransactionPlan.activeDescriptorOwner.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationTransactionPlan.nativeAddress.present.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationTransactionPlan.allocation.enabled.count"));
        assertEquals("4", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationTransactionPlan.cleanup.planned.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationTransactionPlan.cleanup.active.count"));
        assertEquals("4", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationTransactionPlan.rollback.planned.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationTransactionPlan.rollback.active.count"));
        assertEquals("false", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationTransactionPlan.allocationApply.enabled"));
        assertEquals("false", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationTransactionPlan.nativeMemoryAllocation.enabled"));
        assertEquals("false", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationTransactionPlan.sdkStructByteEncoding.enabled"));
        assertEquals("false", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationTransactionPlan.cleanupApply.enabled"));
        assertEquals("false", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationTransactionPlan.rollbackApply.enabled"));
        assertEquals("cuda-image-sampler-native-descriptor-allocation-transaction-disabled:0:" + Image2DReadOnly.class.getName(), fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationTransactionPlan.firstBlocker"));
        assertEquals("resource", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationTransactionPlan.entry.0.descriptorOwner.0.descriptor.kind"));
        assertEquals("CUDA_RESOURCE_DESC", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationTransactionPlan.entry.0.descriptorOwner.0.struct"));
        assertEquals("false", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationTransactionPlan.entry.0.descriptorOwner.0.nativeAddress.present"));
        assertEquals("texture", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationTransactionPlan.entry.2.descriptorOwner.0.descriptor.kind"));
        assertEquals("planned-inactive", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationTransactionPlan.entry.2.descriptorOwner.0.cleanup.status"));
    }

    private static void assertCase(
            Map<String, CudaImageSamplerNativeDescriptorAllocationTransactionPlanReport.Case> cases,
            String key,
            String expectedTransactionStatus,
            String expectedFirstBlocker,
            int expectedResourceDescriptorOwners,
            int expectedTextureDescriptorOwners
    ) {
        CudaImageSamplerNativeDescriptorAllocationTransactionPlanReport.Case testCase = cases.get(key);
        assertNotNull(testCase, "missing native descriptor allocation-transaction case " + key);
        assertTrue(testCase.ready(), key);
        assertEquals(expectedTransactionStatus, testCase.plan().status());
        assertEquals(expectedFirstBlocker, testCase.plan().firstBlocker());
        assertEquals(expectedResourceDescriptorOwners, testCase.plan().resourceDescriptorOwnerCount());
        assertEquals(expectedTextureDescriptorOwners, testCase.plan().textureDescriptorOwnerCount());
        assertEquals(expectedResourceDescriptorOwners + expectedTextureDescriptorOwners, testCase.plan().descriptorOwnerCount());
        assertEquals(0, testCase.plan().activeDescriptorOwnerCount());
        assertEquals(0, testCase.plan().nativeAddressPresentCount());
        assertEquals(0, testCase.plan().allocationEnabledCount());
        assertEquals(0, testCase.plan().activeNativeDescriptorCount());
    }
}
