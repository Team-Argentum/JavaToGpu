package net.sixik.ga_utils.javatogpu.irvalidation;

import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrArrayAccess;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrVariableRef;
import net.sixik.ga_utils.javatogpu.frontend.ir.model.GpuIrMethod;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrAssignment;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrReturn;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static net.sixik.ga_utils.javatogpu.irvalidation.GpuIrOptimizationValidationRuleTestFixtures.fixedWidthLoop;
import static net.sixik.ga_utils.javatogpu.irvalidation.GpuIrOptimizationValidationRuleTestFixtures.validate;
import static net.sixik.ga_utils.javatogpu.irvalidation.GpuIrOptimizationValidationRuleTestFixtures.validationReport;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuIrOptimizationValidationOptimizerEnablementArtifactRunnerTest {
    @Test
    void defaultRunnerExportsFullReadOnlyEnablementArtifactForCleanReport() {
        GpuIrOptimizationValidationReport validationReport = validationReport("enablementSmokeKernel");

        GpuIrOptimizationValidationOptimizerEnablementArtifactRunner runner =
                new GpuIrOptimizationValidationOptimizerEnablementArtifactRunner();
        GpuIrOptimizationValidationOptimizerEnablementArtifact artifact = runner.run(validationReport);
        Map<String, String> fields = runner.runArtifactFields(validationReport);

        assertEquals(3, runner.registry().rules().size());
        assertEquals("enablementSmokeKernel", artifact.methodName());
        assertTrue(artifact.accepted());
        assertTrue(artifact.readyForOptimizerEnablementReview());
        assertTrue(artifact.allowOptimizerEnablementReview());
        assertFalse(artifact.productionMutationEnabled());
        assertEquals("enablementSmokeKernel", fields.get("validationRulesMethod"));
        assertEquals("true", fields.get("validationRulesPassed"));
        assertEquals("true", fields.get("validationRulesAcceptanceAccepted"));
        assertEquals("readyForOptimizerEnablementReview", fields.get("optimizerReadinessHandoffVerdict"));
        assertEquals("reviewAllowed/productionMutationDisabled", fields.get("optimizerEnablementPolicyVerdict"));
        assertEquals("false", fields.get("optimizerEnablementPolicyProductionMutationEnabled"));
    }

    @Test
    void defaultRunnerCanAlsoExportAggregateEnablementGateFields() {
        GpuIrOptimizationValidationReport validationReport = validationReport("enablementGateSmokeKernel");

        GpuIrOptimizationValidationOptimizerEnablementArtifactRunner runner =
                new GpuIrOptimizationValidationOptimizerEnablementArtifactRunner();
        GpuIrOptimizationValidationOptimizerEnablementGateReport gate = runner.runGate(validationReport);
        Map<String, String> fields = runner.runGateFields(validationReport);

        assertEquals("notReady/cseBlocked", gate.verdict());
        assertFalse(gate.readyForProductionMutation());
        assertEquals("enablementGateSmokeKernel", fields.get("optimizerEnablementGateMethod"));
        assertEquals("notReady/cseBlocked", fields.get("optimizerEnablementGateVerdict"));
        assertEquals("false", fields.get("optimizerEnablementGateReadyForProductionMutation"));
        assertEquals("cseLiteralPromotionNotReady", fields.get("optimizerEnablementGateFirstBlockingReason"));
        assertEquals("false", fields.get("optimizerEnablementGateProductionMutationEnabled"));
    }

    @Test
    void defaultRunnerCanAlsoExportOptimizerValidationBundleFields() {
        GpuIrOptimizationValidationReport validationReport = validationReport("validationBundleSmokeKernel");

        GpuIrOptimizationValidationOptimizerEnablementArtifactRunner runner =
                new GpuIrOptimizationValidationOptimizerEnablementArtifactRunner();
        GpuIrOptimizationValidationOptimizerValidationBundle bundle = runner.runBundle(validationReport);
        Map<String, String> fields = runner.runBundleFields(validationReport);

        assertEquals("validationBundleSmokeKernel", bundle.methodName());
        assertEquals("notReady/cseBlocked", bundle.verdict());
        assertEquals("validationBundleSmokeKernel", fields.get("optimizerValidationBundleMethod"));
        assertEquals("notReady/cseBlocked", fields.get("optimizerValidationBundleVerdict"));
        assertEquals("false", fields.get("optimizerValidationBundleProductionMutationEnabled"));
        assertEquals("notReady/cseBlocked", fields.get("optimizerEnablementGateVerdict"));
    }

    @Test
    void defaultRunnerCanAlsoExportOptimizerLayerReadinessFields() {
        GpuIrOptimizationValidationReport validationReport = validationReport("layerRunnerKernel");

        GpuIrOptimizationValidationOptimizerEnablementArtifactRunner runner =
                new GpuIrOptimizationValidationOptimizerEnablementArtifactRunner();
        GpuIrOptimizationValidationOptimizerLayerReadinessSummaryReport readiness =
                runner.runOptimizerLayerReadiness(validationReport);
        Map<String, String> fields = runner.runOptimizerLayerReadinessFields(validationReport);

        assertEquals("layerRunnerKernel", readiness.methodName());
        assertEquals("blocked", readiness.verdict());
        assertEquals("layerRunnerKernel", fields.get("optimizerLayerReadinessMethod"));
        assertEquals("blocked", fields.get("optimizerLayerReadinessVerdict"));
        assertEquals("cseLiteralPromotion", fields.get("optimizerLayerReadinessFirstBlockingLayer"));
        assertEquals("cseLiteralPromotion,autoVectorization", fields.get("optimizerLayerReadinessBlockingLayers"));
        assertEquals("blocked", fields.get("optimizerLayerReadinessLayer.cseLiteralPromotion"));
        assertEquals("blocked", fields.get("optimizerLayerReadinessLayer.autoVectorization"));
        assertEquals("consistent", fields.get("optimizerLayerReadinessConsistencyVerdict"));
        assertEquals("true", fields.get("optimizerLayerReadinessConsistencyConsistent"));
        assertEquals("0", fields.get("optimizerLayerReadinessConsistencyFailedChecks"));
    }

    @Test
    void defaultRunnerCanExportOptimizerLayerReadinessRegressionFields() {
        GpuIrOptimizationValidationOptimizerEnablementArtifactRunner runner =
                new GpuIrOptimizationValidationOptimizerEnablementArtifactRunner();
        GpuIrOptimizationValidationReport baselineReport = validationReport("layerRegressionKernel");
        GpuIrOptimizationValidationOptimizerLayerReadinessSummaryReport baseline =
                runner.runOptimizerLayerReadiness(baselineReport);

        Map<String, String> fields = runner.runOptimizerLayerReadinessRegressionFields(
                baseline,
                validationReport("layerRegressionKernel")
        );

        assertEquals("layerRegressionKernel", fields.get("optimizerLayerReadinessRegressionMethod"));
        assertEquals("unchanged", fields.get("optimizerLayerReadinessRegressionOutcome"));
        assertEquals("false", fields.get("optimizerLayerReadinessRegressionImproved"));
        assertEquals("false", fields.get("optimizerLayerReadinessRegressionRegressed"));
        assertEquals("true", fields.get("optimizerLayerReadinessRegressionUnchanged"));
        assertEquals("0", fields.get("optimizerLayerReadinessRegressionBlockingLayerDelta"));
        assertEquals("0", fields.get("optimizerLayerReadinessRegressionReadyLayerDelta"));
        assertEquals("[]", fields.get("optimizerLayerReadinessRegressionChangedLayers"));
        assertEquals("0", fields.get("optimizerLayerReadinessRegressionChangedLayerCount"));
    }

    @Test
    void defaultRunnerCanExportOptimizerLayerReadinessRegressionRuleArtifactFields() {
        GpuIrOptimizationValidationOptimizerEnablementArtifactRunner runner =
                new GpuIrOptimizationValidationOptimizerEnablementArtifactRunner();
        GpuIrOptimizationValidationOptimizerLayerReadinessSummaryReport baseline =
                runner.runOptimizerLayerReadiness(validationReport("layerRegressionRuleKernel"));

        GpuIrOptimizationValidationRuleArtifactReport artifact =
                runner.runOptimizerLayerReadinessRegressionRuleArtifact(
                        baseline,
                        validationReport("layerRegressionRuleKernel")
                );
        Map<String, String> fields = runner.runOptimizerLayerReadinessRegressionRuleArtifactFields(
                baseline,
                validationReport("layerRegressionRuleKernel")
        );

        assertEquals("layerRegressionRuleKernel", artifact.validationReport().methodName());
        assertEquals("pass", artifact.verdict());
        assertTrue(artifact.passed());
        assertEquals("layerRegressionRuleKernel", fields.get("validationRulesMethod"));
        assertEquals("pass", fields.get("validationRulesVerdict"));
        assertEquals("true", fields.get("validationRulesPassed"));
        assertEquals("1", fields.get("validationRulesRules"));
        assertEquals("optimizer.layerReadinessRegressionGate", fields.get("validationRulesRegistryResult.0.RuleId"));
        assertEquals("pass", fields.get("validationRulesRegistryResult.0.Status"));
        assertEquals("unchanged", fields.get("validationRulesRegistryResult.0.Metadata.regressionOutcome"));
        assertEquals("true", fields.get("validationRulesRegistryResult.0.Metadata.regressionUnchanged"));
    }

    @Test
    void defaultRunnerCanAlsoExportProductionPreflightFields() {
        GpuIrOptimizationValidationReport validationReport = validationReport("productionPreflightSmokeKernel");

        GpuIrOptimizationValidationOptimizerEnablementArtifactRunner runner =
                new GpuIrOptimizationValidationOptimizerEnablementArtifactRunner();
        GpuIrOptimizationValidationProductionEnablementPreflightDecision decision =
                runner.runProductionPreflight(validationReport);
        Map<String, String> fields = runner.runProductionPreflightFields(validationReport);

        assertEquals("productionPreflightSmokeKernel", decision.methodName());
        assertEquals("blocked/optimizerValidationBundleNotReady", decision.verdict());
        assertEquals("productionPreflightSmokeKernel", fields.get("optimizerProductionPreflightMethod"));
        assertEquals("blocked/optimizerValidationBundleNotReady", fields.get("optimizerProductionPreflightVerdict"));
        assertEquals("true", fields.get("optimizerProductionPreflightBlocked"));
        assertEquals("false", fields.get("optimizerProductionPreflightProductionMutationEnabled"));
    }

    @Test
    void defaultRunnerFailClosesWhenValidationReportHasBlockingOptimizerDiagnostics() {
        GpuIrOptimizationValidationReport validationReport = validate(new GpuIrMethod("blockedEnablementSmokeKernel", List.of(
                fixedWidthLoop(4, List.of(new GpuIrAssignment(
                        new GpuIrArrayAccess("out", new GpuIrVariableRef("i")),
                        new GpuIrArrayAccess("left", new GpuIrVariableRef("i"))
                ))),
                new GpuIrReturn(null)
        )));

        GpuIrOptimizationValidationOptimizerEnablementArtifactRunner runner =
                new GpuIrOptimizationValidationOptimizerEnablementArtifactRunner();
        GpuIrOptimizationValidationOptimizerEnablementArtifact artifact = runner.run(validationReport);
        Map<String, String> fields = artifact.artifactFields();

        assertFalse(artifact.accepted());
        assertFalse(artifact.readyForOptimizerEnablementReview());
        assertFalse(artifact.allowOptimizerEnablementReview());
        assertFalse(artifact.productionMutationEnabled());
        assertEquals("false", fields.get("validationRulesPassed"));
        assertEquals("rejected/blockingResultsPresent", fields.get("validationRulesAcceptanceReason"));
        assertEquals("notReady/ruleArtifactRejected", fields.get("optimizerReadinessHandoffVerdict"));
        assertEquals("blocked/handoffNotReady", fields.get("optimizerEnablementPolicyVerdict"));
        assertEquals("ruleArtifactRejected", fields.get("optimizerEnablementPolicyFirstBlockingReason"));
    }

    @Test
    void customRegistryRunnerKeepsTheSameDetachedRegistryContract() {
        GpuIrOptimizationValidationRuleRegistry registry = GpuIrOptimizationValidationRuleRegistry.of(List.of(
                GpuIrOptimizationValidationRules.safetyClean()
        ));
        GpuIrOptimizationValidationRuleArtifactRunner ruleArtifactRunner =
                new GpuIrOptimizationValidationRuleArtifactRunner(registry);

        GpuIrOptimizationValidationOptimizerEnablementArtifactRunner runner =
                new GpuIrOptimizationValidationOptimizerEnablementArtifactRunner(ruleArtifactRunner);
        GpuIrOptimizationValidationOptimizerEnablementArtifact artifact = runner.run(validationReport("customSmokeKernel"));
        Map<String, String> fields = artifact.artifactFields();

        assertSame(registry, runner.registry());
        assertEquals(1, artifact.ruleArtifact().ruleCount());
        assertTrue(artifact.accepted());
        assertEquals("1", fields.get("validationRulesRules"));
        assertEquals("safety.clean", fields.get("validationRulesRegistryResult.0.RuleId"));
        assertEquals("reviewAllowed/productionMutationDisabled", fields.get("optimizerEnablementPolicyVerdict"));
    }

    @Test
    void rejectsNullInputs() {
        GpuIrOptimizationValidationOptimizerEnablementArtifactRunner runner =
                new GpuIrOptimizationValidationOptimizerEnablementArtifactRunner();

        assertThrows(NullPointerException.class, () -> new GpuIrOptimizationValidationOptimizerEnablementArtifactRunner((GpuIrOptimizationValidationRuleRegistry) null));
        assertThrows(NullPointerException.class, () -> new GpuIrOptimizationValidationOptimizerEnablementArtifactRunner((GpuIrOptimizationValidationRuleArtifactRunner) null));
        assertThrows(NullPointerException.class, () -> runner.run(null));
        assertThrows(NullPointerException.class, () -> runner.runArtifactFields(null));
        assertThrows(NullPointerException.class, () -> runner.runGate(null));
        assertThrows(NullPointerException.class, () -> runner.runGateFields(null));
        assertThrows(NullPointerException.class, () -> runner.runBundle(null));
        assertThrows(NullPointerException.class, () -> runner.runBundleFields(null));
        assertThrows(NullPointerException.class, () -> runner.runOptimizerLayerReadiness(null));
        assertThrows(NullPointerException.class, () -> runner.runOptimizerLayerReadinessFields(null));
        assertThrows(NullPointerException.class, () -> runner.runOptimizerLayerReadinessRegression(null, validationReport("nullBaselineKernel")));
        assertThrows(NullPointerException.class, () -> runner.runOptimizerLayerReadinessRegressionFields(null, validationReport("nullBaselineFieldsKernel")));
        assertThrows(NullPointerException.class, () -> runner.runOptimizerLayerReadinessRegressionRuleArtifact(null, validationReport("nullBaselineRuleArtifactKernel")));
        assertThrows(NullPointerException.class, () -> runner.runOptimizerLayerReadinessRegressionRuleArtifactFields(null, validationReport("nullBaselineRuleArtifactFieldsKernel")));
        assertThrows(NullPointerException.class, () -> runner.runProductionPreflight(null));
        assertThrows(NullPointerException.class, () -> runner.runProductionPreflightFields(null));
    }
}
