package net.sixik.ga_utils.javatogpu.runtime.opencl;

import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileArtifactDumper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OpenClRuntimeIrOptimizerEvidenceValidatorCliTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void acceptsFailClosedReviewPackageEvidenceFile() throws Exception {
        Path artifact = temporaryDirectory.resolve(GpuRuntimeCompileArtifactDumper.RUNTIME_IR_OPTIMIZER_EVIDENCE_ARTIFACT);
        Files.writeString(artifact, failClosedEvidence("pending-manual-review", "true",
                "preview-readiness-blocked-by-proof", "1", "1"));

        assertDoesNotThrow(() -> OpenClRuntimeIrOptimizerEvidenceValidatorCli.main(
                new String[]{artifact.toString()}
        ));
    }

    @Test
    void acceptsArtifactDirectory() throws Exception {
        Path artifactDirectory = temporaryDirectory.resolve("runtime-compile-artifacts").resolve("kernel-a");
        Files.createDirectories(artifactDirectory);
        Files.writeString(
                artifactDirectory.resolve(GpuRuntimeCompileArtifactDumper.RUNTIME_IR_OPTIMIZER_EVIDENCE_ARTIFACT),
                failClosedEvidence("not-required", "false", "review-package-not-required", "0", "0")
        );

        assertDoesNotThrow(() -> OpenClRuntimeIrOptimizerEvidenceValidatorCli.main(
                new String[]{temporaryDirectory.toString()}
        ));
    }

    @Test
    void rejectsMissingArtifact() {
        assertThrows(
                IllegalStateException.class,
                () -> OpenClRuntimeIrOptimizerEvidenceValidatorCli.main(
                        new String[]{temporaryDirectory.resolve("missing").toString()}
                )
        );
    }

    @Test
    void rejectsProductionMutationGuardrailRegression() throws Exception {
        Path artifact = temporaryDirectory.resolve(GpuRuntimeCompileArtifactDumper.RUNTIME_IR_OPTIMIZER_EVIDENCE_ARTIFACT);
        Files.writeString(artifact, failClosedEvidence("pending-manual-review", "true",
                "manual-review-required", "1", "0")
                .replace("reviewPackage.productionMutation=disabled", "reviewPackage.productionMutation=enabled"));

        assertThrows(
                IllegalStateException.class,
                () -> OpenClRuntimeIrOptimizerEvidenceValidatorCli.main(new String[]{artifact.toString()})
        );
    }

    @Test
    void rejectsCompletedReviewPackage() throws Exception {
        Path artifact = temporaryDirectory.resolve(GpuRuntimeCompileArtifactDumper.RUNTIME_IR_OPTIMIZER_EVIDENCE_ARTIFACT);
        Files.writeString(artifact, failClosedEvidence("pending-manual-review", "true",
                "manual-review-required", "1", "0")
                .replace("reviewPackage.complete=false", "reviewPackage.complete=true"));

        assertThrows(
                IllegalStateException.class,
                () -> OpenClRuntimeIrOptimizerEvidenceValidatorCli.main(new String[]{artifact.toString()})
        );
    }

    @Test
    void rejectsOptimizedArtifactCandidateSelectionRegression() throws Exception {
        Path artifact = temporaryDirectory.resolve(GpuRuntimeCompileArtifactDumper.RUNTIME_IR_OPTIMIZER_EVIDENCE_ARTIFACT);
        Files.writeString(artifact, failClosedEvidence("pending-manual-review", "true",
                "manual-review-required", "1", "0")
                .replace("optimizedArtifactCandidate.selectionApplied=false",
                        "optimizedArtifactCandidate.selectionApplied=" + Boolean.TRUE));

        assertThrows(
                IllegalStateException.class,
                () -> OpenClRuntimeIrOptimizerEvidenceValidatorCli.main(new String[]{artifact.toString()})
        );
    }

    @Test
    void rejectsOptimizedArtifactCandidateSelectedIrReplacementRegression() throws Exception {
        Path artifact = temporaryDirectory.resolve(GpuRuntimeCompileArtifactDumper.RUNTIME_IR_OPTIMIZER_EVIDENCE_ARTIFACT);
        Files.writeString(artifact, failClosedEvidence("pending-manual-review", "true",
                "manual-review-required", "1", "0")
                .replace("optimizedArtifactCandidate.selectedIrReplacement.count=0",
                        "optimizedArtifactCandidate.selectedIrReplacement.count=1"));

        assertThrows(
                IllegalStateException.class,
                () -> OpenClRuntimeIrOptimizerEvidenceValidatorCli.main(new String[]{artifact.toString()})
        );
    }

    @Test
    void rejectsRequiredPackageWithoutBlocker() throws Exception {
        Path artifact = temporaryDirectory.resolve(GpuRuntimeCompileArtifactDumper.RUNTIME_IR_OPTIMIZER_EVIDENCE_ARTIFACT);
        Files.writeString(artifact, failClosedEvidence("pending-manual-review", "true", "none", "1", "0"));

        assertThrows(
                IllegalStateException.class,
                () -> OpenClRuntimeIrOptimizerEvidenceValidatorCli.main(new String[]{artifact.toString()})
        );
    }

    @Test
    void acceptsMadFmaReviewReadyEvidenceAsManualReviewOnly() throws Exception {
        Path artifact = temporaryDirectory.resolve(GpuRuntimeCompileArtifactDumper.RUNTIME_IR_OPTIMIZER_EVIDENCE_ARTIFACT);
        Files.writeString(artifact, failClosedEvidence(
                "pending-manual-review",
                "true",
                "approval-template-pending",
                "1",
                "1"
        ) + madFmaMaterializationEvidence(
                "review-ready",
                "none",
                "1",
                "1",
                "1",
                "true"
        ));

        assertDoesNotThrow(() -> OpenClRuntimeIrOptimizerEvidenceValidatorCli.main(new String[]{artifact.toString()}));
    }

    @Test
    void rejectsMadFmaReviewReadyWithoutRuntimeEquivalencePayload() throws Exception {
        Path artifact = temporaryDirectory.resolve(GpuRuntimeCompileArtifactDumper.RUNTIME_IR_OPTIMIZER_EVIDENCE_ARTIFACT);
        Files.writeString(artifact, failClosedEvidence(
                "pending-manual-review",
                "true",
                "approval-template-pending",
                "1",
                "1"
        ) + madFmaMaterializationEvidence(
                "review-ready",
                "none",
                "1",
                "0",
                "0",
                "true"
        ));

        assertThrows(
                IllegalStateException.class,
                () -> OpenClRuntimeIrOptimizerEvidenceValidatorCli.main(new String[]{artifact.toString()})
        );
    }

    @Test
    void rejectsMadFmaMaterializationWithoutFastMathPolicyEvidence() throws Exception {
        Path artifact = temporaryDirectory.resolve(GpuRuntimeCompileArtifactDumper.RUNTIME_IR_OPTIMIZER_EVIDENCE_ARTIFACT);
        Files.writeString(artifact, failClosedEvidence(
                "pending-manual-review",
                "true",
                "runtime-equivalence-payload-not-recorded",
                "1",
                "1"
        ) + madFmaMaterializationEvidence(
                "pending-runtime-equivalence",
                "runtime-equivalence-payload-not-recorded",
                "1",
                "0",
                "0",
                "false"
        ));

        assertThrows(
                IllegalStateException.class,
                () -> OpenClRuntimeIrOptimizerEvidenceValidatorCli.main(new String[]{artifact.toString()})
        );
    }

    @Test
    void acceptsIntrinsicMaterializationReviewReadyEvidenceAsManualReviewOnly() throws Exception {
        Path artifact = temporaryDirectory.resolve(GpuRuntimeCompileArtifactDumper.RUNTIME_IR_OPTIMIZER_EVIDENCE_ARTIFACT);
        Files.writeString(artifact, failClosedEvidence(
                "pending-manual-review",
                "true",
                "approval-template-pending",
                "3",
                "3"
        ) + clampMaterializationEvidence(
                "review-ready",
                "none",
                "1",
                "1",
                "1",
                "true",
                "true",
                "false"
        ) + stepMaterializationEvidence(
                "review-ready",
                "none",
                "2",
                "1",
                "1",
                "1",
                "1",
                "true",
                "true",
                "true",
                "true",
                "false"
        ) + mixMaterializationEvidence(
                "review-ready",
                "none",
                "3",
                "1",
                "1",
                "1",
                "1",
                "1",
                "true",
                "true",
                "false",
                "true",
                "true"
        ));

        assertDoesNotThrow(() -> OpenClRuntimeIrOptimizerEvidenceValidatorCli.main(new String[]{artifact.toString()}));
    }

    @Test
    void rejectsMixExpandedMaterializationWithoutFastMathPolicyEvidence() throws Exception {
        Path artifact = temporaryDirectory.resolve(GpuRuntimeCompileArtifactDumper.RUNTIME_IR_OPTIMIZER_EVIDENCE_ARTIFACT);
        Files.writeString(artifact, failClosedEvidence(
                "pending-manual-review",
                "true",
                "approval-template-pending",
                "1",
                "1"
        ) + mixMaterializationEvidence(
                "review-ready",
                "none",
                "3",
                "1",
                "1",
                "1",
                "1",
                "1",
                "false",
                "true",
                "false",
                "true",
                "true"
        ));

        assertThrows(
                IllegalStateException.class,
                () -> OpenClRuntimeIrOptimizerEvidenceValidatorCli.main(new String[]{artifact.toString()})
        );
    }

    @Test
    void acceptsLoopVectorizationReviewReadyEvidenceAsManualReviewOnly() throws Exception {
        Path artifact = temporaryDirectory.resolve(GpuRuntimeCompileArtifactDumper.RUNTIME_IR_OPTIMIZER_EVIDENCE_ARTIFACT);
        Files.writeString(artifact, failClosedEvidence(
                "pending-manual-review",
                "true",
                "approval-template-pending",
                "1",
                "1"
        ) + loopVectorizationMaterializationEvidence(
                "review-ready",
                "none",
                "1",
                "1",
                "1",
                "true",
                "true",
                "true"
        ));

        assertDoesNotThrow(() -> OpenClRuntimeIrOptimizerEvidenceValidatorCli.main(new String[]{artifact.toString()}));
    }

    @Test
    void rejectsLoopVectorizationReviewReadyWithoutProofs() throws Exception {
        Path artifact = temporaryDirectory.resolve(GpuRuntimeCompileArtifactDumper.RUNTIME_IR_OPTIMIZER_EVIDENCE_ARTIFACT);
        Files.writeString(artifact, failClosedEvidence(
                "pending-manual-review",
                "true",
                "approval-template-pending",
                "1",
                "1"
        ) + loopVectorizationMaterializationEvidence(
                "review-ready",
                "none",
                "1",
                "1",
                "1",
                "true",
                "false",
                "true"
        ));

        assertThrows(
                IllegalStateException.class,
                () -> OpenClRuntimeIrOptimizerEvidenceValidatorCli.main(new String[]{artifact.toString()})
        );
    }

    @Test
    void acceptsSafeLocalCseReviewReadyEvidenceAsManualReviewOnly() throws Exception {
        Path artifact = temporaryDirectory.resolve(GpuRuntimeCompileArtifactDumper.RUNTIME_IR_OPTIMIZER_EVIDENCE_ARTIFACT);
        Files.writeString(artifact, failClosedEvidence(
                "pending-manual-review",
                "true",
                "approval-template-pending",
                "1",
                "1"
        ) + safeLocalCseMaterializationEvidence(
                "review-ready",
                "none",
                "1",
                "1",
                "1",
                "true",
                "true"
        ));

        assertDoesNotThrow(() -> OpenClRuntimeIrOptimizerEvidenceValidatorCli.main(new String[]{artifact.toString()}));
    }

    @Test
    void rejectsSafeLocalCseReviewReadyWithoutProofs() throws Exception {
        Path artifact = temporaryDirectory.resolve(GpuRuntimeCompileArtifactDumper.RUNTIME_IR_OPTIMIZER_EVIDENCE_ARTIFACT);
        Files.writeString(artifact, failClosedEvidence(
                "pending-manual-review",
                "true",
                "approval-template-pending",
                "1",
                "1"
        ) + safeLocalCseMaterializationEvidence(
                "review-ready",
                "none",
                "1",
                "1",
                "1",
                "false",
                "true"
        ));

        assertThrows(
                IllegalStateException.class,
                () -> OpenClRuntimeIrOptimizerEvidenceValidatorCli.main(new String[]{artifact.toString()})
        );
    }

    @Test
    void acceptsTypedDeadCodeReviewReadyEvidenceAsManualReviewOnly() throws Exception {
        Path artifact = temporaryDirectory.resolve(GpuRuntimeCompileArtifactDumper.RUNTIME_IR_OPTIMIZER_EVIDENCE_ARTIFACT);
        Files.writeString(artifact, failClosedEvidence(
                "pending-manual-review",
                "true",
                "approval-template-pending",
                "1",
                "1"
        ) + typedDeadCodeMaterializationEvidence(
                "review-ready",
                "none",
                "3",
                "3",
                "1",
                "1",
                "true"
        ));

        assertDoesNotThrow(() -> OpenClRuntimeIrOptimizerEvidenceValidatorCli.main(new String[]{artifact.toString()}));
    }

    @Test
    void rejectsTypedDeadCodeReviewReadyWithoutSideEffectProof() throws Exception {
        Path artifact = temporaryDirectory.resolve(GpuRuntimeCompileArtifactDumper.RUNTIME_IR_OPTIMIZER_EVIDENCE_ARTIFACT);
        Files.writeString(artifact, failClosedEvidence(
                "pending-manual-review",
                "true",
                "approval-template-pending",
                "1",
                "1"
        ) + typedDeadCodeMaterializationEvidence(
                "review-ready",
                "none",
                "3",
                "3",
                "1",
                "1",
                "false"
        ));

        assertThrows(
                IllegalStateException.class,
                () -> OpenClRuntimeIrOptimizerEvidenceValidatorCli.main(new String[]{artifact.toString()})
        );
    }

    private static String failClosedEvidence(
            String status,
            String required,
            String firstBlocker,
            String proposalPassCount,
            String pendingApprovalCount
    ) {
        return String.join("\n",
                "status=recorded",
                "runtimeEquivalenceReview.productionMutation=disabled",
                "runtimeEquivalenceReview.selectedIrReplacement=disabled",
                "runtimeEquivalenceReview.manualReviewOnly=true",
                "optimizedArtifactCandidate.status=not-recorded",
                "optimizedArtifactCandidate.count=0",
                "optimizedArtifactCandidate.ready.count=0",
                "optimizedArtifactCandidate.blocked.count=0",
                "optimizedArtifactCandidate.selectionReady.count=0",
                "optimizedArtifactCandidate.selectionApplied.count=0",
                "optimizedArtifactCandidate.selectedIrReplacement.count=0",
                "optimizedArtifactCandidate.mutationAllowed.count=0",
                "optimizedArtifactCandidate.firstBlocker=no-candidates",
                "optimizedArtifactCandidate.selectionFirstBlocker=no-candidates",
                "optimizedArtifactCandidate.selectionApplied=false",
                "optimizedArtifactCandidate.selectedIrReplacement=false",
                "reviewPackage.status=" + status,
                "reviewPackage.required=" + required,
                "reviewPackage.complete=false",
                "reviewPackage.firstBlocker=" + firstBlocker,
                "reviewPackage.proposalPass.count=" + proposalPassCount,
                "reviewPackage.pendingApproval.count=" + pendingApprovalCount,
                "reviewPackage.runtimeEquivalence.status=blocked",
                "reviewPackage.originalIrRequired=true",
                "reviewPackage.optimizedIrRequired=true",
                "reviewPackage.proofSummaryRequired=true",
                "reviewPackage.manualReviewOnly=true",
                "reviewPackage.productionMutation=disabled",
                "reviewPackage.selectedIrReplacement=disabled",
                ""
        );
    }

    private static String madFmaMaterializationEvidence(
            String status,
            String firstBlocker,
            String transformedNodeCount,
            String payloadPresentCount,
            String payloadPassedCount,
            String fastMathAllowed
    ) {
        return String.join("\n",
                "madFmaMaterialization.pass.count=1",
                "madFmaMaterialization.candidate.count=1",
                "madFmaMaterialization.transformedNode.count=" + transformedNodeCount,
                "madFmaMaterialization.changedMethodBody.count=1",
                "madFmaMaterialization.bodyTextReplacement.count=1",
                "madFmaMaterialization.fixedPoint.pass.count=1",
                "madFmaMaterialization.skipped.fastMathPolicy.count=0",
                "madFmaMaterialization.skipped.bodyTextPatternMissing.count=0",
                "madFmaMaterialization.runtimeEquivalenceRequiredBeforeSelection=true",
                "madFmaMaterialization.runtimeEquivalencePayloadRequired=true",
                "madFmaMaterialization.runtimeEquivalencePayloadPresent.count=" + payloadPresentCount,
                "madFmaMaterialization.runtimeEquivalencePassed.count=" + payloadPassedCount,
                "madFmaMaterialization.approvalRequiredBeforeProduction=true",
                "madFmaMaterialization.fastMathAllowed=" + fastMathAllowed,
                "madFmaMaterialization.status=" + status,
                "madFmaMaterialization.firstBlocker=" + firstBlocker,
                ""
        );
    }

    private static String clampMaterializationEvidence(
            String status,
            String firstBlocker,
            String transformedNodeCount,
            String payloadPresentCount,
            String payloadPassedCount,
            String strictFloatPreserved,
            String argumentOrderPreserved,
            String fastMathRequired
    ) {
        return intrinsicMaterializationEvidence(
                "clampMaterialization",
                status,
                firstBlocker,
                "1",
                transformedNodeCount,
                payloadPresentCount,
                payloadPassedCount
        ) + String.join("\n",
                "clampMaterialization.strictFloatPreserved=" + strictFloatPreserved,
                "clampMaterialization.argumentOrderPreserved=" + argumentOrderPreserved,
                "clampMaterialization.fastMathRequired=" + fastMathRequired,
                ""
        );
    }

    private static String stepMaterializationEvidence(
            String status,
            String firstBlocker,
            String transformedNodeCount,
            String payloadPresentCount,
            String payloadPassedCount,
            String directStepCount,
            String invertedStepCount,
            String strictFloatPreserved,
            String strictComparisonPreserved,
            String equalityBehaviorPreserved,
            String nanComparisonPreserved,
            String fastMathRequired
    ) {
        return intrinsicMaterializationEvidence(
                "stepMaterialization",
                status,
                firstBlocker,
                "2",
                transformedNodeCount,
                payloadPresentCount,
                payloadPassedCount
        ) + String.join("\n",
                "stepMaterialization.directStep.count=" + directStepCount,
                "stepMaterialization.invertedStep.count=" + invertedStepCount,
                "stepMaterialization.strictFloatPreserved=" + strictFloatPreserved,
                "stepMaterialization.strictComparisonPreserved=" + strictComparisonPreserved,
                "stepMaterialization.equalityBehaviorPreserved=" + equalityBehaviorPreserved,
                "stepMaterialization.nanComparisonPreserved=" + nanComparisonPreserved,
                "stepMaterialization.fastMathRequired=" + fastMathRequired,
                ""
        );
    }

    private static String mixMaterializationEvidence(
            String status,
            String firstBlocker,
            String transformedNodeCount,
            String payloadPresentCount,
            String payloadPassedCount,
            String canonicalMixCount,
            String expandedMixCount,
            String madExpandedMixCount,
            String fastMathAllowed,
            String fastMathRequired,
            String strictFloatPreserved,
            String algebraicReassociationRequired,
            String mixArgumentOrderPreserved
    ) {
        return intrinsicMaterializationEvidence(
                "mixMaterialization",
                status,
                firstBlocker,
                "3",
                transformedNodeCount,
                payloadPresentCount,
                payloadPassedCount
        ) + String.join("\n",
                "mixMaterialization.canonicalMix.count=" + canonicalMixCount,
                "mixMaterialization.expandedMix.count=" + expandedMixCount,
                "mixMaterialization.madExpandedMix.count=" + madExpandedMixCount,
                "mixMaterialization.fastMathAllowed=" + fastMathAllowed,
                "mixMaterialization.fastMathRequired=" + fastMathRequired,
                "mixMaterialization.strictFloatPreserved=" + strictFloatPreserved,
                "mixMaterialization.algebraicReassociationRequired=" + algebraicReassociationRequired,
                "mixMaterialization.mixArgumentOrderPreserved=" + mixArgumentOrderPreserved,
                ""
        );
    }

    private static String intrinsicMaterializationEvidence(
            String prefix,
            String status,
            String firstBlocker,
            String candidateCount,
            String transformedNodeCount,
            String payloadPresentCount,
            String payloadPassedCount
    ) {
        return String.join("\n",
                prefix + ".pass.count=1",
                prefix + ".candidate.count=" + candidateCount,
                prefix + ".transformedNode.count=" + transformedNodeCount,
                prefix + ".changedMethodBody.count=1",
                prefix + ".bodyTextReplacement.count=" + transformedNodeCount,
                prefix + ".fixedPoint.pass.count=1",
                prefix + ".skipped.typedBodyMissing.count=0",
                prefix + ".skipped.unsupportedFormat.count=0",
                prefix + ".skipped.fastMathPolicy.count=0",
                prefix + ".skipped.missingChildReference.count=0",
                prefix + ".skipped.unsupportedShape.count=0",
                prefix + ".skipped.bodyTextPatternMissing.count=0",
                prefix + ".runtimeEquivalenceRequiredBeforeSelection=true",
                prefix + ".runtimeEquivalencePayloadRequired=true",
                prefix + ".runtimeEquivalencePayloadPresent.count=" + payloadPresentCount,
                prefix + ".runtimeEquivalencePassed.count=" + payloadPassedCount,
                prefix + ".approvalRequiredBeforeProduction=true",
                prefix + ".status=" + status,
                prefix + ".firstBlocker=" + firstBlocker,
                ""
        );
    }

    private static String safeLocalCseMaterializationEvidence(
            String status,
            String firstBlocker,
            String transformedNodeCount,
            String payloadPresentCount,
            String payloadPassedCount,
            String dominanceProven,
            String sideEffectFreedomProven
    ) {
        return String.join("\n",
                "safeLocalCseMaterialization.pass.count=1",
                "safeLocalCseMaterialization.localBinding.count=1",
                "safeLocalCseMaterialization.candidate.count=1",
                "safeLocalCseMaterialization.transformedNode.count=" + transformedNodeCount,
                "safeLocalCseMaterialization.changedMethodBody.count=1",
                "safeLocalCseMaterialization.bodyTextReplacement.count=1",
                "safeLocalCseMaterialization.fixedPoint.pass.count=1",
                "safeLocalCseMaterialization.skipped.controlFlowBoundary.count=0",
                "safeLocalCseMaterialization.skipped.unsupportedOperator.count=0",
                "safeLocalCseMaterialization.skipped.impureOperand.count=0",
                "safeLocalCseMaterialization.skipped.bodyTextPatternMissing.count=0",
                "safeLocalCseMaterialization.runtimeEquivalenceRequiredBeforeSelection=true",
                "safeLocalCseMaterialization.runtimeEquivalencePayloadRequired=true",
                "safeLocalCseMaterialization.runtimeEquivalencePayloadPresent.count=" + payloadPresentCount,
                "safeLocalCseMaterialization.runtimeEquivalencePassed.count=" + payloadPassedCount,
                "safeLocalCseMaterialization.approvalRequiredBeforeProduction=true",
                "safeLocalCseMaterialization.dominanceProven=" + dominanceProven,
                "safeLocalCseMaterialization.sideEffectFreedomProven=" + sideEffectFreedomProven,
                "safeLocalCseMaterialization.status=" + status,
                "safeLocalCseMaterialization.firstBlocker=" + firstBlocker,
                ""
        );
    }

    private static String loopVectorizationMaterializationEvidence(
            String status,
            String firstBlocker,
            String transformedLoopCount,
            String payloadPresentCount,
            String payloadPassedCount,
            String loopTripCountProven,
            String contiguousLoadProven,
            String orderedReductionPreserved
    ) {
        return String.join("\n",
                "loopVectorizationMaterialization.pass.count=1",
                "loopVectorizationMaterialization.candidate.count=1",
                "loopVectorizationMaterialization.transformedLoop.count=" + transformedLoopCount,
                "loopVectorizationMaterialization.changedMethodBody.count=1",
                "loopVectorizationMaterialization.bodyTextReplacement.count=1",
                "loopVectorizationMaterialization.typedBody.materialized.count=1",
                "loopVectorizationMaterialization.typedBody.invalidated.count=0",
                "loopVectorizationMaterialization.skipped.loopShape.count=0",
                "loopVectorizationMaterialization.skipped.unsupportedWidth.count=0",
                "loopVectorizationMaterialization.skipped.unsafeLoadPattern.count=0",
                "loopVectorizationMaterialization.runtimeEquivalenceRequiredBeforeSelection=true",
                "loopVectorizationMaterialization.runtimeEquivalencePayloadRequired=true",
                "loopVectorizationMaterialization.runtimeEquivalencePayloadPresent.count=" + payloadPresentCount,
                "loopVectorizationMaterialization.runtimeEquivalencePassed.count=" + payloadPassedCount,
                "loopVectorizationMaterialization.approvalRequiredBeforeProduction=true",
                "loopVectorizationMaterialization.loopTripCountProven=" + loopTripCountProven,
                "loopVectorizationMaterialization.contiguousLoadProven=" + contiguousLoadProven,
                "loopVectorizationMaterialization.orderedReductionPreserved=" + orderedReductionPreserved,
                "loopVectorizationMaterialization.status=" + status,
                "loopVectorizationMaterialization.firstBlocker=" + firstBlocker,
                ""
        );
    }

    private static String typedDeadCodeMaterializationEvidence(
            String status,
            String firstBlocker,
            String unreachableNodeCount,
            String removedNodeCount,
            String payloadPresentCount,
            String payloadPassedCount,
            String sideEffectFreedomProven
    ) {
        return String.join("\n",
                "typedDeadCodeMaterialization.pass.count=1",
                "typedDeadCodeMaterialization.node.count=5",
                "typedDeadCodeMaterialization.unreachableNode.count=" + unreachableNodeCount,
                "typedDeadCodeMaterialization.removedNode.count=" + removedNodeCount,
                "typedDeadCodeMaterialization.changedMethodBody.count=1",
                "typedDeadCodeMaterialization.blocked.missingRoot.count=0",
                "typedDeadCodeMaterialization.blocked.missingChildReference.count=0",
                "typedDeadCodeMaterialization.blocked.sideEffectingUnreachableNode.count=0",
                "typedDeadCodeMaterialization.runtimeEquivalenceRequiredBeforeSelection=true",
                "typedDeadCodeMaterialization.runtimeEquivalencePayloadRequired=true",
                "typedDeadCodeMaterialization.runtimeEquivalencePayloadPresent.count=" + payloadPresentCount,
                "typedDeadCodeMaterialization.runtimeEquivalencePassed.count=" + payloadPassedCount,
                "typedDeadCodeMaterialization.approvalRequiredBeforeProduction=true",
                "typedDeadCodeMaterialization.sideEffectFreedomProven=" + sideEffectFreedomProven,
                "typedDeadCodeMaterialization.status=" + status,
                "typedDeadCodeMaterialization.firstBlocker=" + firstBlocker,
                ""
        );
    }
}
