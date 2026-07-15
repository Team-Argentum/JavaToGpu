package net.sixik.ga_utils.javatogpu.runtime;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Compact CI-facing summary for detecting runtime optimizer behavior drift across validation runs.
 */
public record GpuRuntimeOptimizerDriftArtifact(
        int passCount,
        int appliedPassCount,
        int skippedPassCount,
        int rolledBackPassCount,
        int failedPassCount,
        String fallbackDecision,
        String selectedRuntimeIrStage,
        String selectedRuntimeIrIdentity,
        boolean optimizedIrRejected,
        String strategyName,
        String selectedProfile,
        String baselineStatus,
        boolean promotionEligible,
        int proofArtifactCount,
        int acceptedProofArtifactCount,
        int blockingProofArtifactCount,
        int replacementPlanCompleteCount,
        int replacementPlanPartialCount,
        String replacementPlanFirstBlocker,
        int replacementPlanValidationCount,
        int replacementPlanValidationValidCount,
        int replacementPlanValidationInvalidCount,
        String replacementPlanValidationFirstBlocker,
        int rewriteVisitorCount,
        int rewriteVisitorReadyCount,
        int rewriteVisitorBlockedCount,
        String rewriteVisitorFirstBlocker,
        int replacementBlueprintCount,
        int replacementBlueprintReadyCount,
        int replacementBlueprintBlockedCount,
        String replacementBlueprintFirstBlocker,
        int rewriteTransactionCount,
        int rewriteTransactionReadyCount,
        int rewriteTransactionBlockedCount,
        String rewriteTransactionFirstBlocker,
        int nodeIdAllocationCount,
        int nodeIdAllocationReadyCount,
        int nodeIdAllocationBlockedCount,
        String nodeIdAllocationFirstBlocker,
        int replacementNodeCount,
        int replacementNodeReadyCount,
        int replacementNodeBlockedCount,
        String replacementNodeFirstBlocker,
        int graphPatchCount,
        int graphPatchReadyCount,
        int graphPatchBlockedCount,
        String graphPatchFirstBlocker,
        int transformedGraphCount,
        int transformedGraphReadyCount,
        int transformedGraphBlockedCount,
        String transformedGraphFirstBlocker,
        int irArtifactEnvelopeCount,
        int irArtifactEnvelopeReadyCount,
        int irArtifactEnvelopeBlockedCount,
        String irArtifactEnvelopeFirstBlocker,
        int artifactProofBindingCount,
        int artifactProofBindingReadyCount,
        int artifactProofBindingBlockedCount,
        String artifactProofBindingFirstBlocker,
        int artifactSelectionCount,
        int artifactSelectionReadyCount,
        int artifactSelectionBlockedCount,
        String artifactSelectionFirstBlocker,
        int rewriteSketchCount,
        int rewriteSketchReadyCount,
        int rewriteSketchBlockedCount,
        String rewriteSketchFirstBlocker,
        int rewriteSketchConflictCount,
        String rewriteSketchConflictFirstBlocker,
        String rewriteSelectionStatus,
        String rewriteSelectionFirstBlocker,
        String rewriteProofStatus,
        String rewriteProofFirstBlocker,
        String rewriteReviewPackageStatus,
        String rewriteReviewPackageFirstBlocker,
        int optimizerRuleCount,
        String optimizerRuleSummary,
        String optimizerRuleDetailsProperties,
        int optimizerFamilyCount,
        int optimizerFamilyPromotionReadyCount,
        String optimizerFamilySummary,
        String productionGateStatus,
        boolean productionProfileRequested
) {

    public static GpuRuntimeOptimizerDriftArtifact from(GpuRuntimeCompileArtifactSnapshot snapshot) {
        if (snapshot == null) {
            return new GpuRuntimeOptimizerDriftArtifact(
                    0,
                    0,
                    0,
                    0,
                    0,
                    GpuRuntimeFallbackEvidence.NONE,
                    "missing",
                    "irgpu:missing",
                    false,
                    "strategy:none",
                    "off",
                    "missing",
                    false,
                    0,
                    0,
                    0,
                    0,
                    0,
                    "none",
                    0,
                    0,
                    0,
                    "none",
                    0,
                    0,
                    0,
                    "none",
                    0,
                    0,
                    0,
                    "none",
                    0,
                    0,
                    0,
                    "none",
                    0,
                    0,
                    0,
                    "none",
                    0,
                    0,
                    0,
                    "none",
                    0,
                    0,
                    0,
                    "none",
                    0,
                    0,
                    0,
                    "none",
                    0,
                    0,
                    0,
                    "none",
                    0,
                    0,
                    0,
                    "none",
                    0,
                    0,
                    0,
                    "none",
                    0,
                    0,
                    0,
                    "none",
                    0,
                    "none",
                    "not-required",
                    "no-rewrite-sketches",
                    "not-required",
                    "no-proof-candidates",
                    "not-required",
                    "no-review-candidates",
                    0,
                    "none",
                    "",
                    0,
                    0,
                    "",
                    "not-requested",
                    false
            );
        }

        GpuRuntimeIrOptimizationReport report = snapshot.optimizationReport();
        GpuOptimizationStrategyDecision strategy = report.strategyDecision();
        GpuOptimizationVendorBaseline baseline = strategy.vendorBaseline();
        GpuRuntimeProductionOptimizerGate gate = snapshot.productionOptimizerGate();
        GpuRuntimeIrSelection selection = snapshot.runtimeIrSelection();
        Map<String, OptimizerFamilyEvidence> optimizerFamilies = optimizerFamilies(report);
        Map<String, OptimizerRuleEvidence> optimizerRules = optimizerRules(report);
        int rewriteSketchCount = rewriteSketchCount(report);
        int rewriteSketchReadyCount = rewriteSketchReadyCount(report);
        int rewriteSketchBlockedCount = rewriteSketchBlockedCount(report);
        String rewriteSketchFirstBlocker = rewriteSketchFirstBlocker(report);
        int rewriteVisitorCount = rewriteVisitorCount(report);
        int rewriteVisitorReadyCount = rewriteVisitorReadyCount(report);
        int rewriteVisitorBlockedCount = rewriteVisitorBlockedCount(report);
        String rewriteVisitorFirstBlocker = rewriteVisitorFirstBlocker(report);
        int replacementBlueprintCount = replacementBlueprintCount(report);
        int replacementBlueprintReadyCount = replacementBlueprintReadyCount(report);
        int replacementBlueprintBlockedCount = replacementBlueprintBlockedCount(report);
        String replacementBlueprintFirstBlocker = replacementBlueprintFirstBlocker(report);
        int rewriteTransactionCount = rewriteTransactionCount(report);
        int rewriteTransactionReadyCount = rewriteTransactionReadyCount(report);
        int rewriteTransactionBlockedCount = rewriteTransactionBlockedCount(report);
        String rewriteTransactionFirstBlocker = rewriteTransactionFirstBlocker(report);
        int nodeIdAllocationCount = nodeIdAllocationCount(report);
        int nodeIdAllocationReadyCount = nodeIdAllocationReadyCount(report);
        int nodeIdAllocationBlockedCount = nodeIdAllocationBlockedCount(report);
        String nodeIdAllocationFirstBlocker = nodeIdAllocationFirstBlocker(report);
        int replacementNodeCount = replacementNodeCount(report);
        int replacementNodeReadyCount = replacementNodeReadyCount(report);
        int replacementNodeBlockedCount = replacementNodeBlockedCount(report);
        String replacementNodeFirstBlocker = replacementNodeFirstBlocker(report);
        int graphPatchCount = graphPatchCount(report);
        int graphPatchReadyCount = graphPatchReadyCount(report);
        int graphPatchBlockedCount = graphPatchBlockedCount(report);
        String graphPatchFirstBlocker = graphPatchFirstBlocker(report);
        int transformedGraphCount = transformedGraphCount(report);
        int transformedGraphReadyCount = transformedGraphReadyCount(report);
        int transformedGraphBlockedCount = transformedGraphBlockedCount(report);
        String transformedGraphFirstBlocker = transformedGraphFirstBlocker(report);
        int irArtifactEnvelopeCount = irArtifactEnvelopeCount(report);
        int irArtifactEnvelopeReadyCount = irArtifactEnvelopeReadyCount(report);
        int irArtifactEnvelopeBlockedCount = irArtifactEnvelopeBlockedCount(report);
        String irArtifactEnvelopeFirstBlocker = irArtifactEnvelopeFirstBlocker(report);
        ProofFieldSummary artifactProofBinding = proofFieldSummary(report, "artifactProofBinding");
        ProofFieldSummary artifactSelection = proofFieldSummary(report, "artifactSelection");
        int rewriteSketchConflictCount = rewriteSketchConflictCount(report);
        String rewriteSketchConflictFirstBlocker = rewriteSketchConflictFirstBlocker(report);
        String rewriteProofStatus = rewriteProofStatus(report, rewriteSketchReadyCount);
        String rewriteProofFirstBlocker = rewriteProofFirstBlocker(report);
        return new GpuRuntimeOptimizerDriftArtifact(
                optimizationPassCount(report),
                count(report, GpuRuntimeIrOptimizationOutcome.APPLIED),
                count(report, GpuRuntimeIrOptimizationOutcome.SKIPPED),
                count(report, GpuRuntimeIrOptimizationOutcome.ROLLED_BACK),
                count(report, GpuRuntimeIrOptimizationOutcome.FAILED),
                selection.fallbackDecision(),
                selection.selectedStage(),
                selection.selectedIdentity(),
                selection.optimizedRejected(),
                strategy.strategyName(),
                strategy.selectedProfile(),
                baseline.status(),
                baseline.promotionEligible(),
                proofArtifactCount(report),
                acceptedProofArtifactCount(report),
                blockingProofArtifactCount(report),
                replacementPlanCompleteCount(report),
                replacementPlanPartialCount(report),
                replacementPlanFirstBlocker(report),
                replacementPlanValidationCount(report),
                replacementPlanValidationValidCount(report),
                replacementPlanValidationInvalidCount(report),
                replacementPlanValidationFirstBlocker(report),
                rewriteVisitorCount,
                rewriteVisitorReadyCount,
                rewriteVisitorBlockedCount,
                rewriteVisitorFirstBlocker,
                replacementBlueprintCount,
                replacementBlueprintReadyCount,
                replacementBlueprintBlockedCount,
                replacementBlueprintFirstBlocker,
                rewriteTransactionCount,
                rewriteTransactionReadyCount,
                rewriteTransactionBlockedCount,
                rewriteTransactionFirstBlocker,
                nodeIdAllocationCount,
                nodeIdAllocationReadyCount,
                nodeIdAllocationBlockedCount,
                nodeIdAllocationFirstBlocker,
                replacementNodeCount,
                replacementNodeReadyCount,
                replacementNodeBlockedCount,
                replacementNodeFirstBlocker,
                graphPatchCount,
                graphPatchReadyCount,
                graphPatchBlockedCount,
                graphPatchFirstBlocker,
                transformedGraphCount,
                transformedGraphReadyCount,
                transformedGraphBlockedCount,
                transformedGraphFirstBlocker,
                irArtifactEnvelopeCount,
                irArtifactEnvelopeReadyCount,
                irArtifactEnvelopeBlockedCount,
                irArtifactEnvelopeFirstBlocker,
                artifactProofBinding.count(),
                artifactProofBinding.readyCount(),
                artifactProofBinding.blockedCount(),
                artifactProofBinding.firstBlocker(),
                artifactSelection.count(),
                artifactSelection.readyCount(),
                artifactSelection.blockedCount(),
                artifactSelection.firstBlocker(),
                rewriteSketchCount,
                rewriteSketchReadyCount,
                rewriteSketchBlockedCount,
                rewriteSketchFirstBlocker,
                rewriteSketchConflictCount,
                rewriteSketchConflictFirstBlocker,
                rewriteSelectionStatus(report, rewriteSketchCount),
                rewriteSelectionFirstBlocker(
                        report,
                        rewriteSketchCount,
                        rewriteSketchReadyCount,
                        rewriteSketchBlockedCount,
                        rewriteSketchFirstBlocker,
                        rewriteSketchConflictCount
                ),
                rewriteProofStatus,
                rewriteProofFirstBlocker,
                rewriteReviewPackageStatus(report, rewriteSketchReadyCount),
                rewriteReviewPackageFirstBlocker(
                        report,
                        rewriteSketchReadyCount,
                        rewriteSketchConflictCount,
                        rewriteProofFirstBlocker
                ),
                optimizerRules.size(),
                formatOptimizerRuleSummary(optimizerRules),
                formatOptimizerRuleProperties(optimizerRules),
                optimizerFamilies.size(),
                promotionReadyFamilyCount(optimizerFamilies),
                formatOptimizerFamilySummary(optimizerFamilies),
                gate.status(),
                gate.productionProfileRequested()
        );
    }

    private static int optimizationPassCount(GpuRuntimeIrOptimizationReport report) {
        return (int) report.passReports().stream()
                .filter(passReport -> !passReport.analysisOnly())
                .count();
    }

    public String toPropertiesText() {
        StringBuilder builder = new StringBuilder();
        builder.append("pass.count=").append(passCount).append('\n');
        builder.append("pass.applied.count=").append(appliedPassCount).append('\n');
        builder.append("pass.skipped.count=").append(skippedPassCount).append('\n');
        builder.append("pass.rolledBack.count=").append(rolledBackPassCount).append('\n');
        builder.append("pass.failed.count=").append(failedPassCount).append('\n');
        builder.append("fallbackDecision=").append(fallbackDecision).append('\n');
        builder.append("selectedRuntimeIrStage=").append(selectedRuntimeIrStage).append('\n');
        builder.append("selectedRuntimeIrIdentity=").append(selectedRuntimeIrIdentity).append('\n');
        builder.append("optimizedIrRejected=").append(optimizedIrRejected).append('\n');
        builder.append("strategyName=").append(strategyName).append('\n');
        builder.append("selectedProfile=").append(selectedProfile).append('\n');
        builder.append("baselineStatus=").append(baselineStatus).append('\n');
        builder.append("promotionEligible=").append(promotionEligible).append('\n');
        builder.append("proofArtifact.count=").append(proofArtifactCount).append('\n');
        builder.append("proofArtifact.accepted.count=").append(acceptedProofArtifactCount).append('\n');
        builder.append("proofArtifact.blocking.count=").append(blockingProofArtifactCount).append('\n');
        builder.append("replacementPlan.complete.count=").append(replacementPlanCompleteCount).append('\n');
        builder.append("replacementPlan.partial.count=").append(replacementPlanPartialCount).append('\n');
        builder.append("replacementPlan.firstBlocker=").append(replacementPlanFirstBlocker).append('\n');
        builder.append("replacementPlan.validation.count=").append(replacementPlanValidationCount).append('\n');
        builder.append("replacementPlan.validation.valid.count=").append(replacementPlanValidationValidCount).append('\n');
        builder.append("replacementPlan.validation.invalid.count=").append(replacementPlanValidationInvalidCount).append('\n');
        builder.append("replacementPlan.validation.firstBlocker=").append(replacementPlanValidationFirstBlocker).append('\n');
        builder.append("rewriteVisitor.count=").append(rewriteVisitorCount).append('\n');
        builder.append("rewriteVisitor.ready.count=").append(rewriteVisitorReadyCount).append('\n');
        builder.append("rewriteVisitor.blocked.count=").append(rewriteVisitorBlockedCount).append('\n');
        builder.append("rewriteVisitor.firstBlocker=").append(rewriteVisitorFirstBlocker).append('\n');
        builder.append("rewriteVisitor.visitorImplemented=true\n");
        builder.append("rewriteVisitor.replacementBuilderImplemented=false\n");
        builder.append("rewriteVisitor.transformedIrBuilt=false\n");
        builder.append("rewriteVisitor.mutationAllowed=false\n");
        builder.append("rewriteVisitor.selectedIrReplacement=false\n");
        builder.append("replacementBlueprint.count=").append(replacementBlueprintCount).append('\n');
        builder.append("replacementBlueprint.ready.count=").append(replacementBlueprintReadyCount).append('\n');
        builder.append("replacementBlueprint.blocked.count=").append(replacementBlueprintBlockedCount).append('\n');
        builder.append("replacementBlueprint.firstBlocker=").append(replacementBlueprintFirstBlocker).append('\n');
        builder.append("replacementBlueprint.blueprintImplemented=true\n");
        builder.append("replacementBlueprint.replacementBuilderImplemented=false\n");
        builder.append("replacementBlueprint.transformedIrBuilt=false\n");
        builder.append("replacementBlueprint.mutationAllowed=false\n");
        builder.append("replacementBlueprint.selectedIrReplacement=false\n");
        builder.append("rewriteTransaction.count=").append(rewriteTransactionCount).append('\n');
        builder.append("rewriteTransaction.ready.count=").append(rewriteTransactionReadyCount).append('\n');
        builder.append("rewriteTransaction.blocked.count=").append(rewriteTransactionBlockedCount).append('\n');
        builder.append("rewriteTransaction.firstBlocker=").append(rewriteTransactionFirstBlocker).append('\n');
        builder.append("rewriteTransaction.transactionPreflightImplemented=true\n");
        builder.append("rewriteTransaction.nodeIdAllocatorImplemented=false\n");
        builder.append("rewriteTransaction.graphRewriteImplemented=false\n");
        builder.append("rewriteTransaction.transformedIrBuilt=false\n");
        builder.append("rewriteTransaction.mutationAllowed=false\n");
        builder.append("rewriteTransaction.selectedIrReplacement=false\n");
        builder.append("nodeIdAllocation.count=").append(nodeIdAllocationCount).append('\n');
        builder.append("nodeIdAllocation.ready.count=").append(nodeIdAllocationReadyCount).append('\n');
        builder.append("nodeIdAllocation.blocked.count=").append(nodeIdAllocationBlockedCount).append('\n');
        builder.append("nodeIdAllocation.firstBlocker=").append(nodeIdAllocationFirstBlocker).append('\n');
        builder.append("nodeIdAllocation.allocationPreflightImplemented=true\n");
        builder.append("nodeIdAllocation.nodeIdsReserved=false\n");
        builder.append("nodeIdAllocation.nodeIdAllocatorApplied=false\n");
        builder.append("nodeIdAllocation.graphRewriteImplemented=false\n");
        builder.append("nodeIdAllocation.transformedIrBuilt=false\n");
        builder.append("nodeIdAllocation.mutationAllowed=false\n");
        builder.append("nodeIdAllocation.selectedIrReplacement=false\n");
        builder.append("replacementNode.count=").append(replacementNodeCount).append('\n');
        builder.append("replacementNode.ready.count=").append(replacementNodeReadyCount).append('\n');
        builder.append("replacementNode.blocked.count=").append(replacementNodeBlockedCount).append('\n');
        builder.append("replacementNode.firstBlocker=").append(replacementNodeFirstBlocker).append('\n');
        builder.append("replacementNode.replacementNodePreflightImplemented=true\n");
        builder.append("replacementNode.replacementNodeBuilt=false\n");
        builder.append("replacementNode.replacementBuilderImplemented=false\n");
        builder.append("replacementNode.graphRewriteImplemented=false\n");
        builder.append("replacementNode.transformedIrBuilt=false\n");
        builder.append("replacementNode.mutationAllowed=false\n");
        builder.append("replacementNode.selectedIrReplacement=false\n");
        builder.append("graphPatch.count=").append(graphPatchCount).append('\n');
        builder.append("graphPatch.ready.count=").append(graphPatchReadyCount).append('\n');
        builder.append("graphPatch.blocked.count=").append(graphPatchBlockedCount).append('\n');
        builder.append("graphPatch.firstBlocker=").append(graphPatchFirstBlocker).append('\n');
        builder.append("graphPatch.graphPatchPreflightImplemented=true\n");
        builder.append("graphPatch.graphPatchApplied=false\n");
        builder.append("graphPatch.graphRewriteImplemented=false\n");
        builder.append("graphPatch.transformedIrBuilt=false\n");
        builder.append("graphPatch.mutationAllowed=false\n");
        builder.append("graphPatch.selectedIrReplacement=false\n");
        builder.append("transformedGraph.count=").append(transformedGraphCount).append('\n');
        builder.append("transformedGraph.ready.count=").append(transformedGraphReadyCount).append('\n');
        builder.append("transformedGraph.blocked.count=").append(transformedGraphBlockedCount).append('\n');
        builder.append("transformedGraph.firstBlocker=").append(transformedGraphFirstBlocker).append('\n');
        builder.append("transformedGraph.materializationPreflightImplemented=true\n");
        builder.append("transformedGraph.transformedGraphBuilt=false\n");
        builder.append("transformedGraph.transformedIrBuilt=false\n");
        builder.append("transformedGraph.graphPatchApplied=false\n");
        builder.append("transformedGraph.graphRewriteImplemented=false\n");
        builder.append("transformedGraph.mutationAllowed=false\n");
        builder.append("transformedGraph.selectedIrReplacement=false\n");
        builder.append("irArtifactEnvelope.count=").append(irArtifactEnvelopeCount).append('\n');
        builder.append("irArtifactEnvelope.ready.count=").append(irArtifactEnvelopeReadyCount).append('\n');
        builder.append("irArtifactEnvelope.blocked.count=").append(irArtifactEnvelopeBlockedCount).append('\n');
        builder.append("irArtifactEnvelope.firstBlocker=").append(irArtifactEnvelopeFirstBlocker).append('\n');
        builder.append("irArtifactEnvelope.artifactEnvelopePreflightImplemented=true\n");
        builder.append("irArtifactEnvelope.artifactEnvelopeBuilt=false\n");
        builder.append("irArtifactEnvelope.optimizedArtifactBuilt=false\n");
        builder.append("irArtifactEnvelope.transformedGraphBuilt=false\n");
        builder.append("irArtifactEnvelope.transformedIrBuilt=false\n");
        builder.append("irArtifactEnvelope.graphPatchApplied=false\n");
        builder.append("irArtifactEnvelope.graphRewriteImplemented=false\n");
        builder.append("irArtifactEnvelope.mutationAllowed=false\n");
        builder.append("irArtifactEnvelope.selectedIrReplacement=false\n");
        appendArtifactProofBindingProperties(
                builder,
                "",
                artifactProofBindingCount,
                artifactProofBindingReadyCount,
                artifactProofBindingBlockedCount,
                artifactProofBindingFirstBlocker
        );
        appendArtifactSelectionProperties(
                builder,
                "",
                artifactSelectionCount,
                artifactSelectionReadyCount,
                artifactSelectionBlockedCount,
                artifactSelectionFirstBlocker
        );
        builder.append("rewriteSketch.count=").append(rewriteSketchCount).append('\n');
        builder.append("rewriteSketch.ready.count=").append(rewriteSketchReadyCount).append('\n');
        builder.append("rewriteSketch.blocked.count=").append(rewriteSketchBlockedCount).append('\n');
        builder.append("rewriteSketch.firstBlocker=").append(rewriteSketchFirstBlocker).append('\n');
        builder.append("rewriteSketch.rewriteBuilderImplemented=false\n");
        builder.append("rewriteSketch.mutationAllowed=false\n");
        builder.append("rewriteSketch.selectedIrReplacement=false\n");
        builder.append("rewriteSketch.conflict.count=").append(rewriteSketchConflictCount).append('\n');
        builder.append("rewriteSketch.conflict.firstBlocker=").append(rewriteSketchConflictFirstBlocker).append('\n');
        builder.append("rewriteSketch.conflict.conflictResolutionImplemented=false\n");
        builder.append("rewriteSketch.conflict.selectionApplied=false\n");
        builder.append("rewriteSketch.conflict.mutationAllowed=false\n");
        builder.append("rewriteSketch.conflict.selectedIrReplacement=false\n");
        builder.append("rewriteSelection.sketch.count=").append(rewriteSketchCount).append('\n');
        builder.append("rewriteSelection.sketch.ready.count=").append(rewriteSketchReadyCount).append('\n');
        builder.append("rewriteSelection.sketch.blocked.count=").append(rewriteSketchBlockedCount).append('\n');
        builder.append("rewriteSelection.conflict.count=").append(rewriteSketchConflictCount).append('\n');
        builder.append("rewriteSelection.status=").append(rewriteSelectionStatus).append('\n');
        builder.append("rewriteSelection.firstBlocker=").append(rewriteSelectionFirstBlocker).append('\n');
        builder.append("rewriteSelection.rewriteBuilderImplemented=false\n");
        builder.append("rewriteSelection.conflictResolutionImplemented=false\n");
        builder.append("rewriteSelection.runtimeEquivalenceRequired=").append(rewriteSketchReadyCount > 0).append('\n');
        builder.append("rewriteSelection.runtimeEquivalenceProven=false\n");
        builder.append("rewriteSelection.approvalRequired=").append(rewriteSketchReadyCount > 0).append('\n');
        builder.append("rewriteSelection.approvalAccepted=false\n");
        builder.append("rewriteSelection.mutationAllowed=false\n");
        builder.append("rewriteSelection.selectionApplied=false\n");
        builder.append("rewriteSelection.selectedIrReplacement=false\n");
        builder.append("rewriteProof.status=").append(rewriteProofStatus).append('\n');
        builder.append("rewriteProof.firstBlocker=").append(rewriteProofFirstBlocker).append('\n');
        builder.append("rewriteProof.proofAccepted=false\n");
        builder.append("rewriteProof.runtimeEquivalencePayload.present=false\n");
        builder.append("rewriteProof.runtimeEquivalencePayload.complete=false\n");
        builder.append("rewriteProof.rollbackEvidence.present=false\n");
        builder.append("rewriteProof.rollbackClean=false\n");
        builder.append("rewriteProof.approvalAccepted=false\n");
        builder.append("rewriteProof.mutationAllowed=false\n");
        builder.append("rewriteProof.selectedIrReplacement=false\n");
        builder.append("rewriteReviewPackage.status=").append(rewriteReviewPackageStatus).append('\n');
        builder.append("rewriteReviewPackage.firstBlocker=").append(rewriteReviewPackageFirstBlocker).append('\n');
        builder.append("rewriteReviewPackage.required=").append(rewriteSketchReadyCount > 0).append('\n');
        builder.append("rewriteReviewPackage.complete=false\n");
        builder.append("rewriteReviewPackage.conflict.count=").append(rewriteSketchConflictCount).append('\n');
        builder.append("rewriteReviewPackage.proofAccepted=false\n");
        builder.append("rewriteReviewPackage.runtimeEquivalencePayload.present=false\n");
        builder.append("rewriteReviewPackage.runtimeEquivalencePayload.complete=false\n");
        builder.append("rewriteReviewPackage.rollbackEvidence.present=false\n");
        builder.append("rewriteReviewPackage.rollbackClean=false\n");
        builder.append("rewriteReviewPackage.approvalAccepted=false\n");
        builder.append("rewriteReviewPackage.mutationAllowed=false\n");
        builder.append("rewriteReviewPackage.selectionApplied=false\n");
        builder.append("rewriteReviewPackage.selectedIrReplacement=false\n");
        builder.append("rewriteReviewPackage.manualReviewOnly=true\n");
        builder.append("optimizerRule.count=").append(optimizerRuleCount).append('\n');
        builder.append("optimizerRule.summary=").append(optimizerRuleSummary).append('\n');
        if (optimizerRuleDetailsProperties != null && !optimizerRuleDetailsProperties.isBlank()) {
            builder.append(optimizerRuleDetailsProperties);
        }
        builder.append("optimizerFamily.count=").append(optimizerFamilyCount).append('\n');
        builder.append("optimizerFamily.promotionReady.count=").append(optimizerFamilyPromotionReadyCount).append('\n');
        builder.append("optimizerFamily.summary=").append(optimizerFamilySummary).append('\n');
        builder.append("productionGateStatus=").append(productionGateStatus).append('\n');
        builder.append("productionProfileRequested=").append(productionProfileRequested).append('\n');
        return builder.toString();
    }

    private static int count(GpuRuntimeIrOptimizationReport report, GpuRuntimeIrOptimizationOutcome outcome) {
        return (int) report.passReports().stream()
                .filter(passReport -> !passReport.analysisOnly())
                .filter(passReport -> passReport.outcome() == outcome)
                .count();
    }

    private static int proofArtifactCount(GpuRuntimeIrOptimizationReport report) {
        return (int) report.passReports().stream()
                .filter(passReport -> !passReport.analysisOnly())
                .filter(GpuRuntimeOptimizerDriftArtifact::hasProofArtifact)
                .count();
    }

    private static int acceptedProofArtifactCount(GpuRuntimeIrOptimizationReport report) {
        return (int) report.passReports().stream()
                .filter(passReport -> !passReport.analysisOnly())
                .filter(GpuRuntimeOptimizerDriftArtifact::hasProofArtifact)
                .filter(passReport -> isAcceptedVerdict(passReport.proofArtifact().verdict()))
                .count();
    }

    private static int blockingProofArtifactCount(GpuRuntimeIrOptimizationReport report) {
        return (int) report.passReports().stream()
                .filter(passReport -> !passReport.analysisOnly())
                .filter(GpuRuntimeOptimizerDriftArtifact::hasProofArtifact)
                .filter(passReport -> isBlockingVerdict(passReport.proofArtifact().verdict()))
                .count();
    }

    private static int replacementPlanCompleteCount(GpuRuntimeIrOptimizationReport report) {
        return replacementPlanRuleCount(report, ".replacementPlan.complete.count");
    }

    private static int replacementPlanPartialCount(GpuRuntimeIrOptimizationReport report) {
        int total = 0;
        for (GpuRuntimeIrOptimizationPassReport passReport : report.passReports()) {
            if (!passReport.analysisOnly() && hasProofArtifact(passReport)) {
                Map<String, String> fields = passReport.proofArtifact().fields();
                int passTotal = parseInt(fields.get("replacementPlan.partial.count"));
                total += passTotal > 0 ? passTotal : replacementPlanRuleCount(fields, ".replacementPlan.partial.count");
            }
        }
        return total;
    }

    private static int replacementPlanRuleCount(GpuRuntimeIrOptimizationReport report, String suffix) {
        int total = 0;
        for (GpuRuntimeIrOptimizationPassReport passReport : report.passReports()) {
            if (!passReport.analysisOnly() && hasProofArtifact(passReport)) {
                total += replacementPlanRuleCount(passReport.proofArtifact().fields(), suffix);
            }
        }
        return total;
    }

    private static int replacementPlanRuleCount(Map<String, String> fields, String suffix) {
        return fields.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith("rule."))
                .filter(entry -> entry.getKey().endsWith(suffix))
                .mapToInt(entry -> parseInt(entry.getValue()))
                .sum();
    }

    private static int proofFieldRuleCount(GpuRuntimeIrOptimizationReport report, String suffix) {
        int total = 0;
        for (GpuRuntimeIrOptimizationPassReport passReport : report.passReports()) {
            if (!passReport.analysisOnly() && hasProofArtifact(passReport)) {
                total += proofFieldRuleCount(passReport.proofArtifact().fields(), suffix);
            }
        }
        return total;
    }

    private static int proofFieldRuleCount(Map<String, String> fields, String suffix) {
        return fields.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith("rule."))
                .filter(entry -> entry.getKey().endsWith(suffix))
                .mapToInt(entry -> parseInt(entry.getValue()))
                .sum();
    }

    private static int proofFieldBlockedCount(GpuRuntimeIrOptimizationReport report, String aggregateKey, String ruleSuffix) {
        int total = 0;
        for (GpuRuntimeIrOptimizationPassReport passReport : report.passReports()) {
            if (!passReport.analysisOnly() && hasProofArtifact(passReport)) {
                Map<String, String> fields = passReport.proofArtifact().fields();
                int passTotal = parseInt(fields.get(aggregateKey));
                total += passTotal > 0 ? passTotal : proofFieldRuleCount(fields, ruleSuffix);
            }
        }
        return total;
    }

    private static String proofFieldFirstBlocker(
            GpuRuntimeIrOptimizationReport report,
            String aggregateKey,
            String ruleSuffix
    ) {
        for (GpuRuntimeIrOptimizationPassReport passReport : report.passReports()) {
            if (!passReport.analysisOnly() && hasProofArtifact(passReport)) {
                Map<String, String> fields = passReport.proofArtifact().fields();
                String blocker = fields.getOrDefault(aggregateKey, "none");
                if (!blocker.isBlank() && !"none".equals(blocker)) {
                    return blocker;
                }
                String ruleBlocker = firstRuleBlocker(fields, ruleSuffix);
                if (!"none".equals(ruleBlocker)) {
                    return ruleBlocker;
                }
            }
        }
        return "none";
    }

    private static ProofFieldSummary proofFieldSummary(GpuRuntimeIrOptimizationReport report, String fieldPrefix) {
        return new ProofFieldSummary(
                proofFieldRuleCount(report, "." + fieldPrefix + ".count"),
                proofFieldRuleCount(report, "." + fieldPrefix + ".ready.count"),
                proofFieldBlockedCount(report, fieldPrefix + ".blocked.count", "." + fieldPrefix + ".blocked.count"),
                proofFieldFirstBlocker(report, fieldPrefix + ".firstBlocker", "." + fieldPrefix + ".firstBlocker")
        );
    }

    private static void appendProofFieldSummaryProperties(
            StringBuilder builder,
            String prefix,
            String fieldPrefix,
            int count,
            int readyCount,
            int blockedCount,
            String firstBlocker
    ) {
        builder.append(prefix).append(fieldPrefix).append(".count=").append(count).append('\n');
        builder.append(prefix).append(fieldPrefix).append(".ready.count=").append(readyCount).append('\n');
        builder.append(prefix).append(fieldPrefix).append(".blocked.count=").append(blockedCount).append('\n');
        builder.append(prefix).append(fieldPrefix).append(".firstBlocker=").append(firstBlocker).append('\n');
    }

    private static void appendArtifactProofBindingProperties(
            StringBuilder builder,
            String prefix,
            int count,
            int readyCount,
            int blockedCount,
            String firstBlocker
    ) {
        appendProofFieldSummaryProperties(
                builder,
                prefix,
                "artifactProofBinding",
                count,
                readyCount,
                blockedCount,
                firstBlocker
        );
        builder.append(prefix).append("artifactProofBinding.bindingPreflightImplemented=true\n");
        builder.append(prefix).append("artifactProofBinding.proofBound=false\n");
        builder.append(prefix).append("artifactProofBinding.rollbackBound=false\n");
        builder.append(prefix).append("artifactProofBinding.approvalBound=false\n");
        appendArtifactBuildGuardProperties(builder, prefix, "artifactProofBinding");
        appendMutationGuardProperties(builder, prefix, "artifactProofBinding");
    }

    private static void appendArtifactSelectionProperties(
            StringBuilder builder,
            String prefix,
            int count,
            int readyCount,
            int blockedCount,
            String firstBlocker
    ) {
        appendProofFieldSummaryProperties(
                builder,
                prefix,
                "artifactSelection",
                count,
                readyCount,
                blockedCount,
                firstBlocker
        );
        builder.append(prefix).append("artifactSelection.selectionPreflightImplemented=true\n");
        builder.append(prefix).append("artifactSelection.productionGateRequired=").append(count > 0).append('\n');
        builder.append(prefix).append("artifactSelection.productionGateAccepted=false\n");
        builder.append(prefix).append("artifactSelection.mutationPolicyAllowed=false\n");
        builder.append(prefix).append("artifactSelection.selectionApplied=false\n");
        builder.append(prefix).append("artifactSelection.optimizedArtifactSelected=false\n");
        appendArtifactBuildGuardProperties(builder, prefix, "artifactSelection");
        appendMutationGuardProperties(builder, prefix, "artifactSelection");
    }

    private static void appendArtifactBuildGuardProperties(StringBuilder builder, String prefix, String fieldPrefix) {
        builder.append(prefix).append(fieldPrefix).append(".optimizedArtifactBuilt=false\n");
        builder.append(prefix).append(fieldPrefix).append(".transformedIrBuilt=false\n");
    }

    private static void appendMutationGuardProperties(StringBuilder builder, String prefix, String fieldPrefix) {
        builder.append(prefix).append(fieldPrefix).append(".mutationAllowed=false\n");
        builder.append(prefix).append(fieldPrefix).append(".selectedIrReplacement=false\n");
    }

    private static String replacementPlanFirstBlocker(GpuRuntimeIrOptimizationReport report) {
        for (GpuRuntimeIrOptimizationPassReport passReport : report.passReports()) {
            if (!passReport.analysisOnly() && hasProofArtifact(passReport)) {
                String blocker = passReport.proofArtifact().fields().getOrDefault("replacementPlan.firstBlocker", "none");
                if (!blocker.isBlank() && !"none".equals(blocker)) {
                    return blocker;
                }
            }
        }
        return "none";
    }

    private static int replacementPlanValidationCount(GpuRuntimeIrOptimizationReport report) {
        return replacementPlanRuleCount(report, ".replacementPlan.validation.count");
    }

    private static int replacementPlanValidationValidCount(GpuRuntimeIrOptimizationReport report) {
        return replacementPlanRuleCount(report, ".replacementPlan.validation.valid.count");
    }

    private static int replacementPlanValidationInvalidCount(GpuRuntimeIrOptimizationReport report) {
        int total = 0;
        for (GpuRuntimeIrOptimizationPassReport passReport : report.passReports()) {
            if (!passReport.analysisOnly() && hasProofArtifact(passReport)) {
                Map<String, String> fields = passReport.proofArtifact().fields();
                int passTotal = parseInt(fields.get("replacementPlan.validation.invalid.count"));
                total += passTotal > 0 ? passTotal : replacementPlanRuleCount(fields, ".replacementPlan.validation.invalid.count");
            }
        }
        return total;
    }

    private static String replacementPlanValidationFirstBlocker(GpuRuntimeIrOptimizationReport report) {
        for (GpuRuntimeIrOptimizationPassReport passReport : report.passReports()) {
            if (!passReport.analysisOnly() && hasProofArtifact(passReport)) {
                Map<String, String> fields = passReport.proofArtifact().fields();
                String blocker = fields.getOrDefault("replacementPlan.validation.firstBlocker", "none");
                if (!blocker.isBlank() && !"none".equals(blocker)) {
                    return blocker;
                }
                String ruleBlocker = firstRuleBlocker(fields, ".replacementPlan.validation.firstBlocker");
                if (!"none".equals(ruleBlocker)) {
                    return ruleBlocker;
                }
            }
        }
        return "none";
    }

    private static int rewriteVisitorCount(GpuRuntimeIrOptimizationReport report) {
        return proofFieldRuleCount(report, ".rewriteVisitor.count");
    }

    private static int rewriteVisitorReadyCount(GpuRuntimeIrOptimizationReport report) {
        return proofFieldRuleCount(report, ".rewriteVisitor.ready.count");
    }

    private static int rewriteVisitorBlockedCount(GpuRuntimeIrOptimizationReport report) {
        return proofFieldBlockedCount(report, "rewriteVisitor.blocked.count", ".rewriteVisitor.blocked.count");
    }

    private static String rewriteVisitorFirstBlocker(GpuRuntimeIrOptimizationReport report) {
        return proofFieldFirstBlocker(report, "rewriteVisitor.firstBlocker", ".rewriteVisitor.firstBlocker");
    }

    private static int replacementBlueprintCount(GpuRuntimeIrOptimizationReport report) {
        return proofFieldRuleCount(report, ".replacementBlueprint.count");
    }

    private static int replacementBlueprintReadyCount(GpuRuntimeIrOptimizationReport report) {
        return proofFieldRuleCount(report, ".replacementBlueprint.ready.count");
    }

    private static int replacementBlueprintBlockedCount(GpuRuntimeIrOptimizationReport report) {
        return proofFieldBlockedCount(report, "replacementBlueprint.blocked.count", ".replacementBlueprint.blocked.count");
    }

    private static String replacementBlueprintFirstBlocker(GpuRuntimeIrOptimizationReport report) {
        return proofFieldFirstBlocker(report, "replacementBlueprint.firstBlocker", ".replacementBlueprint.firstBlocker");
    }

    private static int rewriteTransactionCount(GpuRuntimeIrOptimizationReport report) {
        return proofFieldRuleCount(report, ".rewriteTransaction.count");
    }

    private static int rewriteTransactionReadyCount(GpuRuntimeIrOptimizationReport report) {
        return proofFieldRuleCount(report, ".rewriteTransaction.ready.count");
    }

    private static int rewriteTransactionBlockedCount(GpuRuntimeIrOptimizationReport report) {
        return proofFieldBlockedCount(report, "rewriteTransaction.blocked.count", ".rewriteTransaction.blocked.count");
    }

    private static String rewriteTransactionFirstBlocker(GpuRuntimeIrOptimizationReport report) {
        return proofFieldFirstBlocker(report, "rewriteTransaction.firstBlocker", ".rewriteTransaction.firstBlocker");
    }

    private static int nodeIdAllocationCount(GpuRuntimeIrOptimizationReport report) {
        return proofFieldRuleCount(report, ".nodeIdAllocation.count");
    }

    private static int nodeIdAllocationReadyCount(GpuRuntimeIrOptimizationReport report) {
        return proofFieldRuleCount(report, ".nodeIdAllocation.ready.count");
    }

    private static int nodeIdAllocationBlockedCount(GpuRuntimeIrOptimizationReport report) {
        return proofFieldBlockedCount(report, "nodeIdAllocation.blocked.count", ".nodeIdAllocation.blocked.count");
    }

    private static String nodeIdAllocationFirstBlocker(GpuRuntimeIrOptimizationReport report) {
        return proofFieldFirstBlocker(report, "nodeIdAllocation.firstBlocker", ".nodeIdAllocation.firstBlocker");
    }

    private static int replacementNodeCount(GpuRuntimeIrOptimizationReport report) {
        return proofFieldRuleCount(report, ".replacementNode.count");
    }

    private static int replacementNodeReadyCount(GpuRuntimeIrOptimizationReport report) {
        return proofFieldRuleCount(report, ".replacementNode.ready.count");
    }

    private static int replacementNodeBlockedCount(GpuRuntimeIrOptimizationReport report) {
        return proofFieldBlockedCount(report, "replacementNode.blocked.count", ".replacementNode.blocked.count");
    }

    private static String replacementNodeFirstBlocker(GpuRuntimeIrOptimizationReport report) {
        return proofFieldFirstBlocker(report, "replacementNode.firstBlocker", ".replacementNode.firstBlocker");
    }

    private static int graphPatchCount(GpuRuntimeIrOptimizationReport report) {
        return proofFieldRuleCount(report, ".graphPatch.count");
    }

    private static int graphPatchReadyCount(GpuRuntimeIrOptimizationReport report) {
        return proofFieldRuleCount(report, ".graphPatch.ready.count");
    }

    private static int graphPatchBlockedCount(GpuRuntimeIrOptimizationReport report) {
        return proofFieldBlockedCount(report, "graphPatch.blocked.count", ".graphPatch.blocked.count");
    }

    private static String graphPatchFirstBlocker(GpuRuntimeIrOptimizationReport report) {
        return proofFieldFirstBlocker(report, "graphPatch.firstBlocker", ".graphPatch.firstBlocker");
    }

    private static int transformedGraphCount(GpuRuntimeIrOptimizationReport report) {
        return proofFieldRuleCount(report, ".transformedGraph.count");
    }

    private static int transformedGraphReadyCount(GpuRuntimeIrOptimizationReport report) {
        return proofFieldRuleCount(report, ".transformedGraph.ready.count");
    }

    private static int transformedGraphBlockedCount(GpuRuntimeIrOptimizationReport report) {
        return proofFieldBlockedCount(report, "transformedGraph.blocked.count", ".transformedGraph.blocked.count");
    }

    private static String transformedGraphFirstBlocker(GpuRuntimeIrOptimizationReport report) {
        return proofFieldFirstBlocker(report, "transformedGraph.firstBlocker", ".transformedGraph.firstBlocker");
    }

    private static int irArtifactEnvelopeCount(GpuRuntimeIrOptimizationReport report) {
        return proofFieldRuleCount(report, ".irArtifactEnvelope.count");
    }

    private static int irArtifactEnvelopeReadyCount(GpuRuntimeIrOptimizationReport report) {
        return proofFieldRuleCount(report, ".irArtifactEnvelope.ready.count");
    }

    private static int irArtifactEnvelopeBlockedCount(GpuRuntimeIrOptimizationReport report) {
        return proofFieldBlockedCount(report, "irArtifactEnvelope.blocked.count", ".irArtifactEnvelope.blocked.count");
    }

    private static String irArtifactEnvelopeFirstBlocker(GpuRuntimeIrOptimizationReport report) {
        return proofFieldFirstBlocker(report, "irArtifactEnvelope.firstBlocker", ".irArtifactEnvelope.firstBlocker");
    }

    private static int rewriteSketchCount(GpuRuntimeIrOptimizationReport report) {
        return proofFieldRuleCount(report, ".rewriteSketch.count");
    }

    private static int rewriteSketchReadyCount(GpuRuntimeIrOptimizationReport report) {
        return proofFieldRuleCount(report, ".rewriteSketch.ready.count");
    }

    private static int rewriteSketchBlockedCount(GpuRuntimeIrOptimizationReport report) {
        return proofFieldBlockedCount(report, "rewriteSketch.blocked.count", ".rewriteSketch.blocked.count");
    }

    private static String rewriteSketchFirstBlocker(GpuRuntimeIrOptimizationReport report) {
        return proofFieldFirstBlocker(report, "rewriteSketch.firstBlocker", ".rewriteSketch.firstBlocker");
    }

    private static int rewriteSketchConflictCount(GpuRuntimeIrOptimizationReport report) {
        int total = 0;
        for (GpuRuntimeIrOptimizationPassReport passReport : report.passReports()) {
            if (!passReport.analysisOnly() && hasProofArtifact(passReport)) {
                total += parseInt(passReport.proofArtifact().fields().get("rewriteSketch.conflict.count"));
            }
        }
        return total;
    }

    private static String rewriteSketchConflictFirstBlocker(GpuRuntimeIrOptimizationReport report) {
        for (GpuRuntimeIrOptimizationPassReport passReport : report.passReports()) {
            if (!passReport.analysisOnly() && hasProofArtifact(passReport)) {
                String blocker = passReport.proofArtifact().fields().getOrDefault(
                        "rewriteSketch.conflict.firstBlocker",
                        "none"
                );
                if (!blocker.isBlank() && !"none".equals(blocker)) {
                    return blocker;
                }
            }
        }
        return "none";
    }

    private static String rewriteSelectionStatus(GpuRuntimeIrOptimizationReport report, int rewriteSketchCount) {
        String fallback = rewriteSketchCount == 0 ? "not-required" : "blocked";
        for (GpuRuntimeIrOptimizationPassReport passReport : report.passReports()) {
            if (!passReport.analysisOnly() && hasProofArtifact(passReport)) {
                String status = passReport.proofArtifact().fields().getOrDefault("rewriteSelection.status", "");
                if ("blocked".equals(status)) {
                    return status;
                }
                if (!status.isBlank()) {
                    fallback = status;
                }
            }
        }
        return fallback;
    }

    private static String rewriteSelectionFirstBlocker(
            GpuRuntimeIrOptimizationReport report,
            int rewriteSketchCount,
            int rewriteSketchReadyCount,
            int rewriteSketchBlockedCount,
            String rewriteSketchFirstBlocker,
            int rewriteSketchConflictCount
    ) {
        String fallback = derivedRewriteSelectionFirstBlocker(
                rewriteSketchCount,
                rewriteSketchReadyCount,
                rewriteSketchBlockedCount,
                rewriteSketchFirstBlocker,
                rewriteSketchConflictCount
        );
        for (GpuRuntimeIrOptimizationPassReport passReport : report.passReports()) {
            if (!passReport.analysisOnly() && hasProofArtifact(passReport)) {
                String blocker = passReport.proofArtifact().fields().getOrDefault("rewriteSelection.firstBlocker", "");
                if (!blocker.isBlank() && !"none".equals(blocker) && !"no-rewrite-sketches".equals(blocker)) {
                    return blocker;
                }
                if (!blocker.isBlank()) {
                    fallback = blocker;
                }
            }
        }
        return fallback;
    }

    private static String derivedRewriteSelectionFirstBlocker(
            int rewriteSketchCount,
            int rewriteSketchReadyCount,
            int rewriteSketchBlockedCount,
            String rewriteSketchFirstBlocker,
            int rewriteSketchConflictCount
    ) {
        if (rewriteSketchCount == 0) {
            return "no-rewrite-sketches";
        }
        if (rewriteSketchBlockedCount > 0) {
            return rewriteSketchFirstBlocker == null || rewriteSketchFirstBlocker.isBlank() || "none".equals(rewriteSketchFirstBlocker)
                    ? "rewrite-sketch-blocked"
                    : rewriteSketchFirstBlocker;
        }
        if (rewriteSketchConflictCount > 0) {
            return "rewrite-sketch-conflict-resolution-required";
        }
        return rewriteSketchReadyCount > 0 ? "rewrite-builder-not-implemented" : "no-rewrite-sketches";
    }

    private static String rewriteProofStatus(GpuRuntimeIrOptimizationReport report, int rewriteSketchReadyCount) {
        String fallback = rewriteSketchReadyCount == 0 ? "not-required" : "blocked";
        for (GpuRuntimeIrOptimizationPassReport passReport : report.passReports()) {
            if (!passReport.analysisOnly() && hasProofArtifact(passReport)) {
                String status = passReport.proofArtifact().fields().getOrDefault("rewriteProof.status", "");
                if ("blocked".equals(status)) {
                    return status;
                }
                if (!status.isBlank()) {
                    fallback = status;
                }
            }
        }
        return fallback;
    }

    private static String rewriteProofFirstBlocker(GpuRuntimeIrOptimizationReport report) {
        String fallback = "no-proof-candidates";
        for (GpuRuntimeIrOptimizationPassReport passReport : report.passReports()) {
            if (!passReport.analysisOnly() && hasProofArtifact(passReport)) {
                Map<String, String> fields = passReport.proofArtifact().fields();
                String blocker = fields.getOrDefault("rewriteProof.firstBlocker", "");
                if (!blocker.isBlank() && !"none".equals(blocker) && !"no-proof-candidates".equals(blocker)) {
                    return blocker;
                }
                if (!blocker.isBlank()) {
                    fallback = blocker;
                }
            }
        }
        return fallback;
    }

    private static String rewriteReviewPackageStatus(GpuRuntimeIrOptimizationReport report, int rewriteSketchReadyCount) {
        String fallback = rewriteSketchReadyCount == 0 ? "not-required" : "blocked";
        for (GpuRuntimeIrOptimizationPassReport passReport : report.passReports()) {
            if (!passReport.analysisOnly() && hasProofArtifact(passReport)) {
                String status = passReport.proofArtifact().fields().getOrDefault("rewriteReviewPackage.status", "");
                if ("blocked".equals(status)) {
                    return status;
                }
                if (!status.isBlank()) {
                    fallback = status;
                }
            }
        }
        return fallback;
    }

    private static String rewriteReviewPackageFirstBlocker(
            GpuRuntimeIrOptimizationReport report,
            int rewriteSketchReadyCount,
            int rewriteSketchConflictCount,
            String rewriteProofFirstBlocker
    ) {
        String fallback = derivedRewriteReviewPackageFirstBlocker(
                rewriteSketchReadyCount,
                rewriteSketchConflictCount,
                rewriteProofFirstBlocker
        );
        for (GpuRuntimeIrOptimizationPassReport passReport : report.passReports()) {
            if (!passReport.analysisOnly() && hasProofArtifact(passReport)) {
                String blocker = passReport.proofArtifact().fields().getOrDefault("rewriteReviewPackage.firstBlocker", "");
                if (!blocker.isBlank() && !"none".equals(blocker) && !"no-review-candidates".equals(blocker)) {
                    return blocker;
                }
                if (!blocker.isBlank()) {
                    fallback = blocker;
                }
            }
        }
        return fallback;
    }

    private static String derivedRewriteReviewPackageFirstBlocker(
            int rewriteSketchReadyCount,
            int rewriteSketchConflictCount,
            String rewriteProofFirstBlocker
    ) {
        if (rewriteSketchReadyCount == 0) {
            return "no-review-candidates";
        }
        if (rewriteSketchConflictCount > 0) {
            return "rewrite-sketch-conflict-resolution-required";
        }
        if (rewriteProofFirstBlocker == null
                || rewriteProofFirstBlocker.isBlank()
                || "none".equals(rewriteProofFirstBlocker)
                || "no-proof-candidates".equals(rewriteProofFirstBlocker)) {
            return "runtime-equivalence-payload-missing";
        }
        return rewriteProofFirstBlocker;
    }

    private static String firstRuleBlocker(Map<String, String> fields, String suffix) {
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            String blocker = entry.getValue();
            if (entry.getKey().startsWith("rule.")
                    && entry.getKey().endsWith(suffix)
                    && blocker != null
                    && !blocker.isBlank()
                    && !"none".equals(blocker)) {
                return blocker;
            }
        }
        return "none";
    }

    private static Map<String, OptimizerRuleEvidence> optimizerRules(GpuRuntimeIrOptimizationReport report) {
        Map<String, OptimizerRuleEvidence> rules = new LinkedHashMap<>();
        for (GpuRuntimeIrOptimizationPassReport passReport : report.passReports()) {
            if (passReport.analysisOnly() || !hasProofArtifact(passReport)) {
                continue;
            }
            Map<String, String> fields = passReport.proofArtifact().fields();
            int ruleCount = parseInt(fields.get("rule.count"));
            for (int index = 0; index < ruleCount; index++) {
                String prefix = "rule." + index;
                String id = fields.getOrDefault(prefix + ".id", "");
                if (id.isBlank()) {
                    continue;
                }
                OptimizerRuleEvidence existing = rules.getOrDefault(id, OptimizerRuleEvidence.empty(id));
                rules.put(id, existing.add(fields, prefix));
            }
        }
        return rules;
    }

    private static String formatOptimizerRuleSummary(Map<String, OptimizerRuleEvidence> rules) {
        if (rules.isEmpty()) {
            return "none";
        }
        StringBuilder builder = new StringBuilder();
        for (OptimizerRuleEvidence rule : rules.values()) {
            if (!builder.isEmpty()) {
                builder.append(", ");
            }
            builder.append(rule.id())
                    .append("[candidates=")
                    .append(rule.candidateCount())
                    .append(", proposals=")
                    .append(rule.proposalCount())
                    .append(", applied=")
                    .append(rule.appliedCount())
                    .append(", skipped=")
                    .append(rule.skippedCount())
                    .append(", blocked=")
                    .append(rule.blockedCount())
                    .append(", mutationProposed=")
                    .append(rule.mutationProposed())
                    .append(", replacementPlans=")
                    .append(rule.replacementPlanCount())
                    .append(", completePlans=")
                    .append(rule.replacementPlanCompleteCount())
                    .append(", partialPlans=")
                    .append(rule.replacementPlanPartialCount())
                    .append(", planValidations=")
                    .append(rule.replacementPlanValidationCount())
                    .append(", invalidPlanValidations=")
                    .append(rule.replacementPlanValidationInvalidCount())
                    .append(", planValidationFirstBlocker=")
                    .append(rule.replacementPlanValidationFirstBlocker())
                    .append(", rewriteVisitors=")
                    .append(rule.rewriteVisitorCount())
                    .append(", readyVisitors=")
                    .append(rule.rewriteVisitorReadyCount())
                    .append(", blockedVisitors=")
                    .append(rule.rewriteVisitorBlockedCount())
                    .append(", rewriteVisitorFirstBlocker=")
                    .append(rule.rewriteVisitorFirstBlocker())
                    .append(", replacementBlueprints=")
                    .append(rule.replacementBlueprintCount())
                    .append(", readyBlueprints=")
                    .append(rule.replacementBlueprintReadyCount())
                    .append(", blockedBlueprints=")
                    .append(rule.replacementBlueprintBlockedCount())
                    .append(", replacementBlueprintFirstBlocker=")
                    .append(rule.replacementBlueprintFirstBlocker())
                    .append(", rewriteTransactions=")
                    .append(rule.rewriteTransactionCount())
                    .append(", readyTransactions=")
                    .append(rule.rewriteTransactionReadyCount())
                    .append(", blockedTransactions=")
                    .append(rule.rewriteTransactionBlockedCount())
                    .append(", rewriteTransactionFirstBlocker=")
                    .append(rule.rewriteTransactionFirstBlocker())
                    .append(", nodeIdAllocations=")
                    .append(rule.nodeIdAllocationCount())
                    .append(", readyNodeIdAllocations=")
                    .append(rule.nodeIdAllocationReadyCount())
                    .append(", blockedNodeIdAllocations=")
                    .append(rule.nodeIdAllocationBlockedCount())
                    .append(", nodeIdAllocationFirstBlocker=")
                    .append(rule.nodeIdAllocationFirstBlocker())
                    .append(", replacementNodes=")
                    .append(rule.replacementNodeCount())
                    .append(", readyReplacementNodes=")
                    .append(rule.replacementNodeReadyCount())
                    .append(", blockedReplacementNodes=")
                    .append(rule.replacementNodeBlockedCount())
                    .append(", replacementNodeFirstBlocker=")
                    .append(rule.replacementNodeFirstBlocker())
                    .append(", graphPatches=")
                    .append(rule.graphPatchCount())
                    .append(", readyGraphPatches=")
                    .append(rule.graphPatchReadyCount())
                    .append(", blockedGraphPatches=")
                    .append(rule.graphPatchBlockedCount())
                    .append(", graphPatchFirstBlocker=")
                    .append(rule.graphPatchFirstBlocker())
                    .append(", transformedGraphs=")
                    .append(rule.transformedGraphCount())
                    .append(", readyTransformedGraphs=")
                    .append(rule.transformedGraphReadyCount())
                    .append(", blockedTransformedGraphs=")
                    .append(rule.transformedGraphBlockedCount())
                    .append(", transformedGraphFirstBlocker=")
                    .append(rule.transformedGraphFirstBlocker())
                    .append(", irArtifactEnvelopes=")
                    .append(rule.irArtifactEnvelopeCount())
                    .append(", readyIrArtifactEnvelopes=")
                    .append(rule.irArtifactEnvelopeReadyCount())
                    .append(", blockedIrArtifactEnvelopes=")
                    .append(rule.irArtifactEnvelopeBlockedCount())
                    .append(", irArtifactEnvelopeFirstBlocker=")
                    .append(rule.irArtifactEnvelopeFirstBlocker())
                    .append(", artifactProofBindings=")
                    .append(rule.artifactProofBindingCount())
                    .append(", readyArtifactProofBindings=")
                    .append(rule.artifactProofBindingReadyCount())
                    .append(", blockedArtifactProofBindings=")
                    .append(rule.artifactProofBindingBlockedCount())
                    .append(", artifactProofBindingFirstBlocker=")
                    .append(rule.artifactProofBindingFirstBlocker())
                    .append(", artifactSelections=")
                    .append(rule.artifactSelectionCount())
                    .append(", readyArtifactSelections=")
                    .append(rule.artifactSelectionReadyCount())
                    .append(", blockedArtifactSelections=")
                    .append(rule.artifactSelectionBlockedCount())
                    .append(", artifactSelectionFirstBlocker=")
                    .append(rule.artifactSelectionFirstBlocker())
                    .append(", rewriteSketches=")
                    .append(rule.rewriteSketchCount())
                    .append(", readySketches=")
                    .append(rule.rewriteSketchReadyCount())
                    .append(", blockedSketches=")
                    .append(rule.rewriteSketchBlockedCount())
                    .append(", rewriteSketchFirstBlocker=")
                    .append(rule.rewriteSketchFirstBlocker())
                    .append(", rewriteSelectionStatus=")
                    .append(rule.rewriteSelectionStatus())
                    .append(", rewriteSelectionFirstBlocker=")
                    .append(rule.rewriteSelectionFirstBlocker())
                    .append(", rewriteProofStatus=")
                    .append(rule.rewriteProofStatus())
                    .append(", rewriteProofFirstBlocker=")
                    .append(rule.rewriteProofFirstBlocker())
                    .append(", rewriteReviewPackageStatus=")
                    .append(rule.rewriteReviewPackageStatus())
                    .append(", rewriteReviewPackageFirstBlocker=")
                    .append(rule.rewriteReviewPackageFirstBlocker())
                    .append(", firstBlocker=")
                    .append(rule.firstBlocker())
                    .append(']');
        }
        return builder.toString();
    }

    private static String formatOptimizerRuleProperties(Map<String, OptimizerRuleEvidence> rules) {
        if (rules.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        int index = 0;
        for (OptimizerRuleEvidence rule : rules.values()) {
            String prefix = "optimizerRule." + index + ".";
            builder.append(prefix).append("id=").append(rule.id()).append('\n');
            builder.append(prefix).append("version=").append(rule.version()).append('\n');
            builder.append(prefix).append("extensionId=").append(rule.extensionId()).append('\n');
            builder.append(prefix).append("extensionVersion=").append(rule.extensionVersion()).append('\n');
            builder.append(prefix).append("proofStatus=").append(rule.proofStatus()).append('\n');
            builder.append(prefix).append("candidate.count=").append(rule.candidateCount()).append('\n');
            builder.append(prefix).append("proposal.count=").append(rule.proposalCount()).append('\n');
            builder.append(prefix).append("applied.count=").append(rule.appliedCount()).append('\n');
            builder.append(prefix).append("skipped.count=").append(rule.skippedCount()).append('\n');
            builder.append(prefix).append("blocked.count=").append(rule.blockedCount()).append('\n');
            builder.append(prefix).append("mutationProposed=").append(rule.mutationProposed()).append('\n');
            builder.append(prefix).append("replacementPlan.count=").append(rule.replacementPlanCount()).append('\n');
            builder.append(prefix).append("replacementPlan.complete.count=").append(rule.replacementPlanCompleteCount()).append('\n');
            builder.append(prefix).append("replacementPlan.partial.count=").append(rule.replacementPlanPartialCount()).append('\n');
            builder.append(prefix).append("replacementPlan.firstBlocker=").append(rule.firstBlocker()).append('\n');
            builder.append(prefix).append("replacementPlan.validation.count=").append(rule.replacementPlanValidationCount()).append('\n');
            builder.append(prefix).append("replacementPlan.validation.valid.count=").append(rule.replacementPlanValidationValidCount()).append('\n');
            builder.append(prefix).append("replacementPlan.validation.invalid.count=").append(rule.replacementPlanValidationInvalidCount()).append('\n');
            builder.append(prefix).append("replacementPlan.validation.firstBlocker=").append(rule.replacementPlanValidationFirstBlocker()).append('\n');
            builder.append(prefix).append("rewriteVisitor.count=").append(rule.rewriteVisitorCount()).append('\n');
            builder.append(prefix).append("rewriteVisitor.ready.count=").append(rule.rewriteVisitorReadyCount()).append('\n');
            builder.append(prefix).append("rewriteVisitor.blocked.count=").append(rule.rewriteVisitorBlockedCount()).append('\n');
            builder.append(prefix).append("rewriteVisitor.firstBlocker=").append(rule.rewriteVisitorFirstBlocker()).append('\n');
            builder.append(prefix).append("rewriteVisitor.visitorImplemented=true\n");
            builder.append(prefix).append("rewriteVisitor.replacementBuilderImplemented=false\n");
            builder.append(prefix).append("rewriteVisitor.transformedIrBuilt=false\n");
            builder.append(prefix).append("rewriteVisitor.mutationAllowed=false\n");
            builder.append(prefix).append("rewriteVisitor.selectedIrReplacement=false\n");
            builder.append(prefix).append("replacementBlueprint.count=").append(rule.replacementBlueprintCount()).append('\n');
            builder.append(prefix).append("replacementBlueprint.ready.count=").append(rule.replacementBlueprintReadyCount()).append('\n');
            builder.append(prefix).append("replacementBlueprint.blocked.count=").append(rule.replacementBlueprintBlockedCount()).append('\n');
            builder.append(prefix).append("replacementBlueprint.firstBlocker=").append(rule.replacementBlueprintFirstBlocker()).append('\n');
            builder.append(prefix).append("replacementBlueprint.blueprintImplemented=true\n");
            builder.append(prefix).append("replacementBlueprint.replacementBuilderImplemented=false\n");
            builder.append(prefix).append("replacementBlueprint.transformedIrBuilt=false\n");
            builder.append(prefix).append("replacementBlueprint.mutationAllowed=false\n");
            builder.append(prefix).append("replacementBlueprint.selectedIrReplacement=false\n");
            builder.append(prefix).append("rewriteTransaction.count=").append(rule.rewriteTransactionCount()).append('\n');
            builder.append(prefix).append("rewriteTransaction.ready.count=").append(rule.rewriteTransactionReadyCount()).append('\n');
            builder.append(prefix).append("rewriteTransaction.blocked.count=").append(rule.rewriteTransactionBlockedCount()).append('\n');
            builder.append(prefix).append("rewriteTransaction.firstBlocker=").append(rule.rewriteTransactionFirstBlocker()).append('\n');
            builder.append(prefix).append("rewriteTransaction.transactionPreflightImplemented=true\n");
            builder.append(prefix).append("rewriteTransaction.nodeIdAllocatorImplemented=false\n");
            builder.append(prefix).append("rewriteTransaction.graphRewriteImplemented=false\n");
            builder.append(prefix).append("rewriteTransaction.transformedIrBuilt=false\n");
            builder.append(prefix).append("rewriteTransaction.mutationAllowed=false\n");
            builder.append(prefix).append("rewriteTransaction.selectedIrReplacement=false\n");
            builder.append(prefix).append("nodeIdAllocation.count=").append(rule.nodeIdAllocationCount()).append('\n');
            builder.append(prefix).append("nodeIdAllocation.ready.count=").append(rule.nodeIdAllocationReadyCount()).append('\n');
            builder.append(prefix).append("nodeIdAllocation.blocked.count=").append(rule.nodeIdAllocationBlockedCount()).append('\n');
            builder.append(prefix).append("nodeIdAllocation.firstBlocker=").append(rule.nodeIdAllocationFirstBlocker()).append('\n');
            builder.append(prefix).append("nodeIdAllocation.allocationPreflightImplemented=true\n");
            builder.append(prefix).append("nodeIdAllocation.nodeIdsReserved=false\n");
            builder.append(prefix).append("nodeIdAllocation.nodeIdAllocatorApplied=false\n");
            builder.append(prefix).append("nodeIdAllocation.graphRewriteImplemented=false\n");
            builder.append(prefix).append("nodeIdAllocation.transformedIrBuilt=false\n");
            builder.append(prefix).append("nodeIdAllocation.mutationAllowed=false\n");
            builder.append(prefix).append("nodeIdAllocation.selectedIrReplacement=false\n");
            builder.append(prefix).append("replacementNode.count=").append(rule.replacementNodeCount()).append('\n');
            builder.append(prefix).append("replacementNode.ready.count=").append(rule.replacementNodeReadyCount()).append('\n');
            builder.append(prefix).append("replacementNode.blocked.count=").append(rule.replacementNodeBlockedCount()).append('\n');
            builder.append(prefix).append("replacementNode.firstBlocker=").append(rule.replacementNodeFirstBlocker()).append('\n');
            builder.append(prefix).append("replacementNode.replacementNodePreflightImplemented=true\n");
            builder.append(prefix).append("replacementNode.replacementNodeBuilt=false\n");
            builder.append(prefix).append("replacementNode.replacementBuilderImplemented=false\n");
            builder.append(prefix).append("replacementNode.graphRewriteImplemented=false\n");
            builder.append(prefix).append("replacementNode.transformedIrBuilt=false\n");
            builder.append(prefix).append("replacementNode.mutationAllowed=false\n");
            builder.append(prefix).append("replacementNode.selectedIrReplacement=false\n");
            builder.append(prefix).append("graphPatch.count=").append(rule.graphPatchCount()).append('\n');
            builder.append(prefix).append("graphPatch.ready.count=").append(rule.graphPatchReadyCount()).append('\n');
            builder.append(prefix).append("graphPatch.blocked.count=").append(rule.graphPatchBlockedCount()).append('\n');
            builder.append(prefix).append("graphPatch.firstBlocker=").append(rule.graphPatchFirstBlocker()).append('\n');
            builder.append(prefix).append("graphPatch.graphPatchPreflightImplemented=true\n");
            builder.append(prefix).append("graphPatch.graphPatchApplied=false\n");
            builder.append(prefix).append("graphPatch.graphRewriteImplemented=false\n");
            builder.append(prefix).append("graphPatch.transformedIrBuilt=false\n");
            builder.append(prefix).append("graphPatch.mutationAllowed=false\n");
            builder.append(prefix).append("graphPatch.selectedIrReplacement=false\n");
            builder.append(prefix).append("transformedGraph.count=").append(rule.transformedGraphCount()).append('\n');
            builder.append(prefix).append("transformedGraph.ready.count=").append(rule.transformedGraphReadyCount()).append('\n');
            builder.append(prefix).append("transformedGraph.blocked.count=").append(rule.transformedGraphBlockedCount()).append('\n');
            builder.append(prefix).append("transformedGraph.firstBlocker=").append(rule.transformedGraphFirstBlocker()).append('\n');
            builder.append(prefix).append("transformedGraph.materializationPreflightImplemented=true\n");
            builder.append(prefix).append("transformedGraph.transformedGraphBuilt=false\n");
            builder.append(prefix).append("transformedGraph.transformedIrBuilt=false\n");
            builder.append(prefix).append("transformedGraph.graphPatchApplied=false\n");
            builder.append(prefix).append("transformedGraph.graphRewriteImplemented=false\n");
            builder.append(prefix).append("transformedGraph.mutationAllowed=false\n");
            builder.append(prefix).append("transformedGraph.selectedIrReplacement=false\n");
            builder.append(prefix).append("irArtifactEnvelope.count=").append(rule.irArtifactEnvelopeCount()).append('\n');
            builder.append(prefix).append("irArtifactEnvelope.ready.count=").append(rule.irArtifactEnvelopeReadyCount()).append('\n');
            builder.append(prefix).append("irArtifactEnvelope.blocked.count=").append(rule.irArtifactEnvelopeBlockedCount()).append('\n');
            builder.append(prefix).append("irArtifactEnvelope.firstBlocker=").append(rule.irArtifactEnvelopeFirstBlocker()).append('\n');
            builder.append(prefix).append("irArtifactEnvelope.artifactEnvelopePreflightImplemented=true\n");
            builder.append(prefix).append("irArtifactEnvelope.artifactEnvelopeBuilt=false\n");
            builder.append(prefix).append("irArtifactEnvelope.optimizedArtifactBuilt=false\n");
            builder.append(prefix).append("irArtifactEnvelope.transformedGraphBuilt=false\n");
            builder.append(prefix).append("irArtifactEnvelope.transformedIrBuilt=false\n");
            builder.append(prefix).append("irArtifactEnvelope.graphPatchApplied=false\n");
            builder.append(prefix).append("irArtifactEnvelope.graphRewriteImplemented=false\n");
            builder.append(prefix).append("irArtifactEnvelope.mutationAllowed=false\n");
            builder.append(prefix).append("irArtifactEnvelope.selectedIrReplacement=false\n");
            appendArtifactProofBindingProperties(
                    builder,
                    prefix,
                    rule.artifactProofBindingCount(),
                    rule.artifactProofBindingReadyCount(),
                    rule.artifactProofBindingBlockedCount(),
                    rule.artifactProofBindingFirstBlocker()
            );
            appendArtifactSelectionProperties(
                    builder,
                    prefix,
                    rule.artifactSelectionCount(),
                    rule.artifactSelectionReadyCount(),
                    rule.artifactSelectionBlockedCount(),
                    rule.artifactSelectionFirstBlocker()
            );
            builder.append(prefix).append("rewriteSketch.count=").append(rule.rewriteSketchCount()).append('\n');
            builder.append(prefix).append("rewriteSketch.ready.count=").append(rule.rewriteSketchReadyCount()).append('\n');
            builder.append(prefix).append("rewriteSketch.blocked.count=").append(rule.rewriteSketchBlockedCount()).append('\n');
            builder.append(prefix).append("rewriteSketch.firstBlocker=").append(rule.rewriteSketchFirstBlocker()).append('\n');
            builder.append(prefix).append("rewriteSketch.rewriteBuilderImplemented=false\n");
            builder.append(prefix).append("rewriteSketch.mutationAllowed=false\n");
            builder.append(prefix).append("rewriteSketch.selectedIrReplacement=false\n");
            builder.append(prefix).append("rewriteSelection.status=").append(rule.rewriteSelectionStatus()).append('\n');
            builder.append(prefix).append("rewriteSelection.firstBlocker=").append(rule.rewriteSelectionFirstBlocker()).append('\n');
            builder.append(prefix).append("rewriteSelection.selectionApplied=false\n");
            builder.append(prefix).append("rewriteSelection.selectedIrReplacement=false\n");
            builder.append(prefix).append("rewriteProof.status=").append(rule.rewriteProofStatus()).append('\n');
            builder.append(prefix).append("rewriteProof.firstBlocker=").append(rule.rewriteProofFirstBlocker()).append('\n');
            builder.append(prefix).append("rewriteProof.proofAccepted=false\n");
            builder.append(prefix).append("rewriteProof.runtimeEquivalencePayload.complete=false\n");
            builder.append(prefix).append("rewriteProof.rollbackClean=false\n");
            builder.append(prefix).append("rewriteProof.selectedIrReplacement=false\n");
            builder.append(prefix).append("rewriteReviewPackage.status=").append(rule.rewriteReviewPackageStatus()).append('\n');
            builder.append(prefix).append("rewriteReviewPackage.firstBlocker=").append(rule.rewriteReviewPackageFirstBlocker()).append('\n');
            builder.append(prefix).append("rewriteReviewPackage.complete=false\n");
            builder.append(prefix).append("rewriteReviewPackage.selectedIrReplacement=false\n");
            builder.append(prefix).append("firstBlocker=").append(rule.firstBlocker()).append('\n');
            index++;
        }
        return builder.toString();
    }

    private static int parseInt(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static boolean hasProofArtifact(GpuRuntimeIrOptimizationPassReport passReport) {
        GpuRuntimeIrOptimizationProofArtifact proofArtifact = passReport.proofArtifact();
        return proofArtifact != null
                && (!"none".equals(proofArtifact.source()) || !proofArtifact.fields().isEmpty());
    }

    private static Map<String, OptimizerFamilyEvidence> optimizerFamilies(GpuRuntimeIrOptimizationReport report) {
        Map<String, OptimizerFamilyEvidence> families = new LinkedHashMap<>();
        for (GpuRuntimeIrOptimizationPassReport passReport : report.passReports()) {
            if (passReport.analysisOnly()) {
                continue;
            }
            String familyName = optimizerFamilyName(passReport);
            OptimizerFamilyEvidence existing = families.getOrDefault(familyName, OptimizerFamilyEvidence.empty(familyName));
            families.put(familyName, existing.add(passReport));
        }
        return families;
    }

    private static String optimizerFamilyName(GpuRuntimeIrOptimizationPassReport passReport) {
        GpuRuntimeIrOptimizationProofArtifact proofArtifact = passReport.proofArtifact();
        String explicitFamily = proofArtifact == null ? "" : proofArtifact.fields().getOrDefault("optimizerFamily", "");
        if (!explicitFamily.isBlank()) {
            return explicitFamily;
        }
        String optimizerVersion = passReport.optimizerVersion();
        int separator = optimizerVersion.indexOf(':');
        return separator >= 0 && separator < optimizerVersion.length() - 1
                ? optimizerVersion.substring(separator + 1)
                : optimizerVersion;
    }

    private static int promotionReadyFamilyCount(Map<String, OptimizerFamilyEvidence> families) {
        int count = 0;
        for (OptimizerFamilyEvidence family : families.values()) {
            if (family.promotionReady()) {
                count++;
            }
        }
        return count;
    }

    private static String formatOptimizerFamilySummary(Map<String, OptimizerFamilyEvidence> families) {
        if (families.isEmpty()) {
            return "none";
        }
        StringBuilder builder = new StringBuilder();
        for (OptimizerFamilyEvidence family : families.values()) {
            if (!builder.isEmpty()) {
                builder.append(", ");
            }
            builder.append(family.name())
                    .append("[passes=")
                    .append(family.passCount())
                    .append(", acceptedProof=")
                    .append(family.acceptedProofCount())
                    .append(", blockingProof=")
                    .append(family.blockingProofCount())
                    .append(", rolledBack=")
                    .append(family.rolledBackCount())
                    .append(", failed=")
                    .append(family.failedCount())
                    .append(", promotionReady=")
                    .append(family.promotionReady())
                    .append(']');
        }
        return builder.toString();
    }

    private static boolean isAcceptedVerdict(String verdict) {
        String normalized = verdict == null ? "" : verdict.toLowerCase(java.util.Locale.ROOT);
        return normalized.contains("accepted") || normalized.contains("passed") || normalized.contains("ready");
    }

    private static boolean isBlockingVerdict(String verdict) {
        String normalized = verdict == null ? "" : verdict.toLowerCase(java.util.Locale.ROOT);
        return normalized.contains("reject")
                || normalized.contains("block")
                || normalized.contains("fail")
                || normalized.contains("invalid");
    }

    private record OptimizerFamilyEvidence(
            String name,
            int passCount,
            int acceptedProofCount,
            int blockingProofCount,
            int rolledBackCount,
            int failedCount
    ) {

        private static OptimizerFamilyEvidence empty(String name) {
            return new OptimizerFamilyEvidence(name, 0, 0, 0, 0, 0);
        }

        private OptimizerFamilyEvidence add(GpuRuntimeIrOptimizationPassReport passReport) {
            boolean hasProofArtifact = hasProofArtifact(passReport);
            return new OptimizerFamilyEvidence(
                    name,
                    passCount + 1,
                    acceptedProofCount + (hasProofArtifact && isAcceptedVerdict(passReport.proofArtifact().verdict()) ? 1 : 0),
                    blockingProofCount + (hasProofArtifact && isBlockingVerdict(passReport.proofArtifact().verdict()) ? 1 : 0),
                    rolledBackCount + (passReport.outcome() == GpuRuntimeIrOptimizationOutcome.ROLLED_BACK ? 1 : 0),
                    failedCount + (passReport.outcome() == GpuRuntimeIrOptimizationOutcome.FAILED ? 1 : 0)
            );
        }

        private boolean promotionReady() {
            return acceptedProofCount > 0 && blockingProofCount == 0 && rolledBackCount == 0 && failedCount == 0;
        }
    }

    private record ProofFieldSummary(
            int count,
            int readyCount,
            int blockedCount,
            String firstBlocker
    ) {
    }

    private record OptimizerRuleEvidence(
            String id,
            String version,
            String extensionId,
            String extensionVersion,
            String proofStatus,
            int candidateCount,
            int proposalCount,
            int appliedCount,
            int skippedCount,
            int blockedCount,
            boolean mutationProposed,
            int replacementPlanCount,
            int replacementPlanCompleteCount,
            int replacementPlanPartialCount,
            int replacementPlanValidationCount,
            int replacementPlanValidationValidCount,
            int replacementPlanValidationInvalidCount,
            String replacementPlanValidationFirstBlocker,
            int rewriteVisitorCount,
            int rewriteVisitorReadyCount,
            int rewriteVisitorBlockedCount,
            String rewriteVisitorFirstBlocker,
            int replacementBlueprintCount,
            int replacementBlueprintReadyCount,
            int replacementBlueprintBlockedCount,
            String replacementBlueprintFirstBlocker,
            int rewriteTransactionCount,
            int rewriteTransactionReadyCount,
            int rewriteTransactionBlockedCount,
            String rewriteTransactionFirstBlocker,
            int nodeIdAllocationCount,
            int nodeIdAllocationReadyCount,
            int nodeIdAllocationBlockedCount,
            String nodeIdAllocationFirstBlocker,
            int replacementNodeCount,
            int replacementNodeReadyCount,
            int replacementNodeBlockedCount,
            String replacementNodeFirstBlocker,
            int graphPatchCount,
            int graphPatchReadyCount,
            int graphPatchBlockedCount,
            String graphPatchFirstBlocker,
            int transformedGraphCount,
            int transformedGraphReadyCount,
            int transformedGraphBlockedCount,
            String transformedGraphFirstBlocker,
            int irArtifactEnvelopeCount,
            int irArtifactEnvelopeReadyCount,
            int irArtifactEnvelopeBlockedCount,
            String irArtifactEnvelopeFirstBlocker,
            int artifactProofBindingCount,
            int artifactProofBindingReadyCount,
            int artifactProofBindingBlockedCount,
            String artifactProofBindingFirstBlocker,
            int artifactSelectionCount,
            int artifactSelectionReadyCount,
            int artifactSelectionBlockedCount,
            String artifactSelectionFirstBlocker,
            int rewriteSketchCount,
            int rewriteSketchReadyCount,
            int rewriteSketchBlockedCount,
            String rewriteSketchFirstBlocker,
            String rewriteSelectionStatus,
            String rewriteSelectionFirstBlocker,
            String rewriteProofStatus,
            String rewriteProofFirstBlocker,
            String rewriteReviewPackageStatus,
            String rewriteReviewPackageFirstBlocker,
            String firstBlocker
    ) {

        private static OptimizerRuleEvidence empty(String id) {
            return new OptimizerRuleEvidence(
                    id,
                    "unknown",
                    "unknown",
                    "unknown",
                    "unknown",
                    0,
                    0,
                    0,
                    0,
                    0,
                    false,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    "none",
                    0,
                    0,
                    0,
                    "none",
                    0,
                    0,
                    0,
                    "none",
                    0,
                    0,
                    0,
                    "none",
                    0,
                    0,
                    0,
                    "none",
                    0,
                    0,
                    0,
                    "none",
                    0,
                    0,
                    0,
                    "none",
                    0,
                    0,
                    0,
                    "none",
                    0,
                    0,
                    0,
                    "none",
                    0,
                    0,
                    0,
                    "none",
                    0,
                    0,
                    0,
                    "none",
                    0,
                    0,
                    0,
                    "none",
                    "not-required",
                    "no-rewrite-sketches",
                    "not-required",
                    "no-proof-candidates",
                    "not-required",
                    "no-review-candidates",
                    "none"
            );
        }

        private OptimizerRuleEvidence add(Map<String, String> fields, String prefix) {
            String blocker = firstBlocker;
            String validationBlocker = firstNonNone(replacementPlanValidationFirstBlocker, fields.getOrDefault(prefix + ".replacementPlan.validation.firstBlocker", "none"));
            String sketchBlocker = firstNonNone(rewriteSketchFirstBlocker, fields.getOrDefault(prefix + ".rewriteSketch.firstBlocker", "none"));
            String visitorBlocker = firstNonNone(rewriteVisitorFirstBlocker, fields.getOrDefault(prefix + ".rewriteVisitor.firstBlocker", "none"));
            String blueprintBlocker = firstNonNone(replacementBlueprintFirstBlocker, fields.getOrDefault(prefix + ".replacementBlueprint.firstBlocker", "none"));
            String transactionBlocker = firstNonNone(rewriteTransactionFirstBlocker, fields.getOrDefault(prefix + ".rewriteTransaction.firstBlocker", "none"));
            String allocationBlocker = firstNonNone(nodeIdAllocationFirstBlocker, fields.getOrDefault(prefix + ".nodeIdAllocation.firstBlocker", "none"));
            String replacementNodeBlocker = firstNonNone(replacementNodeFirstBlocker, fields.getOrDefault(prefix + ".replacementNode.firstBlocker", "none"));
            String graphPatchBlocker = firstNonNone(graphPatchFirstBlocker, fields.getOrDefault(prefix + ".graphPatch.firstBlocker", "none"));
            String transformedGraphBlocker = firstNonNone(transformedGraphFirstBlocker, fields.getOrDefault(prefix + ".transformedGraph.firstBlocker", "none"));
            String irArtifactEnvelopeBlocker = firstNonNone(irArtifactEnvelopeFirstBlocker, fields.getOrDefault(prefix + ".irArtifactEnvelope.firstBlocker", "none"));
            String artifactProofBindingBlocker = firstNonNone(artifactProofBindingFirstBlocker, fields.getOrDefault(prefix + ".artifactProofBinding.firstBlocker", "none"));
            String artifactSelectionBlocker = firstNonNone(artifactSelectionFirstBlocker, fields.getOrDefault(prefix + ".artifactSelection.firstBlocker", "none"));
            String selectionStatus = firstNonDefault(
                    rewriteSelectionStatus,
                    fields.getOrDefault(prefix + ".rewriteSelection.status", "not-required"),
                    "not-required"
            );
            String selectionBlocker = firstNonDefault(
                    rewriteSelectionFirstBlocker,
                    fields.getOrDefault(prefix + ".rewriteSelection.firstBlocker", "no-rewrite-sketches"),
                    "no-rewrite-sketches"
            );
            String proofStatus = firstNonDefault(
                    rewriteProofStatus,
                    fields.getOrDefault(prefix + ".rewriteProof.status", "not-required"),
                    "not-required"
            );
            String proofBlocker = firstNonDefault(
                    rewriteProofFirstBlocker,
                    fields.getOrDefault(prefix + ".rewriteProof.firstBlocker", "no-proof-candidates"),
                    "no-proof-candidates"
            );
            String reviewPackageStatus = firstNonDefault(
                    rewriteReviewPackageStatus,
                    fields.getOrDefault(prefix + ".rewriteReviewPackage.status", "not-required"),
                    "not-required"
            );
            String reviewPackageBlocker = firstNonDefault(
                    rewriteReviewPackageFirstBlocker,
                    fields.getOrDefault(prefix + ".rewriteReviewPackage.firstBlocker", "no-review-candidates"),
                    "no-review-candidates"
            );
            String nextBlocker = fields.getOrDefault(
                    prefix + ".firstBlocker",
                    fields.getOrDefault(prefix + ".replacementPlan.firstBlocker", "none")
            );
            if ("none".equals(blocker) && !nextBlocker.isBlank() && !"none".equals(nextBlocker)) {
                blocker = nextBlocker;
            }
            return new OptimizerRuleEvidence(
                    id,
                    firstKnown(version, fields.getOrDefault(prefix + ".version", "unknown")),
                    firstKnown(extensionId, fields.getOrDefault(prefix + ".extensionId", "unknown")),
                    firstKnown(extensionVersion, fields.getOrDefault(prefix + ".extensionVersion", "unknown")),
                    firstKnown(proofStatus, fields.getOrDefault(prefix + ".proofStatus", "unknown")),
                    candidateCount + parseInt(fields.get(prefix + ".candidate.count")),
                    proposalCount + parseInt(fields.get(prefix + ".proposal.count")),
                    appliedCount + parseInt(fields.get(prefix + ".applied.count")),
                    skippedCount + parseInt(fields.get(prefix + ".skipped.count")),
                    blockedCount + parseInt(fields.get(prefix + ".blocked.count")),
                    mutationProposed || Boolean.parseBoolean(fields.getOrDefault(prefix + ".mutationProposed", "false")),
                    replacementPlanCount + parseInt(fields.get(prefix + ".replacementPlan.count")),
                    replacementPlanCompleteCount + parseInt(fields.get(prefix + ".replacementPlan.complete.count")),
                    replacementPlanPartialCount + parseInt(fields.get(prefix + ".replacementPlan.partial.count")),
                    replacementPlanValidationCount + parseInt(fields.get(prefix + ".replacementPlan.validation.count")),
                    replacementPlanValidationValidCount + parseInt(fields.get(prefix + ".replacementPlan.validation.valid.count")),
                    replacementPlanValidationInvalidCount + parseInt(fields.get(prefix + ".replacementPlan.validation.invalid.count")),
                    validationBlocker,
                    rewriteVisitorCount + parseInt(fields.get(prefix + ".rewriteVisitor.count")),
                    rewriteVisitorReadyCount + parseInt(fields.get(prefix + ".rewriteVisitor.ready.count")),
                    rewriteVisitorBlockedCount + parseInt(fields.get(prefix + ".rewriteVisitor.blocked.count")),
                    visitorBlocker,
                    replacementBlueprintCount + parseInt(fields.get(prefix + ".replacementBlueprint.count")),
                    replacementBlueprintReadyCount + parseInt(fields.get(prefix + ".replacementBlueprint.ready.count")),
                    replacementBlueprintBlockedCount + parseInt(fields.get(prefix + ".replacementBlueprint.blocked.count")),
                    blueprintBlocker,
                    rewriteTransactionCount + parseInt(fields.get(prefix + ".rewriteTransaction.count")),
                    rewriteTransactionReadyCount + parseInt(fields.get(prefix + ".rewriteTransaction.ready.count")),
                    rewriteTransactionBlockedCount + parseInt(fields.get(prefix + ".rewriteTransaction.blocked.count")),
                    transactionBlocker,
                    nodeIdAllocationCount + parseInt(fields.get(prefix + ".nodeIdAllocation.count")),
                    nodeIdAllocationReadyCount + parseInt(fields.get(prefix + ".nodeIdAllocation.ready.count")),
                    nodeIdAllocationBlockedCount + parseInt(fields.get(prefix + ".nodeIdAllocation.blocked.count")),
                    allocationBlocker,
                    replacementNodeCount + parseInt(fields.get(prefix + ".replacementNode.count")),
                    replacementNodeReadyCount + parseInt(fields.get(prefix + ".replacementNode.ready.count")),
                    replacementNodeBlockedCount + parseInt(fields.get(prefix + ".replacementNode.blocked.count")),
                    replacementNodeBlocker,
                    graphPatchCount + parseInt(fields.get(prefix + ".graphPatch.count")),
                    graphPatchReadyCount + parseInt(fields.get(prefix + ".graphPatch.ready.count")),
                    graphPatchBlockedCount + parseInt(fields.get(prefix + ".graphPatch.blocked.count")),
                    graphPatchBlocker,
                    transformedGraphCount + parseInt(fields.get(prefix + ".transformedGraph.count")),
                    transformedGraphReadyCount + parseInt(fields.get(prefix + ".transformedGraph.ready.count")),
                    transformedGraphBlockedCount + parseInt(fields.get(prefix + ".transformedGraph.blocked.count")),
                    transformedGraphBlocker,
                    irArtifactEnvelopeCount + parseInt(fields.get(prefix + ".irArtifactEnvelope.count")),
                    irArtifactEnvelopeReadyCount + parseInt(fields.get(prefix + ".irArtifactEnvelope.ready.count")),
                    irArtifactEnvelopeBlockedCount + parseInt(fields.get(prefix + ".irArtifactEnvelope.blocked.count")),
                    irArtifactEnvelopeBlocker,
                    artifactProofBindingCount + parseInt(fields.get(prefix + ".artifactProofBinding.count")),
                    artifactProofBindingReadyCount + parseInt(fields.get(prefix + ".artifactProofBinding.ready.count")),
                    artifactProofBindingBlockedCount + parseInt(fields.get(prefix + ".artifactProofBinding.blocked.count")),
                    artifactProofBindingBlocker,
                    artifactSelectionCount + parseInt(fields.get(prefix + ".artifactSelection.count")),
                    artifactSelectionReadyCount + parseInt(fields.get(prefix + ".artifactSelection.ready.count")),
                    artifactSelectionBlockedCount + parseInt(fields.get(prefix + ".artifactSelection.blocked.count")),
                    artifactSelectionBlocker,
                    rewriteSketchCount + parseInt(fields.get(prefix + ".rewriteSketch.count")),
                    rewriteSketchReadyCount + parseInt(fields.get(prefix + ".rewriteSketch.ready.count")),
                    rewriteSketchBlockedCount + parseInt(fields.get(prefix + ".rewriteSketch.blocked.count")),
                    sketchBlocker,
                    selectionStatus,
                    selectionBlocker,
                    proofStatus,
                    proofBlocker,
                    reviewPackageStatus,
                    reviewPackageBlocker,
                    blocker
            );
        }

        private static String firstNonDefault(String current, String next, String defaultValue) {
            if (current != null && !current.isBlank() && !defaultValue.equals(current) && !"unknown".equals(current)) {
                return current;
            }
            if (next != null && !next.isBlank() && !defaultValue.equals(next) && !"unknown".equals(next)) {
                return next;
            }
            return defaultValue;
        }

        private static String firstNonNone(String current, String next) {
            return firstNonDefault(current, next, "none");
        }

        private static String firstKnown(String current, String next) {
            if (current != null && !current.isBlank() && !"unknown".equals(current)) {
                return current;
            }
            return next == null || next.isBlank() ? "unknown" : next;
        }
    }
}
