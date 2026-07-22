package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.runtime.validation.GpuBackendSourcePromotionWorkloadSummaryCli;
import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuBackendSourcePromotionWorkloadSummaryTest {

    @Test
    void summarizesIndexedSourceSwitchingEvidenceForHistoryAndReports() {
        Properties properties = new Properties();
        properties.setProperty("status", "blocked");
        properties.setProperty("reviewReady", "false");
        properties.setProperty("sourceParityMatched", "true");
        properties.setProperty("runtimeEquivalencePassed", "false");
        properties.setProperty("realWorkloadEvidence", "runtime-snapshot");
        properties.setProperty("productionSourceSwitching", "false");
        properties.setProperty("productionPromotionOperatorAccepted.count", "1");
        properties.setProperty("productionPromotionOperatorAccepted.all", "true");
        properties.setProperty("kernel.count", "1");
        properties.setProperty("kernel.0.sourceKernelResource", "inline://integration/perlin-kernel.cl");
        properties.setProperty("kernel.0.diagnostic.count", "1");
        properties.setProperty("kernel.0.sourceSwitching.decision", "reject-production-irgpu-source");
        properties.setProperty("kernel.0.sourceSwitching.productionPromotionOperatorAccepted", "true");
        properties.setProperty(
                "kernel.0.sourceSwitching.sourcePromotionFirstBlocker",
                "runtime equivalence must execute and pass before backend source promotion"
        );
        properties.setProperty("kernel.0.runtimeIrHandoff.selectedStage", "original");
        properties.setProperty("kernel.0.runtimeOptimizerDrift.status", "recorded");
        properties.setProperty("kernel.0.runtimeOptimizerDrift.pass.count", "3");
        properties.setProperty("kernel.0.runtimeOptimizerDrift.pass.rolledBack.count", "0");
        properties.setProperty("kernel.0.runtimeOptimizerDrift.proofArtifact.count", "2");
        properties.setProperty("kernel.0.runtimeOptimizerDrift.proofArtifact.accepted.count", "1");
        properties.setProperty("kernel.0.runtimeOptimizerDrift.proofArtifact.blocking.count", "1");
        properties.setProperty("kernel.0.runtimeOptimizerDrift.replacementPlan.complete.count", "3");
        properties.setProperty("kernel.0.runtimeOptimizerDrift.replacementPlan.partial.count", "1");
        properties.setProperty("kernel.0.runtimeOptimizerDrift.replacementPlan.firstBlocker", "multiply-operands-incomplete");
        properties.setProperty("kernel.0.runtimeOptimizerDrift.replacementPlan.validation.count", "4");
        properties.setProperty("kernel.0.runtimeOptimizerDrift.replacementPlan.validation.valid.count", "3");
        properties.setProperty("kernel.0.runtimeOptimizerDrift.replacementPlan.validation.invalid.count", "1");
        properties.setProperty("kernel.0.runtimeOptimizerDrift.replacementPlan.validation.firstBlocker", "replacement-plan-root-missing");
        properties.setProperty("kernel.0.runtimeOptimizerDrift.rewriteSketch.count", "4");
        properties.setProperty("kernel.0.runtimeOptimizerDrift.rewriteSketch.ready.count", "3");
        properties.setProperty("kernel.0.runtimeOptimizerDrift.rewriteSketch.blocked.count", "1");
        properties.setProperty("kernel.0.runtimeOptimizerDrift.rewriteSketch.firstBlocker", "replacement-plan-root-missing");
        properties.setProperty("kernel.0.runtimeOptimizerDrift.rewriteSketch.conflict.count", "1");
        properties.setProperty("kernel.0.runtimeOptimizerDrift.rewriteSketch.conflict.firstBlocker", "rewrite-sketch-covered-node-overlap");
        properties.setProperty("kernel.0.runtimeOptimizerDrift.rewriteSketch.conflict.conflictResolutionImplemented", "false");
        properties.setProperty("kernel.0.runtimeOptimizerDrift.rewriteSketch.conflict.selectionApplied", "false");
        properties.setProperty("kernel.0.runtimeOptimizerDrift.rewriteSketch.rewriteBuilderImplemented", "false");
        properties.setProperty("kernel.0.runtimeOptimizerDrift.rewriteSketch.selectedIrReplacement", "false");
        properties.setProperty("kernel.0.runtimeOptimizerDrift.optimizerFamily.count", "2");
        properties.setProperty("kernel.0.runtimeOptimizerDrift.optimizerFamily.promotionReady.count", "1");
        properties.setProperty(
                "kernel.0.runtimeOptimizerDrift.optimizerFamily.summary",
                "cse[passes=1, acceptedProof=1, blockingProof=0, rolledBack=0, failed=0, promotionReady=true], vector[passes=1, acceptedProof=0, blockingProof=1, rolledBack=0, failed=0, promotionReady=false]"
        );
        properties.setProperty("kernel.0.runtimeOptimizerDrift.fallbackDecision", "production-ir-gate-blocked");
        properties.setProperty("kernel.0.runtimeProductionMutationSafety.productionMutationEnabled", "false");
        properties.setProperty("kernel.0.i3Readiness.sourceReady", "false");
        properties.setProperty("kernel.0.i3Readiness.status", "blocked");
        properties.setProperty("kernel.0.blockerFamily.count", "1");
        properties.setProperty("kernel.0.blockerFamily.0.name", "source-parity");
        properties.setProperty("kernel.0.blockerFamily.0.count", "1");
        properties.setProperty("sourceSwitching.sourcePromotionFirstBlocker.count", "1");
        properties.setProperty(
                "sourceSwitching.sourcePromotionFirstBlocker.0.name",
                "runtime equivalence must execute and pass before backend source promotion"
        );
        properties.setProperty("sourceSwitching.sourcePromotionFirstBlocker.0.count", "1");
        properties.setProperty("sourceSwitching.sourcePromotionFirstBlockerFamily.count", "1");
        properties.setProperty("sourceSwitching.sourcePromotionFirstBlockerFamily.0.name", "runtime-equivalence");
        properties.setProperty("sourceSwitching.sourcePromotionFirstBlockerFamily.0.count", "1");
        properties.setProperty("runtimeExtensionParticipation.recordedKernel.count", "1");
        properties.setProperty("runtimeExtensionParticipation.entry.count", "3");
        properties.setProperty("runtimeExtensionParticipation.failedContinued.count", "1");
        properties.setProperty("runtimeExtensionParticipation.failedClosed.count", "0");
        properties.setProperty("runtimeExtensionParticipation.source.count", "2");
        properties.setProperty("runtimeExtensionParticipation.source.0.name", "original-irgpu:ir-validation");
        properties.setProperty("runtimeExtensionParticipation.source.0.count", "1");
        properties.setProperty("runtimeExtensionParticipation.source.1.name", "backend-compiler-feedback");
        properties.setProperty("runtimeExtensionParticipation.source.1.count", "2");
        properties.setProperty("kernel.0.runtimeExtensionParticipation.status", "recorded");
        properties.setProperty("kernel.0.runtimeExtensionParticipation.entry.count", "3");
        properties.setProperty("kernel.0.runtimeExtensionParticipation.failedContinued.count", "1");
        properties.setProperty("kernel.0.runtimeExtensionParticipation.failedClosed.count", "0");

        GpuBackendSourcePromotionWorkloadSummary summary =
                GpuBackendSourcePromotionWorkloadSummary.fromProperties(properties);

        assertEquals("reject-production-irgpu-source=1", summary.sourceSwitchingDecisions());
        assertEquals(
                "runtime equivalence must execute and pass before backend source promotion=1",
                summary.sourcePromotionFirstBlockers()
        );
        assertEquals("runtime-equivalence=1", summary.sourcePromotionFirstBlockerFamilies());
        assertEquals(2, summary.optimizerProofArtifactCount());
        assertEquals(1, summary.optimizerAcceptedProofArtifactCount());
        assertEquals(1, summary.optimizerBlockingProofArtifactCount());
        assertEquals(3, summary.optimizerReplacementPlanCompleteCount());
        assertEquals(1, summary.optimizerReplacementPlanPartialCount());
        assertEquals("multiply-operands-incomplete=1", summary.optimizerReplacementPlanFirstBlockers());
        assertEquals(4, summary.optimizerReplacementPlanValidationCount());
        assertEquals(3, summary.optimizerReplacementPlanValidationValidCount());
        assertEquals(1, summary.optimizerReplacementPlanValidationInvalidCount());
        assertEquals("replacement-plan-root-missing=1", summary.optimizerReplacementPlanValidationFirstBlockers());
        assertEquals(4, summary.optimizerRewriteSketchCount());
        assertEquals(3, summary.optimizerRewriteSketchReadyCount());
        assertEquals(1, summary.optimizerRewriteSketchBlockedCount());
        assertEquals("replacement-plan-root-missing=1", summary.optimizerRewriteSketchFirstBlockers());
        assertEquals(1, summary.optimizerRewriteSketchConflictCount());
        assertEquals("rewrite-sketch-covered-node-overlap=1", summary.optimizerRewriteSketchConflictFirstBlockers());
        assertEquals(2, summary.optimizerFamilyCount());
        assertEquals(1, summary.optimizerFamilyPromotionReadyCount());
        assertEquals(
                "cse[passes=1, acceptedProof=1, blockingProof=0, rolledBack=0, failed=0, promotionReady=true], vector[passes=1, acceptedProof=0, blockingProof=1, rolledBack=0, failed=0, promotionReady=false]",
                summary.optimizerFamilySummary()
        );
        assertEquals(1, summary.productionPromotionOperatorAcceptedCount());
        assertEquals("true", summary.productionPromotionOperatorAcceptedAll());
        assertEquals(1, summary.runtimeExtensionParticipationRecordedKernelCount());
        assertEquals(3, summary.runtimeExtensionParticipationEntryCount());
        assertEquals(1, summary.runtimeExtensionParticipationFailedContinuedCount());
        assertEquals(0, summary.runtimeExtensionParticipationFailedClosedCount());
        assertEquals(
                "original-irgpu:ir-validation=1, backend-compiler-feedback=2",
                summary.runtimeExtensionParticipationSources()
        );
        assertTrue(summary.historyStatus().contains("gateStatus=blocked"));
        assertTrue(summary.historyStatus().contains("realWorkloadEvidence=runtime-snapshot"));
        assertTrue(summary.historyStatus().contains("productionPromotionOperatorAccepted=1/1"));
        assertTrue(summary.historyStatus().contains("productionPromotionOperatorAcceptedAll=true"));
        assertTrue(summary.historyStatus().contains("runtimeExtensionParticipation=recordedKernels=1/executions=3/failedContinued=1/failedClosed=0/sources=original-irgpu:ir-validation=1, backend-compiler-feedback=2"));
        assertTrue(summary.historyStatus().contains("optimizerFamilies=2"));
        assertTrue(summary.historyStatus().contains("optimizerPromotionReadyFamilies=1"));
        assertTrue(summary.historyStatus().contains("optimizerReplacementPlans=complete=3/partial=1/validation=valid=3/total=4/invalid=1/validationFirstBlockers=replacement-plan-root-missing=1/firstBlockers=multiply-operands-incomplete=1"));
        assertTrue(summary.historyStatus().contains("optimizerRewriteSketches=ready=3/total=4/blocked=1/conflicts=1/rewriteBuilderImplemented=false/mutationAllowed=false/selectedIrReplacement=false/firstBlockers=replacement-plan-root-missing=1/conflictFirstBlockers=rewrite-sketch-covered-node-overlap=1/selectionApplied=false"));
        assertTrue(summary.historyStatus().contains("optimizerFamilySummary=cse[passes=1"));
        assertTrue(summary.historyStatus().contains("kernelCount=1"));
        assertTrue(summary.historyStatus().contains("extensionParticipation=recorded/3executions/failedContinued=1/failedClosed=0"));
        assertTrue(summary.historyStatus().contains("sourceSwitching=reject-production-irgpu-source/operatorAccepted=true"));
        assertTrue(summary.historyStatus().contains("proof=2/acceptedProof=1/blockingProof=1/replacementPlanComplete=3/replacementPlanPartial=1/replacementPlanFirstBlocker=multiply-operands-incomplete/replacementPlanValidationValid=3/replacementPlanValidationTotal=4/replacementPlanValidationInvalid=1/replacementPlanValidationFirstBlocker=replacement-plan-root-missing/rewriteSketchReady=3/rewriteSketchTotal=4/rewriteSketchBlocked=1/rewriteSketchFirstBlocker=replacement-plan-root-missing/rewriteSketchConflicts=1/rewriteSketchConflictFirstBlocker=rewrite-sketch-covered-node-overlap/rewriteSketchConflictResolutionImplemented=false/rewriteSketchSelectionApplied=false/rewriteSelectionStatus=not-required/rewriteSelectionFirstBlocker=no-rewrite-sketches/rewriteSelectionApplied=false/rewriteProofStatus=not-required/rewriteProofFirstBlocker=no-proof-candidates/rewriteProofAccepted=false/rewriteBuilderImplemented=false/selectedIrReplacement=false/rewriteReviewPackageStatus=not-required/rewriteReviewPackageFirstBlocker=no-review-candidates/rewriteReviewPackageComplete=false/optimizerRules=0/optimizerRuleDetails=none/optimizerFamilies=2/promotionReadyFamilies=1"));
        assertTrue(summary.historyStatus().contains("families=source-parity=1"));
    }

    @Test
    void fallsBackToPerKernelFirstBlockersWhenAggregateFieldsAreMissing() {
        Properties properties = new Properties();
        properties.setProperty("status", "blocked");
        properties.setProperty("kernel.count", "2");
        properties.setProperty("kernel.0.sourceSwitching.decision", "compile-irgpu-source-review");
        properties.setProperty("kernel.0.sourceSwitching.sourcePromotionFirstBlocker", "none");
        properties.setProperty("kernel.1.sourceSwitching.decision", "reject-production-irgpu-source");
        properties.setProperty(
                "kernel.1.sourceSwitching.sourcePromotionFirstBlocker",
                "reconstructed source must match descriptor source before promotion review"
        );

        GpuBackendSourcePromotionWorkloadSummary summary =
                GpuBackendSourcePromotionWorkloadSummary.fromProperties(properties);

        assertEquals("compile-irgpu-source-review=1, reject-production-irgpu-source=1", summary.sourceSwitchingDecisions());
        assertEquals(
                "reconstructed source must match descriptor source before promotion review=1",
                summary.sourcePromotionFirstBlockers()
        );
        assertEquals("source-parity=1", summary.sourcePromotionFirstBlockerFamilies());
    }

    @Test
    void summarizesPortableRuntimeBackendSourceFieldsWhenLegacySourceSwitchingFieldsAreMissing() {
        Properties properties = new Properties();
        properties.setProperty("status", "blocked");
        properties.setProperty("kernel.count", "2");
        properties.setProperty("kernel.0.runtime.backend.source.decision", "compile-irgpu-source-review");
        properties.setProperty("kernel.0.runtime.backend.source.promotionFirstBlocker", "none");
        properties.setProperty("kernel.0.runtime.backend.source.productionPromotionOperatorAccepted", "true");
        properties.setProperty("kernel.1.runtime.backend.source.decision", "reject-production-irgpu-source");
        properties.setProperty(
                "kernel.1.runtime.backend.source.promotionFirstBlocker",
                "runtime equivalence must execute and pass before backend source promotion"
        );

        GpuBackendSourcePromotionWorkloadSummary summary =
                GpuBackendSourcePromotionWorkloadSummary.fromProperties(properties);

        assertEquals("compile-irgpu-source-review=1, reject-production-irgpu-source=1", summary.sourceSwitchingDecisions());
        assertEquals(
                "runtime equivalence must execute and pass before backend source promotion=1",
                summary.sourcePromotionFirstBlockers()
        );
        assertEquals("runtime-equivalence=1", summary.sourcePromotionFirstBlockerFamilies());
        assertEquals(1, summary.productionPromotionOperatorAcceptedCount());
        assertEquals("false", summary.productionPromotionOperatorAcceptedAll());
        assertTrue(summary.historyStatus().contains("sourceSwitching=compile-irgpu-source-review=1, reject-production-irgpu-source=1"));
        assertTrue(summary.historyStatus().contains("sourceSwitching=compile-irgpu-source-review/operatorAccepted=true/sourcePromotionFirstBlocker=none"));
    }

    @Test
    void summarizesPortableAggregateSourcePromotionBlockersWhenLegacyAggregatesAreMissing() {
        Properties properties = new Properties();
        properties.setProperty("status", "blocked");
        properties.setProperty("kernel.count", "2");
        properties.setProperty("runtime.backend.source.promotionFirstBlocker.count", "1");
        properties.setProperty(
                "runtime.backend.source.promotionFirstBlocker.0.name",
                "runtime equivalence must execute and pass before backend source promotion"
        );
        properties.setProperty("runtime.backend.source.promotionFirstBlocker.0.count", "2");
        properties.setProperty("runtime.backend.source.promotionFirstBlockerFamily.count", "1");
        properties.setProperty("runtime.backend.source.promotionFirstBlockerFamily.0.name", "runtime-equivalence");
        properties.setProperty("runtime.backend.source.promotionFirstBlockerFamily.0.count", "2");

        GpuBackendSourcePromotionWorkloadSummary summary =
                GpuBackendSourcePromotionWorkloadSummary.fromProperties(properties);

        assertEquals(
                "runtime equivalence must execute and pass before backend source promotion=2",
                summary.sourcePromotionFirstBlockers()
        );
        assertEquals("runtime-equivalence=2", summary.sourcePromotionFirstBlockerFamilies());
    }

    @Test
    void summarizesPortableAggregateSourceDecisionsWhenPerKernelLegacyFieldsAreMissing() {
        Properties properties = new Properties();
        properties.setProperty("status", "blocked");
        properties.setProperty("kernel.count", "2");
        properties.setProperty("runtime.backend.source.decision.count", "2");
        properties.setProperty("runtime.backend.source.decision.0.name", "compile-irgpu-source-review");
        properties.setProperty("runtime.backend.source.decision.0.count", "1");
        properties.setProperty("runtime.backend.source.decision.1.name", "reject-production-irgpu-source");
        properties.setProperty("runtime.backend.source.decision.1.count", "1");

        GpuBackendSourcePromotionWorkloadSummary summary =
                GpuBackendSourcePromotionWorkloadSummary.fromProperties(properties);
        String formatted = GpuBackendSourcePromotionWorkloadSummaryCli.format(summary);

        assertEquals("compile-irgpu-source-review=1, reject-production-irgpu-source=1", summary.sourceSwitchingDecisions());
        assertTrue(formatted.contains("runtime.backend.source.decisions=compile-irgpu-source-review=1, reject-production-irgpu-source=1\n"));
        assertTrue(formatted.contains("sourceSwitching.decisions=compile-irgpu-source-review=1, reject-production-irgpu-source=1\n"));
    }

    @Test
    void aggregatesOptimizerFamilyReadinessAcrossMultipleKernels() {
        Properties properties = new Properties();
        properties.setProperty("status", "blocked");
        properties.setProperty("kernel.count", "2");
        properties.setProperty("kernel.0.runtimeOptimizerDrift.optimizerFamily.count", "1");
        properties.setProperty("kernel.0.runtimeOptimizerDrift.optimizerFamily.promotionReady.count", "1");
        properties.setProperty(
                "kernel.0.runtimeOptimizerDrift.optimizerFamily.summary",
                "cse[passes=1, acceptedProof=1, blockingProof=0, rolledBack=0, failed=0, promotionReady=true]"
        );
        properties.setProperty("kernel.1.runtimeOptimizerDrift.optimizerFamily.count", "1");
        properties.setProperty("kernel.1.runtimeOptimizerDrift.optimizerFamily.promotionReady.count", "0");
        properties.setProperty(
                "kernel.1.runtimeOptimizerDrift.optimizerFamily.summary",
                "cse[passes=2, acceptedProof=1, blockingProof=1, rolledBack=1, failed=0, promotionReady=false]"
        );

        GpuBackendSourcePromotionWorkloadSummary summary =
                GpuBackendSourcePromotionWorkloadSummary.fromProperties(properties);

        assertEquals(2, summary.optimizerFamilyCount());
        assertEquals(1, summary.optimizerFamilyPromotionReadyCount());
        assertEquals(
                "cse[passes=3, acceptedProof=2, blockingProof=1, rolledBack=1, failed=0, promotionReady=false]",
                summary.optimizerFamilySummary()
        );
        assertTrue(summary.historyStatus().contains("optimizerFamilies=2"));
        assertTrue(summary.historyStatus().contains("optimizerPromotionReadyFamilies=1"));
        assertTrue(summary.historyStatus().contains("optimizerFamilySummary=cse[passes=3"));
    }

    @Test
    void ignoresNoFamilyKernelsWhenAggregatingOptimizerPayloadCompleteness() {
        Properties properties = new Properties();
        properties.setProperty("status", "review-ready");
        properties.setProperty("kernel.count", "3");
        properties.setProperty("kernel.0.optimizerFamilyPayload.family.count", "1");
        properties.setProperty("kernel.0.optimizerFamilyPayload.family.complete.count", "1");
        properties.setProperty("kernel.0.optimizerFamilyPayload.family.complete.all", "true");
        properties.setProperty("kernel.1.optimizerFamilyPayload.family.count", "1");
        properties.setProperty("kernel.1.optimizerFamilyPayload.family.complete.count", "1");
        properties.setProperty("kernel.1.optimizerFamilyPayload.family.complete.all", "true");
        properties.setProperty("kernel.2.optimizerFamilyPayload.family.count", "0");
        properties.setProperty("kernel.2.optimizerFamilyPayload.family.complete.count", "0");
        properties.setProperty("kernel.2.optimizerFamilyPayload.family.complete.all", "false");

        GpuBackendSourcePromotionWorkloadSummary summary =
                GpuBackendSourcePromotionWorkloadSummary.fromProperties(properties);

        assertEquals(2, summary.optimizerFamilyPayloadCompleteCount());
        assertEquals("true", summary.optimizerFamilyPayloadCompleteAll());
    }

    @Test
    void reviewReadyHistoryKeepsRuntimeEquivalenceAndOptimizerFamilyEvidence() {
        Properties properties = new Properties();
        properties.setProperty("status", "review-ready");
        properties.setProperty("reviewReady", "true");
        properties.setProperty("sourceParityMatched", "true");
        properties.setProperty("runtimeEquivalencePassed", "true");
        properties.setProperty("realWorkloadEvidence", "runtime-snapshot");
        properties.setProperty("productionSourceSwitching", "false");
        properties.setProperty("productionPromotionOperatorAccepted.count", "0");
        properties.setProperty("productionPromotionOperatorAccepted.all", "false");
        properties.setProperty("kernel.count", "2");
        properties.setProperty("kernel.0.runtimeOptimizerDrift.optimizerFamily.count", "1");
        properties.setProperty("kernel.0.runtimeOptimizerDrift.optimizerFamily.promotionReady.count", "1");
        properties.setProperty(
                "kernel.0.runtimeOptimizerDrift.optimizerFamily.summary",
                "cse[passes=1, acceptedProof=1, blockingProof=0, rolledBack=0, failed=0, promotionReady=true]"
        );
        properties.setProperty("kernel.0.optimizerFamilyPayload.family.count", "1");
        properties.setProperty("kernel.0.optimizerFamilyPayload.family.complete.count", "1");
        properties.setProperty("kernel.0.optimizerFamilyPayload.family.complete.all", "true");
        properties.setProperty("kernel.1.runtimeOptimizerDrift.optimizerFamily.count", "0");
        properties.setProperty("kernel.1.runtimeOptimizerDrift.optimizerFamily.promotionReady.count", "0");
        properties.setProperty("kernel.1.runtimeOptimizerDrift.optimizerFamily.summary", "none");
        properties.setProperty("kernel.1.optimizerFamilyPayload.family.count", "0");
        properties.setProperty("kernel.1.optimizerFamilyPayload.family.complete.count", "0");
        properties.setProperty("kernel.1.optimizerFamilyPayload.family.complete.all", "false");

        GpuBackendSourcePromotionWorkloadSummary summary =
                GpuBackendSourcePromotionWorkloadSummary.fromProperties(properties);

        assertTrue(summary.historyStatus().contains("gateStatus=review-ready"));
        assertTrue(summary.historyStatus().contains("runtimeEquivalencePassed=true"));
        assertTrue(summary.historyStatus().contains("optimizerPromotionReadyFamilies=1"));
        assertTrue(summary.historyStatus().contains("optimizerPayloadCompleteAll=true"));
        assertTrue(summary.historyStatus().contains("unexpectedWorkloadReviewReady=true"));
    }

    @Test
    void emptyPropertiesRemainNotRecorded() {
        GpuBackendSourcePromotionWorkloadSummary summary =
                GpuBackendSourcePromotionWorkloadSummary.fromProperties(new Properties());

        assertEquals("not-recorded", summary.status());
        assertEquals("not recorded", summary.historyStatus());
        assertEquals("", summary.sourceSwitchingEvidenceText());
    }

    @Test
    void cliFormatKeepsCiFriendlyScalarFields() {
        Properties properties = new Properties();
        properties.setProperty("status", "blocked");
        properties.setProperty("kernel.count", "1");
        properties.setProperty("reviewReady", "false");
        properties.setProperty("sourceParityMatched", "true");
        properties.setProperty("runtimeEquivalencePassed", "true");
        properties.setProperty("realWorkloadEvidence", "runtime-snapshot");
        properties.setProperty("productionSourceSwitching", "false");
        properties.setProperty("productionPromotionOperatorAccepted.count", "0");
        properties.setProperty("productionPromotionOperatorAccepted.all", "false");
        properties.setProperty("kernel.0.sourceSwitching.decision", "compile-descriptor-source");
        properties.setProperty("kernel.0.sourceSwitching.productionPromotionOperatorAccepted", "false");
        properties.setProperty("kernel.0.runtimeOptimizerDrift.optimizerFamily.count", "0");
        properties.setProperty("kernel.0.runtimeOptimizerDrift.optimizerFamily.promotionReady.count", "0");
        properties.setProperty("kernel.0.runtimeOptimizerDrift.optimizerFamily.summary", "none");
        properties.setProperty("runtimeExtensionParticipation.recordedKernel.count", "1");
        properties.setProperty("runtimeExtensionParticipation.entry.count", "2");
        properties.setProperty("runtimeExtensionParticipation.failedContinued.count", "0");
        properties.setProperty("runtimeExtensionParticipation.failedClosed.count", "0");
        properties.setProperty("runtimeExtensionParticipation.source.count", "1");
        properties.setProperty("runtimeExtensionParticipation.source.0.name", "original-irgpu:ir-validation");
        properties.setProperty("runtimeExtensionParticipation.source.0.count", "2");
        properties.setProperty(
                "kernel.0.sourceSwitching.sourcePromotionFirstBlocker",
                "backend source must be reconstructed from IrGpu before promotion review"
        );

        GpuBackendSourcePromotionWorkloadSummary summary =
                GpuBackendSourcePromotionWorkloadSummary.fromProperties(properties);
        String formatted = GpuBackendSourcePromotionWorkloadSummaryCli.format(summary);

        assertTrue(formatted.contains("status=blocked\n"));
        assertTrue(formatted.contains("kernel.count=1\n"));
        assertTrue(formatted.contains("reviewReady=false\n"));
        assertTrue(formatted.contains("sourceParityMatched=true\n"));
        assertTrue(formatted.contains("runtimeEquivalencePassed=true\n"));
        assertTrue(formatted.contains("runtime.backend.source.productionPromotionOperatorAccepted.count=0\n"));
        assertTrue(formatted.contains("runtime.backend.source.productionPromotionOperatorAccepted.all=false\n"));
        assertTrue(formatted.contains("productionPromotionOperatorAccepted.count=0\n"));
        assertTrue(formatted.contains("productionPromotionOperatorAccepted.all=false\n"));
        assertTrue(formatted.contains("runtime.backend.source.decisions=compile-descriptor-source=1\n"));
        assertTrue(formatted.contains("sourceSwitching.decisions=compile-descriptor-source=1\n"));
        assertTrue(formatted.contains("runtime.backend.source.promotionFirstBlockers=backend source must be reconstructed from IrGpu before promotion review=1\n"));
        assertTrue(formatted.contains("runtime.backend.source.promotionFirstBlockerFamilies=reconstruction=1\n"));
        assertTrue(formatted.contains("runtime.backend.source.promotionFirstBlockerFamily.0.name=reconstruction\n"));
        assertTrue(formatted.contains("runtime.backend.source.promotionFirstBlockerFamily.0.count=1\n"));
        assertTrue(formatted.contains("sourcePromotionFirstBlockerFamily.0.name=reconstruction\n"));
        assertTrue(formatted.contains("sourcePromotionFirstBlockerFamily.0.count=1\n"));
        assertTrue(formatted.contains("optimizerProofArtifact.count=0\n"));
        assertTrue(formatted.contains("optimizerProofArtifact.accepted.count=0\n"));
        assertTrue(formatted.contains("optimizerProofArtifact.blocking.count=0\n"));
        assertTrue(formatted.contains("optimizerFamily.count=0\n"));
        assertTrue(formatted.contains("optimizerFamily.promotionReady.count=0\n"));
        assertTrue(formatted.contains("optimizerFamily.summary=none\n"));
        assertTrue(formatted.contains("runtimeExtensionParticipation.recordedKernel.count=1\n"));
        assertTrue(formatted.contains("runtimeExtensionParticipation.entry.count=2\n"));
        assertTrue(formatted.contains("runtimeExtensionParticipation.failedContinued.count=0\n"));
        assertTrue(formatted.contains("runtimeExtensionParticipation.failedClosed.count=0\n"));
        assertTrue(formatted.contains("runtimeExtensionParticipation.sources=original-irgpu:ir-validation=2\n"));
        assertTrue(formatted.contains("runtimeExtensionParticipation.source.0.name=original-irgpu:ir-validation\n"));
        assertTrue(formatted.contains("runtimeExtensionParticipation.source.0.count=2\n"));
        assertTrue(formatted.contains("historyStatus=not-promoted"));
    }
}
