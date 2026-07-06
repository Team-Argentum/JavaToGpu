package net.sixik.ga_utils.javatogpu.irvalidation;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static net.sixik.ga_utils.javatogpu.irvalidation.GpuIrOptimizationValidationRuleTestFixtures.validationReport;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuIrOptimizationValidationProductionMutationSwitchContractTest {
    @Test
    void blocksSwitchReviewWhenProductionPreflightIsNotReviewReady() {
        GpuIrOptimizationValidationProductionEnablementPreflightDecision preflight =
                GpuIrOptimizationValidationOptimizerValidationBundle.from(validationReport("blockedSwitchKernel"))
                        .productionPreflightDecision();

        GpuIrOptimizationValidationProductionMutationSwitchContract contract =
                GpuIrOptimizationValidationProductionMutationSwitchContract.from(preflight);
        Map<String, String> fields = contract.artifactFields();

        assertEquals("blockedSwitchKernel", contract.methodName());
        assertEquals("blocked/preflightNotReady", contract.verdict());
        assertFalse(contract.eligibleForSwitchReview());
        assertFalse(contract.productionMutationEnabled());
        assertEquals("blocked/optimizerValidationBundleNotReady", contract.preflightVerdict());
        assertEquals("productionPreflightNotReviewReady", contract.firstBlockingReason().orElseThrow());
        assertTrue(contract.remainingWork().contains("clearProductionPreflightBlocker:cseLiteralPromotionNotReady"));
        assertEquals("blocked/preflightNotReady", fields.get("optimizerProductionSwitchVerdict"));
        assertEquals("false", fields.get("optimizerProductionSwitchEligibleForSwitchReview"));
        assertEquals("false", fields.get("optimizerProductionSwitchProductionMutationEnabled"));
        assertEquals("4", fields.get("optimizerProductionSwitchRequiredEvidenceCount"));
        assertThrows(UnsupportedOperationException.class, () -> fields.put("x", "y"));
    }

    @Test
    void reportsReviewRequiredWhenPreflightIsReviewReadyButMutationIsDisabled() {
        GpuIrOptimizationValidationProductionEnablementPreflightDecision preflight = reviewReadyPreflight();

        GpuIrOptimizationValidationProductionMutationSwitchContract contract =
                GpuIrOptimizationValidationProductionMutationSwitchContract.from(preflight);

        assertEquals("reviewRequired/productionMutationDisabled", contract.verdict());
        assertTrue(contract.eligibleForSwitchReview());
        assertFalse(contract.productionMutationEnabled());
        assertEquals("reviewReady/productionMutationDisabled", contract.preflightVerdict());
        assertEquals(0, contract.blockingReasonCount());
        assertEquals("proveCpuGpuRuntimeEquivalence", contract.firstRemainingWork().orElseThrow());
        assertEquals(List.of(
                "cpuGpuRuntimeEquivalenceStable",
                "crossVendorRuntimeCoverageStable",
                "rollbackStrategyDefined",
                "productionMutationFlagDefined"
        ), contract.requiredEvidence());
    }

    @Test
    void preservesFutureReadySwitchContractWhenSyntheticMutationGateIsEnabled() {
        GpuIrOptimizationValidationProductionEnablementPreflightDecision preflight = readyPreflight();

        GpuIrOptimizationValidationProductionMutationSwitchContract contract =
                GpuIrOptimizationValidationProductionMutationSwitchContract.from(preflight);

        assertEquals("ready/productionMutationSwitchEnabled", contract.verdict());
        assertFalse(contract.eligibleForSwitchReview());
        assertTrue(contract.productionMutationEnabled());
        assertTrue(contract.preflightReadyForProductionMutation());
        assertEquals("ready/productionMutationEnabled", contract.preflightVerdict());
    }

    @Test
    void runnerCanExportProductionMutationSwitchContractFields() {
        GpuIrOptimizationValidationOptimizerEnablementArtifactRunner runner =
                new GpuIrOptimizationValidationOptimizerEnablementArtifactRunner();

        GpuIrOptimizationValidationProductionMutationSwitchContract contract =
                runner.runProductionMutationSwitchContract(validationReport("runnerSwitchKernel"));
        Map<String, String> fields = runner.runProductionMutationSwitchContractFields(validationReport("runnerSwitchKernel"));

        assertEquals("blocked/preflightNotReady", contract.verdict());
        assertEquals("runnerSwitchKernel", fields.get("optimizerProductionSwitchMethod"));
        assertEquals("blocked/preflightNotReady", fields.get("optimizerProductionSwitchVerdict"));
        assertEquals("productionPreflightNotReviewReady", fields.get("optimizerProductionSwitchFirstBlockingReason"));
    }

    @Test
    void rejectsInvalidInputsAndStates() {
        GpuIrOptimizationValidationProductionMutationSwitchContract contract =
                GpuIrOptimizationValidationProductionMutationSwitchContract.from(reviewReadyPreflight());

        assertThrows(NullPointerException.class, () -> GpuIrOptimizationValidationProductionMutationSwitchContract.from(null));
        assertThrows(IllegalArgumentException.class, () -> contract.artifactFields(""));
        assertThrows(IllegalArgumentException.class, () -> new GpuIrOptimizationValidationProductionMutationSwitchContract(
                "kernel",
                "ready/productionMutationSwitchEnabled",
                true,
                false,
                "reviewReady/productionMutationDisabled",
                true,
                false,
                List.of("cpuGpuRuntimeEquivalenceStable"),
                List.of(),
                List.of("proveCpuGpuRuntimeEquivalence")
        ));
        assertThrows(IllegalArgumentException.class, () -> new GpuIrOptimizationValidationProductionMutationSwitchContract(
                "kernel",
                "reviewRequired/productionMutationDisabled",
                true,
                false,
                "reviewReady/productionMutationDisabled",
                true,
                false,
                List.of("cpuGpuRuntimeEquivalenceStable"),
                List.of("productionPreflightNotReviewReady"),
                List.of("proveCpuGpuRuntimeEquivalence")
        ));
    }

    private static GpuIrOptimizationValidationProductionEnablementPreflightDecision reviewReadyPreflight() {
        return new GpuIrOptimizationValidationProductionEnablementPreflightDecision(
                "reviewReadySwitchKernel",
                "reviewReady/productionMutationDisabled",
                false,
                true,
                false,
                false,
                "reviewReady/productionMutationDisabled",
                "",
                "enableProductionMutationPolicy",
                "summary"
        );
    }

    private static GpuIrOptimizationValidationProductionEnablementPreflightDecision readyPreflight() {
        return new GpuIrOptimizationValidationProductionEnablementPreflightDecision(
                "readySwitchKernel",
                "ready/productionMutationEnabled",
                false,
                false,
                true,
                true,
                "readyForProductionMutation",
                "",
                "none",
                "summary"
        );
    }
}
