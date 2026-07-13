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
                approvalTemplate.runtimeEquivalencePayloadRequired.count=1
                approvalTemplate.runtimeEquivalencePayloadPresent.count=0
                approvalTemplate.runtimeEquivalencePayloadPassed.count=0
                approvalTemplate.runtimeEquivalencePayloadComplete.count=0
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
                backendNeutralSourceMaterialization.pass.count=1
                backendNeutralSourceMaterialization.candidate.count=1
                backendNeutralSourceMaterialization.sourceReady.count=1
                backendNeutralSourceMaterialization.sourceLength.total=321
                backendNeutralSourceMaterialization.materializationOnly.count=1
                backendNeutralSourceMaterialization.status=review-ready
                backendNeutralSourceMaterialization.firstBlocker=none
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
                constantFoldingMaterialization.pass.count=1
                constantFoldingMaterialization.candidate.count=1
                constantFoldingMaterialization.transformedNode.count=1
                constantFoldingMaterialization.literalRewrite.count=1
                constantFoldingMaterialization.identityRewrite.count=0
                constantFoldingMaterialization.fixedPointPass.count=2
                constantFoldingMaterialization.changedMethodBody.count=1
                constantFoldingMaterialization.bodyTextReplacement.count=1
                constantFoldingMaterialization.skipped.divideByZero.count=1
                constantFoldingMaterialization.skipped.nonEvenDivision.count=2
                constantFoldingMaterialization.runtimeEquivalenceRequiredBeforeSelection=true
                constantFoldingMaterialization.runtimeEquivalencePayloadRequired=true
                constantFoldingMaterialization.runtimeEquivalencePayloadPresent.count=0
                constantFoldingMaterialization.runtimeEquivalencePassed.count=0
                constantFoldingMaterialization.approvalRequiredBeforeProduction=true
                constantFoldingMaterialization.status=pending-runtime-equivalence
                constantFoldingMaterialization.firstBlocker=runtime-equivalence-payload-not-recorded
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
                madFmaMaterialization.pass.count=1
                madFmaMaterialization.candidate.count=2
                madFmaMaterialization.transformedNode.count=2
                madFmaMaterialization.changedMethodBody.count=1
                madFmaMaterialization.bodyTextReplacement.count=2
                madFmaMaterialization.fixedPoint.pass.count=1
                madFmaMaterialization.skipped.fastMathPolicy.count=1
                madFmaMaterialization.skipped.bodyTextPatternMissing.count=0
                madFmaMaterialization.runtimeEquivalenceRequiredBeforeSelection=true
                madFmaMaterialization.runtimeEquivalencePayloadRequired=true
                madFmaMaterialization.runtimeEquivalencePayloadPresent.count=1
                madFmaMaterialization.runtimeEquivalencePassed.count=1
                madFmaMaterialization.approvalRequiredBeforeProduction=true
                madFmaMaterialization.fastMathAllowed=true
                madFmaMaterialization.status=review-ready
                madFmaMaterialization.firstBlocker=none
                clampMaterialization.pass.count=1
                clampMaterialization.candidate.count=1
                clampMaterialization.transformedNode.count=1
                clampMaterialization.changedMethodBody.count=1
                clampMaterialization.bodyTextReplacement.count=1
                clampMaterialization.fixedPoint.pass.count=1
                clampMaterialization.skipped.typedBodyMissing.count=0
                clampMaterialization.skipped.unsupportedFormat.count=0
                clampMaterialization.skipped.fastMathPolicy.count=0
                clampMaterialization.skipped.missingChildReference.count=0
                clampMaterialization.skipped.unsupportedShape.count=0
                clampMaterialization.skipped.bodyTextPatternMissing.count=0
                clampMaterialization.runtimeEquivalenceRequiredBeforeSelection=true
                clampMaterialization.runtimeEquivalencePayloadRequired=true
                clampMaterialization.runtimeEquivalencePayloadPresent.count=1
                clampMaterialization.runtimeEquivalencePassed.count=1
                clampMaterialization.approvalRequiredBeforeProduction=true
                clampMaterialization.strictFloatPreserved=true
                clampMaterialization.argumentOrderPreserved=true
                clampMaterialization.fastMathRequired=false
                clampMaterialization.status=review-ready
                clampMaterialization.firstBlocker=none
                stepMaterialization.pass.count=1
                stepMaterialization.candidate.count=2
                stepMaterialization.transformedNode.count=2
                stepMaterialization.changedMethodBody.count=1
                stepMaterialization.bodyTextReplacement.count=2
                stepMaterialization.fixedPoint.pass.count=2
                stepMaterialization.skipped.typedBodyMissing.count=0
                stepMaterialization.skipped.unsupportedFormat.count=0
                stepMaterialization.skipped.fastMathPolicy.count=0
                stepMaterialization.skipped.missingChildReference.count=0
                stepMaterialization.skipped.unsupportedShape.count=0
                stepMaterialization.skipped.bodyTextPatternMissing.count=0
                stepMaterialization.runtimeEquivalenceRequiredBeforeSelection=true
                stepMaterialization.runtimeEquivalencePayloadRequired=true
                stepMaterialization.runtimeEquivalencePayloadPresent.count=1
                stepMaterialization.runtimeEquivalencePassed.count=1
                stepMaterialization.approvalRequiredBeforeProduction=true
                stepMaterialization.directStep.count=1
                stepMaterialization.invertedStep.count=1
                stepMaterialization.strictFloatPreserved=true
                stepMaterialization.strictComparisonPreserved=true
                stepMaterialization.equalityBehaviorPreserved=true
                stepMaterialization.nanComparisonPreserved=true
                stepMaterialization.fastMathRequired=false
                stepMaterialization.status=review-ready
                stepMaterialization.firstBlocker=none
                mixMaterialization.pass.count=1
                mixMaterialization.candidate.count=3
                mixMaterialization.transformedNode.count=3
                mixMaterialization.changedMethodBody.count=1
                mixMaterialization.bodyTextReplacement.count=3
                mixMaterialization.fixedPoint.pass.count=3
                mixMaterialization.skipped.typedBodyMissing.count=0
                mixMaterialization.skipped.unsupportedFormat.count=0
                mixMaterialization.skipped.fastMathPolicy.count=0
                mixMaterialization.skipped.missingChildReference.count=0
                mixMaterialization.skipped.unsupportedShape.count=0
                mixMaterialization.skipped.bodyTextPatternMissing.count=0
                mixMaterialization.runtimeEquivalenceRequiredBeforeSelection=true
                mixMaterialization.runtimeEquivalencePayloadRequired=true
                mixMaterialization.runtimeEquivalencePayloadPresent.count=1
                mixMaterialization.runtimeEquivalencePassed.count=1
                mixMaterialization.approvalRequiredBeforeProduction=true
                mixMaterialization.canonicalMix.count=1
                mixMaterialization.expandedMix.count=1
                mixMaterialization.madExpandedMix.count=1
                mixMaterialization.fastMathAllowed=true
                mixMaterialization.fastMathRequired=true
                mixMaterialization.strictFloatPreserved=false
                mixMaterialization.algebraicReassociationRequired=true
                mixMaterialization.mixArgumentOrderPreserved=true
                mixMaterialization.status=review-ready
                mixMaterialization.firstBlocker=none
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
                reviewPackage.approvalManifest.status=pending-resource-path
                reviewPackage.approvalManifest.required=true
                reviewPackage.approvalManifest.present.count=0
                reviewPackage.approvalManifest.accepted.count=0
                reviewPackage.approvalManifest.resourcePath.summary=none
                reviewPackage.approvalManifest.firstBlocker=approval-manifest-resource-path-missing
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
        assertEquals(1, summary.entries().get(0).approvalTemplateRuntimeEquivalencePayloadRequiredCount());
        assertEquals(0, summary.entries().get(0).approvalTemplateRuntimeEquivalencePayloadPresentCount());
        assertEquals(0, summary.entries().get(0).approvalTemplateRuntimeEquivalencePayloadPassedCount());
        assertEquals(0, summary.entries().get(0).approvalTemplateRuntimeEquivalencePayloadCompleteCount());
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
        assertEquals(1, summary.entries().get(0).constantFoldingMaterializationPassCount());
        assertEquals(1, summary.entries().get(0).constantFoldingMaterializationCandidateCount());
        assertEquals(1, summary.entries().get(0).constantFoldingMaterializationTransformedNodeCount());
        assertEquals(1, summary.entries().get(0).constantFoldingMaterializationLiteralRewriteCount());
        assertEquals(0, summary.entries().get(0).constantFoldingMaterializationIdentityRewriteCount());
        assertEquals(2, summary.entries().get(0).constantFoldingMaterializationFixedPointPassCount());
        assertEquals(1, summary.entries().get(0).constantFoldingMaterializationChangedMethodBodyCount());
        assertEquals(1, summary.entries().get(0).constantFoldingMaterializationBodyTextReplacementCount());
        assertEquals(3, summary.entries().get(0).constantFoldingMaterializationSkippedCount());
        assertTrue(summary.entries().get(0).constantFoldingMaterializationRuntimeEquivalenceRequiredBeforeSelection());
        assertTrue(summary.entries().get(0).constantFoldingMaterializationRuntimeEquivalencePayloadRequired());
        assertEquals(0, summary.entries().get(0).constantFoldingMaterializationRuntimeEquivalencePayloadPresentCount());
        assertEquals(0, summary.entries().get(0).constantFoldingMaterializationRuntimeEquivalencePassedCount());
        assertTrue(summary.entries().get(0).constantFoldingMaterializationApprovalRequiredBeforeProduction());
        assertEquals("pending-runtime-equivalence", summary.entries().get(0).constantFoldingMaterializationStatus());
        assertEquals(
                "runtime-equivalence-payload-not-recorded",
                summary.entries().get(0).constantFoldingMaterializationFirstBlocker()
        );
        assertEquals(1, summary.entries().get(0).safeLocalCsePreviewPassCount());
        assertEquals(6, summary.entries().get(0).safeLocalCsePreviewExpressionCount());
        assertEquals(4, summary.entries().get(0).safeLocalCsePreviewCandidateExpressionCount());
        assertEquals(2, summary.entries().get(0).safeLocalCsePreviewDuplicateExpressionCount());
        assertEquals(6, summary.entries().get(0).safeLocalCsePreviewBlockedCount());
        assertTrue(summary.entries().get(0).safeLocalCsePreviewRuntimeEquivalenceRequiredBeforeRewrite());
        assertTrue(summary.entries().get(0).safeLocalCsePreviewApprovalRequiredBeforeRewrite());
        assertEquals(1, summary.entries().get(0).madFmaMaterializationPassCount());
        assertEquals(2, summary.entries().get(0).madFmaMaterializationCandidateCount());
        assertEquals(2, summary.entries().get(0).madFmaMaterializationTransformedNodeCount());
        assertEquals(1, summary.entries().get(0).madFmaMaterializationChangedMethodBodyCount());
        assertEquals(2, summary.entries().get(0).madFmaMaterializationBodyTextReplacementCount());
        assertEquals(1, summary.entries().get(0).madFmaMaterializationFixedPointPassCount());
        assertEquals(1, summary.entries().get(0).madFmaMaterializationSkippedCount());
        assertTrue(summary.entries().get(0).madFmaMaterializationRuntimeEquivalenceRequiredBeforeSelection());
        assertTrue(summary.entries().get(0).madFmaMaterializationRuntimeEquivalencePayloadRequired());
        assertEquals(1, summary.entries().get(0).madFmaMaterializationRuntimeEquivalencePayloadPresentCount());
        assertEquals(1, summary.entries().get(0).madFmaMaterializationRuntimeEquivalencePassedCount());
        assertTrue(summary.entries().get(0).madFmaMaterializationApprovalRequiredBeforeProduction());
        assertTrue(summary.entries().get(0).madFmaMaterializationFastMathAllowed());
        assertEquals("review-ready", summary.entries().get(0).madFmaMaterializationStatus());
        assertEquals("none", summary.entries().get(0).madFmaMaterializationFirstBlocker());
        assertEquals(1, summary.totalClampMaterializationPassCount());
        assertEquals(1, summary.totalClampMaterializationCandidateCount());
        assertEquals(1, summary.totalClampMaterializationTransformedNodeCount());
        assertEquals(1, summary.totalClampMaterializationBodyTextReplacementCount());
        assertEquals(1, summary.totalClampMaterializationRuntimeEquivalencePayloadPresentCount());
        assertEquals(1, summary.totalClampMaterializationRuntimeEquivalencePassedCount());
        assertEquals("review-ready", summary.clampMaterializationStatus());
        assertEquals("none", summary.clampMaterializationFirstBlocker());
        assertEquals(1, summary.totalStepMaterializationPassCount());
        assertEquals(2, summary.totalStepMaterializationCandidateCount());
        assertEquals(2, summary.totalStepMaterializationTransformedNodeCount());
        assertEquals(1, summary.totalStepMaterializationDirectStepCount());
        assertEquals(1, summary.totalStepMaterializationInvertedStepCount());
        assertEquals(1, summary.totalStepMaterializationRuntimeEquivalencePayloadPresentCount());
        assertEquals(1, summary.totalStepMaterializationRuntimeEquivalencePassedCount());
        assertEquals("review-ready", summary.stepMaterializationStatus());
        assertEquals("none", summary.stepMaterializationFirstBlocker());
        assertEquals(1, summary.totalMixMaterializationPassCount());
        assertEquals(3, summary.totalMixMaterializationCandidateCount());
        assertEquals(3, summary.totalMixMaterializationTransformedNodeCount());
        assertEquals(1, summary.totalMixMaterializationCanonicalMixCount());
        assertEquals(1, summary.totalMixMaterializationExpandedMixCount());
        assertEquals(1, summary.totalMixMaterializationMadExpandedMixCount());
        assertEquals(1, summary.totalMixMaterializationRuntimeEquivalencePayloadPresentCount());
        assertEquals(1, summary.totalMixMaterializationRuntimeEquivalencePassedCount());
        assertTrue(summary.mixMaterializationFastMathAllowed());
        assertTrue(summary.mixMaterializationFastMathRequired());
        assertTrue(summary.mixMaterializationAlgebraicReassociationRequired());
        assertEquals("review-ready", summary.mixMaterializationStatus());
        assertEquals("none", summary.mixMaterializationFirstBlocker());
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
        assertEquals(1, summary.totalApprovalTemplateRuntimeEquivalencePayloadRequiredCount());
        assertEquals(0, summary.totalApprovalTemplateRuntimeEquivalencePayloadPresentCount());
        assertEquals(0, summary.totalApprovalTemplateRuntimeEquivalencePayloadPassedCount());
        assertEquals(0, summary.totalApprovalTemplateRuntimeEquivalencePayloadCompleteCount());
        assertEquals("none", summary.optimizedArtifactCandidateFirstBlocker());
        assertEquals("mutation-disabled", summary.optimizedArtifactCandidateSelectionFirstBlocker());
        assertEquals(1, summary.totalBackendNeutralSourceMaterializationPassCount());
        assertEquals(1, summary.totalBackendNeutralSourceMaterializationCandidateCount());
        assertEquals(1, summary.totalBackendNeutralSourceMaterializationSourceReadyCount());
        assertEquals(321, summary.totalBackendNeutralSourceMaterializationSourceLength());
        assertEquals("review-ready", summary.backendNeutralSourceMaterializationStatus());
        assertEquals("none", summary.backendNeutralSourceMaterializationFirstBlocker());
        assertEquals(1, summary.totalConstantFoldingPreviewPassCount());
        assertEquals(3, summary.totalConstantFoldingPreviewCandidateCount());
        assertEquals(5, summary.totalConstantFoldingPreviewSkippedCount());
        assertEquals(1, summary.totalConstantFoldingMaterializationPassCount());
        assertEquals(1, summary.totalConstantFoldingMaterializationTransformedNodeCount());
        assertEquals(1, summary.totalConstantFoldingMaterializationLiteralRewriteCount());
        assertEquals(0, summary.totalConstantFoldingMaterializationIdentityRewriteCount());
        assertEquals(2, summary.totalConstantFoldingMaterializationFixedPointPassCount());
        assertEquals(3, summary.totalConstantFoldingMaterializationSkippedCount());
        assertEquals(0, summary.totalConstantFoldingMaterializationRuntimeEquivalencePayloadPresentCount());
        assertEquals(0, summary.totalConstantFoldingMaterializationRuntimeEquivalencePassedCount());
        assertEquals("pending-runtime-equivalence", summary.constantFoldingMaterializationStatus());
        assertEquals("runtime-equivalence-payload-not-recorded", summary.constantFoldingMaterializationFirstBlocker());
        assertEquals(1, summary.totalSafeLocalCsePreviewPassCount());
        assertEquals(4, summary.totalSafeLocalCsePreviewCandidateExpressionCount());
        assertEquals(2, summary.totalSafeLocalCsePreviewDuplicateExpressionCount());
        assertEquals(6, summary.totalSafeLocalCsePreviewBlockedCount());
        assertEquals(0, summary.totalSafeLocalCseMaterializationPassCount());
        assertEquals(0, summary.totalSafeLocalCseMaterializationTransformedNodeCount());
        assertEquals("not-recorded", summary.safeLocalCseMaterializationStatus());
        assertEquals("not-recorded", summary.safeLocalCseMaterializationFirstBlocker());
        assertEquals(1, summary.totalMadFmaMaterializationPassCount());
        assertEquals(2, summary.totalMadFmaMaterializationCandidateCount());
        assertEquals(2, summary.totalMadFmaMaterializationTransformedNodeCount());
        assertEquals(2, summary.totalMadFmaMaterializationBodyTextReplacementCount());
        assertEquals(1, summary.totalMadFmaMaterializationFixedPointPassCount());
        assertEquals(1, summary.totalMadFmaMaterializationSkippedCount());
        assertEquals(1, summary.totalMadFmaMaterializationRuntimeEquivalencePayloadPresentCount());
        assertEquals(1, summary.totalMadFmaMaterializationRuntimeEquivalencePassedCount());
        assertTrue(summary.madFmaMaterializationFastMathAllowed());
        assertEquals("review-ready", summary.madFmaMaterializationStatus());
        assertEquals("none", summary.madFmaMaterializationFirstBlocker());
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
        assertEquals("runtime-equivalence-payload-not-recorded", summary.runtimeEquivalenceReviewFirstBlocker());
        assertEquals("pending-manual-review", summary.reviewPackageStatus());
        assertEquals(1, summary.totalReviewPackageRequiredCount());
        assertEquals(0, summary.totalReviewPackageCompleteCount());
        assertEquals(1, summary.totalReviewPackageProposalPassCount());
        assertEquals(1, summary.totalReviewPackagePendingApprovalCount());
        assertEquals("pending-resource-path", summary.reviewPackageApprovalManifestStatus());
        assertEquals(1, summary.totalReviewPackageApprovalManifestRequiredCount());
        assertEquals(0, summary.totalReviewPackageApprovalManifestPresentCount());
        assertEquals(0, summary.totalReviewPackageApprovalManifestAcceptedCount());
        assertEquals("approval-manifest-resource-path-missing", summary.reviewPackageApprovalManifestFirstBlocker());
        assertEquals("preview-readiness-blocked-by-proof", summary.reviewPackageFirstBlocker());
        assertEquals(
                "ir-optimizer:no-op:1=1, ir-optimizer:text-canonicalization:1=2",
                summary.providerSummary()
        );
        assertTrue(summary.toMarkdown().contains("## Runtime IR Optimizer Evidence"));
        assertTrue(summary.toMarkdown().contains("- Proposal-only count: `2`"));
        assertTrue(summary.toMarkdown().contains("- Approval templates pending: `1`"));
        assertTrue(summary.toMarkdown().contains("- Approval templates not applicable: `2`"));
        assertTrue(summary.toMarkdown().contains(
                "- Approval templates runtime-equivalence payload required: `1`"
        ));
        assertTrue(summary.toMarkdown().contains(
                "- Approval templates runtime-equivalence payload complete: `0`"
        ));
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
        assertTrue(summary.toMarkdown().contains("- Backend-neutral source materialization status: `review-ready`"));
        assertTrue(summary.toMarkdown().contains("- Backend-neutral source materialization passes: `1`"));
        assertTrue(summary.toMarkdown().contains("- Backend-neutral source materialization candidates: `1`"));
        assertTrue(summary.toMarkdown().contains("- Backend-neutral source materialization source-ready count: `1`"));
        assertTrue(summary.toMarkdown().contains("- Backend-neutral source materialization source length total: `321`"));
        assertTrue(summary.toMarkdown().contains("- Backend-neutral source materialization first blocker: `none`"));
        assertTrue(summary.toMarkdown().contains("- Constant folding preview passes: `1`"));
        assertTrue(summary.toMarkdown().contains("- Constant folding preview candidates: `3`"));
        assertTrue(summary.toMarkdown().contains("- Constant folding preview skipped blockers: `5`"));
        assertTrue(summary.toMarkdown().contains("- Constant folding materialization status: `pending-runtime-equivalence`"));
        assertTrue(summary.toMarkdown().contains("- Constant folding materialization passes: `1`"));
        assertTrue(summary.toMarkdown().contains("- Constant folding materialized nodes: `1`"));
        assertTrue(summary.toMarkdown().contains("- Constant folding materialization literal rewrites: `1`"));
        assertTrue(summary.toMarkdown().contains("- Constant folding materialization identity rewrites: `0`"));
        assertTrue(summary.toMarkdown().contains("- Constant folding materialization fixed-point passes: `2`"));
        assertTrue(summary.toMarkdown().contains("- Constant folding materialization skipped blockers: `3`"));
        assertTrue(summary.toMarkdown().contains("- Constant folding materialization runtime-equivalence payloads: `0`"));
        assertTrue(summary.toMarkdown().contains("- Constant folding materialization runtime-equivalence passed: `0`"));
        assertTrue(summary.toMarkdown().contains("- Constant folding materialization first blocker: `runtime-equivalence-payload-not-recorded`"));
        assertTrue(summary.toMarkdown().contains("- Safe local CSE materialization status: `not-recorded`"));
        assertTrue(summary.toMarkdown().contains("- Safe local CSE materialized nodes: `0`"));
        assertTrue(summary.toMarkdown().contains("- Safe local CSE materialization first blocker: `not-recorded`"));
        assertTrue(summary.toMarkdown().contains("- Mad/FMA materialization status: `review-ready`"));
        assertTrue(summary.toMarkdown().contains("- Mad/FMA materialization passes: `1`"));
        assertTrue(summary.toMarkdown().contains("- Mad/FMA materialization candidates: `2`"));
        assertTrue(summary.toMarkdown().contains("- Mad/FMA materialized nodes: `2`"));
        assertTrue(summary.toMarkdown().contains("- Mad/FMA materialization text replacements: `2`"));
        assertTrue(summary.toMarkdown().contains("- Mad/FMA materialization fixed-point passes: `1`"));
        assertTrue(summary.toMarkdown().contains("- Mad/FMA materialization skipped blockers: `1`"));
        assertTrue(summary.toMarkdown().contains("- Mad/FMA materialization runtime-equivalence payloads: `1`"));
        assertTrue(summary.toMarkdown().contains("- Mad/FMA materialization runtime-equivalence passed: `1`"));
        assertTrue(summary.toMarkdown().contains("- Mad/FMA materialization fast-math allowed: `true`"));
        assertTrue(summary.toMarkdown().contains("- Mad/FMA materialization first blocker: `none`"));
        assertTrue(summary.toMarkdown().contains("- Clamp materialization status: `review-ready`"));
        assertTrue(summary.toMarkdown().contains("- Clamp materialized nodes: `1`"));
        assertTrue(summary.toMarkdown().contains("- Clamp materialization runtime-equivalence payloads: `1`"));
        assertTrue(summary.toMarkdown().contains("- Step materialization status: `review-ready`"));
        assertTrue(summary.toMarkdown().contains("- Step materialization direct step count: `1`"));
        assertTrue(summary.toMarkdown().contains("- Step materialization inverted step count: `1`"));
        assertTrue(summary.toMarkdown().contains("- Mix materialization status: `review-ready`"));
        assertTrue(summary.toMarkdown().contains("- Mix materialization canonical count: `1`"));
        assertTrue(summary.toMarkdown().contains("- Mix materialization expanded count: `1`"));
        assertTrue(summary.toMarkdown().contains("- Mix materialization MAD-expanded count: `1`"));
        assertTrue(summary.toMarkdown().contains("- Mix materialization fast-math allowed: `true`"));
        assertTrue(summary.toMarkdown().contains("- Mix materialization algebraic reassociation required: `true`"));
        assertTrue(summary.toMarkdown().contains("- Typed dead-code materialization status: `not-recorded`"));
        assertTrue(summary.toMarkdown().contains("- Typed dead-code materialization removed nodes: `0`"));
        assertTrue(summary.toMarkdown().contains("- Typed dead-code materialization first blocker: `not-recorded`"));
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
        assertTrue(summary.toMarkdown().contains("- Runtime-equivalence review first blocker: `runtime-equivalence-payload-not-recorded`"));
        assertTrue(summary.toMarkdown().contains("- Runtime-equivalence review production mutation: `disabled`"));
        assertTrue(summary.toMarkdown().contains("- Runtime-equivalence review selected IR replacement: `disabled`"));
        assertTrue(summary.toMarkdown().contains("- Review package status: `pending-manual-review`"));
        assertTrue(summary.toMarkdown().contains("- Review package required kernels: `1`"));
        assertTrue(summary.toMarkdown().contains("- Review package complete kernels: `0`"));
        assertTrue(summary.toMarkdown().contains("- Review package proposal passes: `1`"));
        assertTrue(summary.toMarkdown().contains("- Review package pending approvals: `1`"));
        assertTrue(summary.toMarkdown().contains("- Review package approval manifest status: `pending-resource-path`"));
        assertTrue(summary.toMarkdown().contains("- Review package approval manifests required: `1`"));
        assertTrue(summary.toMarkdown().contains(
                "- Review package approval manifest first blocker: `approval-manifest-resource-path-missing`"
        ));
        assertTrue(summary.toMarkdown().contains("- Review package first blocker: `preview-readiness-blocked-by-proof`"));
        assertTrue(summary.toMarkdown().contains("- Review package manual review only: `true`"));
        assertTrue(summary.toMarkdown().contains("- Review package production mutation: `disabled`"));
        assertTrue(summary.toMarkdown().contains("- Review package selected IR replacement: `disabled`"));
        assertTrue(summary.toMarkdown().contains("- Providers: `ir-optimizer:no-op:1=1, ir-optimizer:text-canonicalization:1=2`"));
        assertTrue(summary.toMarkdown().contains("| `kernel-a.cl` | `recorded` | `3` | `2` | `1` | `1` | `1` | `2` | `candidate-ready` | `none` | `mutation-disabled` | `3` | `5` | `1` | `runtime-equivalence-payload-not-recorded` | `4` | `2` | `6` | `0` | `not-recorded` | `3` | `4` | `0` | `not-recorded` | `pending-manual-review` | `preview-readiness-blocked-by-proof` | `ir-optimizer:no-op:1=1, ir-optimizer:text-canonicalization:1=2` |"));
        assertTrue(summary.toMarkdown().contains("| `kernel-b.cl` | `missing` | `0` | `0` | `0` | `0` | `0` | `0` | `not-recorded` | `no-candidates` | `no-candidates` | `0` | `0` | `0` | `not-recorded` | `0` | `0` | `0` | `0` | `not-recorded` | `0` | `0` | `0` | `not-recorded` | `not-recorded` | `review-package-not-recorded` | `none` |"));
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

    @Test
    void marksMaterializedConstantFoldingReadyWhenRuntimeEquivalencePayloadPassed() throws Exception {
        Path reportDirectory = temporaryDirectory.resolve("materialized-ready-reports").resolve("opencl");
        Files.createDirectories(reportDirectory);
        Path workloadGate = reportDirectory.resolve("backend-source-promotion-workload-gate.properties");
        Files.writeString(workloadGate, """
                kernel.count=1
                kernel.0.sourceKernelResource=kernel-folded.cl
                """);
        Path artifactDirectory = reportDirectory
                .resolve("runtime-compile-artifacts")
                .resolve("kernel-folded");
        Files.createDirectories(artifactDirectory);
        Files.writeString(artifactDirectory.resolve("backend-module.properties"), """
                resource=kernel-folded.cl
                backendTarget=OPENCL
                """);
        Files.writeString(artifactDirectory.resolve(
                GpuRuntimeCompileArtifactDumper.RUNTIME_IR_OPTIMIZER_EVIDENCE_ARTIFACT), """
                status=recorded
                pass.count=1
                proposalOnly.count=1
                approvalTemplate.runtimeEquivalencePayloadRequired.count=1
                approvalTemplate.runtimeEquivalencePayloadPresent.count=1
                approvalTemplate.runtimeEquivalencePayloadPassed.count=1
                approvalTemplate.runtimeEquivalencePayloadComplete.count=1
                constantFoldingMaterialization.pass.count=1
                constantFoldingMaterialization.candidate.count=1
                constantFoldingMaterialization.transformedNode.count=1
                constantFoldingMaterialization.literalRewrite.count=0
                constantFoldingMaterialization.identityRewrite.count=1
                constantFoldingMaterialization.fixedPointPass.count=1
                constantFoldingMaterialization.changedMethodBody.count=1
                constantFoldingMaterialization.bodyTextReplacement.count=1
                constantFoldingMaterialization.runtimeEquivalenceRequiredBeforeSelection=true
                constantFoldingMaterialization.runtimeEquivalencePayloadRequired=true
                constantFoldingMaterialization.runtimeEquivalencePayloadPresent.count=1
                constantFoldingMaterialization.runtimeEquivalencePassed.count=1
                constantFoldingMaterialization.approvalRequiredBeforeProduction=true
                constantFoldingMaterialization.status=review-ready
                constantFoldingMaterialization.firstBlocker=none
                reviewPackage.status=pending-manual-review
                reviewPackage.required=true
                reviewPackage.complete=false
                reviewPackage.firstBlocker=approval-template-pending
                reviewPackage.proposalPass.count=1
                reviewPackage.pendingApproval.count=1
                reviewPackage.runtimeEquivalence.status=review-ready
                reviewPackage.approvalManifest.status=pending-manifest-validation
                reviewPackage.approvalManifest.required=true
                reviewPackage.approvalManifest.present.count=0
                reviewPackage.approvalManifest.accepted.count=0
                reviewPackage.approvalManifest.resourcePath.summary=META-INF/javatogpu/ir-optimization-approvals/approval-test.properties=1
                reviewPackage.approvalManifest.firstBlocker=approval-manifest-not-loaded
                reviewPackage.manualReviewOnly=true
                pass.0.passVersion=ir-optimizer:constant-folding-materialization:1
                """);

        OpenClRuntimeIrOptimizerEvidenceSummary summary = OpenClRuntimeIrOptimizerEvidenceSummary.read(workloadGate);

        assertEquals("review-ready", summary.constantFoldingMaterializationStatus());
        assertEquals(0, summary.totalConstantFoldingMaterializationLiteralRewriteCount());
        assertEquals(1, summary.totalConstantFoldingMaterializationIdentityRewriteCount());
        assertEquals("none", summary.constantFoldingMaterializationFirstBlocker());
        assertEquals("review-ready", summary.runtimeEquivalenceReviewStatus());
        assertTrue(summary.runtimeEquivalenceReviewEligible());
        assertTrue(summary.runtimeEquivalenceReviewRequired());
        assertEquals("none", summary.runtimeEquivalenceReviewFirstBlocker());
        assertEquals("pending-manual-review", summary.reviewPackageStatus());
        assertEquals("approval-template-pending", summary.reviewPackageFirstBlocker());
        assertEquals("pending-manifest-validation", summary.reviewPackageApprovalManifestStatus());
        assertEquals(1, summary.totalReviewPackageApprovalManifestRequiredCount());
        assertEquals(0, summary.totalReviewPackageApprovalManifestAcceptedCount());
        assertEquals("approval-manifest-not-loaded", summary.reviewPackageApprovalManifestFirstBlocker());
        assertEquals(1, summary.totalApprovalTemplateRuntimeEquivalencePayloadRequiredCount());
        assertEquals(1, summary.totalApprovalTemplateRuntimeEquivalencePayloadPresentCount());
        assertEquals(1, summary.totalApprovalTemplateRuntimeEquivalencePayloadPassedCount());
        assertEquals(1, summary.totalApprovalTemplateRuntimeEquivalencePayloadCompleteCount());
        assertTrue(summary.toMarkdown().contains("- Constant folding materialization status: `review-ready`"));
        assertTrue(summary.toMarkdown().contains("- Constant folding materialization identity rewrites: `1`"));
        assertTrue(summary.toMarkdown().contains("- Constant folding materialization runtime-equivalence payloads: `1`"));
        assertTrue(summary.toMarkdown().contains(
                "- Approval templates runtime-equivalence payload complete: `1`"
        ));
        assertTrue(summary.toMarkdown().contains(
                "- Review package approval manifest status: `pending-manifest-validation`"
        ));
        assertTrue(summary.toMarkdown().contains("- Runtime-equivalence review status: `review-ready`"));
        assertTrue(summary.toMarkdown().contains("- Review package first blocker: `approval-template-pending`"));
    }

    @Test
    void marksMadFmaMaterializationPendingUntilRuntimeEquivalencePayloadPassed() throws Exception {
        Path reportDirectory = temporaryDirectory.resolve("mad-fma-materialized-pending-reports").resolve("opencl");
        Files.createDirectories(reportDirectory);
        Path workloadGate = reportDirectory.resolve("backend-source-promotion-workload-gate.properties");
        Files.writeString(workloadGate, """
                kernel.count=1
                kernel.0.sourceKernelResource=kernel-mad-fma.cl
                """);
        Path artifactDirectory = reportDirectory
                .resolve("runtime-compile-artifacts")
                .resolve("kernel-mad-fma");
        Files.createDirectories(artifactDirectory);
        Files.writeString(artifactDirectory.resolve("backend-module.properties"), """
                resource=kernel-mad-fma.cl
                backendTarget=OPENCL
                """);
        Files.writeString(artifactDirectory.resolve(
                GpuRuntimeCompileArtifactDumper.RUNTIME_IR_OPTIMIZER_EVIDENCE_ARTIFACT), """
                status=recorded
                pass.count=1
                proposalOnly.count=1
                approvalTemplate.runtimeEquivalencePayloadRequired.count=1
                approvalTemplate.runtimeEquivalencePayloadPresent.count=0
                approvalTemplate.runtimeEquivalencePayloadPassed.count=0
                approvalTemplate.runtimeEquivalencePayloadComplete.count=0
                madFmaMaterialization.pass.count=1
                madFmaMaterialization.candidate.count=1
                madFmaMaterialization.transformedNode.count=1
                madFmaMaterialization.changedMethodBody.count=1
                madFmaMaterialization.bodyTextReplacement.count=1
                madFmaMaterialization.fixedPoint.pass.count=1
                madFmaMaterialization.skipped.fastMathPolicy.count=0
                madFmaMaterialization.skipped.bodyTextPatternMissing.count=0
                madFmaMaterialization.runtimeEquivalenceRequiredBeforeSelection=true
                madFmaMaterialization.runtimeEquivalencePayloadRequired=true
                madFmaMaterialization.runtimeEquivalencePayloadPresent.count=0
                madFmaMaterialization.runtimeEquivalencePassed.count=0
                madFmaMaterialization.approvalRequiredBeforeProduction=true
                madFmaMaterialization.fastMathAllowed=true
                madFmaMaterialization.status=pending-runtime-equivalence
                madFmaMaterialization.firstBlocker=runtime-equivalence-payload-not-recorded
                reviewPackage.status=pending-manual-review
                reviewPackage.required=true
                reviewPackage.complete=false
                reviewPackage.firstBlocker=runtime-equivalence-payload-not-recorded
                reviewPackage.proposalPass.count=1
                reviewPackage.pendingApproval.count=1
                reviewPackage.runtimeEquivalence.status=blocked
                reviewPackage.manualReviewOnly=true
                pass.0.passVersion=ir-optimizer:mad-fma-materialization:1
                """);

        OpenClRuntimeIrOptimizerEvidenceSummary summary = OpenClRuntimeIrOptimizerEvidenceSummary.read(workloadGate);

        assertEquals("pending-runtime-equivalence", summary.madFmaMaterializationStatus());
        assertEquals(1, summary.totalMadFmaMaterializationTransformedNodeCount());
        assertEquals(0, summary.totalMadFmaMaterializationRuntimeEquivalencePayloadPresentCount());
        assertEquals("runtime-equivalence-payload-not-recorded", summary.madFmaMaterializationFirstBlocker());
        assertEquals("blocked", summary.runtimeEquivalenceReviewStatus());
        assertTrue(summary.runtimeEquivalenceReviewRequired());
        assertEquals("runtime-equivalence-payload-not-recorded", summary.runtimeEquivalenceReviewFirstBlocker());
        assertTrue(summary.toMarkdown().contains("- Mad/FMA materialization status: `pending-runtime-equivalence`"));
        assertTrue(summary.toMarkdown().contains("- Runtime-equivalence review first blocker: `runtime-equivalence-payload-not-recorded`"));
    }

    @Test
    void marksMaterializedTypedDeadCodeReadyWhenRuntimeEquivalencePayloadPassed() throws Exception {
        Path reportDirectory = temporaryDirectory.resolve("typed-materialized-ready-reports").resolve("opencl");
        Files.createDirectories(reportDirectory);
        Path workloadGate = reportDirectory.resolve("backend-source-promotion-workload-gate.properties");
        Files.writeString(workloadGate, """
                kernel.count=1
                kernel.0.sourceKernelResource=kernel-tdc.cl
                """);
        Path artifactDirectory = reportDirectory
                .resolve("runtime-compile-artifacts")
                .resolve("kernel-tdc");
        Files.createDirectories(artifactDirectory);
        Files.writeString(artifactDirectory.resolve("backend-module.properties"), """
                resource=kernel-tdc.cl
                backendTarget=OPENCL
                """);
        Files.writeString(artifactDirectory.resolve(
                GpuRuntimeCompileArtifactDumper.RUNTIME_IR_OPTIMIZER_EVIDENCE_ARTIFACT), """
                status=recorded
                pass.count=1
                proposalOnly.count=1
                approvalTemplate.runtimeEquivalencePayloadRequired.count=1
                approvalTemplate.runtimeEquivalencePayloadPresent.count=1
                approvalTemplate.runtimeEquivalencePayloadPassed.count=1
                approvalTemplate.runtimeEquivalencePayloadComplete.count=1
                typedDeadCodeMaterialization.pass.count=1
                typedDeadCodeMaterialization.node.count=5
                typedDeadCodeMaterialization.unreachableNode.count=3
                typedDeadCodeMaterialization.removedNode.count=3
                typedDeadCodeMaterialization.changedMethodBody.count=1
                typedDeadCodeMaterialization.blocked.missingRoot.count=0
                typedDeadCodeMaterialization.blocked.missingChildReference.count=0
                typedDeadCodeMaterialization.blocked.sideEffectingUnreachableNode.count=0
                typedDeadCodeMaterialization.runtimeEquivalenceRequiredBeforeSelection=true
                typedDeadCodeMaterialization.runtimeEquivalencePayloadRequired=true
                typedDeadCodeMaterialization.runtimeEquivalencePayloadPresent.count=1
                typedDeadCodeMaterialization.runtimeEquivalencePassed.count=1
                typedDeadCodeMaterialization.approvalRequiredBeforeProduction=true
                typedDeadCodeMaterialization.sideEffectFreedomProven=true
                typedDeadCodeMaterialization.status=review-ready
                typedDeadCodeMaterialization.firstBlocker=none
                runtimeEquivalenceReview.status=review-ready
                runtimeEquivalenceReview.eligible=true
                runtimeEquivalenceReview.required=true
                runtimeEquivalenceReview.firstBlocker=none
                runtimeEquivalenceReview.familySummary=constant-folding=not-recorded, safe-local-cse=not-recorded, typed-dead-code=not-recorded, constant-folding-materialization=not-recorded, typed-dead-code-materialization=review-ready
                reviewPackage.status=pending-manual-review
                reviewPackage.required=true
                reviewPackage.complete=false
                reviewPackage.firstBlocker=approval-template-pending
                reviewPackage.proposalPass.count=1
                reviewPackage.pendingApproval.count=1
                reviewPackage.runtimeEquivalence.status=review-ready
                reviewPackage.manualReviewOnly=true
                pass.0.passVersion=ir-optimizer:typed-dead-code-materialization:1
                """);

        OpenClRuntimeIrOptimizerEvidenceSummary summary = OpenClRuntimeIrOptimizerEvidenceSummary.read(workloadGate);

        assertEquals("review-ready", summary.typedDeadCodeMaterializationStatus());
        assertEquals(1, summary.totalTypedDeadCodeMaterializationPassCount());
        assertEquals(5, summary.totalTypedDeadCodeMaterializationNodeCount());
        assertEquals(3, summary.totalTypedDeadCodeMaterializationUnreachableNodeCount());
        assertEquals(3, summary.totalTypedDeadCodeMaterializationRemovedNodeCount());
        assertEquals(1, summary.totalTypedDeadCodeMaterializationRuntimeEquivalencePayloadPresentCount());
        assertEquals(1, summary.totalTypedDeadCodeMaterializationRuntimeEquivalencePassedCount());
        assertEquals("none", summary.typedDeadCodeMaterializationFirstBlocker());
        assertEquals("review-ready", summary.runtimeEquivalenceReviewStatus());
        assertTrue(summary.runtimeEquivalenceReviewEligible());
        assertTrue(summary.runtimeEquivalenceReviewRequired());
        assertEquals("none", summary.runtimeEquivalenceReviewFirstBlocker());
        assertTrue(summary.toMarkdown().contains("- Typed dead-code materialization status: `review-ready`"));
        assertTrue(summary.toMarkdown().contains("- Typed dead-code materialization removed nodes: `3`"));
        assertTrue(summary.toMarkdown().contains("- Typed dead-code materialization runtime-equivalence payloads: `1`"));
        assertTrue(summary.toMarkdown().contains("- Runtime-equivalence review status: `review-ready`"));
    }

    @Test
    void recordsAcceptedApprovalManifestAsReviewOnlyEvidence() throws Exception {
        Path reportDirectory = temporaryDirectory.resolve("approved-manifest-reports").resolve("opencl");
        Files.createDirectories(reportDirectory);
        Path workloadGate = reportDirectory.resolve("backend-source-promotion-workload-gate.properties");
        Files.writeString(workloadGate, """
                kernel.count=1
                kernel.0.sourceKernelResource=kernel-approved-fold.cl
                """);
        Path artifactDirectory = reportDirectory
                .resolve("runtime-compile-artifacts")
                .resolve("kernel-approved-fold");
        Files.createDirectories(artifactDirectory);
        Files.writeString(artifactDirectory.resolve("backend-module.properties"), """
                resource=kernel-approved-fold.cl
                backendTarget=OPENCL
                """);
        Files.writeString(artifactDirectory.resolve(
                GpuRuntimeCompileArtifactDumper.RUNTIME_IR_OPTIMIZER_EVIDENCE_ARTIFACT), """
                status=recorded
                pass.count=1
                proposalOnly.count=1
                selectedOptimized.count=0
                approvalTemplate.pending.count=1
                approvalTemplate.runtimeEquivalencePayloadRequired.count=1
                approvalTemplate.runtimeEquivalencePayloadPresent.count=1
                approvalTemplate.runtimeEquivalencePayloadPassed.count=1
                approvalTemplate.runtimeEquivalencePayloadComplete.count=1
                optimizedArtifactCandidate.selectionApplied=false
                optimizedArtifactCandidate.selectedIrReplacement=false
                constantFoldingMaterialization.pass.count=1
                constantFoldingMaterialization.candidate.count=1
                constantFoldingMaterialization.transformedNode.count=1
                constantFoldingMaterialization.literalRewrite.count=0
                constantFoldingMaterialization.identityRewrite.count=1
                constantFoldingMaterialization.fixedPointPass.count=1
                constantFoldingMaterialization.runtimeEquivalencePayloadRequired=true
                constantFoldingMaterialization.runtimeEquivalencePayloadPresent.count=1
                constantFoldingMaterialization.runtimeEquivalencePassed.count=1
                constantFoldingMaterialization.status=review-ready
                constantFoldingMaterialization.firstBlocker=none
                runtimeEquivalenceReview.status=review-ready
                runtimeEquivalenceReview.eligible=true
                runtimeEquivalenceReview.required=true
                runtimeEquivalenceReview.firstBlocker=none
                runtimeEquivalenceReview.productionMutation=disabled
                runtimeEquivalenceReview.selectedIrReplacement=disabled
                reviewPackage.status=pending-manual-review
                reviewPackage.required=true
                reviewPackage.complete=false
                reviewPackage.firstBlocker=manual-review-required
                reviewPackage.proposalPass.count=1
                reviewPackage.pendingApproval.count=1
                reviewPackage.runtimeEquivalence.status=review-ready
                reviewPackage.approvalManifest.status=accepted
                reviewPackage.approvalManifest.required=true
                reviewPackage.approvalManifest.present.count=1
                reviewPackage.approvalManifest.accepted.count=1
                reviewPackage.approvalManifest.resourcePath.summary=META-INF/javatogpu/ir-optimization-approvals/accepted-fold.properties=1
                reviewPackage.approvalManifest.firstBlocker=none
                reviewPackage.manualReviewOnly=true
                reviewPackage.productionMutation=disabled
                reviewPackage.selectedIrReplacement=disabled
                pass.0.passVersion=ir-optimizer:constant-folding-materialization:1
                """);

        OpenClRuntimeIrOptimizerEvidenceSummary summary = OpenClRuntimeIrOptimizerEvidenceSummary.read(workloadGate);

        assertEquals("accepted", summary.reviewPackageApprovalManifestStatus());
        assertEquals(1, summary.totalReviewPackageApprovalManifestRequiredCount());
        assertEquals(1, summary.totalReviewPackageApprovalManifestPresentCount());
        assertEquals(1, summary.totalReviewPackageApprovalManifestAcceptedCount());
        assertEquals("none", summary.reviewPackageApprovalManifestFirstBlocker());
        assertEquals("pending-manual-review", summary.reviewPackageStatus());
        assertEquals(0, summary.totalReviewPackageCompleteCount());
        assertEquals("manual-review-required", summary.reviewPackageFirstBlocker());
        assertEquals(0, summary.totalSelectedOptimizedCount());
        assertEquals(0, summary.totalOptimizedArtifactCandidateSelectionAppliedCount());
        assertEquals(0, summary.totalOptimizedArtifactCandidateSelectedIrReplacementCount());
        assertTrue(summary.toMarkdown().contains("- Review package approval manifest status: `accepted`"));
        assertTrue(summary.toMarkdown().contains("- Review package approval manifests present: `1`"));
        assertTrue(summary.toMarkdown().contains("- Review package approval manifests accepted: `1`"));
        assertTrue(summary.toMarkdown().contains("- Review package first blocker: `manual-review-required`"));
        assertTrue(summary.toMarkdown().contains("- Review package production mutation: `disabled`"));
        assertTrue(summary.toMarkdown().contains("- Review package selected IR replacement: `disabled`"));
    }
}
