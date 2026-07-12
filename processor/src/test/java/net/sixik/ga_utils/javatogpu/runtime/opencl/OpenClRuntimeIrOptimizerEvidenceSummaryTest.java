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
                constantFoldingPreview.pass.count=1
                constantFoldingPreview.candidate.count=3
                constantFoldingPreview.skipped.nonPlainLiteral.count=1
                constantFoldingPreview.skipped.divideByZero.count=1
                constantFoldingPreview.skipped.nonEvenDivision.count=1
                constantFoldingPreview.skipped.unsupportedOperator.count=1
                constantFoldingPreview.skipped.nonLiteralOperand.count=1
                constantFoldingPreview.runtimeEquivalenceRequiredBeforeRewrite=true
                constantFoldingPreview.approvalRequiredBeforeRewrite=true
                constantFoldingPreview.integerOverflowProven=false
                constantFoldingPreview.floatingPointRoundingProven=false
                safeLocalCsePreview.pass.count=1
                safeLocalCsePreview.expression.count=6
                safeLocalCsePreview.candidateExpression.count=4
                safeLocalCsePreview.duplicateExpression.count=2
                safeLocalCsePreview.equivalenceClass.count=1
                safeLocalCsePreview.blocked.unsupportedOperator.count=1
                safeLocalCsePreview.blocked.impureOperand.count=2
                safeLocalCsePreview.blocked.controlFlowBoundary.count=3
                safeLocalCsePreview.runtimeEquivalenceRequiredBeforeRewrite=true
                safeLocalCsePreview.approvalRequiredBeforeRewrite=true
                safeLocalCsePreview.dominanceProven=false
                safeLocalCsePreview.sideEffectFreedomProven=false
                typedDeadCodePreview.pass.count=1
                typedDeadCodePreview.node.count=8
                typedDeadCodePreview.reachableNode.count=5
                typedDeadCodePreview.unreachableNode.count=3
                typedDeadCodePreview.blocked.missingRoot.count=1
                typedDeadCodePreview.blocked.missingChildReference.count=2
                typedDeadCodePreview.blocked.sideEffectingUnreachableNode.count=1
                typedDeadCodePreview.runtimeEquivalenceRequiredBeforeRewrite=true
                typedDeadCodePreview.approvalRequiredBeforeRewrite=true
                typedDeadCodePreview.sideEffectFreedomProven=false
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
        assertEquals(1, summary.entries().get(0).constantFoldingPreviewPassCount());
        assertEquals(3, summary.entries().get(0).constantFoldingPreviewCandidateCount());
        assertEquals(5, summary.entries().get(0).constantFoldingPreviewSkippedCount());
        assertTrue(summary.entries().get(0).constantFoldingPreviewRuntimeEquivalenceRequiredBeforeRewrite());
        assertTrue(summary.entries().get(0).constantFoldingPreviewApprovalRequiredBeforeRewrite());
        assertEquals(1, summary.entries().get(0).safeLocalCsePreviewPassCount());
        assertEquals(6, summary.entries().get(0).safeLocalCsePreviewExpressionCount());
        assertEquals(4, summary.entries().get(0).safeLocalCsePreviewCandidateExpressionCount());
        assertEquals(2, summary.entries().get(0).safeLocalCsePreviewDuplicateExpressionCount());
        assertEquals(6, summary.entries().get(0).safeLocalCsePreviewBlockedCount());
        assertTrue(summary.entries().get(0).safeLocalCsePreviewRuntimeEquivalenceRequiredBeforeRewrite());
        assertTrue(summary.entries().get(0).safeLocalCsePreviewApprovalRequiredBeforeRewrite());
        assertEquals(1, summary.entries().get(0).typedDeadCodePreviewPassCount());
        assertEquals(8, summary.entries().get(0).typedDeadCodePreviewNodeCount());
        assertEquals(5, summary.entries().get(0).typedDeadCodePreviewReachableNodeCount());
        assertEquals(3, summary.entries().get(0).typedDeadCodePreviewUnreachableNodeCount());
        assertEquals(4, summary.entries().get(0).typedDeadCodePreviewBlockedCount());
        assertTrue(summary.entries().get(0).typedDeadCodePreviewRuntimeEquivalenceRequiredBeforeRewrite());
        assertTrue(summary.entries().get(0).typedDeadCodePreviewApprovalRequiredBeforeRewrite());
        assertEquals("missing", summary.entries().get(1).status());
        assertEquals(3, summary.totalPassCount());
        assertEquals(2, summary.totalProposalOnlyCount());
        assertEquals(1, summary.totalSelectedOptimizedCount());
        assertEquals(1, summary.totalRolledBackCount());
        assertEquals(1, summary.totalApprovalTemplatePendingCount());
        assertEquals(2, summary.totalApprovalTemplateNotApplicableCount());
        assertEquals(1, summary.totalConstantFoldingPreviewPassCount());
        assertEquals(3, summary.totalConstantFoldingPreviewCandidateCount());
        assertEquals(5, summary.totalConstantFoldingPreviewSkippedCount());
        assertEquals(1, summary.totalSafeLocalCsePreviewPassCount());
        assertEquals(4, summary.totalSafeLocalCsePreviewCandidateExpressionCount());
        assertEquals(2, summary.totalSafeLocalCsePreviewDuplicateExpressionCount());
        assertEquals(6, summary.totalSafeLocalCsePreviewBlockedCount());
        assertEquals(1, summary.totalTypedDeadCodePreviewPassCount());
        assertEquals(3, summary.totalTypedDeadCodePreviewUnreachableNodeCount());
        assertEquals(4, summary.totalTypedDeadCodePreviewBlockedCount());
        assertEquals(
                "ir-optimizer:no-op:1=1, ir-optimizer:text-canonicalization:1=2",
                summary.providerSummary()
        );
        assertTrue(summary.toMarkdown().contains("## Runtime IR Optimizer Evidence"));
        assertTrue(summary.toMarkdown().contains("- Proposal-only count: `2`"));
        assertTrue(summary.toMarkdown().contains("- Approval templates pending: `1`"));
        assertTrue(summary.toMarkdown().contains("- Approval templates not applicable: `2`"));
        assertTrue(summary.toMarkdown().contains("- Constant folding preview passes: `1`"));
        assertTrue(summary.toMarkdown().contains("- Constant folding preview candidates: `3`"));
        assertTrue(summary.toMarkdown().contains("- Constant folding preview skipped blockers: `5`"));
        assertTrue(summary.toMarkdown().contains("- Safe local CSE preview passes: `1`"));
        assertTrue(summary.toMarkdown().contains("- Safe local CSE preview candidate expressions: `4`"));
        assertTrue(summary.toMarkdown().contains("- Safe local CSE preview duplicate expressions: `2`"));
        assertTrue(summary.toMarkdown().contains("- Safe local CSE preview blockers: `6`"));
        assertTrue(summary.toMarkdown().contains("- Typed dead-code preview passes: `1`"));
        assertTrue(summary.toMarkdown().contains("- Typed dead-code preview unreachable nodes: `3`"));
        assertTrue(summary.toMarkdown().contains("- Typed dead-code preview blockers: `4`"));
        assertTrue(summary.toMarkdown().contains("- Providers: `ir-optimizer:no-op:1=1, ir-optimizer:text-canonicalization:1=2`"));
        assertTrue(summary.toMarkdown().contains("| `kernel-a.cl` | `recorded` | `3` | `2` | `1` | `1` | `1` | `2` | `3` | `5` | `4` | `2` | `6` | `3` | `4` | `ir-optimizer:no-op:1=1, ir-optimizer:text-canonicalization:1=2` |"));
        assertTrue(summary.toMarkdown().contains("| `kernel-b.cl` | `missing` | `0` | `0` | `0` | `0` | `0` | `0` | `0` | `0` | `0` | `0` | `0` | `0` | `0` | `none` |"));
    }
}
