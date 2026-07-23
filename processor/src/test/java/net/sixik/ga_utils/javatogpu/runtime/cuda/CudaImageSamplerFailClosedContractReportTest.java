package net.sixik.ga_utils.javatogpu.runtime.cuda;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CudaImageSamplerFailClosedContractReportTest {

    @Test
    void failClosedContractPinsAllImageSamplerNativeMutationBoundariesClosed() {
        CudaImageSamplerFailClosedContractReport report = CudaImageSamplerFailClosedContractReport.inspectBuiltIns();
        Map<String, CudaImageSamplerFailClosedContractReport.Component> components = report.components().stream()
                .collect(Collectors.toMap(CudaImageSamplerFailClosedContractReport.Component::key, component -> component));
        Map<String, String> fields = report.artifactFields("test.cuda.imageSamplerFailClosedContract");
        String rendered = CudaImageSamplerFailClosedContractCli.render(report);

        assertEquals("ready", report.status());
        assertTrue(report.ready());
        assertEquals("none", report.firstBlocker());
        assertEquals(15, report.components().size());
        assertEquals(15, report.componentReadyCount());
        assertEquals(0, report.componentBlockedCount());
        assertEquals(25, report.plannedNativeDescriptorCount());
        assertEquals(16, report.plannedObjectRequestCount());
        assertEquals(44, report.plannedRuntimeKernelParameterSlotCount());
        assertEquals(0, report.nativeMutationCount());
        assertEquals(0, report.runtimeBindingKernelParameterSlotCount());
        assertEquals(0, report.objectCreationCallEnabledCount());
        assertEquals(0, report.nativeDescriptorAvailableCount());
        assertEquals(0, report.nativeDescriptorAddressPresentCount());
        assertEquals(0, report.nativeDescriptorWriteEnabledCount());
        assertEquals(0, report.objectHandleAvailableCount());
        assertEquals(0, report.activeNativeDescriptorCount());
        assertEquals(0, report.activeObjectCount());

        assertReady(components, "argument-contract");
        assertReady(components, "abi-plan");
        assertReady(components, "object-creation-contract");
        assertReady(components, "descriptor-contract");
        assertReady(components, "native-descriptor-layout");
        assertReady(components, "descriptor-build-plan");
        assertReady(components, "descriptor-payload-model");
        assertReady(components, "native-descriptor-encoding-plan");
        assertReady(components, "native-descriptor-allocation-preflight");
        assertReady(components, "native-descriptor-allocation-transaction-plan");
        assertReady(components, "native-descriptor-encoding-transaction-plan");
        assertReady(components, "object-creation-request-plan");
        assertReady(components, "native-object-preparation-preflight");
        assertReady(components, "runtime-object-binding-plan");
        assertReady(components, "runtime-object-binding-transaction-preflight");

        assertEquals("ready", fields.get("runtime.cuda.imageSamplerFailClosedContract.status"));
        assertEquals("15", fields.get("runtime.cuda.imageSamplerFailClosedContract.component.ready.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerFailClosedContract.component.blocked.count"));
        assertEquals("25", fields.get("runtime.cuda.imageSamplerFailClosedContract.plannedNativeDescriptor.count"));
        assertEquals("16", fields.get("runtime.cuda.imageSamplerFailClosedContract.plannedObjectRequest.count"));
        assertEquals("44", fields.get("runtime.cuda.imageSamplerFailClosedContract.plannedRuntimeKernelParameterSlot.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerFailClosedContract.nativeMutation.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerFailClosedContract.runtimeBinding.kernelParameterSlot.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerFailClosedContract.objectCreationCall.enabled.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerFailClosedContract.nativeDescriptor.available.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerFailClosedContract.nativeDescriptor.address.present.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerFailClosedContract.nativeDescriptorWrite.enabled.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerFailClosedContract.objectHandle.available.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerFailClosedContract.activeNativeDescriptor.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerFailClosedContract.activeObject.count"));
        assertEquals("false", fields.get("runtime.cuda.imageSamplerFailClosedContract.nativeDescriptorAllocation.enabled"));
        assertEquals("false", fields.get("runtime.cuda.imageSamplerFailClosedContract.nativeDescriptorWrite.enabled"));
        assertEquals("false", fields.get("runtime.cuda.imageSamplerFailClosedContract.objectCreation.enabled"));
        assertEquals("false", fields.get("runtime.cuda.imageSamplerFailClosedContract.objectBinding.enabled"));
        assertEquals("false", fields.get("runtime.cuda.imageSamplerFailClosedContract.kernelParameterWrite.enabled"));
        assertEquals("none", fields.get("runtime.cuda.imageSamplerFailClosedContract.firstBlocker"));

        assertTrue(report.toMarkdown().contains("CUDA image/sampler fail-closed contract: ready"));
        assertTrue(report.toMarkdown().contains("Native mutation count: 0"));
        assertTrue(rendered.contains("componentReady=15/15"));
        assertTrue(rendered.contains("nativeMutationCount=0"));
        assertTrue(rendered.contains("rule=image/sampler native descriptor allocation"));
    }

    private static void assertReady(
            Map<String, CudaImageSamplerFailClosedContractReport.Component> components,
            String key
    ) {
        CudaImageSamplerFailClosedContractReport.Component component = components.get(key);
        assertTrue(component != null, "missing component " + key);
        assertEquals("ready", component.status(), key);
        assertTrue(component.ready(), key);
        assertEquals("none", component.firstBlocker(), key);
    }
}
