package net.sixik.ga_utils.javatogpu.runtime.opencl;

import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileArtifactDumper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenClExtensionParticipationSummaryTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void readsRuntimeExtensionParticipationArtifactsAndRoundTripsHistorySnapshot() throws Exception {
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
                .resolve(GpuRuntimeCompileArtifactDumper.RUNTIME_EXTENSION_PARTICIPATION_ARTIFACT);
        Files.createDirectories(artifact.getParent());
        Files.writeString(artifact, """
                status=recorded
                backendTarget=OPENCL
                backendFormat=opencl-c
                backendResource=kernel-a.cl
                entry.count=3
                succeeded.count=2
                skipped.count=0
                failedContinued.count=1
                failedClosed.count=0
                pipelineContinued.all=true
                firstFailure=compiler-feedback:mock:FAILED_CONTINUED
                """);

        OpenClExtensionParticipationSummary summary = OpenClExtensionParticipationSummary.read(workloadGate);

        assertEquals("recorded", summary.status());
        assertEquals(2, summary.entries().size());
        assertEquals("recorded", summary.entries().get(0).status());
        assertEquals(3, summary.entries().get(0).executionCount());
        assertEquals(1, summary.entries().get(0).failedContinuedCount());
        assertEquals("compiler-feedback:mock:FAILED_CONTINUED", summary.entries().get(0).firstFailure());
        assertEquals("missing", summary.entries().get(1).status());
        assertEquals(3, summary.totalExecutions());
        assertEquals(1, summary.totalFailedContinued());
        assertEquals(0, summary.totalFailedClosed());
        assertEquals(1, summary.missingCount());
        assertTrue(summary.toMarkdown().contains("## Runtime Extension Participation"));
        assertTrue(summary.toMarkdown().contains("| `kernel-a.cl` | `recorded` | `3` | `1` | `0` | `true` | `compiler-feedback:mock:FAILED_CONTINUED` |"));

        String history = summary.toHistorySummary();
        List<OpenClExtensionParticipationSummary.Entry> parsed =
                OpenClExtensionParticipationSummary.parseHistoryEntries(history).orElseThrow();
        assertEquals(summary.entries(), parsed);
        assertEquals(
                "recorded (kernels=2, executions=3, failedContinued=1, failedClosed=0, missing=1)",
                OpenClExtensionParticipationSummary.aggregateHistorySummary(history)
        );
    }
}
