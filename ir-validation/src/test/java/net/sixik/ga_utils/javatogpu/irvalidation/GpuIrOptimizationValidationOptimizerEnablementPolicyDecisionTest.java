package net.sixik.ga_utils.javatogpu.irvalidation;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static net.sixik.ga_utils.javatogpu.irvalidation.GpuIrOptimizationValidationRuleTestFixtures.rule;
import static net.sixik.ga_utils.javatogpu.irvalidation.GpuIrOptimizationValidationRuleTestFixtures.validationReport;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuIrOptimizationValidationOptimizerEnablementPolicyDecisionTest {
    @Test
    void allowsReviewForReadyHandoffButKeepsProductionMutationDisabled() {
        GpuIrOptimizationValidationRuleArtifactReport artifact = new GpuIrOptimizationValidationRuleArtifactRunner()
                .run(validationReport("readyPolicyKernel"));
        GpuIrOptimizationValidationOptimizerReadinessHandoffReport handoff =
                GpuIrOptimizationValidationOptimizerReadinessHandoffReport.from(artifact);

        GpuIrOptimizationValidationOptimizerEnablementPolicyDecision decision =
                GpuIrOptimizationValidationOptimizerEnablementPolicyDecision.from(handoff);
        Map<String, String> fields = decision.artifactFields();

        assertTrue(decision.allowOptimizerEnablementReview());
        assertFalse(decision.productionMutationEnabled());
        assertEquals("reviewAllowed/productionMutationDisabled", decision.verdict());
        assertEquals("readyForOptimizerEnablementReview", decision.handoffVerdict());
        assertEquals("enableProductionMutationPolicy", decision.firstRemainingWork());
        assertEquals("true", fields.get("optimizerEnablementPolicyAllowOptimizerEnablementReview"));
        assertEquals("false", fields.get("optimizerEnablementPolicyProductionMutationEnabled"));
        assertEquals("enableProductionMutationPolicy", fields.get("optimizerEnablementPolicyFirstRemainingWork"));
        assertThrows(UnsupportedOperationException.class, () -> fields.put("x", "y"));
    }

    @Test
    void blocksPolicyWhenHandoffHasWarnings() {
        GpuIrOptimizationValidationRuleArtifactReport artifact = GpuIrOptimizationValidationRuleArtifactReport.evaluate(
                validationReport("warningPolicyKernel"),
                GpuIrOptimizationValidationRuleRegistry.of(List.of(rule(
                        "optimizer.warning",
                        context -> GpuIrOptimizationValidationRuleResult.warned(
                                "optimizer.warning",
                                "non-blocking optimizer signal"
                        )
                )))
        );
        GpuIrOptimizationValidationOptimizerReadinessHandoffReport handoff =
                GpuIrOptimizationValidationOptimizerReadinessHandoffReport.from(artifact);

        GpuIrOptimizationValidationOptimizerEnablementPolicyDecision decision =
                GpuIrOptimizationValidationOptimizerEnablementPolicyDecision.from(handoff);
        Map<String, String> fields = decision.artifactFields("policy.");

        assertFalse(decision.allowOptimizerEnablementReview());
        assertEquals("blocked/handoffNotReady", decision.verdict());
        assertEquals("ruleArtifactWarningsPresent", decision.firstBlockingReason());
        assertEquals("reviewValidationRuleWarnings", decision.firstRemainingWork());
        assertEquals("false", fields.get("policy.AllowOptimizerEnablementReview"));
        assertEquals("ruleArtifactWarningsPresent", fields.get("policy.FirstBlockingReason"));
    }

    @Test
    void rejectsInvalidPolicyContracts() {
        GpuIrOptimizationValidationRuleArtifactReport artifact = new GpuIrOptimizationValidationRuleArtifactRunner()
                .run(validationReport("invalidPolicyKernel"));
        GpuIrOptimizationValidationOptimizerReadinessHandoffReport handoff =
                GpuIrOptimizationValidationOptimizerReadinessHandoffReport.from(artifact);
        GpuIrOptimizationValidationOptimizerEnablementPolicyDecision decision =
                GpuIrOptimizationValidationOptimizerEnablementPolicyDecision.from(handoff);

        assertThrows(NullPointerException.class, () -> GpuIrOptimizationValidationOptimizerEnablementPolicyDecision.from(null));
        assertThrows(IllegalArgumentException.class, () -> decision.artifactFields(""));
        assertThrows(IllegalArgumentException.class, () -> new GpuIrOptimizationValidationOptimizerEnablementPolicyDecision(
                "kernel",
                "reviewAllowed/productionMutationDisabled",
                true,
                true,
                "readyForOptimizerEnablementReview",
                true,
                "",
                "enableProductionMutationPolicy",
                "summary"
        ));
        assertThrows(IllegalArgumentException.class, () -> new GpuIrOptimizationValidationOptimizerEnablementPolicyDecision(
                "kernel",
                "reviewAllowed/productionMutationDisabled",
                false,
                false,
                "readyForOptimizerEnablementReview",
                true,
                "",
                "enableProductionMutationPolicy",
                "summary"
        ));
    }
}
