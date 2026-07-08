package net.sixik.ga_utils.javatogpu.irvalidation;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static net.sixik.ga_utils.javatogpu.irvalidation.GpuIrOptimizationValidationRuleTestFixtures.validationReport;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuIrOptimizationValidationProductionEnablementPreflightDecisionTest {
    @Test
    void reportsBlockedWhenValidationBundleGateStillHasReadinessBlockers() {
        GpuIrOptimizationValidationOptimizerValidationBundle bundle =
                GpuIrOptimizationValidationOptimizerValidationBundle.from(validationReport("blockedPreflightKernel"));

        GpuIrOptimizationValidationProductionEnablementPreflightDecision decision =
                GpuIrOptimizationValidationProductionEnablementPreflightDecision.from(bundle);
        Map<String, String> fields = decision.artifactFields();

        assertEquals("blocked/optimizerValidationBundleNotReady", decision.verdict());
        assertTrue(decision.blocked());
        assertFalse(decision.reviewReady());
        assertFalse(decision.readyForProductionMutation());
        assertFalse(decision.productionMutationEnabled());
        assertEquals("notReady/cseBlocked", decision.bundleVerdict());
        assertEquals("cseLiteralPromotionNotReady", decision.firstBlockingReason());
        assertEquals("collectPreviewCandidates", decision.firstRemainingWork());
        assertEquals("blockedPreflightKernel", fields.get("optimizerProductionPreflightMethod"));
        assertEquals("blocked/optimizerValidationBundleNotReady", fields.get("optimizerProductionPreflightVerdict"));
        assertEquals("true", fields.get("optimizerProductionPreflightBlocked"));
        assertEquals("false", fields.get("optimizerProductionPreflightReviewReady"));
        assertEquals("false", fields.get("optimizerProductionPreflightProductionMutationEnabled"));
        assertEquals("cseLiteralPromotionNotReady", fields.get("optimizerProductionPreflightFirstBlockingReason"));
        assertThrows(UnsupportedOperationException.class, () -> fields.put("x", "y"));
    }

    @Test
    void reportsReviewReadyWhenBundleIsClearButProductionMutationIsDisabled() {
        GpuIrOptimizationValidationOptimizerValidationBundle bundle = bundleWithGate(reviewReadyGate());

        GpuIrOptimizationValidationProductionEnablementPreflightDecision decision =
                GpuIrOptimizationValidationProductionEnablementPreflightDecision.from(bundle);

        assertEquals("reviewReady/productionMutationDisabled", decision.verdict());
        assertFalse(decision.blocked());
        assertTrue(decision.reviewReady());
        assertFalse(decision.readyForProductionMutation());
        assertFalse(decision.productionMutationEnabled());
        assertEquals("reviewReady/productionMutationDisabled", decision.bundleVerdict());
        assertEquals("", decision.firstBlockingReason());
        assertEquals("enableProductionMutationPolicy", decision.firstRemainingWork());
    }

    @Test
    void reportsReadyWhenFutureProductionMutationGateIsEnabled() {
        GpuIrOptimizationValidationOptimizerValidationBundle bundle = bundleWithGate(readyGate());

        GpuIrOptimizationValidationProductionEnablementPreflightDecision decision =
                GpuIrOptimizationValidationProductionEnablementPreflightDecision.from(bundle);

        assertEquals("ready/productionMutationEnabled", decision.verdict());
        assertFalse(decision.blocked());
        assertFalse(decision.reviewReady());
        assertTrue(decision.readyForProductionMutation());
        assertTrue(decision.productionMutationEnabled());
        assertEquals("readyForProductionMutation", decision.bundleVerdict());
        assertEquals("none", decision.firstRemainingWork());
    }

    @Test
    void runnerCanExportProductionPreflightFields() {
        GpuIrOptimizationValidationOptimizerEnablementArtifactRunner runner =
                new GpuIrOptimizationValidationOptimizerEnablementArtifactRunner();

        GpuIrOptimizationValidationProductionEnablementPreflightDecision decision =
                runner.runProductionPreflight(validationReport("runnerPreflightKernel"));
        Map<String, String> fields = runner.runProductionPreflightFields(validationReport("runnerPreflightKernel"));

        assertEquals("blocked/optimizerValidationBundleNotReady", decision.verdict());
        assertEquals("runnerPreflightKernel", fields.get("optimizerProductionPreflightMethod"));
        assertEquals("blocked/optimizerValidationBundleNotReady", fields.get("optimizerProductionPreflightVerdict"));
        assertEquals("cseLiteralPromotionNotReady", fields.get("optimizerProductionPreflightFirstBlockingReason"));
    }

    @Test
    void rejectsInvalidInputsAndStates() {
        GpuIrOptimizationValidationProductionEnablementPreflightDecision decision =
                GpuIrOptimizationValidationProductionEnablementPreflightDecision.from(
                        GpuIrOptimizationValidationOptimizerValidationBundle.from(validationReport("invalidPreflightKernel"))
                );

        assertThrows(NullPointerException.class, () -> GpuIrOptimizationValidationProductionEnablementPreflightDecision.from(null));
        assertThrows(IllegalArgumentException.class, () -> decision.artifactFields(""));
        assertThrows(IllegalArgumentException.class, () -> new GpuIrOptimizationValidationProductionEnablementPreflightDecision(
                "kernel",
                "ready/productionMutationEnabled",
                false,
                false,
                true,
                false,
                "readyForProductionMutation",
                "",
                "none",
                "summary"
        ));
        assertThrows(IllegalArgumentException.class, () -> new GpuIrOptimizationValidationProductionEnablementPreflightDecision(
                "kernel",
                "blocked/optimizerValidationBundleNotReady",
                true,
                true,
                false,
                false,
                "notReady/cseBlocked",
                "cseLiteralPromotionNotReady",
                "collectPreviewCandidates",
                "summary"
        ));
    }

    private static GpuIrOptimizationValidationOptimizerValidationBundle bundleWithGate(
            GpuIrOptimizationValidationOptimizerEnablementGateReport gate
    ) {
        GpuIrOptimizationValidationReport validationReport = validationReport(gate.methodName());
        GpuIrOptimizationValidationOptimizerEnablementArtifact artifact =
                new GpuIrOptimizationValidationOptimizerEnablementArtifactRunner().run(validationReport);
        return new GpuIrOptimizationValidationOptimizerValidationBundle(validationReport, artifact, gate);
    }

    private static GpuIrOptimizationValidationOptimizerEnablementGateReport reviewReadyGate() {
        return new GpuIrOptimizationValidationOptimizerEnablementGateReport(
                "reviewReadyPreflightKernel",
                "reviewReady/productionMutationDisabled",
                false,
                true,
                true,
                true,
                true,
                false,
                "readyForProductionMutation",
                "readyForPrototypeRewrite",
                "reviewAllowed/productionMutationDisabled",
                List.of("productionMutationDisabled"),
                List.of("enableProductionMutationPolicy")
        );
    }

    private static GpuIrOptimizationValidationOptimizerEnablementGateReport readyGate() {
        return new GpuIrOptimizationValidationOptimizerEnablementGateReport(
                "readyPreflightKernel",
                "readyForProductionMutation",
                true,
                true,
                true,
                true,
                true,
                true,
                "readyForProductionMutation",
                "readyForPrototypeRewrite",
                "futureProductionMutationEnabled",
                List.of(),
                List.of()
        );
    }
}
