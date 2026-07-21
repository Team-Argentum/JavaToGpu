package net.sixik.ga_utils.javatogpu.runtime.cuda;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CudaImageSamplerNativeDescriptorAllocationResultReportTest {

    @Test
    void explicitAllocationResultReportAllocatesAndClosesWithoutCrossingRuntimeBoundaries() {
        CudaImageSamplerNativeDescriptorAllocationResultReport report =
                CudaImageSamplerNativeDescriptorAllocationResultReport.inspectSample();
        Map<String, String> fields = report.artifactFields("test.cuda.imageSamplerNativeDescriptorAllocationResultReport");

        assertEquals("ready", report.status());
        assertTrue(report.ready());
        assertEquals("none", report.firstBlocker());
        assertEquals("blocked", report.allocationTransactionPlanStatus());
        assertTrue(report.allocationTransactionPlanPresent());
        assertTrue(report.allocationApplyEnabled());
        assertTrue(report.nativeMemoryAllocationEnabled());
        assertFalse(report.sdkStructByteEncodingEnabled());
        assertTrue(report.cleanupApplyEnabled());
        assertTrue(report.rollbackApplyEnabled());
        assertFalse(report.objectCreationEnabled());
        assertFalse(report.runtimeBindingEnabled());
        assertEquals("native-memory:lwjgl", report.nativeMemoryServiceSummary());
        assertEquals(3, report.entryCount());
        assertEquals(3, report.entryReadyCount());
        assertEquals(0, report.entryBlockedCount());
        assertEquals(4, report.descriptorOwnerCount());
        assertEquals(2, report.resourceDescriptorOwnerCount());
        assertEquals(2, report.textureDescriptorOwnerCount());
        assertEquals(4, report.activeDescriptorOwnerCountBeforeClose());
        assertEquals(4, report.nativeAddressPresentCountBeforeClose());
        assertEquals(4, report.allocationEnabledCount());
        assertEquals(4, report.cleanupActiveCountBeforeClose());
        assertEquals(4, report.rollbackActiveCountBeforeClose());
        assertEquals(384, report.expectedNativeByteSize());
        assertEquals(384, report.nativeByteSize());
        assertTrue(report.closedAfterClose());
        assertEquals("blocked", report.statusAfterClose());
        assertEquals("cuda-image-sampler-native-descriptor-allocation-result-closed", report.firstBlockerAfterClose());
        assertEquals(0, report.activeDescriptorOwnerCountAfterClose());
        assertEquals(0, report.nativeAddressPresentCountAfterClose());
        assertEquals(0, report.cleanupActiveCountAfterClose());
        assertEquals(0, report.rollbackActiveCountAfterClose());

        assertEquals("ready", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationResultReport.status"));
        assertEquals("true", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationResultReport.nativeMemoryAllocation.enabled"));
        assertEquals("false", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationResultReport.sdkStructByteEncoding.enabled"));
        assertEquals("false", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationResultReport.objectCreation.enabled"));
        assertEquals("false", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationResultReport.runtimeBinding.enabled"));
        assertEquals("native-memory:lwjgl", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationResultReport.nativeMemory.service.summary"));
        assertEquals("4", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationResultReport.nativeAddress.present.beforeClose.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationResultReport.nativeAddress.present.afterClose.count"));
        assertEquals("384", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationResultReport.nativeByteSize"));

        String cli = CudaImageSamplerNativeDescriptorAllocationResultCli.render(report);
        assertTrue(cli.contains("status=ready"));
        assertTrue(cli.contains("nativeMemoryAllocationEnabled=true"));
        assertTrue(cli.contains("sdkStructByteEncodingEnabled=false"));
        assertTrue(cli.contains("objectCreationEnabled=false"));
        assertTrue(cli.contains("runtimeBindingEnabled=false"));
        assertTrue(cli.contains("nativeMemoryServices=native-memory:lwjgl"));
        assertTrue(cli.contains("nativeAddressesPresentAfterClose=0"));
    }
}
