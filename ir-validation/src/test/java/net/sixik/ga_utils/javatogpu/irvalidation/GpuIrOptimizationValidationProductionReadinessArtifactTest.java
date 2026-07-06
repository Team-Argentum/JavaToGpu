package net.sixik.ga_utils.javatogpu.irvalidation;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static net.sixik.ga_utils.javatogpu.irvalidation.GpuIrOptimizationValidationRuleTestFixtures.validationReport;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuIrOptimizationValidationProductionReadinessArtifactTest {
    @Test
    void aggregatesBundlePreflightSwitchAndPromotionConfidenceForCi() {
        GpuIrOptimizationValidationOptimizerValidationBundle bundle =
                GpuIrOptimizationValidationOptimizerValidationBundle.from(validationReport("productionReadinessKernel"));

        GpuIrOptimizationValidationProductionReadinessArtifact artifact =
                GpuIrOptimizationValidationProductionReadinessArtifact.from(bundle);
        GpuIrOptimizationValidationProductionReadinessArtifactConsistencyReport consistency =
                artifact.consistencyReport();
        Map<String, String> fields = artifact.artifactFields();

        assertEquals("productionReadinessKernel", artifact.methodName());
        assertTrue(consistency.consistent());
        assertEquals(0, consistency.failedChecks());
        assertEquals("blocked/productionSwitchNotReady", artifact.verdict());
        assertTrue(artifact.blocked());
        assertFalse(artifact.reviewReady());
        assertFalse(artifact.productionMutationEnabled());
        assertFalse(artifact.promotionAllowed());
        assertEquals("notReady/cseBlocked", artifact.bundleVerdict());
        assertEquals("blocked/optimizerValidationBundleNotReady", artifact.preflightVerdict());
        assertEquals("blocked/preflightNotReady", artifact.switchVerdict());
        assertEquals("blocked/productionSwitchNotReady", artifact.promotionConfidenceVerdict());
        assertEquals("productionMutationSwitchNotReady", artifact.firstBlockingReason().orElseThrow());
        assertTrue(artifact.remainingWork().contains("collectPreviewCandidates"));
        assertTrue(artifact.remainingWork().contains("stabilizeA1A2RuntimeEquivalenceHistory"));
        assertEquals("productionReadinessKernel", fields.get("optimizerProductionReadinessMethod"));
        assertEquals("blocked/productionSwitchNotReady", fields.get("optimizerProductionReadinessVerdict"));
        assertEquals("true", fields.get("optimizerProductionReadinessBlocked"));
        assertEquals("true", fields.get("optimizerProductionReadinessConsistency.Consistent"));
        assertEquals("0", fields.get("optimizerProductionReadinessConsistency.FailedChecks"));
        assertEquals("productionMutationSwitchNotReady", fields.get("optimizerProductionReadinessFirstBlockingReason"));
        assertEquals("blocked/preflightNotReady", fields.get("optimizerProductionSwitchVerdict"));
        assertEquals("blocked/productionSwitchNotReady", fields.get("optimizerPromotionConfidenceVerdict"));
        assertThrows(UnsupportedOperationException.class, () -> fields.put("x", "y"));
    }

    @Test
    void consistencyReportFailsClosedWhenNestedContractsDrift() {
        GpuIrOptimizationValidationOptimizerValidationBundle bundle =
                GpuIrOptimizationValidationOptimizerValidationBundle.from(validationReport("driftedProductionReadinessKernel"));
        GpuIrOptimizationValidationProductionEnablementPreflightDecision preflight =
                bundle.productionPreflightDecision();
        GpuIrOptimizationValidationProductionMutationSwitchContract switchContract =
                GpuIrOptimizationValidationProductionMutationSwitchContract.from(preflight);
        GpuIrOptimizationValidationOptimizerPromotionConfidenceContract driftedPromotion =
                new GpuIrOptimizationValidationOptimizerPromotionConfidenceContract(
                        "driftedProductionReadinessKernel",
                        "blocked/runtimeConfidenceNotStable",
                        false,
                        true,
                        "ready/productionMutationSwitchEnabled",
                        false,
                        false,
                        java.util.List.of("a1A2RuntimeEquivalenceHistoryStable"),
                        java.util.List.of("a1A2RuntimeConfidenceNotStable"),
                        java.util.List.of("stabilizeA1A2RuntimeEquivalenceHistory")
                );

        GpuIrOptimizationValidationProductionReadinessArtifact artifact =
                new GpuIrOptimizationValidationProductionReadinessArtifact(
                        bundle,
                        preflight,
                        switchContract,
                        driftedPromotion
                );
        GpuIrOptimizationValidationProductionReadinessArtifactConsistencyReport consistency =
                artifact.consistencyReport();
        Map<String, String> fields = artifact.artifactFields();

        assertFalse(consistency.consistent());
        assertTrue(consistency.failedCheckList().contains("promotionSwitchVerdictMatchesSwitchContract"));
        assertEquals("false", fields.get("optimizerProductionReadinessConsistency.Consistent"));
        assertTrue(fields.get("optimizerProductionReadinessConsistency.FailedCheckList")
                .contains("promotionSwitchVerdictMatchesSwitchContract"));
    }

    @Test
    void acceptanceRejectsBlockedProductionReadinessArtifact() {
        GpuIrOptimizationValidationProductionReadinessArtifact artifact =
                GpuIrOptimizationValidationProductionReadinessArtifact.from(
                        GpuIrOptimizationValidationOptimizerValidationBundle.from(
                                validationReport("blockedProductionReadinessAcceptanceKernel")
                        )
                );

        GpuIrOptimizationValidationProductionReadinessArtifactAcceptance acceptance =
                artifact.acceptanceDecision();
        Map<String, String> fields = artifact.artifactFields();

        assertFalse(acceptance.accepted());
        assertTrue(acceptance.rejected());
        assertEquals("rejected/productionReadinessBlocked", acceptance.reason());
        assertEquals("productionMutationSwitchNotReady", acceptance.firstBlockingReason());
        assertEquals("false", fields.get("optimizerProductionReadinessAcceptance.Accepted"));
        assertEquals("true", fields.get("optimizerProductionReadinessAcceptance.Rejected"));
        assertEquals(
                "rejected/productionReadinessBlocked",
                fields.get("optimizerProductionReadinessAcceptance.Reason")
        );
    }

    @Test
    void acceptanceRejectsInconsistentProductionReadinessArtifactBeforeBlockedState() {
        GpuIrOptimizationValidationOptimizerValidationBundle bundle =
                GpuIrOptimizationValidationOptimizerValidationBundle.from(validationReport("inconsistentAcceptanceKernel"));
        GpuIrOptimizationValidationProductionEnablementPreflightDecision preflight =
                bundle.productionPreflightDecision();
        GpuIrOptimizationValidationProductionMutationSwitchContract switchContract =
                GpuIrOptimizationValidationProductionMutationSwitchContract.from(preflight);
        GpuIrOptimizationValidationOptimizerPromotionConfidenceContract driftedPromotion =
                new GpuIrOptimizationValidationOptimizerPromotionConfidenceContract(
                        "inconsistentAcceptanceKernel",
                        "blocked/runtimeConfidenceNotStable",
                        false,
                        true,
                        "ready/productionMutationSwitchEnabled",
                        false,
                        false,
                        java.util.List.of("a1A2RuntimeEquivalenceHistoryStable"),
                        java.util.List.of("a1A2RuntimeConfidenceNotStable"),
                        java.util.List.of("stabilizeA1A2RuntimeEquivalenceHistory")
                );
        GpuIrOptimizationValidationProductionReadinessArtifact artifact =
                new GpuIrOptimizationValidationProductionReadinessArtifact(
                        bundle,
                        preflight,
                        switchContract,
                        driftedPromotion
                );

        GpuIrOptimizationValidationProductionReadinessArtifactAcceptance acceptance =
                artifact.acceptanceDecision();

        assertFalse(acceptance.accepted());
        assertEquals("rejected/inconsistentArtifact", acceptance.reason());
        assertEquals("promotionSwitchVerdictMatchesSwitchContract", acceptance.firstConsistencyFailedCheck());
    }

    @Test
    void runnerCanExportProductionReadinessAcceptanceFields() {
        GpuIrOptimizationValidationProductionEnablementReadinessRunner runner =
                new GpuIrOptimizationValidationProductionEnablementReadinessRunner();

        GpuIrOptimizationValidationProductionReadinessArtifactAcceptance acceptance =
                runner.runProductionReadinessArtifactAcceptance(validationReport("productionReadinessAcceptanceRunnerKernel"));
        Map<String, String> fields = runner.runProductionReadinessArtifactAcceptanceFields(
                validationReport("productionReadinessAcceptanceRunnerKernel")
        );

        assertFalse(acceptance.accepted());
        assertEquals("productionReadinessAcceptanceRunnerKernel", fields.get("optimizerProductionReadinessAcceptance.Method"));
        assertEquals("false", fields.get("optimizerProductionReadinessAcceptance.Accepted"));
        assertEquals("rejected/productionReadinessBlocked", fields.get("optimizerProductionReadinessAcceptance.Reason"));
    }

    @Test
    void runnerCanExportProductionReadinessArtifactAndFields() {
        GpuIrOptimizationValidationProductionEnablementReadinessRunner runner =
                new GpuIrOptimizationValidationProductionEnablementReadinessRunner();

        GpuIrOptimizationValidationProductionReadinessArtifact artifact =
                runner.runProductionReadinessArtifact(validationReport("productionReadinessRunnerKernel"));
        Map<String, String> fields = runner.runProductionReadinessArtifactFields(
                validationReport("productionReadinessRunnerKernel")
        );

        assertEquals("productionReadinessRunnerKernel", artifact.methodName());
        assertEquals("blocked/productionSwitchNotReady", artifact.verdict());
        assertEquals("productionReadinessRunnerKernel", fields.get("optimizerProductionReadinessMethod"));
        assertEquals("blocked/productionSwitchNotReady", fields.get("optimizerProductionReadinessVerdict"));
        assertEquals("productionMutationSwitchNotReady", fields.get("optimizerProductionReadinessFirstBlockingReason"));
    }

    @Test
    void rejectsNullInputsAndBlankPrefixes() {
        GpuIrOptimizationValidationProductionReadinessArtifact artifact =
                GpuIrOptimizationValidationProductionReadinessArtifact.from(
                        GpuIrOptimizationValidationOptimizerValidationBundle.from(validationReport("invalidProductionReadinessKernel"))
                );
        GpuIrOptimizationValidationProductionEnablementReadinessRunner runner =
                new GpuIrOptimizationValidationProductionEnablementReadinessRunner();

        assertThrows(NullPointerException.class, () -> GpuIrOptimizationValidationProductionReadinessArtifact.from(null));
        assertThrows(IllegalArgumentException.class, () -> artifact.artifactFields(""));
        assertThrows(NullPointerException.class, () -> runner.runProductionReadinessArtifact(null));
        assertThrows(NullPointerException.class, () -> runner.runProductionReadinessArtifactFields(null));
        assertThrows(NullPointerException.class, () -> GpuIrOptimizationValidationProductionReadinessArtifactAcceptance.from(null));
        assertThrows(NullPointerException.class, () -> runner.runProductionReadinessArtifactAcceptance(null));
        assertThrows(NullPointerException.class, () -> runner.runProductionReadinessArtifactAcceptanceFields(null));
    }
}
