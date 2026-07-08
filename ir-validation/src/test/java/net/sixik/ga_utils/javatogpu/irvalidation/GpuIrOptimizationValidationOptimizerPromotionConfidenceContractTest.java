package net.sixik.ga_utils.javatogpu.irvalidation;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static net.sixik.ga_utils.javatogpu.irvalidation.GpuIrOptimizationValidationRuleTestFixtures.validationReport;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuIrOptimizationValidationOptimizerPromotionConfidenceContractTest {
    @Test
    void blocksPromotionWhenProductionSwitchIsNotReady() {
        GpuIrOptimizationValidationProductionMutationSwitchContract switchContract =
                GpuIrOptimizationValidationOptimizerValidationBundle.from(validationReport("blockedPromotionKernel"))
                        .productionMutationSwitchContract();

        GpuIrOptimizationValidationOptimizerPromotionConfidenceContract contract =
                GpuIrOptimizationValidationOptimizerPromotionConfidenceContract.from(switchContract);
        Map<String, String> fields = contract.artifactFields();

        assertEquals("blockedPromotionKernel", contract.methodName());
        assertEquals("blocked/productionSwitchNotReady", contract.verdict());
        assertFalse(contract.promotionAllowed());
        assertFalse(contract.productionMutationEnabled());
        assertFalse(contract.runtimeConfidenceStable());
        assertEquals("blocked/preflightNotReady", contract.switchVerdict());
        assertEquals("productionMutationSwitchNotReady", contract.firstBlockingReason().orElseThrow());
        assertTrue(contract.blockingReasons().contains("a1A2RuntimeConfidenceNotStable"));
        assertTrue(contract.remainingWork().contains("stabilizeA1A2RuntimeEquivalenceHistory"));
        assertEquals("blocked/productionSwitchNotReady", fields.get("optimizerPromotionConfidenceVerdict"));
        assertEquals("false", fields.get("optimizerPromotionConfidencePromotionAllowed"));
        assertEquals("5", fields.get("optimizerPromotionConfidenceRequiredConfidenceEvidenceCount"));
        assertThrows(UnsupportedOperationException.class, () -> fields.put("x", "y"));
    }

    @Test
    void blocksPromotionOnRuntimeConfidenceEvenWhenSyntheticSwitchIsEnabled() {
        GpuIrOptimizationValidationProductionMutationSwitchContract switchContract = readySwitchContract();

        GpuIrOptimizationValidationOptimizerPromotionConfidenceContract contract =
                GpuIrOptimizationValidationOptimizerPromotionConfidenceContract.from(switchContract);

        assertEquals("blocked/runtimeConfidenceNotStable", contract.verdict());
        assertFalse(contract.promotionAllowed());
        assertTrue(contract.productionMutationEnabled());
        assertEquals("ready/productionMutationSwitchEnabled", contract.switchVerdict());
        assertEquals(List.of("a1A2RuntimeConfidenceNotStable"), contract.blockingReasons());
        assertEquals("stabilizeA1A2RuntimeEquivalenceHistory", contract.firstRemainingWork().orElseThrow());
        assertEquals(List.of(
                "a1A2RuntimeEquivalenceHistoryStable",
                "longRunningOperationalStressStable",
                "nvidiaInterimCoverageStable",
                "crossVendorPromotionGateDefined",
                "selectedRewriteRollbackVerified"
        ), contract.requiredConfidenceEvidence());
    }

    @Test
    void runnerCanExportOptimizerPromotionConfidenceFields() {
        GpuIrOptimizationValidationOptimizerEnablementArtifactRunner runner =
                new GpuIrOptimizationValidationOptimizerEnablementArtifactRunner();

        GpuIrOptimizationValidationOptimizerPromotionConfidenceContract contract =
                runner.runOptimizerPromotionConfidenceContract(validationReport("runnerPromotionKernel"));
        Map<String, String> fields = runner.runOptimizerPromotionConfidenceContractFields(validationReport("runnerPromotionKernel"));

        assertEquals("blocked/productionSwitchNotReady", contract.verdict());
        assertEquals("runnerPromotionKernel", fields.get("optimizerPromotionConfidenceMethod"));
        assertEquals("blocked/productionSwitchNotReady", fields.get("optimizerPromotionConfidenceVerdict"));
        assertEquals("productionMutationSwitchNotReady", fields.get("optimizerPromotionConfidenceFirstBlockingReason"));
    }

    @Test
    void rejectsInvalidInputsAndStates() {
        GpuIrOptimizationValidationOptimizerPromotionConfidenceContract contract =
                GpuIrOptimizationValidationOptimizerPromotionConfidenceContract.from(readySwitchContract());

        assertThrows(NullPointerException.class, () -> GpuIrOptimizationValidationOptimizerPromotionConfidenceContract.from(null));
        assertThrows(IllegalArgumentException.class, () -> contract.artifactFields(""));
        assertThrows(IllegalArgumentException.class, () -> new GpuIrOptimizationValidationOptimizerPromotionConfidenceContract(
                "kernel",
                "ready/selectedRewritePromotionAllowed",
                false,
                true,
                "ready/productionMutationSwitchEnabled",
                false,
                true,
                List.of("a1A2RuntimeEquivalenceHistoryStable"),
                List.of(),
                List.of()
        ));
        assertThrows(IllegalArgumentException.class, () -> new GpuIrOptimizationValidationOptimizerPromotionConfidenceContract(
                "kernel",
                "ready/selectedRewritePromotionAllowed",
                true,
                false,
                "ready/productionMutationSwitchEnabled",
                false,
                true,
                List.of("a1A2RuntimeEquivalenceHistoryStable"),
                List.of(),
                List.of()
        ));
        assertThrows(IllegalArgumentException.class, () -> new GpuIrOptimizationValidationOptimizerPromotionConfidenceContract(
                "kernel",
                "ready/selectedRewritePromotionAllowed",
                true,
                true,
                "ready/productionMutationSwitchEnabled",
                false,
                false,
                List.of("a1A2RuntimeEquivalenceHistoryStable"),
                List.of(),
                List.of()
        ));
    }

    private static GpuIrOptimizationValidationProductionMutationSwitchContract readySwitchContract() {
        return new GpuIrOptimizationValidationProductionMutationSwitchContract(
                "readyPromotionKernel",
                "ready/productionMutationSwitchEnabled",
                false,
                true,
                "ready/productionMutationEnabled",
                false,
                true,
                List.of(
                        "cpuGpuRuntimeEquivalenceStable",
                        "crossVendorRuntimeCoverageStable",
                        "rollbackStrategyDefined",
                        "productionMutationFlagDefined"
                ),
                List.of(),
                List.of()
        );
    }
}
