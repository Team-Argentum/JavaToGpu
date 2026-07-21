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

class CudaImageSamplerNativeDescriptorAllocationPreflightReportTest {

    @Test
    void nativeDescriptorAllocationPreflightReportPinsFailClosedOwnershipLifecycle() {
        CudaImageSamplerNativeDescriptorAllocationPreflightReport report =
                CudaImageSamplerNativeDescriptorAllocationPreflightReport.inspectBuiltIns();
        Map<String, CudaImageSamplerNativeDescriptorAllocationPreflightReport.Case> cases = report.cases().stream()
                .collect(Collectors.toMap(CudaImageSamplerNativeDescriptorAllocationPreflightReport.Case::key, testCase -> testCase));
        Map<String, String> fields = report.artifactFields("test.cuda.imageSamplerNativeDescriptorAllocationPreflightReport");
        String rendered = CudaImageSamplerNativeDescriptorAllocationPreflightCli.render(report);

        assertEquals("ready", report.status());
        assertTrue(report.ready());
        assertEquals("none", report.firstBlocker());
        assertEquals(3, report.cases().size());
        assertEquals(3, report.caseReadyCount());
        assertEquals(0, report.caseBlockedCount());
        assertEquals(1, report.planReadyCount());
        assertEquals(2, report.planBlockedCount());
        assertEquals(0, report.preflightReadyCount());
        assertEquals(3, report.preflightBlockedCount());
        assertEquals(19, report.entryCount());
        assertEquals(16, report.resourceDescriptorAllocationCount());
        assertEquals(0, report.resourceDescriptorAllocatedCount());
        assertEquals(9, report.textureDescriptorAllocationCount());
        assertEquals(0, report.textureDescriptorAllocatedCount());
        assertEquals(25, report.plannedNativeDescriptorCount());
        assertEquals(0, report.allocatedNativeDescriptorCount());
        assertEquals(25, report.nativeDescriptorOwnershipPlannedCount());
        assertEquals(0, report.nativeDescriptorOwnershipActiveCount());
        assertEquals(25, report.cleanupPlannedCount());
        assertEquals(0, report.cleanupActiveCount());
        assertEquals(25, report.rollbackPlannedCount());
        assertEquals(0, report.rollbackActiveCount());
        assertEquals(0, report.allocationEnabledCount());
        assertEquals(0, report.sdkStructByteEncodingEnabledCount());
        assertEquals(0, report.activeNativeDescriptorCount());

        assertCase(
                cases,
                "all-builtins-valid",
                "blocked",
                "cuda-image-sampler-native-descriptor-allocation-disabled:0:" + Image1DReadOnly.class.getName(),
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

        assertEquals("ready", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationPreflightReport.status"));
        assertEquals("3", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationPreflightReport.case.ready.count"));
        assertEquals("1", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationPreflightReport.plan.ready.count"));
        assertEquals("2", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationPreflightReport.plan.blocked.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationPreflightReport.preflight.ready.count"));
        assertEquals("3", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationPreflightReport.preflight.blocked.count"));
        assertEquals("16", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationPreflightReport.resourceDescriptorAllocation.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationPreflightReport.resourceDescriptor.allocated.count"));
        assertEquals("9", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationPreflightReport.textureDescriptorAllocation.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationPreflightReport.textureDescriptor.allocated.count"));
        assertEquals("25", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationPreflightReport.plannedNativeDescriptor.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationPreflightReport.allocatedNativeDescriptor.count"));
        assertEquals("25", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationPreflightReport.nativeDescriptorOwnership.planned.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationPreflightReport.nativeDescriptorOwnership.active.count"));
        assertEquals("25", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationPreflightReport.cleanup.planned.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationPreflightReport.cleanup.active.count"));
        assertEquals("25", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationPreflightReport.rollback.planned.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationPreflightReport.rollback.active.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationPreflightReport.allocation.enabled.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationPreflightReport.sdkStructByteEncoding.enabled.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationPreflightReport.activeNativeDescriptor.count"));
        assertEquals("false", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationPreflightReport.nativeDescriptorAllocation.enabled"));
        assertEquals("false", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationPreflightReport.sdkStructByteEncoding.enabled"));
        assertEquals("false", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationPreflightReport.objectCreation.enabled"));
        assertEquals("false", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationPreflightReport.runtimeBinding.enabled"));
        assertEquals("none", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationPreflightReport.firstBlocker"));

        assertTrue(report.toMarkdown().contains("CUDA image/sampler native descriptor allocation preflight: ready"));
        assertTrue(report.toMarkdown().contains("Native descriptor allocations: resource=16, texture=9, planned=25, allocated=0"));
        assertTrue(rendered.contains("caseReady=3/3"));
        assertTrue(rendered.contains("plannedNativeDescriptors=25"));
        assertTrue(rendered.contains("rule=native descriptor allocation, ownership, cleanup, and rollback are planned only"));
    }

    @Test
    void nativeDescriptorAllocationPreflightRecordsAllocationOwnershipAndRollbackGaps() {
        CudaImageSamplerNativeDescriptorAllocationPreflight preflight =
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
                                                        Image2DReadOnly.borrowed(0xCAFE_7801L, 8, 4),
                                                        Image2DWriteOnly.borrowed(0xCAFE_7802L, 8, 4),
                                                        Sampler.borrowed(0xCAFE_7803L)
                                                }
                                        )
                                )
                        )
                );
        Map<String, String> fields = preflight.artifactFields("test.cuda.imageSamplerNativeDescriptorAllocationPreflight");

        assertEquals("blocked", preflight.status());
        assertFalse(preflight.ready());
        assertEquals("cuda-image-sampler-native-descriptor-allocation-disabled:0:" + Image2DReadOnly.class.getName(), preflight.firstBlocker());
        assertEquals(3, preflight.entries().size());
        assertEquals(0, preflight.entryReadyCount());
        assertEquals(3, preflight.entryBlockedCount());
        assertEquals(2, preflight.resourceDescriptorAllocationCount());
        assertEquals(0, preflight.resourceDescriptorAllocatedCount());
        assertEquals(2, preflight.textureDescriptorAllocationCount());
        assertEquals(0, preflight.textureDescriptorAllocatedCount());
        assertEquals(4, preflight.plannedNativeDescriptorCount());
        assertEquals(0, preflight.allocatedNativeDescriptorCount());
        assertEquals(4, preflight.nativeDescriptorOwnershipPlannedCount());
        assertEquals(0, preflight.nativeDescriptorOwnershipActiveCount());
        assertEquals(4, preflight.cleanupPlannedCount());
        assertEquals(0, preflight.cleanupActiveCount());
        assertEquals(4, preflight.rollbackPlannedCount());
        assertEquals(0, preflight.rollbackActiveCount());
        assertEquals(0, preflight.allocationEnabledCount());
        assertEquals(0, preflight.sdkStructByteEncodingEnabledCount());
        assertFalse(preflight.nativeDescriptorAllocationEnabled());
        assertFalse(preflight.sdkStructByteEncodingEnabled());
        assertFalse(preflight.objectCreationEnabled());
        assertFalse(preflight.runtimeBindingEnabled());
        assertEquals(0, preflight.activeNativeDescriptorCount());

        CudaImageSamplerNativeDescriptorAllocationPreflight.Entry texture = preflight.entries().get(0);
        assertTrue(texture.resourceDescriptorAllocationRequired());
        assertTrue(texture.resourceDescriptorAllocationPlanned());
        assertFalse(texture.resourceDescriptorAllocated());
        assertTrue(texture.textureDescriptorAllocationRequired());
        assertTrue(texture.textureDescriptorAllocationPlanned());
        assertFalse(texture.textureDescriptorAllocated());
        assertEquals(2, texture.plannedNativeDescriptorCount());
        assertEquals(2, texture.nativeDescriptorOwnershipPlannedCount());
        assertEquals(0, texture.nativeDescriptorOwnershipActiveCount());
        assertEquals("blocked", texture.status());

        CudaImageSamplerNativeDescriptorAllocationPreflight.Entry surface = preflight.entries().get(1);
        assertTrue(surface.resourceDescriptorAllocationRequired());
        assertFalse(surface.textureDescriptorAllocationRequired());
        assertEquals(1, surface.plannedNativeDescriptorCount());
        assertEquals("blocked", surface.status());

        CudaImageSamplerNativeDescriptorAllocationPreflight.Entry sampler = preflight.entries().get(2);
        assertFalse(sampler.resourceDescriptorAllocationRequired());
        assertTrue(sampler.textureDescriptorAllocationRequired());
        assertEquals(1, sampler.plannedNativeDescriptorCount());
        assertEquals("blocked", sampler.status());

        assertEquals("true", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationPreflight.present"));
        assertEquals("blocked", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationPreflight.status"));
        assertEquals("3", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationPreflight.entry.count"));
        assertEquals("2", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationPreflight.resourceDescriptorAllocation.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationPreflight.resourceDescriptor.allocated.count"));
        assertEquals("2", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationPreflight.textureDescriptorAllocation.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationPreflight.textureDescriptor.allocated.count"));
        assertEquals("4", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationPreflight.plannedNativeDescriptor.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationPreflight.allocatedNativeDescriptor.count"));
        assertEquals("4", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationPreflight.nativeDescriptorOwnership.planned.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationPreflight.nativeDescriptorOwnership.active.count"));
        assertEquals("4", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationPreflight.cleanup.planned.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationPreflight.cleanup.active.count"));
        assertEquals("4", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationPreflight.rollback.planned.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationPreflight.rollback.active.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationPreflight.allocation.enabled.count"));
        assertEquals("false", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationPreflight.nativeDescriptorAllocation.enabled"));
        assertEquals("cuda-image-sampler-native-descriptor-allocation-disabled:0:" + Image2DReadOnly.class.getName(), fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationPreflight.firstBlocker"));
        assertEquals("CUDA_RESOURCE_DESC", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationPreflight.entry.0.resourceDescriptor.struct"));
        assertEquals("CUDA_TEXTURE_DESC", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationPreflight.entry.0.textureDescriptor.struct"));
        assertEquals("false", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationPreflight.entry.0.resourceDescriptor.allocated"));
        assertEquals("planned-inactive", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationPreflight.entry.0.cleanup.status"));
        assertEquals("planned-inactive", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationPreflight.entry.0.rollback.status"));
        assertEquals("CUDA_TEXTURE_DESC", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationPreflight.entry.2.textureDescriptor.struct"));
    }

    private static void assertCase(
            Map<String, CudaImageSamplerNativeDescriptorAllocationPreflightReport.Case> cases,
            String key,
            String expectedPreflightStatus,
            String expectedFirstBlocker,
            int expectedResourceDescriptorAllocations,
            int expectedTextureDescriptorAllocations
    ) {
        CudaImageSamplerNativeDescriptorAllocationPreflightReport.Case testCase = cases.get(key);
        assertNotNull(testCase, "missing native descriptor allocation-preflight case " + key);
        assertTrue(testCase.ready(), key);
        assertEquals(expectedPreflightStatus, testCase.preflight().status());
        assertEquals(expectedFirstBlocker, testCase.preflight().firstBlocker());
        assertEquals(expectedResourceDescriptorAllocations, testCase.preflight().resourceDescriptorAllocationCount());
        assertEquals(expectedTextureDescriptorAllocations, testCase.preflight().textureDescriptorAllocationCount());
        assertEquals(expectedResourceDescriptorAllocations + expectedTextureDescriptorAllocations, testCase.preflight().plannedNativeDescriptorCount());
        assertEquals(0, testCase.preflight().allocatedNativeDescriptorCount());
        assertEquals(0, testCase.preflight().allocationEnabledCount());
        assertEquals(0, testCase.preflight().sdkStructByteEncodingEnabledCount());
        assertEquals(0, testCase.preflight().activeNativeDescriptorCount());
    }
}
