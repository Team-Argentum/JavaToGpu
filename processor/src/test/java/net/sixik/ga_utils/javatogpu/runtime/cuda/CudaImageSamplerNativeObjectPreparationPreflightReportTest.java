package net.sixik.ga_utils.javatogpu.runtime.cuda;

import net.sixik.ga_utils.javatogpu.api.images.Image1DReadOnly;
import net.sixik.ga_utils.javatogpu.api.images.Image2DReadOnly;
import net.sixik.ga_utils.javatogpu.api.images.Image2DWriteOnly;
import net.sixik.ga_utils.javatogpu.api.images.Sampler;
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

class CudaImageSamplerNativeObjectPreparationPreflightReportTest {

    @Test
    void nativeObjectPreparationPreflightReportPinsFailClosedNativePrerequisites() {
        CudaImageSamplerNativeObjectPreparationPreflightReport report =
                CudaImageSamplerNativeObjectPreparationPreflightReport.inspectBuiltIns();
        Map<String, CudaImageSamplerNativeObjectPreparationPreflightReport.Case> cases = report.cases().stream()
                .collect(Collectors.toMap(CudaImageSamplerNativeObjectPreparationPreflightReport.Case::key, testCase -> testCase));
        Map<String, String> fields = report.artifactFields("test.cuda.imageSamplerNativeObjectPreparationPreflightReport");
        String rendered = CudaImageSamplerNativeObjectPreparationPreflightCli.render(report);

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
        assertEquals(16, report.objectPreparationCount());
        assertEquals(8, report.textureObjectPreparationCount());
        assertEquals(8, report.surfaceObjectPreparationCount());
        assertEquals(1, report.foldedSamplerPreparationCount());
        assertEquals(16, report.resourceDescriptorRequiredCount());
        assertEquals(0, report.resourceDescriptorAvailableCount());
        assertEquals(8, report.textureDescriptorRequiredCount());
        assertEquals(0, report.textureDescriptorAvailableCount());
        assertEquals(16, report.createFunctionRequiredCount());
        assertEquals(16, report.createFunctionAvailableCount());
        assertEquals(16, report.destroyFunctionRequiredCount());
        assertEquals(16, report.destroyFunctionAvailableCount());
        assertEquals(16, report.objectHandleRequiredCount());
        assertEquals(0, report.objectHandleAvailableCount());
        assertEquals(0, report.objectOwnershipAvailableCount());
        assertEquals(0, report.objectCreationCallEnabledCount());
        assertEquals(0, report.activeNativeDescriptorCount());
        assertEquals(0, report.activeObjectCount());

        assertCase(
                cases,
                "all-builtins-valid",
                "blocked",
                "cuda-image-sampler-native-resource-descriptor-address-unavailable:0:" + Image1DReadOnly.class.getName(),
                16,
                8,
                8,
                1,
                16,
                8,
                16,
                16
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
                0,
                0
        );

        assertEquals("ready", fields.get("runtime.cuda.imageSamplerNativeObjectPreparationPreflightReport.status"));
        assertEquals("3", fields.get("runtime.cuda.imageSamplerNativeObjectPreparationPreflightReport.case.ready.count"));
        assertEquals("1", fields.get("runtime.cuda.imageSamplerNativeObjectPreparationPreflightReport.plan.ready.count"));
        assertEquals("2", fields.get("runtime.cuda.imageSamplerNativeObjectPreparationPreflightReport.plan.blocked.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerNativeObjectPreparationPreflightReport.preflight.ready.count"));
        assertEquals("3", fields.get("runtime.cuda.imageSamplerNativeObjectPreparationPreflightReport.preflight.blocked.count"));
        assertEquals("16", fields.get("runtime.cuda.imageSamplerNativeObjectPreparationPreflightReport.objectPreparation.count"));
        assertEquals("8", fields.get("runtime.cuda.imageSamplerNativeObjectPreparationPreflightReport.textureObjectPreparation.count"));
        assertEquals("8", fields.get("runtime.cuda.imageSamplerNativeObjectPreparationPreflightReport.surfaceObjectPreparation.count"));
        assertEquals("1", fields.get("runtime.cuda.imageSamplerNativeObjectPreparationPreflightReport.foldedSamplerPreparation.count"));
        assertEquals("16", fields.get("runtime.cuda.imageSamplerNativeObjectPreparationPreflightReport.resourceDescriptor.required.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerNativeObjectPreparationPreflightReport.resourceDescriptor.available.count"));
        assertEquals("16", fields.get("runtime.cuda.imageSamplerNativeObjectPreparationPreflightReport.resourceDescriptorOwner.present.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerNativeObjectPreparationPreflightReport.resourceDescriptor.nativeAddress.present.count"));
        assertEquals("16", fields.get("runtime.cuda.imageSamplerNativeObjectPreparationPreflightReport.resourceDescriptorWrite.planned.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerNativeObjectPreparationPreflightReport.resourceDescriptorNativeWrite.enabled.count"));
        assertEquals("8", fields.get("runtime.cuda.imageSamplerNativeObjectPreparationPreflightReport.textureDescriptor.required.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerNativeObjectPreparationPreflightReport.textureDescriptor.available.count"));
        assertEquals("8", fields.get("runtime.cuda.imageSamplerNativeObjectPreparationPreflightReport.textureDescriptorOwner.present.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerNativeObjectPreparationPreflightReport.textureDescriptor.nativeAddress.present.count"));
        assertEquals("8", fields.get("runtime.cuda.imageSamplerNativeObjectPreparationPreflightReport.textureDescriptorWrite.planned.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerNativeObjectPreparationPreflightReport.textureDescriptorNativeWrite.enabled.count"));
        assertEquals("16", fields.get("runtime.cuda.imageSamplerNativeObjectPreparationPreflightReport.createFunction.required.count"));
        assertEquals("16", fields.get("runtime.cuda.imageSamplerNativeObjectPreparationPreflightReport.createFunction.available.count"));
        assertEquals("16", fields.get("runtime.cuda.imageSamplerNativeObjectPreparationPreflightReport.destroyFunction.required.count"));
        assertEquals("16", fields.get("runtime.cuda.imageSamplerNativeObjectPreparationPreflightReport.destroyFunction.available.count"));
        assertEquals("16", fields.get("runtime.cuda.imageSamplerNativeObjectPreparationPreflightReport.objectHandle.required.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerNativeObjectPreparationPreflightReport.objectHandle.available.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerNativeObjectPreparationPreflightReport.objectCreationCall.enabled.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerNativeObjectPreparationPreflightReport.activeNativeDescriptor.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerNativeObjectPreparationPreflightReport.activeObject.count"));
        assertEquals("false", fields.get("runtime.cuda.imageSamplerNativeObjectPreparationPreflightReport.nativeDescriptorAllocation.enabled"));
        assertEquals("false", fields.get("runtime.cuda.imageSamplerNativeObjectPreparationPreflightReport.objectCreationCall.enabled"));
        assertEquals("false", fields.get("runtime.cuda.imageSamplerNativeObjectPreparationPreflightReport.objectOwnership.enabled"));
        assertEquals("false", fields.get("runtime.cuda.imageSamplerNativeObjectPreparationPreflightReport.runtimeBinding.enabled"));
        assertEquals("none", fields.get("runtime.cuda.imageSamplerNativeObjectPreparationPreflightReport.firstBlocker"));

        assertTrue(report.toMarkdown().contains("CUDA image/sampler native object preparation preflight: ready"));
        assertTrue(report.toMarkdown().contains("Preflights: ready=0, blocked=3"));
        assertTrue(rendered.contains("caseReady=3/3"));
        assertTrue(rendered.contains("resourceDescriptorsAvailable=0"));
        assertTrue(rendered.contains("rule=native object preparation is blocked"));
    }

    @Test
    void nativeObjectPreparationPreflightRecordsDescriptorAndLifecycleGaps() {
        CudaImageSamplerNativeDescriptorEncodingPlan encodingPlan = CudaImageSamplerNativeDescriptorEncodingPlan.from(
                CudaImageSamplerDescriptorPayloadModel.from(
                        CudaImageSamplerDescriptorBuildPlan.from(
                                List.of(
                                        new GpuKernelParameterDescriptor("inputImage", Image2DReadOnly.class.getName(), GpuKernelParameterAccess.READ_ONLY),
                                        new GpuKernelParameterDescriptor("outputImage", Image2DWriteOnly.class.getName(), GpuKernelParameterAccess.READ_WRITE),
                                        new GpuKernelParameterDescriptor("sampler", Sampler.class.getName(), GpuKernelParameterAccess.VALUE)
                                ),
                                new Object[]{
                                        Image2DReadOnly.borrowed(0xCAFE_7601L, 8, 4),
                                        Image2DWriteOnly.borrowed(0xCAFE_7602L, 8, 4),
                                        Sampler.borrowed(0xCAFE_7603L)
                                }
                        )
                )
        );
        CudaImageSamplerNativeObjectPreparationPreflight preflight =
                CudaImageSamplerNativeObjectPreparationPreflight.from(
                        CudaImageSamplerObjectCreationRequestPlan.from(encodingPlan),
                        CudaImageSamplerNativeDescriptorEncodingTransactionPlan.from(
                                encodingPlan,
                                CudaImageSamplerNativeDescriptorAllocationTransactionPlan.from(
                                        CudaImageSamplerNativeDescriptorAllocationPreflight.from(encodingPlan)
                                )
                        )
                );
        Map<String, String> fields = preflight.artifactFields("test.cuda.imageSamplerNativeObjectPreparationPreflight");

        assertEquals("blocked", preflight.status());
        assertFalse(preflight.ready());
        assertEquals("cuda-image-sampler-native-resource-descriptor-address-unavailable:0:" + Image2DReadOnly.class.getName(), preflight.firstBlocker());
        assertEquals(3, preflight.entries().size());
        assertEquals(1, preflight.entryReadyCount());
        assertEquals(2, preflight.entryBlockedCount());
        assertEquals(2, preflight.objectPreparationCount());
        assertEquals(1, preflight.textureObjectPreparationCount());
        assertEquals(1, preflight.surfaceObjectPreparationCount());
        assertEquals(1, preflight.foldedSamplerPreparationCount());
        assertEquals(2, preflight.resourceDescriptorRequiredCount());
        assertEquals(0, preflight.resourceDescriptorAvailableCount());
        assertEquals(2, preflight.resourceDescriptorOwnerPresentCount());
        assertEquals(0, preflight.resourceDescriptorNativeAddressPresentCount());
        assertEquals(2, preflight.resourceDescriptorWritePlannedCount());
        assertEquals(0, preflight.resourceDescriptorNativeWriteEnabledCount());
        assertEquals(1, preflight.textureDescriptorRequiredCount());
        assertEquals(0, preflight.textureDescriptorAvailableCount());
        assertEquals(1, preflight.textureDescriptorOwnerPresentCount());
        assertEquals(0, preflight.textureDescriptorNativeAddressPresentCount());
        assertEquals(1, preflight.textureDescriptorWritePlannedCount());
        assertEquals(0, preflight.textureDescriptorNativeWriteEnabledCount());
        assertEquals(2, preflight.createFunctionRequiredCount());
        assertEquals(2, preflight.createFunctionAvailableCount());
        assertEquals(2, preflight.destroyFunctionRequiredCount());
        assertEquals(2, preflight.destroyFunctionAvailableCount());
        assertEquals(2, preflight.objectHandleRequiredCount());
        assertEquals(0, preflight.objectHandleAvailableCount());
        assertEquals(0, preflight.objectOwnershipAvailableCount());
        assertEquals(0, preflight.objectCreationCallEnabledCount());
        assertFalse(preflight.nativeDescriptorAllocationEnabled());
        assertFalse(preflight.objectCreationCallEnabled());
        assertFalse(preflight.objectOwnershipEnabled());
        assertFalse(preflight.runtimeBindingEnabled());
        assertEquals(0, preflight.activeNativeDescriptorCount());
        assertEquals(0, preflight.activeObjectCount());

        CudaImageSamplerNativeObjectPreparationPreflight.Entry texture = preflight.entries().get(0);
        assertTrue(texture.textureObjectPreparation());
        assertEquals("texture", texture.objectKind());
        assertEquals("CUtexObject", texture.parameterCarrier());
        assertTrue(texture.resourceDescriptorRequired());
        assertFalse(texture.resourceDescriptorAvailable());
        assertTrue(texture.resourceDescriptorOwnerPresent());
        assertFalse(texture.resourceDescriptorNativeAddressPresent());
        assertTrue(texture.resourceDescriptorWritePlanned());
        assertFalse(texture.resourceDescriptorNativeWriteEnabled());
        assertTrue(texture.textureDescriptorRequired());
        assertFalse(texture.textureDescriptorAvailable());
        assertTrue(texture.textureDescriptorOwnerPresent());
        assertFalse(texture.textureDescriptorNativeAddressPresent());
        assertTrue(texture.textureDescriptorWritePlanned());
        assertFalse(texture.textureDescriptorNativeWriteEnabled());
        assertTrue(texture.createFunctionAvailable());
        assertTrue(texture.destroyFunctionAvailable());
        assertTrue(texture.objectHandleRequired());
        assertFalse(texture.objectHandleAvailable());
        assertTrue(texture.objectOwnershipRequired());
        assertFalse(texture.objectOwnershipAvailable());
        assertEquals("blocked", texture.status());

        CudaImageSamplerNativeObjectPreparationPreflight.Entry surface = preflight.entries().get(1);
        assertTrue(surface.surfaceObjectPreparation());
        assertEquals("surface", surface.objectKind());
        assertEquals("CUsurfObject", surface.parameterCarrier());
        assertTrue(surface.resourceDescriptorRequired());
        assertFalse(surface.textureDescriptorRequired());
        assertTrue(surface.resourceDescriptorOwnerPresent());
        assertTrue(surface.resourceDescriptorWritePlanned());
        assertEquals("blocked", surface.status());

        CudaImageSamplerNativeObjectPreparationPreflight.Entry sampler = preflight.entries().get(2);
        assertFalse(sampler.objectPreparationRequired());
        assertTrue(sampler.foldedSamplerDescriptorState());
        assertTrue(sampler.ready());
        assertEquals("ready", sampler.status());
        assertEquals("none", sampler.firstBlocker());

        assertEquals("true", fields.get("runtime.cuda.imageSamplerNativeObjectPreparationPreflight.present"));
        assertEquals("blocked", fields.get("runtime.cuda.imageSamplerNativeObjectPreparationPreflight.status"));
        assertEquals("2", fields.get("runtime.cuda.imageSamplerNativeObjectPreparationPreflight.objectPreparation.count"));
        assertEquals("1", fields.get("runtime.cuda.imageSamplerNativeObjectPreparationPreflight.textureObjectPreparation.count"));
        assertEquals("1", fields.get("runtime.cuda.imageSamplerNativeObjectPreparationPreflight.surfaceObjectPreparation.count"));
        assertEquals("1", fields.get("runtime.cuda.imageSamplerNativeObjectPreparationPreflight.foldedSamplerPreparation.count"));
        assertEquals("2", fields.get("runtime.cuda.imageSamplerNativeObjectPreparationPreflight.resourceDescriptor.required.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerNativeObjectPreparationPreflight.resourceDescriptor.available.count"));
        assertEquals("2", fields.get("runtime.cuda.imageSamplerNativeObjectPreparationPreflight.resourceDescriptorOwner.present.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerNativeObjectPreparationPreflight.resourceDescriptor.nativeAddress.present.count"));
        assertEquals("2", fields.get("runtime.cuda.imageSamplerNativeObjectPreparationPreflight.resourceDescriptorWrite.planned.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerNativeObjectPreparationPreflight.resourceDescriptorNativeWrite.enabled.count"));
        assertEquals("1", fields.get("runtime.cuda.imageSamplerNativeObjectPreparationPreflight.textureDescriptor.required.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerNativeObjectPreparationPreflight.textureDescriptor.available.count"));
        assertEquals("1", fields.get("runtime.cuda.imageSamplerNativeObjectPreparationPreflight.textureDescriptorOwner.present.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerNativeObjectPreparationPreflight.textureDescriptor.nativeAddress.present.count"));
        assertEquals("1", fields.get("runtime.cuda.imageSamplerNativeObjectPreparationPreflight.textureDescriptorWrite.planned.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerNativeObjectPreparationPreflight.textureDescriptorNativeWrite.enabled.count"));
        assertEquals("2", fields.get("runtime.cuda.imageSamplerNativeObjectPreparationPreflight.createFunction.available.count"));
        assertEquals("2", fields.get("runtime.cuda.imageSamplerNativeObjectPreparationPreflight.destroyFunction.available.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerNativeObjectPreparationPreflight.objectHandle.available.count"));
        assertEquals("false", fields.get("runtime.cuda.imageSamplerNativeObjectPreparationPreflight.nativeDescriptorAllocation.enabled"));
        assertEquals("false", fields.get("runtime.cuda.imageSamplerNativeObjectPreparationPreflight.objectCreationCall.enabled"));
        assertEquals("texture", fields.get("runtime.cuda.imageSamplerNativeObjectPreparationPreflight.entry.0.object.kind"));
        assertEquals("false", fields.get("runtime.cuda.imageSamplerNativeObjectPreparationPreflight.entry.0.resourceDescriptor.available"));
        assertEquals("true", fields.get("runtime.cuda.imageSamplerNativeObjectPreparationPreflight.entry.0.resourceDescriptorOwner.present"));
        assertEquals("true", fields.get("runtime.cuda.imageSamplerNativeObjectPreparationPreflight.entry.0.resourceDescriptorWrite.planned"));
        assertEquals("false", fields.get("runtime.cuda.imageSamplerNativeObjectPreparationPreflight.entry.0.resourceDescriptorNativeWrite.enabled"));
        assertEquals("surface", fields.get("runtime.cuda.imageSamplerNativeObjectPreparationPreflight.entry.1.object.kind"));
        assertEquals("true", fields.get("runtime.cuda.imageSamplerNativeObjectPreparationPreflight.entry.2.foldedSamplerDescriptorState"));
    }

    private static void assertCase(
            Map<String, CudaImageSamplerNativeObjectPreparationPreflightReport.Case> cases,
            String key,
            String expectedPreflightStatus,
            String expectedFirstBlocker,
            int expectedObjectPreparations,
            int expectedTexturePreparations,
            int expectedSurfacePreparations,
            int expectedFoldedSamplers,
            int expectedResourceDescriptors,
            int expectedTextureDescriptors,
            int expectedCreateFunctions,
            int expectedDestroyFunctions
    ) {
        CudaImageSamplerNativeObjectPreparationPreflightReport.Case testCase = cases.get(key);
        assertNotNull(testCase, "missing native object-preparation preflight case " + key);
        assertTrue(testCase.ready(), key);
        assertEquals(expectedPreflightStatus, testCase.preflight().status());
        assertEquals(expectedFirstBlocker, testCase.preflight().firstBlocker());
        assertEquals(expectedObjectPreparations, testCase.preflight().objectPreparationCount());
        assertEquals(expectedTexturePreparations, testCase.preflight().textureObjectPreparationCount());
        assertEquals(expectedSurfacePreparations, testCase.preflight().surfaceObjectPreparationCount());
        assertEquals(expectedFoldedSamplers, testCase.preflight().foldedSamplerPreparationCount());
        assertEquals(expectedResourceDescriptors, testCase.preflight().resourceDescriptorRequiredCount());
        assertEquals(expectedResourceDescriptors, testCase.preflight().resourceDescriptorOwnerPresentCount());
        assertEquals(expectedResourceDescriptors, testCase.preflight().resourceDescriptorWritePlannedCount());
        assertEquals(expectedTextureDescriptors, testCase.preflight().textureDescriptorRequiredCount());
        assertEquals(expectedTextureDescriptors, testCase.preflight().textureDescriptorOwnerPresentCount());
        assertEquals(expectedTextureDescriptors, testCase.preflight().textureDescriptorWritePlannedCount());
        assertEquals(expectedCreateFunctions, testCase.preflight().createFunctionRequiredCount());
        assertEquals(expectedCreateFunctions, testCase.preflight().createFunctionAvailableCount());
        assertEquals(expectedDestroyFunctions, testCase.preflight().destroyFunctionRequiredCount());
        assertEquals(expectedDestroyFunctions, testCase.preflight().destroyFunctionAvailableCount());
        assertEquals(0, testCase.preflight().resourceDescriptorAvailableCount());
        assertEquals(0, testCase.preflight().resourceDescriptorNativeAddressPresentCount());
        assertEquals(0, testCase.preflight().resourceDescriptorNativeWriteEnabledCount());
        assertEquals(0, testCase.preflight().textureDescriptorAvailableCount());
        assertEquals(0, testCase.preflight().textureDescriptorNativeAddressPresentCount());
        assertEquals(0, testCase.preflight().textureDescriptorNativeWriteEnabledCount());
        assertEquals(0, testCase.preflight().objectHandleAvailableCount());
        assertEquals(0, testCase.preflight().objectCreationCallEnabledCount());
        assertEquals(0, testCase.preflight().activeNativeDescriptorCount());
        assertEquals(0, testCase.preflight().activeObjectCount());
    }
}
