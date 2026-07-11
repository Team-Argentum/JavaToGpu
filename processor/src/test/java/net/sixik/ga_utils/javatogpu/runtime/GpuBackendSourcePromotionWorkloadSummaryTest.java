package net.sixik.ga_utils.javatogpu.runtime;

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
        assertEquals(2, summary.optimizerFamilyCount());
        assertEquals(1, summary.optimizerFamilyPromotionReadyCount());
        assertEquals(
                "cse[passes=1, acceptedProof=1, blockingProof=0, rolledBack=0, failed=0, promotionReady=true], vector[passes=1, acceptedProof=0, blockingProof=1, rolledBack=0, failed=0, promotionReady=false]",
                summary.optimizerFamilySummary()
        );
        assertEquals(1, summary.productionPromotionOperatorAcceptedCount());
        assertEquals("true", summary.productionPromotionOperatorAcceptedAll());
        assertTrue(summary.historyStatus().contains("gateStatus=blocked"));
        assertTrue(summary.historyStatus().contains("realWorkloadEvidence=runtime-snapshot"));
        assertTrue(summary.historyStatus().contains("productionPromotionOperatorAccepted=1/1"));
        assertTrue(summary.historyStatus().contains("productionPromotionOperatorAcceptedAll=true"));
        assertTrue(summary.historyStatus().contains("optimizerFamilies=2"));
        assertTrue(summary.historyStatus().contains("optimizerPromotionReadyFamilies=1"));
        assertTrue(summary.historyStatus().contains("optimizerFamilySummary=cse[passes=1"));
        assertTrue(summary.historyStatus().contains("kernelCount=1"));
        assertTrue(summary.historyStatus().contains("sourceSwitching=reject-production-irgpu-source/operatorAccepted=true"));
        assertTrue(summary.historyStatus().contains("proof=2/acceptedProof=1/blockingProof=1/optimizerFamilies=2/promotionReadyFamilies=1"));
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
        assertTrue(formatted.contains("productionPromotionOperatorAccepted.count=0\n"));
        assertTrue(formatted.contains("productionPromotionOperatorAccepted.all=false\n"));
        assertTrue(formatted.contains("sourceSwitching.decisions=compile-descriptor-source=1\n"));
        assertTrue(formatted.contains("sourcePromotionFirstBlockerFamily.0.name=reconstruction\n"));
        assertTrue(formatted.contains("sourcePromotionFirstBlockerFamily.0.count=1\n"));
        assertTrue(formatted.contains("optimizerProofArtifact.count=0\n"));
        assertTrue(formatted.contains("optimizerProofArtifact.accepted.count=0\n"));
        assertTrue(formatted.contains("optimizerProofArtifact.blocking.count=0\n"));
        assertTrue(formatted.contains("optimizerFamily.count=0\n"));
        assertTrue(formatted.contains("optimizerFamily.promotionReady.count=0\n"));
        assertTrue(formatted.contains("optimizerFamily.summary=none\n"));
        assertTrue(formatted.contains("historyStatus=not-promoted"));
    }
}
