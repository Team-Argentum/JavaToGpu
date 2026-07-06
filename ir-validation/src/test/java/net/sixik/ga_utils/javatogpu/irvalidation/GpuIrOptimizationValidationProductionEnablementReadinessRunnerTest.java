package net.sixik.ga_utils.javatogpu.irvalidation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
    void canAlsoExportOptimizerLayerReadinessFieldsForCiConsumers() {
        GpuIrOptimizationValidationProductionEnablementReadinessRunner runner =
                new GpuIrOptimizationValidationProductionEnablementReadinessRunner();

        GpuIrOptimizationValidationOptimizerLayerReadinessSummaryReport readiness =
                runner.runOptimizerLayerReadiness(validationReport("readinessLayerKernel"));
        Map<String, String> fields = runner.runOptimizerLayerReadinessFields(validationReport("readinessLayerKernel"));

        assertEquals("readinessLayerKernel", readiness.methodName());
        assertEquals("blocked", readiness.verdict());
        assertEquals("readinessLayerKernel", fields.get("optimizerLayerReadinessMethod"));
        assertEquals("blocked", fields.get("optimizerLayerReadinessVerdict"));
        assertEquals("cseLiteralPromotion", fields.get("optimizerLayerReadinessFirstBlockingLayer"));
        assertEquals("cseLiteralPromotion,autoVectorization", fields.get("optimizerLayerReadinessBlockingLayers"));
        assertEquals("consistent", fields.get("optimizerLayerReadinessConsistencyVerdict"));
        assertEquals("true", fields.get("optimizerLayerReadinessConsistencyConsistent"));
    }

    @Test
    void canStoreAndCompareOptimizerLayerReadinessBaselineFieldsForCiConsumers() {
        GpuIrOptimizationValidationProductionEnablementReadinessRunner runner =
                new GpuIrOptimizationValidationProductionEnablementReadinessRunner();
        Map<String, String> baselineFields = runner.runOptimizerLayerReadinessBaselineSnapshotFields(
                validationReport("readinessBaselineKernel")
        );

        Map<String, String> comparisonFields = runner.runOptimizerLayerReadinessBaselineComparisonFields(
                baselineFields,
                validationReport("readinessBaselineKernel")
        );

        assertEquals("readinessBaselineKernel", baselineFields.get("optimizerLayerReadinessBaselineSnapshotMethod"));
        assertEquals("blocked", baselineFields.get("optimizerLayerReadinessBaselineSnapshotVerdict"));
        assertEquals("readinessBaselineKernel", comparisonFields.get("optimizerLayerReadinessBaselineComparisonMethod"));
        assertEquals("unchanged", comparisonFields.get("optimizerLayerReadinessBaselineComparisonOutcome"));
        assertEquals("0", comparisonFields.get("optimizerLayerReadinessBaselineComparisonBlockingLayerDelta"));
    }

    @Test
    void canPersistOptimizerLayerReadinessBaselinePropertiesFileForCiConsumers(@TempDir Path tempDir) throws IOException {
        GpuIrOptimizationValidationProductionEnablementReadinessRunner runner =
                new GpuIrOptimizationValidationProductionEnablementReadinessRunner();
        Path baselinePath = tempDir.resolve("readiness-baseline.properties");

        runner.saveOptimizerLayerReadinessBaselineSnapshot(
                baselinePath,
                validationReport("readinessBaselineFileKernel")
        );
        Map<String, String> loadedFields = runner.loadOptimizerLayerReadinessBaselineSnapshotFields(baselinePath);
        Map<String, String> ciFields = runner.runOptimizerLayerReadinessBaselineCiFailClosedFields(
                baselinePath,
                validationReport("readinessBaselineFileKernel")
        );

        assertTrue(Files.exists(baselinePath));
        assertEquals("readinessBaselineFileKernel", loadedFields.get("optimizerLayerReadinessBaselineSnapshotMethod"));
        assertEquals("blocked", loadedFields.get("optimizerLayerReadinessBaselineSnapshotVerdict"));
        assertEquals("readinessBaselineFileKernel", ciFields.get("optimizerLayerReadinessBaselineCiMethod"));
        assertEquals("unchanged", ciFields.get("optimizerLayerReadinessBaselineCiOutcome"));
        assertEquals("false", ciFields.get("optimizerLayerReadinessBaselineCiFailBuild"));
    }

    @Test
    void canAlsoExportOptimizerLayerReadinessBaselineCiFieldsForCiConsumers() {
        GpuIrOptimizationValidationProductionEnablementReadinessRunner runner =
                new GpuIrOptimizationValidationProductionEnablementReadinessRunner();
        Map<String, String> baselineFields = runner.runOptimizerLayerReadinessBaselineSnapshotFields(
                validationReport("readinessBaselineCiKernel")
        );

        Map<String, String> fields = runner.runOptimizerLayerReadinessBaselineCiFields(
                baselineFields,
                validationReport("readinessBaselineCiKernel")
        );

        assertEquals("readinessBaselineCiKernel", fields.get("optimizerLayerReadinessBaselineComparisonMethod"));
        assertEquals("unchanged", fields.get("optimizerLayerReadinessBaselineComparisonOutcome"));
        assertEquals("readinessBaselineCiKernel", fields.get("optimizerLayerReadinessBaselineCiMethod"));
        assertEquals("unchanged", fields.get("optimizerLayerReadinessBaselineCiOutcome"));
        assertEquals("accepted/noRegression", fields.get("optimizerLayerReadinessBaselineCiDecision"));
        assertEquals("true", fields.get("optimizerLayerReadinessBaselineCiAccepted"));
        assertEquals("false", fields.get("optimizerLayerReadinessBaselineCiRejected"));
        assertEquals("false", fields.get("optimizerLayerReadinessBaselineCiFailBuild"));
    }

    @Test
    void canFailCloseInvalidOptimizerLayerReadinessBaselineCiFieldsForCiConsumers() {
        GpuIrOptimizationValidationProductionEnablementReadinessRunner runner =
                new GpuIrOptimizationValidationProductionEnablementReadinessRunner();
        Map<String, String> damagedBaselineFields = Map.of(
                "optimizerLayerReadinessBaselineSnapshotMethod",
                "readinessInvalidBaselineCiKernel"
        );

        Map<String, String> fields = runner.runOptimizerLayerReadinessBaselineCiFailClosedFields(
                damagedBaselineFields,
                validationReport("readinessInvalidBaselineCiKernel")
        );

        assertEquals("readinessInvalidBaselineCiKernel", fields.get("optimizerLayerReadinessBaselineCiMethod"));
        assertEquals("readinessInvalidBaselineCiKernel", fields.get("optimizerLayerReadinessBaselineCiCurrentMethod"));
        assertEquals("readinessInvalidBaselineCiKernel", fields.get("optimizerLayerReadinessBaselineCiBaselineSourceMethod"));
        assertEquals("invalidBaseline", fields.get("optimizerLayerReadinessBaselineCiOutcome"));
        assertEquals("fail/invalidBaseline", fields.get("optimizerLayerReadinessBaselineCiDecision"));
        assertEquals("false", fields.get("optimizerLayerReadinessBaselineCiAccepted"));
        assertEquals("true", fields.get("optimizerLayerReadinessBaselineCiRejected"));
        assertEquals("true", fields.get("optimizerLayerReadinessBaselineCiFailBuild"));
    }

    @Test
    void canFailCloseMismatchedOptimizerLayerReadinessBaselineMethodForCiConsumers() {
        GpuIrOptimizationValidationProductionEnablementReadinessRunner runner =
                new GpuIrOptimizationValidationProductionEnablementReadinessRunner();
        Map<String, String> baselineFields = runner.runOptimizerLayerReadinessBaselineSnapshotFields(
                validationReport("readinessBaselineMethodMismatchKernel")
        );

        Map<String, String> fields = runner.runOptimizerLayerReadinessBaselineCiFailClosedFields(
                baselineFields,
                validationReport("readinessCurrentMethodMismatchKernel")
        );

        assertEquals("readinessCurrentMethodMismatchKernel", fields.get("optimizerLayerReadinessBaselineCiMethod"));
        assertEquals("readinessCurrentMethodMismatchKernel", fields.get("optimizerLayerReadinessBaselineCiCurrentMethod"));
        assertEquals("readinessBaselineMethodMismatchKernel", fields.get("optimizerLayerReadinessBaselineCiBaselineSourceMethod"));
        assertEquals("invalidBaseline", fields.get("optimizerLayerReadinessBaselineCiOutcome"));
        assertEquals("fail/invalidBaseline", fields.get("optimizerLayerReadinessBaselineCiDecision"));
        assertEquals("false", fields.get("optimizerLayerReadinessBaselineCiAccepted"));
        assertEquals("true", fields.get("optimizerLayerReadinessBaselineCiRejected"));
        assertEquals("true", fields.get("optimizerLayerReadinessBaselineCiFailBuild"));
        assertEquals("IllegalArgumentException", fields.get("optimizerLayerReadinessBaselineCiFailureType"));
    }

    @Test
    void canAlsoExportOptimizerLayerReadinessRegressionFieldsForCiConsumers() {
        GpuIrOptimizationValidationProductionEnablementReadinessRunner runner =
                new GpuIrOptimizationValidationProductionEnablementReadinessRunner();
        GpuIrOptimizationValidationOptimizerLayerReadinessSummaryReport baseline =
                runner.runOptimizerLayerReadiness(validationReport("readinessRegressionKernel"));

        Map<String, String> fields = runner.runOptimizerLayerReadinessRegressionFields(
                baseline,
                validationReport("readinessRegressionKernel")
        );

        assertEquals("readinessRegressionKernel", fields.get("optimizerLayerReadinessRegressionMethod"));
        assertEquals("unchanged", fields.get("optimizerLayerReadinessRegressionOutcome"));
        assertEquals("false", fields.get("optimizerLayerReadinessRegressionImproved"));
        assertEquals("false", fields.get("optimizerLayerReadinessRegressionRegressed"));
        assertEquals("true", fields.get("optimizerLayerReadinessRegressionUnchanged"));
    }

    @Test
    void canAlsoExportOptimizerLayerReadinessRegressionRuleArtifactFieldsForCiConsumers() {
        GpuIrOptimizationValidationProductionEnablementReadinessRunner runner =
                new GpuIrOptimizationValidationProductionEnablementReadinessRunner();
        GpuIrOptimizationValidationOptimizerLayerReadinessSummaryReport baseline =
                runner.runOptimizerLayerReadiness(validationReport("readinessRegressionRuleKernel"));

        Map<String, String> fields = runner.runOptimizerLayerReadinessRegressionRuleArtifactFields(
                baseline,
                validationReport("readinessRegressionRuleKernel")
        );

        assertEquals("readinessRegressionRuleKernel", fields.get("validationRulesMethod"));
        assertEquals("pass", fields.get("validationRulesVerdict"));
        assertEquals("true", fields.get("validationRulesPassed"));
        assertEquals("1", fields.get("validationRulesRules"));
        assertEquals("optimizer.layerReadinessRegressionGate", fields.get("validationRulesRegistryResult.0.RuleId"));
        assertEquals("unchanged", fields.get("validationRulesRegistryResult.0.Metadata.regressionOutcome"));
    }

    @Test
    void canAlsoExportOptimizerLayerReadinessRegressionRuleArtifactAcceptanceFieldsForCiConsumers() {
        GpuIrOptimizationValidationProductionEnablementReadinessRunner runner =
                new GpuIrOptimizationValidationProductionEnablementReadinessRunner();
        GpuIrOptimizationValidationOptimizerLayerReadinessSummaryReport baseline =
                runner.runOptimizerLayerReadiness(validationReport("readinessRegressionAcceptanceKernel"));

        Map<String, String> fields = runner.runOptimizerLayerReadinessRegressionRuleArtifactAcceptanceFields(
                baseline,
                validationReport("readinessRegressionAcceptanceKernel")
        );

        assertEquals("readinessRegressionAcceptanceKernel", fields.get("validationRulesAcceptanceMethod"));
        assertEquals("pass", fields.get("validationRulesAcceptanceVerdict"));
        assertEquals("true", fields.get("validationRulesAcceptanceAccepted"));
        assertEquals("false", fields.get("validationRulesAcceptanceRejected"));
        assertEquals("false", fields.get("validationRulesAcceptanceAcceptedWithWarnings"));
        assertEquals("accepted/pass", fields.get("validationRulesAcceptanceReason"));
    }

    @Test
    void canAlsoExportOptimizerLayerReadinessRegressionCiFieldsForCiConsumers() {
        GpuIrOptimizationValidationProductionEnablementReadinessRunner runner =
                new GpuIrOptimizationValidationProductionEnablementReadinessRunner();
        GpuIrOptimizationValidationOptimizerLayerReadinessSummaryReport baseline =
                runner.runOptimizerLayerReadiness(validationReport("readinessRegressionCiKernel"));

        Map<String, String> fields = runner.runOptimizerLayerReadinessRegressionCiFields(
                baseline,
                validationReport("readinessRegressionCiKernel")
        );

        assertEquals("readinessRegressionCiKernel", fields.get("optimizerLayerReadinessRegressionMethod"));
        assertEquals("unchanged", fields.get("optimizerLayerReadinessRegressionOutcome"));
        assertEquals("readinessRegressionCiKernel", fields.get("validationRulesMethod"));
        assertEquals("pass", fields.get("validationRulesVerdict"));
        assertEquals("readinessRegressionCiKernel", fields.get("validationRulesAcceptanceMethod"));
        assertEquals("true", fields.get("validationRulesAcceptanceAccepted"));
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
        assertThrows(NullPointerException.class, () -> runner.runOptimizerLayerReadiness(null));
        assertThrows(NullPointerException.class, () -> runner.runOptimizerLayerReadinessFields(null));
        assertThrows(NullPointerException.class, () -> runner.runOptimizerLayerReadinessBaselineSnapshot(null));
        assertThrows(NullPointerException.class, () -> runner.runOptimizerLayerReadinessBaselineSnapshotFields(null));
        assertThrows(NullPointerException.class, () -> runner.saveOptimizerLayerReadinessBaselineSnapshot(null, validationReport("nullReadinessBaselinePathKernel")));
        assertThrows(NullPointerException.class, () -> runner.saveOptimizerLayerReadinessBaselineSnapshot(Path.of("baseline.properties"), null));
        assertThrows(NullPointerException.class, () -> runner.loadOptimizerLayerReadinessBaselineSnapshotFields(null));
        assertThrows(NullPointerException.class, () -> runner.runOptimizerLayerReadinessBaselineComparison((GpuIrOptimizationValidationOptimizerLayerReadinessBaselineSnapshot) null, validationReport("nullBaselineReadinessComparisonKernel")));
        assertThrows(NullPointerException.class, () -> runner.runOptimizerLayerReadinessBaselineComparison((Map<String, String>) null, validationReport("nullBaselineReadinessComparisonFieldsKernel")));
        assertThrows(NullPointerException.class, () -> runner.runOptimizerLayerReadinessBaselineComparisonFields((GpuIrOptimizationValidationOptimizerLayerReadinessBaselineSnapshot) null, validationReport("nullBaselineReadinessComparisonResultKernel")));
        assertThrows(NullPointerException.class, () -> runner.runOptimizerLayerReadinessBaselineComparisonFields((Map<String, String>) null, validationReport("nullBaselineReadinessComparisonResultFieldsKernel")));
        assertThrows(NullPointerException.class, () -> runner.runOptimizerLayerReadinessBaselineCi((GpuIrOptimizationValidationOptimizerLayerReadinessBaselineSnapshot) null, validationReport("nullBaselineReadinessCiKernel")));
        assertThrows(NullPointerException.class, () -> runner.runOptimizerLayerReadinessBaselineCi((Map<String, String>) null, validationReport("nullBaselineReadinessCiFieldsKernel")));
        assertThrows(NullPointerException.class, () -> runner.runOptimizerLayerReadinessBaselineCiFields((GpuIrOptimizationValidationOptimizerLayerReadinessBaselineSnapshot) null, validationReport("nullBaselineReadinessCiResultKernel")));
        assertThrows(NullPointerException.class, () -> runner.runOptimizerLayerReadinessBaselineCiFields((Map<String, String>) null, validationReport("nullBaselineReadinessCiResultFieldsKernel")));
        assertThrows(NullPointerException.class, () -> runner.runOptimizerLayerReadinessBaselineCiFailClosedFields((Map<String, String>) null, validationReport("nullBaselineReadinessCiFailClosedKernel")));
        assertThrows(NullPointerException.class, () -> runner.runOptimizerLayerReadinessBaselineCiFailClosedFields(Map.of(), null));
        assertThrows(NullPointerException.class, () -> runner.runOptimizerLayerReadinessBaselineCiFailClosedFields((Path) null, validationReport("nullBaselineReadinessCiFailClosedPathKernel")));
        assertThrows(NullPointerException.class, () -> runner.runOptimizerLayerReadinessRegression(null, validationReport("nullBaselineReadinessKernel")));
        assertThrows(NullPointerException.class, () -> runner.runOptimizerLayerReadinessRegressionFields(null, validationReport("nullBaselineReadinessFieldsKernel")));
        assertThrows(NullPointerException.class, () -> runner.runOptimizerLayerReadinessRegressionRuleArtifact(null, validationReport("nullBaselineReadinessRuleArtifactKernel")));
        assertThrows(NullPointerException.class, () -> runner.runOptimizerLayerReadinessRegressionRuleArtifactFields(null, validationReport("nullBaselineReadinessRuleArtifactFieldsKernel")));
        assertThrows(NullPointerException.class, () -> runner.runOptimizerLayerReadinessRegressionRuleArtifactAcceptance(null, validationReport("nullBaselineReadinessRuleAcceptanceKernel")));
        assertThrows(NullPointerException.class, () -> runner.runOptimizerLayerReadinessRegressionRuleArtifactAcceptanceFields(null, validationReport("nullBaselineReadinessRuleAcceptanceFieldsKernel")));
        assertThrows(NullPointerException.class, () -> runner.runOptimizerLayerReadinessRegressionCiFields(null, validationReport("nullBaselineReadinessCiFieldsKernel")));
        assertThrows(NullPointerException.class, () -> runner.runProductionMutationSwitchContract(null));
        assertThrows(NullPointerException.class, () -> runner.runProductionMutationSwitchContractFields(null));
        assertThrows(NullPointerException.class, () -> runner.runOptimizerPromotionConfidenceContract(null));
        assertThrows(NullPointerException.class, () -> runner.runOptimizerPromotionConfidenceContractFields(null));
    }
}
