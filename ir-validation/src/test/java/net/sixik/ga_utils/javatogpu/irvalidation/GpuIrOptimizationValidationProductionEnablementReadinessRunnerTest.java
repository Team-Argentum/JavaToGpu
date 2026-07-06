package net.sixik.ga_utils.javatogpu.irvalidation;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static net.sixik.ga_utils.javatogpu.irvalidation.GpuIrOptimizationValidationRuleTestFixtures.validationReport;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuIrOptimizationValidationProductionEnablementReadinessRunnerTest {
    @Test
    void exportsReadOnlyProductionPreflightDecisionForValidationReport() {
        GpuIrOptimizationValidationProductionEnablementReadinessRunner runner =
                new GpuIrOptimizationValidationProductionEnablementReadinessRunner();

        GpuIrOptimizationValidationProductionEnablementPreflightDecision decision =
                runner.run(validationReport("readinessRunnerKernel"));
        Map<String, String> fields = runner.runFields(validationReport("readinessRunnerKernel"));

        assertEquals("readinessRunnerKernel", decision.methodName());
        assertEquals("blocked/optimizerValidationBundleNotReady", decision.verdict());
        assertTrue(decision.blocked());
        assertEquals("readinessRunnerKernel", fields.get("optimizerProductionPreflightMethod"));
        assertEquals("blocked/optimizerValidationBundleNotReady", fields.get("optimizerProductionPreflightVerdict"));
        assertEquals("cseLiteralPromotionNotReady", fields.get("optimizerProductionPreflightFirstBlockingReason"));
    }

    @Test
    void canAlsoExportFullBundleFieldsForCiConsumers() {
        GpuIrOptimizationValidationProductionEnablementReadinessRunner runner =
                new GpuIrOptimizationValidationProductionEnablementReadinessRunner();

        GpuIrOptimizationValidationOptimizerValidationBundle bundle =
                runner.runBundle(validationReport("readinessBundleKernel"));
        Map<String, String> fields = runner.runBundleFields(validationReport("readinessBundleKernel"));

        assertEquals("readinessBundleKernel", bundle.methodName());
        assertEquals("notReady/cseBlocked", bundle.verdict());
        assertEquals("readinessBundleKernel", fields.get("optimizerValidationBundleMethod"));
        assertEquals("notReady/cseBlocked", fields.get("optimizerValidationBundleVerdict"));
        assertEquals("blocked/optimizerValidationBundleNotReady", fields.get("optimizerProductionPreflightVerdict"));
        assertEquals("blocked/preflightNotReady", fields.get("optimizerProductionSwitchVerdict"));
        assertEquals("blocked/productionSwitchNotReady", fields.get("optimizerPromotionConfidenceVerdict"));
    }

    @Test
    void canExportProductionMutationSwitchContractForCiConsumers() {
        GpuIrOptimizationValidationProductionEnablementReadinessRunner runner =
                new GpuIrOptimizationValidationProductionEnablementReadinessRunner();

        GpuIrOptimizationValidationProductionMutationSwitchContract contract =
                runner.runProductionMutationSwitchContract(validationReport("readinessSwitchKernel"));
        Map<String, String> fields = runner.runProductionMutationSwitchContractFields(validationReport("readinessSwitchKernel"));

        assertEquals("readinessSwitchKernel", contract.methodName());
        assertEquals("blocked/preflightNotReady", contract.verdict());
        assertEquals("readinessSwitchKernel", fields.get("optimizerProductionSwitchMethod"));
        assertEquals("blocked/preflightNotReady", fields.get("optimizerProductionSwitchVerdict"));
        assertEquals("productionPreflightNotReviewReady", fields.get("optimizerProductionSwitchFirstBlockingReason"));
    }

    @Test
    void canExportOptimizerPromotionConfidenceContractForCiConsumers() {
        GpuIrOptimizationValidationProductionEnablementReadinessRunner runner =
                new GpuIrOptimizationValidationProductionEnablementReadinessRunner();

        GpuIrOptimizationValidationOptimizerPromotionConfidenceContract contract =
                runner.runOptimizerPromotionConfidenceContract(validationReport("readinessPromotionKernel"));
        Map<String, String> fields = runner.runOptimizerPromotionConfidenceContractFields(validationReport("readinessPromotionKernel"));

        assertEquals("readinessPromotionKernel", contract.methodName());
        assertEquals("blocked/productionSwitchNotReady", contract.verdict());
        assertEquals("readinessPromotionKernel", fields.get("optimizerPromotionConfidenceMethod"));
        assertEquals("blocked/productionSwitchNotReady", fields.get("optimizerPromotionConfidenceVerdict"));
        assertEquals("productionMutationSwitchNotReady", fields.get("optimizerPromotionConfidenceFirstBlockingReason"));
    }

    @Test
    void acceptsCustomRegistryThroughUnderlyingEnablementRunner() {
        GpuIrOptimizationValidationRuleRegistry registry = GpuIrOptimizationValidationRuleRegistry.of(List.of(
                GpuIrOptimizationValidationRules.safetyClean()
        ));
        GpuIrOptimizationValidationProductionEnablementReadinessRunner runner =
                new GpuIrOptimizationValidationProductionEnablementReadinessRunner(registry);

        Map<String, String> fields = runner.runBundleFields(validationReport("customReadinessRunnerKernel"));

        assertEquals("1", fields.get("validationRulesRules"));
        assertEquals("safety.clean", fields.get("validationRulesRegistryResult.0.RuleId"));
        assertEquals("blocked/optimizerValidationBundleNotReady", fields.get("optimizerProductionPreflightVerdict"));
    }

    @Test
    void preservesProvidedEnablementRunnerAndRejectsNullInputs() {
        GpuIrOptimizationValidationOptimizerEnablementArtifactRunner enablementRunner =
                new GpuIrOptimizationValidationOptimizerEnablementArtifactRunner();
        GpuIrOptimizationValidationProductionEnablementReadinessRunner runner =
                new GpuIrOptimizationValidationProductionEnablementReadinessRunner(enablementRunner);

        assertSame(enablementRunner, runner.enablementRunner());
        assertThrows(NullPointerException.class, () -> new GpuIrOptimizationValidationProductionEnablementReadinessRunner(
                (GpuIrOptimizationValidationOptimizerEnablementArtifactRunner) null
        ));
        assertThrows(NullPointerException.class, () -> runner.run(null));
        assertThrows(NullPointerException.class, () -> runner.runFields(null));
        assertThrows(NullPointerException.class, () -> runner.runBundle(null));
        assertThrows(NullPointerException.class, () -> runner.runBundleFields(null));
        assertThrows(NullPointerException.class, () -> runner.runProductionMutationSwitchContract(null));
        assertThrows(NullPointerException.class, () -> runner.runProductionMutationSwitchContractFields(null));
        assertThrows(NullPointerException.class, () -> runner.runOptimizerPromotionConfidenceContract(null));
        assertThrows(NullPointerException.class, () -> runner.runOptimizerPromotionConfidenceContractFields(null));
    }
}
