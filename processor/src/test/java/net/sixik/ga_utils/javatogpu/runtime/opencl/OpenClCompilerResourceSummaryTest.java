package net.sixik.ga_utils.javatogpu.runtime.opencl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenClCompilerResourceSummaryTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void readsWorkloadCompilerArtifactsAndRoundTripsHistorySnapshot() throws Exception {
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
                .resolve("backend-compiler-feedback.properties");
        Files.createDirectories(artifact.getParent());
        Files.writeString(artifact, """
                backendResource=kernel-a.cl
                selected.providerId=compiler-feedback:generic
                diagnosticCompilation.binaryInspection.tool=ptxas
                selected.register.effective=38
                selected.spill.storeBytes=0
                selected.spill.loadBytes=0
                selected.stackFrameBytes=0
                """);

        OpenClCompilerResourceSummary summary = OpenClCompilerResourceSummary.read(workloadGate);

        assertEquals("recorded", summary.status());
        assertEquals(2, summary.entries().size());
        assertEquals("recorded", summary.entries().get(0).status());
        assertEquals(38, summary.entries().get(0).registerCount());
        assertEquals("missing", summary.entries().get(1).status());
        assertEquals(1, summary.count("recorded"));
        assertEquals(1, summary.count("missing"));
        assertTrue(summary.toMarkdown().contains("| `kernel-a.cl` | `recorded`"));

        String history = summary.toHistorySummary();
        List<OpenClCompilerResourceSummary.Entry> parsed =
                OpenClCompilerResourceSummary.parseHistoryEntries(history).orElseThrow();
        assertEquals(summary.entries(), parsed);
        assertTrue(OpenClCompilerResourceSummary.aggregateHistorySummary(history).contains("maxRegisters=38"));
    }
}
