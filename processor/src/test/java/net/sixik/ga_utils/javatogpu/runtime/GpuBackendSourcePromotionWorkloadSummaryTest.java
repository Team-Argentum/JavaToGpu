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
        properties.setProperty("kernel.count", "1");
        properties.setProperty("kernel.0.sourceKernelResource", "inline://integration/perlin-kernel.cl");
        properties.setProperty("kernel.0.diagnostic.count", "1");
        properties.setProperty("kernel.0.sourceSwitching.decision", "reject-production-irgpu-source");
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
        assertTrue(summary.historyStatus().contains("gateStatus=blocked"));
        assertTrue(summary.historyStatus().contains("realWorkloadEvidence=runtime-snapshot"));
        assertTrue(summary.historyStatus().contains("kernelCount=1"));
        assertTrue(summary.historyStatus().contains("proof=2/acceptedProof=1/blockingProof=1"));
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
        properties.setProperty("kernel.0.sourceSwitching.decision", "compile-descriptor-source");
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
        assertTrue(formatted.contains("sourceSwitching.decisions=compile-descriptor-source=1\n"));
        assertTrue(formatted.contains("sourcePromotionFirstBlockerFamily.0.name=reconstruction\n"));
        assertTrue(formatted.contains("sourcePromotionFirstBlockerFamily.0.count=1\n"));
        assertTrue(formatted.contains("optimizerProofArtifact.count=0\n"));
        assertTrue(formatted.contains("optimizerProofArtifact.accepted.count=0\n"));
        assertTrue(formatted.contains("optimizerProofArtifact.blocking.count=0\n"));
        assertTrue(formatted.contains("historyStatus=not-promoted"));
    }
}
