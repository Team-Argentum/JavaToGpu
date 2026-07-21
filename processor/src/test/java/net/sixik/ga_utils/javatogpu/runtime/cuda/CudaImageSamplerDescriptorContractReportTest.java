package net.sixik.ga_utils.javatogpu.runtime.cuda;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CudaImageSamplerDescriptorContractReportTest {

    @Test
    void descriptorContractIsReadyButNativeDescriptorBuildRemainsDisabled() {
        CudaImageSamplerDescriptorContractReport report = CudaImageSamplerDescriptorContractReport.inspectBuiltIns();
        Map<String, CudaImageSamplerDescriptorContractReport.Entry> entries = report.entries().stream()
                .collect(Collectors.toMap(CudaImageSamplerDescriptorContractReport.Entry::key, entry -> entry));
        Map<String, String> fields = report.artifactFields("test.cuda.imageSamplerDescriptor");
        String rendered = CudaImageSamplerDescriptorContractCli.render(report);

        assertEquals("ready", report.status());
        assertTrue(report.ready());
        assertEquals("none", report.firstBlocker());
        assertEquals(17, report.entries().size());
        assertEquals(17, report.entryReadyCount());
        assertEquals(0, report.entryBlockedCount());
        assertEquals(8, report.textureEntryCount());
        assertEquals(8, report.surfaceEntryCount());
        assertEquals(1, report.samplerEntryCount());
        assertEquals(16, report.resourceDescriptorRequiredCount());
        assertEquals(9, report.textureDescriptorRequiredCount());
        assertEquals(17, report.nativeLayoutPendingCount());
        assertFalse(report.descriptorBuildEnabled());
        assertFalse(report.nativeDescriptorAllocationEnabled());
        assertEquals(0, report.activeDescriptorCount());
        assertTrue(report.objectCreationContract().ready());

        assertEntry(
                entries,
                "image2d-read-only",
                "CUDA_RESOURCE_TYPE_ARRAY",
                "2d",
                true,
                "nearest-clamp-to-edge-default-until-sampler-metadata-exists"
        );
        assertEntry(
                entries,
                "image2d-write-only",
                "CUDA_RESOURCE_TYPE_ARRAY",
                "2d",
                false,
                "not-required-for-surface-object"
        );
        assertEntry(
                entries,
                "sampler",
                "not-required",
                "none",
                true,
                "folded-sampler-parameter-default"
        );
        assertEntry(
                entries,
                "image1d-buffer-read-only",
                "CUDA_RESOURCE_TYPE_LINEAR",
                "1d",
                true,
                "nearest-clamp-to-edge-default-until-sampler-metadata-exists"
        );
        assertEntry(
                entries,
                "image1d-buffer-write-only",
                "CUDA_RESOURCE_TYPE_ARRAY_STAGING_PENDING",
                "1d",
                false,
                "not-required-for-surface-object"
        );
        assertEntry(
                entries,
                "image2d-mipmapped-read-only",
                "CUDA_RESOURCE_TYPE_MIPMAPPED_ARRAY",
                "2d",
                true,
                "nearest-clamp-to-edge-default-until-sampler-metadata-exists"
        );

        assertEquals("ready", fields.get("runtime.cuda.imageSamplerDescriptor.status"));
        assertEquals("false", fields.get("runtime.cuda.imageSamplerDescriptor.descriptorBuild.enabled"));
        assertEquals("false", fields.get("runtime.cuda.imageSamplerDescriptor.nativeDescriptorAllocation.enabled"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerDescriptor.activeDescriptor.count"));
        assertEquals("16", fields.get("runtime.cuda.imageSamplerDescriptor.resourceDescriptor.required.count"));
        assertEquals("9", fields.get("runtime.cuda.imageSamplerDescriptor.textureDescriptor.required.count"));
        assertEquals("17", fields.get("runtime.cuda.imageSamplerDescriptor.nativeLayout.pending.count"));
        assertEquals("ready", fields.get("runtime.cuda.imageSamplerDescriptor.objectCreationContract.status"));
        assertEquals("true", fields.get("runtime.cuda.imageSamplerDescriptor.objectCreationContract.ready"));
        assertEquals("none", fields.get("runtime.cuda.imageSamplerDescriptor.firstBlocker"));

        assertTrue(report.toMarkdown().contains("CUDA image/sampler descriptor contract: ready"));
        assertTrue(report.toMarkdown().contains("Descriptors: resource=16, texture=9, active=0"));
        assertTrue(rendered.contains("entryReady=17/17"));
        assertTrue(rendered.contains("descriptorBuildEnabled=false"));
        assertTrue(rendered.contains("nativeDescriptorAllocationEnabled=false"));
        assertTrue(rendered.contains("rule=descriptor contract is metadata only"));
    }

    private static void assertEntry(
            Map<String, CudaImageSamplerDescriptorContractReport.Entry> entries,
            String key,
            String resourceKind,
            String dimension,
            boolean textureRequired,
            String samplerStateSource
    ) {
        CudaImageSamplerDescriptorContractReport.Entry entry = entries.get(key);
        assertNotNull(entry, "missing descriptor entry " + key);
        assertTrue(entry.ready(), key);
        assertEquals(resourceKind, entry.resourceDescriptorKind());
        assertEquals(dimension, entry.resourceDescriptorDimension());
        assertEquals(textureRequired, entry.textureDescriptorRequired());
        assertEquals(textureRequired, !"not-required".equals(entry.textureDescriptorAddressMode()));
        assertEquals("fail-closed", entry.descriptorBuildStatus());
        assertEquals("native-layout-pending", entry.nativeLayoutStatus());
        assertEquals(samplerStateSource, entry.samplerStateSource());
        assertFalse(entry.productionSupportEnabled());
    }
}
