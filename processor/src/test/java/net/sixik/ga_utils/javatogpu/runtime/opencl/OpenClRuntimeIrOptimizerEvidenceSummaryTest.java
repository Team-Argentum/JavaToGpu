package net.sixik.ga_utils.javatogpu.runtime.opencl;

import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileArtifactDumper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenClRuntimeIrOptimizerEvidenceSummaryTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void readsRuntimeIrOptimizerEvidenceArtifacts() throws Exception {
        Path reportDirectory = temporaryDirectory.resolve("reports").resolve("opencl");
        Files.createDirectories(reportDirectory);
        Path workloadGate = reportDirectory.resolve("backend-source-promotion-workload-gate.properties");
        Files.writeString(workloadGate, """
                kernel.count=2
                kernel.0.sourceKernelResource=kernel-a.cl
                kernel.1.sourceKernelResource=kernel-b.cl
                """);
        Path artifactDirectory = reportDirectory
                .resolve("runtime-compile-artifacts")
                .resolve("kernel-a");
        Files.createDirectories(artifactDirectory);
        Files.writeString(artifactDirectory.resolve("backend-module.properties"), """
                resource=kernel-a.cl
                backendTarget=OPENCL
                """);
        Files.writeString(artifactDirectory.resolve(
                GpuRuntimeCompileArtifactDumper.RUNTIME_IR_OPTIMIZER_EVIDENCE_ARTIFACT), """
                status=recorded
                pass.count=3
                proposalOnly.count=2
                selectedOptimized.count=1
                rolledBack.count=1
                approvalTemplate.pending.count=1
                approvalTemplate.notApplicable.count=2
                pass.0.passVersion=ir-optimizer:no-op:1
                pass.1.passVersion=ir-optimizer:text-canonicalization:1
                pass.2.passVersion=ir-optimizer:text-canonicalization:1
                """);

        OpenClRuntimeIrOptimizerEvidenceSummary summary = OpenClRuntimeIrOptimizerEvidenceSummary.read(workloadGate);

        assertEquals("recorded", summary.status());
        assertEquals(2, summary.entries().size());
        assertEquals("recorded", summary.entries().get(0).status());
        assertEquals("kernel-a.cl", summary.entries().get(0).kernelResource());
        assertEquals(3, summary.entries().get(0).passCount());
        assertEquals(2, summary.entries().get(0).proposalOnlyCount());
        assertEquals(1, summary.entries().get(0).selectedOptimizedCount());
        assertEquals(1, summary.entries().get(0).rolledBackCount());
        assertEquals(1, summary.entries().get(0).approvalTemplatePendingCount());
        assertEquals(2, summary.entries().get(0).approvalTemplateNotApplicableCount());
        assertEquals("missing", summary.entries().get(1).status());
        assertEquals(3, summary.totalPassCount());
        assertEquals(2, summary.totalProposalOnlyCount());
        assertEquals(1, summary.totalSelectedOptimizedCount());
        assertEquals(1, summary.totalRolledBackCount());
        assertEquals(1, summary.totalApprovalTemplatePendingCount());
        assertEquals(2, summary.totalApprovalTemplateNotApplicableCount());
        assertEquals(
                "ir-optimizer:no-op:1=1, ir-optimizer:text-canonicalization:1=2",
                summary.providerSummary()
        );
        assertTrue(summary.toMarkdown().contains("## Runtime IR Optimizer Evidence"));
        assertTrue(summary.toMarkdown().contains("- Proposal-only count: `2`"));
        assertTrue(summary.toMarkdown().contains("- Approval templates pending: `1`"));
        assertTrue(summary.toMarkdown().contains("- Approval templates not applicable: `2`"));
        assertTrue(summary.toMarkdown().contains("- Providers: `ir-optimizer:no-op:1=1, ir-optimizer:text-canonicalization:1=2`"));
        assertTrue(summary.toMarkdown().contains("| `kernel-a.cl` | `recorded` | `3` | `2` | `1` | `1` | `1` | `2` | `ir-optimizer:no-op:1=1, ir-optimizer:text-canonicalization:1=2` |"));
        assertTrue(summary.toMarkdown().contains("| `kernel-b.cl` | `missing` | `0` | `0` | `0` | `0` | `0` | `0` | `none` |"));
    }
}
