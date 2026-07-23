package net.sixik.ga_utils.javatogpu.runtime.opencl;

import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileArtifactDumper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenClMethodTestEvidenceSummaryTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void readsMethodTestEvidenceArtifactsForValidationReport() throws Exception {
        Path reportDirectory = temporaryDirectory.resolve("reports").resolve("opencl");
        Files.createDirectories(reportDirectory);
        Path workloadGate = reportDirectory.resolve("backend-source-promotion-workload-gate.properties");
        Files.writeString(workloadGate, """
                kernel.count=2
                kernel.0.sourceKernelResource=kernel-a.cl
                kernel.1.sourceKernelResource=kernel-b.cl
                """);
        Path artifact = reportDirectory
                .resolve("runtime-compile-artifacts")
                .resolve("kernel-a")
                .resolve(GpuRuntimeCompileArtifactDumper.RUNTIME_METHOD_TEST_EVIDENCE_ARTIFACT);
        Files.createDirectories(artifact.getParent());
        Files.writeString(artifact, """
                status=recorded
                backendTarget=OPENCL
                backendFormat=opencl-c
                backendResource=kernel-a.cl
                metadata.status=recorded
                metadata.entryTestVector.count=2
                metadata.selectionProbe.count=1
                cacheEvidence.status=active
                cacheEvidence.passed.count=1
                cacheEvidence.failed.count=0
                cacheEvidence.missing.count=1
                cacheEvidence.blocked.count=0
                firstBlocker=none
                """);

        OpenClMethodTestEvidenceSummary summary = OpenClMethodTestEvidenceSummary.read(workloadGate);

        assertEquals("recorded", summary.status());
        assertEquals(2, summary.entries().size());
        assertEquals("recorded", summary.entries().get(0).status());
        assertEquals("recorded", summary.entries().get(0).metadataStatus());
        assertEquals(2, summary.entries().get(0).testVectorCount());
        assertEquals(1, summary.entries().get(0).selectionProbeCount());
        assertEquals("active", summary.entries().get(0).cacheEvidenceStatus());
        assertEquals("missing", summary.entries().get(1).status());
        assertEquals(1, summary.metadataRecordedCount());
        assertEquals(1, summary.cacheEvidenceRecordedCount());
        assertEquals(1, summary.missingCount());
        assertEquals(2, summary.totalTestVectors());
        assertEquals(1, summary.totalSelectionProbes());
        assertEquals(1, summary.totalPassedEvidence());
        assertEquals(0, summary.totalFailedEvidence());
        assertEquals(1, summary.totalMissingEvidence());
        assertTrue(summary.toMarkdown().contains("## Method Test Evidence"));
        assertTrue(summary.toMarkdown().contains("- Metadata recorded kernels: `1`"));
        assertTrue(summary.toMarkdown().contains("- Cached evidence passed/failed/missing: `1/0/1`"));
        assertTrue(summary.toMarkdown().contains("| `kernel-a.cl` | `recorded` | `recorded` | `2` | `1` | `active` | `1` | `0` | `1` | `none` |"));
        assertTrue(summary.toMarkdown().contains("| `kernel-b.cl` | `missing` | `missing` | `0` | `0` | `not-recorded` | `0` | `0` | `0` | `artifact-missing` |"));
    }
}
