package net.sixik.ga_utils.javatogpu.runtime.cuda;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CudaImageSamplerAbiPlanReportTest {

    @Test
    void imageAndSamplerAbiPlanIsCompleteButProductionDisabled() {
        CudaImageSamplerAbiPlanReport report = CudaImageSamplerAbiPlanReport.inspectBuiltIns();
        Map<String, CudaImageSamplerAbiPlanReport.Entry> entries = report.entries().stream()
                .collect(Collectors.toMap(CudaImageSamplerAbiPlanReport.Entry::key, entry -> entry));
        Map<String, String> fields = report.artifactFields("test.cuda.imageSamplerAbiPlan");
        String rendered = CudaImageSamplerAbiPlanCli.render(report);

        assertEquals("ready", report.status());
        assertTrue(report.ready());
        assertEquals("none", report.firstBlocker());
        assertEquals(17, report.entries().size());
        assertEquals(17, report.entryReadyCount());
        assertEquals(0, report.entryBlockedCount());
        assertEquals(2, report.sourcePreviewEnabledCount());
        assertEquals(1, report.sourcePreviewFoldedCount());
        assertEquals(14, report.sourcePreviewPendingCount());
        assertEquals(6, report.sourcePreviewKernelParameterSlotCount());
        assertEquals(4, report.sourcePreviewMetadataSlotCount());
        assertEquals(0, report.runtimeBindingEnabledCount());
        assertEquals(44, report.plannedRuntimeKernelParameterSlotCount());
        assertEquals(28, report.plannedRuntimeMetadataSlotCount());
        assertEquals(0, report.runtimeBindingKernelParameterSlotCount());
        assertTrue(report.failClosedContract().ready());

        assertEquals("ready", fields.get("runtime.cuda.imageSamplerAbiPlan.status"));
        assertEquals("17", fields.get("runtime.cuda.imageSamplerAbiPlan.entry.count"));
        assertEquals("17", fields.get("runtime.cuda.imageSamplerAbiPlan.entry.ready.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerAbiPlan.entry.blocked.count"));
        assertEquals("2", fields.get("runtime.cuda.imageSamplerAbiPlan.sourcePreview.enabled.count"));
        assertEquals("1", fields.get("runtime.cuda.imageSamplerAbiPlan.sourcePreview.folded.count"));
        assertEquals("14", fields.get("runtime.cuda.imageSamplerAbiPlan.sourcePreview.pending.count"));
        assertEquals("6", fields.get("runtime.cuda.imageSamplerAbiPlan.sourcePreview.kernelParameterSlot.count"));
        assertEquals("4", fields.get("runtime.cuda.imageSamplerAbiPlan.sourcePreview.metadataSlot.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerAbiPlan.runtimeBinding.enabled.count"));
        assertEquals("44", fields.get("runtime.cuda.imageSamplerAbiPlan.runtimeBinding.plannedKernelParameterSlot.count"));
        assertEquals("28", fields.get("runtime.cuda.imageSamplerAbiPlan.runtimeBinding.plannedMetadataSlot.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerAbiPlan.runtimeBinding.kernelParameterSlot.count"));
        assertEquals("false", fields.get("runtime.cuda.imageSamplerAbiPlan.productionSupport.enabled"));
        assertEquals("planned", fields.get("runtime.cuda.imageSamplerAbiPlan.implementation.status"));
        assertEquals("true", fields.get("runtime.cuda.imageSamplerAbiPlan.failClosedContract.ready"));

        assertEntry(entries, "image2d-read-only", "read-texture-object", "CUtexObject", "cuda-array-2d");
        assertEntry(entries, "image2d-write-only", "write-surface-object", "CUsurfObject", "cuda-array-2d");
        assertEntry(entries, "sampler", "texture-descriptor-state", "folded-into-CUDA_TEXTURE_DESC", "sampler-state");

        assertTrue(report.toMarkdown().contains("CUDA image/sampler ABI plan: ready"));
        assertTrue(report.toMarkdown().contains("Source preview: 2 enabled, 1 folded, 14 pending"));
        assertTrue(report.toMarkdown().contains("Preview kernel slots: 6 total, 4 metadata"));
        assertTrue(report.toMarkdown().contains("Planned runtime slots: 44 total, 28 metadata; active runtime slots: 0"));
        assertTrue(rendered.contains("entryReady=17/17"));
        assertTrue(rendered.contains("sourcePreviewEnabled=2"));
        assertTrue(rendered.contains("sourcePreviewKernelParameterSlots=6"));
        assertTrue(rendered.contains("runtimeBindingEnabled=0"));
        assertTrue(rendered.contains("runtimeBindingKernelParameterSlots=0"));
        assertTrue(rendered.contains("plannedRuntimeKernelParameterSlots=44"));
        assertTrue(rendered.contains("productionSupportEnabled=false"));
        assertTrue(rendered.contains("rule=ABI plan is metadata only"));
    }

    private static void assertEntry(
            Map<String, CudaImageSamplerAbiPlanReport.Entry> entries,
            String key,
            String role,
            String carrier,
            String resource
    ) {
        CudaImageSamplerAbiPlanReport.Entry entry = entries.get(key);
        assertNotNull(entry, "missing ABI entry " + key);
        assertTrue(entry.ready(), key);
        assertEquals(role, entry.cudaAbiRole());
        assertEquals(carrier, entry.parameterCarrier());
        assertEquals(resource, entry.cudaResourceKind());
        assertEquals("planned", entry.implementationStatus());
        assertEquals("fail-closed", entry.runtimeBindingStatus());
        assertFalse(entry.runtimeBindingEnabled());
        assertEquals(0, entry.runtimeBindingKernelParameterSlotCount());
        assertFalse(entry.productionSupportEnabled());
    }
}
