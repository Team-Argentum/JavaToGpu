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

class CudaImageSamplerRuntimeObjectBindingTransactionPreflightReportTest {

    @Test
    void transactionPreflightReportPinsFailClosedRuntimeBindingPrerequisites() {
        CudaImageSamplerRuntimeObjectBindingTransactionPreflightReport report =
                CudaImageSamplerRuntimeObjectBindingTransactionPreflightReport.inspectBuiltIns();
        Map<String, CudaImageSamplerRuntimeObjectBindingTransactionPreflightReport.Case> cases = report.cases().stream()
                .collect(Collectors.toMap(CudaImageSamplerRuntimeObjectBindingTransactionPreflightReport.Case::key, testCase -> testCase));
        Map<String, String> fields = report.artifactFields("test.cuda.imageSamplerRuntimeObjectBindingTransactionPreflightReport");
        String rendered = CudaImageSamplerRuntimeObjectBindingTransactionPreflightCli.render(report);

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
        assertEquals(16, report.objectBindingTransactionCount());
        assertEquals(8, report.textureObjectTransactionCount());
        assertEquals(8, report.surfaceObjectTransactionCount());
        assertEquals(1, report.foldedSamplerTransactionCount());
        assertEquals(16, report.plannedObjectKernelParameterSlotCount());
        assertEquals(28, report.plannedMetadataKernelParameterSlotCount());
        assertEquals(44, report.plannedKernelParameterSlotCount());
        assertEquals(16, report.objectHandleRequiredCount());
        assertEquals(0, report.objectHandleAvailableCount());
        assertEquals(0, report.nativeDescriptorAvailableCount());
        assertEquals(16, report.resourceDescriptorRequiredCount());
        assertEquals(16, report.resourceDescriptorOwnerPresentCount());
        assertEquals(0, report.resourceDescriptorNativeAddressPresentCount());
        assertEquals(16, report.resourceDescriptorWritePlannedCount());
        assertEquals(0, report.resourceDescriptorNativeWriteEnabledCount());
        assertEquals(8, report.textureDescriptorRequiredCount());
        assertEquals(8, report.textureDescriptorOwnerPresentCount());
        assertEquals(0, report.textureDescriptorNativeAddressPresentCount());
        assertEquals(8, report.textureDescriptorWritePlannedCount());
        assertEquals(0, report.textureDescriptorNativeWriteEnabledCount());
        assertEquals(0, report.transactionApplyEnabledCount());
        assertEquals(0, report.kernelParameterWriteEnabledCount());
        assertEquals(0, report.activeObjectCount());

        assertCase(
                cases,
                "all-builtins-valid",
                "blocked",
                "cuda-image-sampler-runtime-native-resource-descriptor-address-unavailable:0:" + Image1DReadOnly.class.getName(),
                16,
                8,
                8,
                1,
                16,
                28,
                44
        );
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

        assertEquals("ready", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingTransactionPreflightReport.status"));
        assertEquals("3", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingTransactionPreflightReport.case.ready.count"));
        assertEquals("1", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingTransactionPreflightReport.plan.ready.count"));
        assertEquals("2", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingTransactionPreflightReport.plan.blocked.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingTransactionPreflightReport.preflight.ready.count"));
        assertEquals("3", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingTransactionPreflightReport.preflight.blocked.count"));
        assertEquals("19", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingTransactionPreflightReport.entry.count"));
        assertEquals("16", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingTransactionPreflightReport.objectBindingTransaction.count"));
        assertEquals("8", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingTransactionPreflightReport.textureObjectTransaction.count"));
        assertEquals("8", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingTransactionPreflightReport.surfaceObjectTransaction.count"));
        assertEquals("1", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingTransactionPreflightReport.foldedSamplerTransaction.count"));
        assertEquals("16", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingTransactionPreflightReport.objectHandle.required.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingTransactionPreflightReport.objectHandle.available.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingTransactionPreflightReport.nativeDescriptor.available.count"));
        assertEquals("16", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingTransactionPreflightReport.resourceDescriptor.required.count"));
        assertEquals("16", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingTransactionPreflightReport.resourceDescriptorOwner.present.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingTransactionPreflightReport.resourceDescriptor.nativeAddress.present.count"));
        assertEquals("16", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingTransactionPreflightReport.resourceDescriptorWrite.planned.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingTransactionPreflightReport.resourceDescriptorNativeWrite.enabled.count"));
        assertEquals("8", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingTransactionPreflightReport.textureDescriptor.required.count"));
        assertEquals("8", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingTransactionPreflightReport.textureDescriptorOwner.present.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingTransactionPreflightReport.textureDescriptor.nativeAddress.present.count"));
        assertEquals("8", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingTransactionPreflightReport.textureDescriptorWrite.planned.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingTransactionPreflightReport.textureDescriptorNativeWrite.enabled.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingTransactionPreflightReport.transactionApply.enabled.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingTransactionPreflightReport.kernelParameterWrite.enabled.count"));
        assertEquals("false", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingTransactionPreflightReport.nativeDescriptorAllocation.enabled"));
        assertEquals("false", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingTransactionPreflightReport.objectCreationCall.enabled"));
        assertEquals("false", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingTransactionPreflightReport.transactionApply.enabled"));
        assertEquals("false", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingTransactionPreflightReport.kernelParameterWrite.enabled"));
        assertEquals("none", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingTransactionPreflightReport.firstBlocker"));

        assertTrue(report.toMarkdown().contains("CUDA image/sampler runtime object binding transaction preflight: ready"));
        assertTrue(report.toMarkdown().contains("Preflights: ready=0, blocked=3"));
        assertTrue(rendered.contains("caseReady=3/3"));
        assertTrue(rendered.contains("objectHandlesAvailable=0"));
        assertTrue(rendered.contains("resourceDescriptorsRequired=16"));
        assertTrue(rendered.contains("rule=transaction preflight is blocked"));
    }

    @Test
    void transactionPreflightRecordsMissingNativePrerequisitesWithoutWritingKernelArguments() {
        CudaImageSamplerNativeDescriptorEncodingPlan encodingPlan = CudaImageSamplerNativeDescriptorEncodingPlan.from(
                CudaImageSamplerDescriptorPayloadModel.from(
                        CudaImageSamplerDescriptorBuildPlan.from(
                                List.of(
                                        new GpuKernelParameterDescriptor("inputImage", Image2DReadOnly.class.getName(), GpuKernelParameterAccess.READ_ONLY),
                                        new GpuKernelParameterDescriptor("outputImage", Image2DWriteOnly.class.getName(), GpuKernelParameterAccess.READ_WRITE),
                                        new GpuKernelParameterDescriptor("sampler", Sampler.class.getName(), GpuKernelParameterAccess.VALUE)
                                ),
                                new Object[]{
                                        Image2DReadOnly.borrowed(0xCAFE_7501L, 8, 4),
                                        Image2DWriteOnly.borrowed(0xCAFE_7502L, 8, 4),
                                        Sampler.borrowed(0xCAFE_7503L)
                                }
                        )
                )
        );
        CudaImageSamplerObjectCreationRequestPlan requestPlan = CudaImageSamplerObjectCreationRequestPlan.from(encodingPlan);
        CudaImageSamplerRuntimeObjectBindingPlan runtimeObjectBindingPlan = CudaImageSamplerRuntimeObjectBindingPlan.from(requestPlan);
        CudaImageSamplerNativeObjectPreparationPreflight nativeObjectPreparationPreflight =
                CudaImageSamplerNativeObjectPreparationPreflight.from(
                        requestPlan,
                        CudaImageSamplerNativeDescriptorEncodingTransactionPlan.from(
                                encodingPlan,
                                CudaImageSamplerNativeDescriptorAllocationTransactionPlan.from(
                                        CudaImageSamplerNativeDescriptorAllocationPreflight.from(encodingPlan)
                                )
                        )
                );
        CudaImageSamplerRuntimeObjectBindingTransactionPreflight preflight =
                CudaImageSamplerRuntimeObjectBindingTransactionPreflight.from(runtimeObjectBindingPlan, nativeObjectPreparationPreflight);
        Map<String, String> fields = preflight.artifactFields("test.cuda.imageSamplerRuntimeObjectBindingTransactionPreflight");

        assertEquals("blocked", preflight.status());
        assertFalse(preflight.ready());
        assertEquals("cuda-image-sampler-runtime-native-resource-descriptor-address-unavailable:0:" + Image2DReadOnly.class.getName(), preflight.firstBlocker());
        assertEquals(3, preflight.entries().size());
        assertEquals(1, preflight.entryReadyCount());
        assertEquals(2, preflight.entryBlockedCount());
        assertEquals(2, preflight.objectBindingTransactionCount());
        assertEquals(1, preflight.textureObjectTransactionCount());
        assertEquals(1, preflight.surfaceObjectTransactionCount());
        assertEquals(1, preflight.foldedSamplerTransactionCount());
        assertEquals(2, preflight.plannedObjectKernelParameterSlotCount());
        assertEquals(4, preflight.plannedMetadataKernelParameterSlotCount());
        assertEquals(6, preflight.plannedKernelParameterSlotCount());
        assertEquals(2, preflight.objectHandleRequiredCount());
        assertEquals(0, preflight.objectHandleAvailableCount());
        assertEquals(0, preflight.nativeDescriptorAvailableCount());
        assertEquals(2, preflight.resourceDescriptorRequiredCount());
        assertEquals(2, preflight.resourceDescriptorOwnerPresentCount());
        assertEquals(0, preflight.resourceDescriptorNativeAddressPresentCount());
        assertEquals(2, preflight.resourceDescriptorWritePlannedCount());
        assertEquals(0, preflight.resourceDescriptorNativeWriteEnabledCount());
        assertEquals(1, preflight.textureDescriptorRequiredCount());
        assertEquals(1, preflight.textureDescriptorOwnerPresentCount());
        assertEquals(0, preflight.textureDescriptorNativeAddressPresentCount());
        assertEquals(1, preflight.textureDescriptorWritePlannedCount());
        assertEquals(0, preflight.textureDescriptorNativeWriteEnabledCount());
        assertEquals(0, preflight.transactionApplyEnabledCount());
        assertEquals(0, preflight.kernelParameterWriteEnabledCount());
        assertFalse(preflight.nativeDescriptorAllocationEnabled());
        assertFalse(preflight.objectCreationCallEnabled());
        assertFalse(preflight.transactionApplyEnabled());
        assertFalse(preflight.kernelParameterWriteEnabled());
        assertEquals(0, preflight.activeObjectCount());

        CudaImageSamplerRuntimeObjectBindingTransactionPreflight.Entry texture = preflight.entries().get(0);
        assertTrue(texture.textureObjectTransaction());
        assertEquals("texture", texture.objectKind());
        assertEquals("CUtexObject", texture.parameterCarrier());
        assertTrue(texture.nativeDescriptorRequired());
        assertFalse(texture.nativeDescriptorAvailable());
        assertTrue(texture.resourceDescriptorRequired());
        assertTrue(texture.resourceDescriptorOwnerPresent());
        assertFalse(texture.resourceDescriptorNativeAddressPresent());
        assertTrue(texture.resourceDescriptorWritePlanned());
        assertFalse(texture.resourceDescriptorNativeWriteEnabled());
        assertTrue(texture.textureDescriptorRequired());
        assertTrue(texture.textureDescriptorOwnerPresent());
        assertFalse(texture.textureDescriptorNativeAddressPresent());
        assertTrue(texture.textureDescriptorWritePlanned());
        assertFalse(texture.textureDescriptorNativeWriteEnabled());
        assertTrue(texture.objectHandleRequired());
        assertFalse(texture.objectHandleAvailable());
        assertTrue(texture.objectOwnershipRequired());
        assertFalse(texture.objectOwnershipAvailable());
        assertTrue(texture.kernelParameterWriteRequired());
        assertFalse(texture.kernelParameterWriteEnabled());
        assertEquals("blocked", texture.status());

        CudaImageSamplerRuntimeObjectBindingTransactionPreflight.Entry surface = preflight.entries().get(1);
        assertTrue(surface.surfaceObjectTransaction());
        assertEquals("surface", surface.objectKind());
        assertEquals("CUsurfObject", surface.parameterCarrier());
        assertEquals("blocked", surface.status());

        CudaImageSamplerRuntimeObjectBindingTransactionPreflight.Entry sampler = preflight.entries().get(2);
        assertFalse(sampler.objectBindingRequired());
        assertTrue(sampler.foldedSamplerBinding());
        assertTrue(sampler.ready());
        assertEquals("ready", sampler.status());
        assertEquals("none", sampler.firstBlocker());

        assertEquals("true", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingTransactionPreflight.present"));
        assertEquals("blocked", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingTransactionPreflight.status"));
        assertEquals("ready", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingTransactionPreflight.runtimeObjectBindingPlan.status"));
        assertEquals("blocked", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingTransactionPreflight.nativeObjectPreparationPreflight.status"));
        assertEquals("true", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingTransactionPreflight.nativeObjectPreparationPreflight.present"));
        assertEquals("2", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingTransactionPreflight.objectBindingTransaction.count"));
        assertEquals("1", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingTransactionPreflight.textureObjectTransaction.count"));
        assertEquals("1", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingTransactionPreflight.surfaceObjectTransaction.count"));
        assertEquals("1", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingTransactionPreflight.foldedSamplerTransaction.count"));
        assertEquals("2", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingTransactionPreflight.objectHandle.required.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingTransactionPreflight.objectHandle.available.count"));
        assertEquals("2", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingTransactionPreflight.resourceDescriptor.required.count"));
        assertEquals("2", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingTransactionPreflight.resourceDescriptorOwner.present.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingTransactionPreflight.resourceDescriptor.nativeAddress.present.count"));
        assertEquals("2", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingTransactionPreflight.resourceDescriptorWrite.planned.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingTransactionPreflight.resourceDescriptorNativeWrite.enabled.count"));
        assertEquals("1", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingTransactionPreflight.textureDescriptor.required.count"));
        assertEquals("1", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingTransactionPreflight.textureDescriptorOwner.present.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingTransactionPreflight.textureDescriptor.nativeAddress.present.count"));
        assertEquals("1", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingTransactionPreflight.textureDescriptorWrite.planned.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingTransactionPreflight.textureDescriptorNativeWrite.enabled.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingTransactionPreflight.transactionApply.enabled.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingTransactionPreflight.kernelParameterWrite.enabled.count"));
        assertEquals("false", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingTransactionPreflight.transactionApply.enabled"));
        assertEquals("false", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingTransactionPreflight.kernelParameterWrite.enabled"));
        assertEquals("texture", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingTransactionPreflight.entry.0.object.kind"));
        assertEquals("true", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingTransactionPreflight.entry.0.resourceDescriptorOwner.present"));
        assertEquals("false", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingTransactionPreflight.entry.0.resourceDescriptor.nativeAddress.present"));
        assertEquals("true", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingTransactionPreflight.entry.0.resourceDescriptorWrite.planned"));
        assertEquals("false", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingTransactionPreflight.entry.0.resourceDescriptorNativeWrite.enabled"));
        assertEquals("false", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingTransactionPreflight.entry.0.objectHandle.available"));
        assertEquals("surface", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingTransactionPreflight.entry.1.object.kind"));
        assertEquals("true", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingTransactionPreflight.entry.2.foldedSamplerBinding"));
    }

    private static void assertCase(
            Map<String, CudaImageSamplerRuntimeObjectBindingTransactionPreflightReport.Case> cases,
            String key,
            String expectedPreflightStatus,
            String expectedFirstBlocker,
            int expectedObjectBindingTransactions,
            int expectedTextureObjectTransactions,
            int expectedSurfaceObjectTransactions,
            int expectedFoldedSamplers,
            int expectedObjectSlots,
            int expectedMetadataSlots,
            int expectedKernelSlots
    ) {
        CudaImageSamplerRuntimeObjectBindingTransactionPreflightReport.Case testCase = cases.get(key);
        assertNotNull(testCase, "missing runtime object-binding transaction preflight case " + key);
        assertTrue(testCase.ready(), key);
        assertEquals(expectedPreflightStatus, testCase.preflight().status());
        assertEquals(expectedFirstBlocker, testCase.preflight().firstBlocker());
        assertEquals(expectedObjectBindingTransactions, testCase.preflight().objectBindingTransactionCount());
        assertEquals(expectedTextureObjectTransactions, testCase.preflight().textureObjectTransactionCount());
        assertEquals(expectedSurfaceObjectTransactions, testCase.preflight().surfaceObjectTransactionCount());
        assertEquals(expectedFoldedSamplers, testCase.preflight().foldedSamplerTransactionCount());
        assertEquals(expectedObjectSlots, testCase.preflight().plannedObjectKernelParameterSlotCount());
        assertEquals(expectedMetadataSlots, testCase.preflight().plannedMetadataKernelParameterSlotCount());
        assertEquals(expectedKernelSlots, testCase.preflight().plannedKernelParameterSlotCount());
        assertEquals(0, testCase.preflight().objectHandleAvailableCount());
        assertEquals(0, testCase.preflight().nativeDescriptorAvailableCount());
        assertEquals(expectedObjectBindingTransactions, testCase.preflight().resourceDescriptorRequiredCount());
        assertEquals(expectedObjectBindingTransactions, testCase.preflight().resourceDescriptorOwnerPresentCount());
        assertEquals(0, testCase.preflight().resourceDescriptorNativeAddressPresentCount());
        assertEquals(expectedObjectBindingTransactions, testCase.preflight().resourceDescriptorWritePlannedCount());
        assertEquals(0, testCase.preflight().resourceDescriptorNativeWriteEnabledCount());
        assertEquals(expectedTextureObjectTransactions, testCase.preflight().textureDescriptorRequiredCount());
        assertEquals(expectedTextureObjectTransactions, testCase.preflight().textureDescriptorOwnerPresentCount());
        assertEquals(0, testCase.preflight().textureDescriptorNativeAddressPresentCount());
        assertEquals(expectedTextureObjectTransactions, testCase.preflight().textureDescriptorWritePlannedCount());
        assertEquals(0, testCase.preflight().textureDescriptorNativeWriteEnabledCount());
        assertEquals(0, testCase.preflight().transactionApplyEnabledCount());
        assertEquals(0, testCase.preflight().kernelParameterWriteEnabledCount());
        assertEquals(0, testCase.preflight().activeObjectCount());
    }
}
