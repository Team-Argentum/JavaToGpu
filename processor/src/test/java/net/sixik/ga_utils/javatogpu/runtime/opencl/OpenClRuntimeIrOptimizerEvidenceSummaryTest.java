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
                optimizedArtifactCandidate.status=candidate-ready
                optimizedArtifactCandidate.count=1
                optimizedArtifactCandidate.ready.count=1
                optimizedArtifactCandidate.blocked.count=0
                optimizedArtifactCandidate.selectionReady.count=0
                optimizedArtifactCandidate.selectionApplied.count=0
                optimizedArtifactCandidate.selectedIrReplacement.count=0
                optimizedArtifactCandidate.mutationAllowed.count=0
                optimizedArtifactCandidate.firstBlocker=none
                optimizedArtifactCandidate.selectionFirstBlocker=mutation-disabled
                optimizedArtifactCandidate.selectionApplied=false
                optimizedArtifactCandidate.selectedIrReplacement=false
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
                reviewPackage.status=pending-manual-review
                reviewPackage.required=true
                reviewPackage.complete=false
                reviewPackage.firstBlocker=preview-readiness-blocked-by-proof
                reviewPackage.proposalPass.count=1
                reviewPackage.pendingApproval.count=1
                reviewPackage.runtimeEquivalence.status=blocked
                reviewPackage.manualReviewOnly=true
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
        assertEquals("candidate-ready", summary.entries().get(0).optimizedArtifactCandidateStatus());
        assertEquals(1, summary.entries().get(0).optimizedArtifactCandidateCount());
        assertEquals(1, summary.entries().get(0).optimizedArtifactCandidateReadyCount());
        assertEquals(0, summary.entries().get(0).optimizedArtifactCandidateBlockedCount());
        assertEquals(0, summary.entries().get(0).optimizedArtifactCandidateSelectionReadyCount());
        assertEquals(0, summary.entries().get(0).optimizedArtifactCandidateSelectionAppliedCount());
        assertEquals(0, summary.entries().get(0).optimizedArtifactCandidateSelectedIrReplacementCount());
        assertEquals("none", summary.entries().get(0).optimizedArtifactCandidateFirstBlocker());
        assertEquals("mutation-disabled", summary.entries().get(0).optimizedArtifactCandidateSelectionFirstBlocker());
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
        assertEquals("candidate-ready", summary.optimizedArtifactCandidateStatus());
        assertEquals(1, summary.totalOptimizedArtifactCandidateCount());
        assertEquals(1, summary.totalOptimizedArtifactCandidateReadyCount());
        assertEquals(0, summary.totalOptimizedArtifactCandidateBlockedCount());
        assertEquals(0, summary.totalOptimizedArtifactCandidateSelectionReadyCount());
        assertEquals(0, summary.totalOptimizedArtifactCandidateSelectionAppliedCount());
        assertEquals(0, summary.totalOptimizedArtifactCandidateSelectedIrReplacementCount());
        assertEquals(0, summary.totalOptimizedArtifactCandidateMutationAllowedCount());
        assertEquals("none", summary.optimizedArtifactCandidateFirstBlocker());
        assertEquals("mutation-disabled", summary.optimizedArtifactCandidateSelectionFirstBlocker());
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
        assertEquals(3, summary.totalPreviewReadinessFamilyCount());
        assertEquals(3, summary.totalPreviewReadinessCandidateFamilyCount());
        assertEquals(3, summary.totalPreviewReadinessBlockedFamilyCount());
        assertEquals("blocked-by-proof", summary.previewReadinessStatus());
        assertEquals(
                "constant-folding=blocked-by-proof, safe-local-cse=blocked-by-proof, typed-dead-code=blocked-by-proof",
                summary.previewReadinessFamilySummary()
        );
        assertEquals("blocked", summary.runtimeEquivalenceReviewStatus());
        assertTrue(summary.runtimeEquivalenceReviewRequired());
        assertEquals("preview-readiness-blocked-by-proof", summary.runtimeEquivalenceReviewFirstBlocker());
        assertEquals("pending-manual-review", summary.reviewPackageStatus());
        assertEquals(1, summary.totalReviewPackageRequiredCount());
        assertEquals(0, summary.totalReviewPackageCompleteCount());
        assertEquals(1, summary.totalReviewPackageProposalPassCount());
        assertEquals(1, summary.totalReviewPackagePendingApprovalCount());
        assertEquals("preview-readiness-blocked-by-proof", summary.reviewPackageFirstBlocker());
        assertEquals(
                "ir-optimizer:no-op:1=1, ir-optimizer:text-canonicalization:1=2",
                summary.providerSummary()
        );
        assertTrue(summary.toMarkdown().contains("## Runtime IR Optimizer Evidence"));
        assertTrue(summary.toMarkdown().contains("- Proposal-only count: `2`"));
        assertTrue(summary.toMarkdown().contains("- Approval templates pending: `1`"));
        assertTrue(summary.toMarkdown().contains("- Approval templates not applicable: `2`"));
        assertTrue(summary.toMarkdown().contains("- Optimized artifact candidate status: `candidate-ready`"));
        assertTrue(summary.toMarkdown().contains("- Optimized artifact candidates: `1`"));
        assertTrue(summary.toMarkdown().contains("- Optimized artifact candidates ready: `1`"));
        assertTrue(summary.toMarkdown().contains("- Optimized artifact candidates blocked: `0`"));
        assertTrue(summary.toMarkdown().contains("- Optimized artifact candidate first blocker: `none`"));
        assertTrue(summary.toMarkdown().contains("- Optimized artifact candidate selection first blocker: `mutation-disabled`"));
        assertTrue(summary.toMarkdown().contains("- Optimized artifact candidate selection ready count: `0`"));
        assertTrue(summary.toMarkdown().contains("- Optimized artifact candidate selection applied count: `0`"));
        assertTrue(summary.toMarkdown().contains("- Optimized artifact candidate selected IR replacement count: `0`"));
        assertTrue(summary.toMarkdown().contains("- Optimized artifact candidate mutation-allowed count: `0`"));
        assertTrue(summary.toMarkdown().contains("- Optimized artifact candidate selection applied: `false`"));
        assertTrue(summary.toMarkdown().contains("- Optimized artifact candidate selected IR replacement: `false`"));
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
        assertTrue(summary.toMarkdown().contains("- Preview readiness status: `blocked-by-proof`"));
        assertTrue(summary.toMarkdown().contains("- Preview readiness families: `constant-folding=blocked-by-proof, safe-local-cse=blocked-by-proof, typed-dead-code=blocked-by-proof`"));
        assertTrue(summary.toMarkdown().contains("- Preview readiness recorded families: `3`"));
        assertTrue(summary.toMarkdown().contains("- Preview readiness candidate families: `3`"));
        assertTrue(summary.toMarkdown().contains("- Preview readiness blocked families: `3`"));
        assertTrue(summary.toMarkdown().contains("- Runtime-equivalence review status: `blocked`"));
        assertTrue(summary.toMarkdown().contains("- Runtime-equivalence review eligible: `false`"));
        assertTrue(summary.toMarkdown().contains("- Runtime-equivalence review required: `true`"));
        assertTrue(summary.toMarkdown().contains("- Runtime-equivalence review first blocker: `preview-readiness-blocked-by-proof`"));
        assertTrue(summary.toMarkdown().contains("- Runtime-equivalence review production mutation: `disabled`"));
        assertTrue(summary.toMarkdown().contains("- Runtime-equivalence review selected IR replacement: `disabled`"));
        assertTrue(summary.toMarkdown().contains("- Review package status: `pending-manual-review`"));
        assertTrue(summary.toMarkdown().contains("- Review package required kernels: `1`"));
        assertTrue(summary.toMarkdown().contains("- Review package complete kernels: `0`"));
        assertTrue(summary.toMarkdown().contains("- Review package proposal passes: `1`"));
        assertTrue(summary.toMarkdown().contains("- Review package pending approvals: `1`"));
        assertTrue(summary.toMarkdown().contains("- Review package first blocker: `preview-readiness-blocked-by-proof`"));
        assertTrue(summary.toMarkdown().contains("- Review package manual review only: `true`"));
        assertTrue(summary.toMarkdown().contains("- Review package production mutation: `disabled`"));
        assertTrue(summary.toMarkdown().contains("- Review package selected IR replacement: `disabled`"));
        assertTrue(summary.toMarkdown().contains("- Providers: `ir-optimizer:no-op:1=1, ir-optimizer:text-canonicalization:1=2`"));
        assertTrue(summary.toMarkdown().contains("| `kernel-a.cl` | `recorded` | `3` | `2` | `1` | `1` | `1` | `2` | `candidate-ready` | `none` | `mutation-disabled` | `3` | `5` | `4` | `2` | `6` | `3` | `4` | `pending-manual-review` | `preview-readiness-blocked-by-proof` | `ir-optimizer:no-op:1=1, ir-optimizer:text-canonicalization:1=2` |"));
        assertTrue(summary.toMarkdown().contains("| `kernel-b.cl` | `missing` | `0` | `0` | `0` | `0` | `0` | `0` | `not-recorded` | `no-candidates` | `no-candidates` | `0` | `0` | `0` | `0` | `0` | `0` | `0` | `not-recorded` | `review-package-not-recorded` | `none` |"));
    }

    @Test
    void marksPreviewReadinessReadyWhenCandidatesHaveNoBlockers() throws Exception {
        Path reportDirectory = temporaryDirectory.resolve("ready-reports").resolve("opencl");
        Files.createDirectories(reportDirectory);
        Path workloadGate = reportDirectory.resolve("backend-source-promotion-workload-gate.properties");
        Files.writeString(workloadGate, """
                kernel.count=1
                kernel.0.sourceKernelResource=kernel-ready.cl
                """);
        Path artifactDirectory = reportDirectory
                .resolve("runtime-compile-artifacts")
                .resolve("kernel-ready");
        Files.createDirectories(artifactDirectory);
        Files.writeString(artifactDirectory.resolve("backend-module.properties"), """
                resource=kernel-ready.cl
                backendTarget=OPENCL
                """);
        Files.writeString(artifactDirectory.resolve(
                GpuRuntimeCompileArtifactDumper.RUNTIME_IR_OPTIMIZER_EVIDENCE_ARTIFACT), """
                status=recorded
                pass.count=3
                proposalOnly.count=3
                constantFoldingPreview.pass.count=1
                constantFoldingPreview.candidate.count=1
                constantFoldingPreview.integerOverflowProven=true
                constantFoldingPreview.floatingPointRoundingProven=true
                safeLocalCsePreview.pass.count=1
                safeLocalCsePreview.duplicateExpression.count=1
                safeLocalCsePreview.dominanceProven=true
                safeLocalCsePreview.sideEffectFreedomProven=true
                typedDeadCodePreview.pass.count=1
                typedDeadCodePreview.unreachableNode.count=1
                typedDeadCodePreview.sideEffectFreedomProven=true
                reviewPackage.status=pending-manual-review
                reviewPackage.required=true
                reviewPackage.complete=false
                reviewPackage.firstBlocker=optimized-ir-proposal-missing
                reviewPackage.proposalPass.count=0
                reviewPackage.pendingApproval.count=0
                reviewPackage.runtimeEquivalence.status=review-ready
                reviewPackage.manualReviewOnly=true
                pass.0.passVersion=ir-optimizer:constant-folding-preview:1
                pass.1.passVersion=ir-optimizer:safe-local-cse-preview:1
                pass.2.passVersion=ir-optimizer:typed-dead-code-preview:1
                """);

        OpenClRuntimeIrOptimizerEvidenceSummary summary = OpenClRuntimeIrOptimizerEvidenceSummary.read(workloadGate);

        assertEquals("ready-for-runtime-equivalence-review", summary.previewReadinessStatus());
        assertEquals("review-ready", summary.runtimeEquivalenceReviewStatus());
        assertTrue(summary.runtimeEquivalenceReviewEligible());
        assertTrue(summary.runtimeEquivalenceReviewRequired());
        assertEquals("none", summary.runtimeEquivalenceReviewFirstBlocker());
        assertEquals("pending-manual-review", summary.reviewPackageStatus());
        assertEquals("optimized-ir-proposal-missing", summary.reviewPackageFirstBlocker());
        assertEquals(3, summary.totalPreviewReadinessFamilyCount());
        assertEquals(3, summary.totalPreviewReadinessCandidateFamilyCount());
        assertEquals(0, summary.totalPreviewReadinessBlockedFamilyCount());
        assertTrue(summary.toMarkdown().contains("- Preview readiness status: `ready-for-runtime-equivalence-review`"));
        assertTrue(summary.toMarkdown().contains("constant-folding=ready-for-runtime-equivalence-review"));
        assertTrue(summary.toMarkdown().contains("safe-local-cse=ready-for-runtime-equivalence-review"));
        assertTrue(summary.toMarkdown().contains("typed-dead-code=ready-for-runtime-equivalence-review"));
        assertTrue(summary.toMarkdown().contains("- Runtime-equivalence review status: `review-ready`"));
        assertTrue(summary.toMarkdown().contains("- Runtime-equivalence review eligible: `true`"));
        assertTrue(summary.toMarkdown().contains("- Runtime-equivalence review first blocker: `none`"));
    }
}
