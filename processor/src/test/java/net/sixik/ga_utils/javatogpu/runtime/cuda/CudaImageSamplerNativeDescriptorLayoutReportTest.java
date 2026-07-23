package net.sixik.ga_utils.javatogpu.runtime.cuda;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CudaImageSamplerNativeDescriptorLayoutReportTest {

    @Test
    void nativeDescriptorLayoutIsReadyButNativeAllocationRemainsDisabled() {
        CudaImageSamplerNativeDescriptorLayoutReport report = CudaImageSamplerNativeDescriptorLayoutReport.inspectBuiltIns();
        Map<String, CudaImageSamplerNativeDescriptorLayoutReport.Entry> entries = report.entries().stream()
                .collect(Collectors.toMap(CudaImageSamplerNativeDescriptorLayoutReport.Entry::key, entry -> entry));
        Map<String, String> fields = report.artifactFields("test.cuda.imageSamplerNativeDescriptorLayout");
        String rendered = CudaImageSamplerNativeDescriptorLayoutCli.render(report);

        assertEquals("ready", report.status());
        assertTrue(report.ready());
        assertEquals("none", report.firstBlocker());
        assertEquals(17, report.entries().size());
        assertEquals(17, report.entryReadyCount());
        assertEquals(0, report.entryBlockedCount());
        assertEquals(8, report.textureEntryCount());
        assertEquals(8, report.surfaceEntryCount());
        assertEquals(1, report.samplerEntryCount());
        assertEquals(16, report.resourceLayoutRequiredCount());
        assertEquals(35, report.resourceLayoutFieldCount());
        assertEquals(9, report.textureLayoutRequiredCount());
        assertEquals(54, report.textureLayoutFieldCount());
        assertEquals(17, report.nativeLayoutPreviewCount());
        assertFalse(report.nativeLayoutBuildEnabled());
        assertFalse(report.nativeDescriptorAllocationEnabled());
        assertEquals(0, report.activeNativeDescriptorCount());
        assertTrue(report.descriptorContract().ready());

        assertEntry(
                entries,
                "image2d-read-only",
                true,
                "CUDA_RESOURCE_DESC",
                2,
                "resType+res.array.hArray",
                true,
                "CUDA_TEXTURE_DESC",
                6,
                "addressMode[3]+filterMode+flags+readMode"
        );
        assertEntry(
                entries,
                "image2d-write-only",
                true,
                "CUDA_RESOURCE_DESC",
                2,
                "resType+res.array.hArray",
                false,
                "not-required",
                0,
                "not-required"
        );
        assertEntry(
                entries,
                "sampler",
                false,
                "not-required",
                0,
                "not-required",
                true,
                "CUDA_TEXTURE_DESC",
                6,
                "addressMode[3]+filterMode+flags+readMode"
        );
        assertEntry(
                entries,
                "image1d-buffer-read-only",
                true,
                "CUDA_RESOURCE_DESC",
                5,
                "resType+res.linear.devPtr+format+numChannels+sizeInBytes",
                true,
                "CUDA_TEXTURE_DESC",
                6,
                "addressMode[3]+filterMode+flags+readMode"
        );
        assertEntry(
                entries,
                "image1d-buffer-write-only",
                true,
                "CUDA_RESOURCE_DESC",
                2,
                "resType+staged-array-resource-handle-pending",
                false,
                "not-required",
                0,
                "not-required"
        );

        assertEquals("ready", fields.get("runtime.cuda.imageSamplerNativeDescriptorLayout.status"));
        assertEquals("false", fields.get("runtime.cuda.imageSamplerNativeDescriptorLayout.nativeLayoutBuild.enabled"));
        assertEquals("false", fields.get("runtime.cuda.imageSamplerNativeDescriptorLayout.nativeDescriptorAllocation.enabled"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerNativeDescriptorLayout.activeNativeDescriptor.count"));
        assertEquals("16", fields.get("runtime.cuda.imageSamplerNativeDescriptorLayout.resourceLayout.required.count"));
        assertEquals("35", fields.get("runtime.cuda.imageSamplerNativeDescriptorLayout.resourceLayout.field.count"));
        assertEquals("9", fields.get("runtime.cuda.imageSamplerNativeDescriptorLayout.textureLayout.required.count"));
        assertEquals("54", fields.get("runtime.cuda.imageSamplerNativeDescriptorLayout.textureLayout.field.count"));
        assertEquals("17", fields.get("runtime.cuda.imageSamplerNativeDescriptorLayout.nativeLayout.preview.count"));
        assertEquals("ready", fields.get("runtime.cuda.imageSamplerNativeDescriptorLayout.descriptorContract.status"));
        assertEquals("true", fields.get("runtime.cuda.imageSamplerNativeDescriptorLayout.descriptorContract.ready"));
        assertEquals("none", fields.get("runtime.cuda.imageSamplerNativeDescriptorLayout.firstBlocker"));

        assertTrue(report.toMarkdown().contains("CUDA image/sampler native descriptor layout: ready"));
        assertTrue(report.toMarkdown().contains("Resource layouts: 16 entries, 35 fields"));
        assertTrue(report.toMarkdown().contains("Texture layouts: 9 entries, 54 fields"));
        assertTrue(rendered.contains("nativeLayoutBuildEnabled=false"));
        assertTrue(rendered.contains("nativeDescriptorAllocationEnabled=false"));
        assertTrue(rendered.contains("rule=native descriptor layout is a preview only"));
    }

    private static void assertEntry(
            Map<String, CudaImageSamplerNativeDescriptorLayoutReport.Entry> entries,
            String key,
            boolean resourceRequired,
            String resourceStruct,
            int resourceFields,
            String resourceOffsetPolicy,
            boolean textureRequired,
            String textureStruct,
            int textureFields,
            String textureOffsetPolicy
    ) {
        CudaImageSamplerNativeDescriptorLayoutReport.Entry entry = entries.get(key);
        assertNotNull(entry, "missing native descriptor layout entry " + key);
        assertTrue(entry.ready(), key);
        assertEquals(resourceRequired, entry.resourceLayoutRequired());
        assertEquals(resourceStruct, entry.resourceLayoutStruct());
        assertEquals(resourceFields, entry.resourceLayoutFieldCount());
        assertEquals(resourceOffsetPolicy, entry.resourceLayoutOffsetPolicy());
        assertEquals(textureRequired, entry.textureLayoutRequired());
        assertEquals(textureStruct, entry.textureLayoutStruct());
        assertEquals(textureFields, entry.textureLayoutFieldCount());
        assertEquals(textureOffsetPolicy, entry.textureLayoutOffsetPolicy());
        assertEquals("preview-only", entry.nativeLayoutStatus());
        assertEquals("disabled", entry.nativeAllocationStatus());
        assertEquals("fail-closed", entry.runtimeBindingStatus());
        assertFalse(entry.productionSupportEnabled());
    }
}
