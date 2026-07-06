package net.sixik.ga_utils.javatogpu.irvalidation;

import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrBinary;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrLiteral;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrVariableRef;
import net.sixik.ga_utils.javatogpu.frontend.ir.model.GpuIrMethod;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrAssignment;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrReturn;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrVariableDeclaration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static net.sixik.ga_utils.javatogpu.irvalidation.GpuIrOptimizationValidationRuleTestFixtures.validate;
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
    void canAlsoExportOptimizerLayerReadinessRuleArtifactFieldsForCiConsumers() {
        GpuIrOptimizationValidationProductionEnablementReadinessRunner runner =
                new GpuIrOptimizationValidationProductionEnablementReadinessRunner();

        GpuIrOptimizationValidationRuleArtifactReport artifact =
                runner.runOptimizerLayerReadinessRuleArtifact(validationReport("readinessLayerRuleKernel"));
        Map<String, String> fields = runner.runOptimizerLayerReadinessRuleArtifactFields(
                validationReport("readinessLayerRuleKernel")
        );

        assertEquals("readinessLayerRuleKernel", artifact.validationReport().methodName());
        assertEquals("fail", artifact.verdict());
        assertEquals("readinessLayerRuleKernel", fields.get("validationRulesMethod"));
        assertEquals("fail", fields.get("validationRulesVerdict"));
        assertEquals("false", fields.get("validationRulesPassed"));
        assertEquals("optimizer.layerReadinessGate", fields.get("validationRulesRegistryResult.0.RuleId"));
        assertEquals("blocked", fields.get("validationRulesRegistryResult.0.Metadata.readinessVerdict"));
        assertEquals("cseLiteralPromotion", fields.get("validationRulesRegistryResult.0.Metadata.firstBlockingLayer"));
    }

    @Test
    void canAlsoExportOptimizerLayerReadinessRuleArtifactAcceptanceFieldsForCiConsumers() {
        GpuIrOptimizationValidationProductionEnablementReadinessRunner runner =
                new GpuIrOptimizationValidationProductionEnablementReadinessRunner();

        GpuIrOptimizationValidationRuleArtifactAcceptance acceptance =
                runner.runOptimizerLayerReadinessRuleArtifactAcceptance(validationReport("readinessLayerAcceptanceKernel"));
        Map<String, String> fields = runner.runOptimizerLayerReadinessRuleArtifactAcceptanceFields(
                validationReport("readinessLayerAcceptanceKernel")
        );

        assertEquals("readinessLayerAcceptanceKernel", acceptance.methodName());
        assertTrue(acceptance.rejected());
        assertEquals("readinessLayerAcceptanceKernel", fields.get("validationRulesAcceptanceMethod"));
        assertEquals("fail", fields.get("validationRulesAcceptanceVerdict"));
        assertEquals("false", fields.get("validationRulesAcceptanceAccepted"));
        assertEquals("true", fields.get("validationRulesAcceptanceRejected"));
        assertEquals("rejected/blockingResultsPresent", fields.get("validationRulesAcceptanceReason"));
        assertEquals("optimizer.layerReadinessGate", fields.get("validationRulesAcceptanceFirstBlockingRuleId"));
    }

    @Test
    void canAlsoExportOptimizerLayerReadinessCiFieldsForCiConsumers() {
        GpuIrOptimizationValidationProductionEnablementReadinessRunner runner =
                new GpuIrOptimizationValidationProductionEnablementReadinessRunner();

        Map<String, String> fields = runner.runOptimizerLayerReadinessCiFields(
                validationReport("readinessLayerCiKernel")
        );

        assertEquals("readinessLayerCiKernel", fields.get("optimizerLayerReadinessMethod"));
        assertEquals("blocked", fields.get("optimizerLayerReadinessVerdict"));
        assertEquals("readinessLayerCiKernel", fields.get("validationRulesMethod"));
        assertEquals("fail", fields.get("validationRulesVerdict"));
        assertEquals("optimizer.layerReadinessGate", fields.get("validationRulesRegistryResult.0.RuleId"));
        assertEquals("readinessLayerCiKernel", fields.get("validationRulesAcceptanceMethod"));
        assertEquals("false", fields.get("validationRulesAcceptanceAccepted"));
        assertEquals("rejected/blockingResultsPresent", fields.get("validationRulesAcceptanceReason"));
        assertEquals("readinessLayerCiKernel", fields.get("optimizerLayerReadinessCiMethod"));
        assertEquals("blocked", fields.get("optimizerLayerReadinessCiVerdict"));
        assertEquals("fail", fields.get("optimizerLayerReadinessCiRuleVerdict"));
        assertEquals("false", fields.get("optimizerLayerReadinessCiAccepted"));
        assertEquals("true", fields.get("optimizerLayerReadinessCiRejected"));
        assertEquals("rejected/blockingResultsPresent", fields.get("optimizerLayerReadinessCiAcceptanceReason"));
        assertEquals("cseLiteralPromotion", fields.get("optimizerLayerReadinessCiFirstBlockingLayer"));
    }

    @Test
    void canAlsoExportCseLayerReadinessFieldsForCiConsumers() {
        GpuIrOptimizationValidationProductionEnablementReadinessRunner runner =
                new GpuIrOptimizationValidationProductionEnablementReadinessRunner();

        GpuIrCommonSubexpressionLayerReadinessSummaryReport readiness = runner.runCseLayerReadiness(
                validationReport("readinessCseLayerKernel")
        );
        Map<String, String> fields = runner.runCseLayerReadinessFields(validationReport("readinessCseLayerKernel"));

        assertEquals("noRewriteWork", readiness.verdict());
        assertTrue(readiness.allLayersReady());
        assertEquals("noRewriteWork", fields.get("cseLayerReadinessVerdict"));
        assertEquals("true", fields.get("cseLayerReadinessAllLayersReady"));
        assertEquals("false", fields.get("cseLayerReadinessHasBlockingLayers"));
        assertEquals("none", fields.get("cseLayerReadinessFirstBlockingLayer"));
    }

    @Test
    void canAlsoExportCseLayerReadinessRuleArtifactFieldsForCiConsumers() {
        GpuIrOptimizationValidationProductionEnablementReadinessRunner runner =
                new GpuIrOptimizationValidationProductionEnablementReadinessRunner();

        GpuIrOptimizationValidationRuleArtifactReport artifact = runner.runCseLayerReadinessRuleArtifact(
                cseBlockedReport("readinessCseLayerRuleKernel")
        );
        Map<String, String> fields = runner.runCseLayerReadinessRuleArtifactFields(
                cseBlockedReport("readinessCseLayerRuleKernel")
        );

        assertEquals("readinessCseLayerRuleKernel", artifact.validationReport().methodName());
        assertEquals("fail", artifact.verdict());
        assertEquals("readinessCseLayerRuleKernel", fields.get("validationRulesMethod"));
        assertEquals("fail", fields.get("validationRulesVerdict"));
        assertEquals("false", fields.get("validationRulesPassed"));
        assertEquals("cse.layerReadinessGate", fields.get("validationRulesRegistryResult.0.RuleId"));
        assertEquals("blocked", fields.get("validationRulesRegistryResult.0.Metadata.readinessVerdict"));
        assertEquals("rewritePolicy", fields.get("validationRulesRegistryResult.0.Metadata.firstBlockingLayer"));
    }

    @Test
    void canAlsoExportCseLayerReadinessRuleArtifactAcceptanceFieldsForCiConsumers() {
        GpuIrOptimizationValidationProductionEnablementReadinessRunner runner =
                new GpuIrOptimizationValidationProductionEnablementReadinessRunner();

        GpuIrOptimizationValidationRuleArtifactAcceptance acceptance = runner.runCseLayerReadinessRuleArtifactAcceptance(
                cseBlockedReport("readinessCseLayerAcceptanceKernel")
        );
        Map<String, String> fields = runner.runCseLayerReadinessRuleArtifactAcceptanceFields(
                cseBlockedReport("readinessCseLayerAcceptanceKernel")
        );

        assertEquals("readinessCseLayerAcceptanceKernel", acceptance.methodName());
        assertTrue(acceptance.rejected());
        assertEquals("readinessCseLayerAcceptanceKernel", fields.get("validationRulesAcceptanceMethod"));
        assertEquals("fail", fields.get("validationRulesAcceptanceVerdict"));
        assertEquals("false", fields.get("validationRulesAcceptanceAccepted"));
        assertEquals("true", fields.get("validationRulesAcceptanceRejected"));
        assertEquals("rejected/blockingResultsPresent", fields.get("validationRulesAcceptanceReason"));
        assertEquals("cse.layerReadinessGate", fields.get("validationRulesAcceptanceFirstBlockingRuleId"));
    }

    @Test
    void canAlsoExportCseLayerReadinessCiFieldsForCiConsumers() {
        GpuIrOptimizationValidationProductionEnablementReadinessRunner runner =
                new GpuIrOptimizationValidationProductionEnablementReadinessRunner();

        Map<String, String> fields = runner.runCseLayerReadinessCiFields(
                cseBlockedReport("readinessCseLayerCiKernel")
        );

        assertEquals("blocked", fields.get("cseLayerReadinessVerdict"));
        assertEquals("rewritePolicy", fields.get("cseLayerReadinessFirstBlockingLayer"));
        assertEquals("readinessCseLayerCiKernel", fields.get("validationRulesMethod"));
        assertEquals("fail", fields.get("validationRulesVerdict"));
        assertEquals("cse.layerReadinessGate", fields.get("validationRulesRegistryResult.0.RuleId"));
        assertEquals("readinessCseLayerCiKernel", fields.get("validationRulesAcceptanceMethod"));
        assertEquals("false", fields.get("validationRulesAcceptanceAccepted"));
        assertEquals("rejected/blockingResultsPresent", fields.get("validationRulesAcceptanceReason"));
        assertEquals("readinessCseLayerCiKernel", fields.get("cseLayerReadinessCiMethod"));
        assertEquals("blocked", fields.get("cseLayerReadinessCiVerdict"));
        assertEquals("fail", fields.get("cseLayerReadinessCiRuleVerdict"));
        assertEquals("false", fields.get("cseLayerReadinessCiAccepted"));
        assertEquals("true", fields.get("cseLayerReadinessCiRejected"));
        assertEquals("rejected/blockingResultsPresent", fields.get("cseLayerReadinessCiAcceptanceReason"));
        assertEquals("rewritePolicy", fields.get("cseLayerReadinessCiFirstBlockingLayer"));
    }

    @Test
    void canAlsoExportAutoVectorizationReadinessFieldsForCiConsumers() {
        GpuIrOptimizationValidationProductionEnablementReadinessRunner runner =
                new GpuIrOptimizationValidationProductionEnablementReadinessRunner();

        GpuIrAutoVectorizationReadinessSummaryReport readiness = runner.runAutoVectorizationReadiness(
                validationReport("readinessAutoLayerKernel")
        );
        Map<String, String> fields = runner.runAutoVectorizationReadinessFields(validationReport("readinessAutoLayerKernel"));

        assertEquals("notReady/noCandidates", readiness.verdict());
        assertEquals("notReady/noCandidates", fields.get("autoVectorizationReadinessVerdict"));
        assertEquals("false", fields.get("autoVectorizationReadinessReadyForPrototypeRewrite"));
        assertEquals("noRewriteCandidates", fields.get("autoVectorizationReadinessFirstBlockingReason"));
    }

    @Test
    void canAlsoExportAutoVectorizationReadinessRuleArtifactFieldsForCiConsumers() {
        GpuIrOptimizationValidationProductionEnablementReadinessRunner runner =
                new GpuIrOptimizationValidationProductionEnablementReadinessRunner();

        GpuIrOptimizationValidationRuleArtifactReport artifact = runner.runAutoVectorizationReadinessRuleArtifact(
                validationReport("readinessAutoLayerRuleKernel")
        );
        Map<String, String> fields = runner.runAutoVectorizationReadinessRuleArtifactFields(
                validationReport("readinessAutoLayerRuleKernel")
        );

        assertEquals("readinessAutoLayerRuleKernel", artifact.validationReport().methodName());
        assertEquals("fail", artifact.verdict());
        assertEquals("readinessAutoLayerRuleKernel", fields.get("validationRulesMethod"));
        assertEquals("fail", fields.get("validationRulesVerdict"));
        assertEquals("false", fields.get("validationRulesPassed"));
        assertEquals("autoVectorization.layerReadinessGate", fields.get("validationRulesRegistryResult.0.RuleId"));
        assertEquals("notReady/noCandidates", fields.get("validationRulesRegistryResult.0.Metadata.readinessVerdict"));
        assertEquals("noRewriteCandidates", fields.get("validationRulesRegistryResult.0.Metadata.firstBlockingReason"));
    }

    @Test
    void canAlsoExportAutoVectorizationReadinessRuleArtifactAcceptanceFieldsForCiConsumers() {
        GpuIrOptimizationValidationProductionEnablementReadinessRunner runner =
                new GpuIrOptimizationValidationProductionEnablementReadinessRunner();

        GpuIrOptimizationValidationRuleArtifactAcceptance acceptance = runner.runAutoVectorizationReadinessRuleArtifactAcceptance(
                validationReport("readinessAutoLayerAcceptanceKernel")
        );
        Map<String, String> fields = runner.runAutoVectorizationReadinessRuleArtifactAcceptanceFields(
                validationReport("readinessAutoLayerAcceptanceKernel")
        );

        assertEquals("readinessAutoLayerAcceptanceKernel", acceptance.methodName());
        assertTrue(acceptance.rejected());
        assertEquals("readinessAutoLayerAcceptanceKernel", fields.get("validationRulesAcceptanceMethod"));
        assertEquals("fail", fields.get("validationRulesAcceptanceVerdict"));
        assertEquals("false", fields.get("validationRulesAcceptanceAccepted"));
        assertEquals("true", fields.get("validationRulesAcceptanceRejected"));
        assertEquals("rejected/blockingResultsPresent", fields.get("validationRulesAcceptanceReason"));
        assertEquals("autoVectorization.layerReadinessGate", fields.get("validationRulesAcceptanceFirstBlockingRuleId"));
    }

    @Test
    void canAlsoExportAutoVectorizationReadinessCiFieldsForCiConsumers() {
        GpuIrOptimizationValidationProductionEnablementReadinessRunner runner =
                new GpuIrOptimizationValidationProductionEnablementReadinessRunner();

        Map<String, String> fields = runner.runAutoVectorizationReadinessCiFields(
                validationReport("readinessAutoLayerCiKernel")
        );

        assertEquals("notReady/noCandidates", fields.get("autoVectorizationReadinessVerdict"));
        assertEquals("readinessAutoLayerCiKernel", fields.get("validationRulesMethod"));
        assertEquals("fail", fields.get("validationRulesVerdict"));
        assertEquals("autoVectorization.layerReadinessGate", fields.get("validationRulesRegistryResult.0.RuleId"));
        assertEquals("readinessAutoLayerCiKernel", fields.get("validationRulesAcceptanceMethod"));
        assertEquals("false", fields.get("validationRulesAcceptanceAccepted"));
        assertEquals("rejected/blockingResultsPresent", fields.get("validationRulesAcceptanceReason"));
        assertEquals("readinessAutoLayerCiKernel", fields.get("autoVectorizationReadinessCiMethod"));
        assertEquals("notReady/noCandidates", fields.get("autoVectorizationReadinessCiVerdict"));
        assertEquals("fail", fields.get("autoVectorizationReadinessCiRuleVerdict"));
        assertEquals("false", fields.get("autoVectorizationReadinessCiAccepted"));
        assertEquals("true", fields.get("autoVectorizationReadinessCiRejected"));
        assertEquals("rejected/blockingResultsPresent", fields.get("autoVectorizationReadinessCiAcceptanceReason"));
        assertEquals("noRewriteCandidates", fields.get("autoVectorizationReadinessCiFirstBlockingReason"));
    }

    @Test
    void canAlsoExportCiGateIndexFieldsForCiConsumers() {
        GpuIrOptimizationValidationProductionEnablementReadinessRunner runner =
                new GpuIrOptimizationValidationProductionEnablementReadinessRunner();

        GpuIrOptimizationValidationCiGateIndex index = runner.runCiGateIndex(validationReport("readinessCiGateIndexKernel"));
        Map<String, String> fields = runner.runCiGateIndexFields(validationReport("readinessCiGateIndexKernel"));

        assertEquals("readinessCiGateIndexKernel", index.methodName());
        assertTrue(index.rejected());
        assertEquals("autoVectorization", index.firstRejectedGate());
        assertEquals("readinessCiGateIndexKernel", fields.get("optimizerCiGateIndexMethod"));
        assertEquals("rejected", fields.get("optimizerCiGateIndexVerdict"));
        assertEquals("false", fields.get("optimizerCiGateIndexAccepted"));
        assertEquals("true", fields.get("optimizerCiGateIndexRejected"));
        assertEquals("autoVectorization", fields.get("optimizerCiGateIndexFirstRejectedGate"));
        assertEquals("true", fields.get("optimizerCiGateIndexCseAccepted"));
        assertEquals("false", fields.get("optimizerCiGateIndexAutoVectorizationAccepted"));
        assertEquals("false", fields.get("optimizerCiGateIndexOptimizerAccepted"));
        assertEquals("consistent", fields.get("optimizerCiGateIndexConsistencyVerdict"));
        assertEquals("true", fields.get("optimizerCiGateIndexConsistencyConsistent"));
        assertEquals("0", fields.get("optimizerCiGateIndexConsistencyFailedChecks"));
        assertEquals("rejected", fields.get("optimizerCiGateIndexAcceptanceVerdict"));
        assertEquals("false", fields.get("optimizerCiGateIndexAcceptanceAccepted"));
        assertEquals("true", fields.get("optimizerCiGateIndexAcceptanceFailBuild"));
        assertEquals("rejected/gateRejected", fields.get("optimizerCiGateIndexAcceptanceReason"));
        assertEquals("readinessCiGateIndexKernel", fields.get("cseLayerReadinessCiMethod"));
        assertEquals("readinessCiGateIndexKernel", fields.get("autoVectorizationReadinessCiMethod"));
        assertEquals("readinessCiGateIndexKernel", fields.get("optimizerLayerReadinessCiMethod"));
        assertEquals(
                "readinessCiGateIndexKernel",
                fields.get("optimizerCiGateIndexCseGate.cseLayerReadinessCiMethod")
        );
        assertEquals(
                "cse.layerReadinessGate",
                fields.get("optimizerCiGateIndexCseGate.RuleArtifact.validationRulesRegistryResult.0.RuleId")
        );
        assertEquals(
                "autoVectorization.layerReadinessGate",
                fields.get("optimizerCiGateIndexAutoVectorizationGate.RuleArtifact.validationRulesRegistryResult.0.RuleId")
        );
        assertEquals(
                "optimizer.layerReadinessGate",
                fields.get("optimizerCiGateIndexOptimizerGate.RuleArtifact.validationRulesRegistryResult.0.RuleId")
        );
        assertEquals(
                "accepted/pass",
                fields.get("optimizerCiGateIndexCseGate.Acceptance.validationRulesAcceptanceReason")
        );
        assertEquals(
                "rejected/blockingResultsPresent",
                fields.get("optimizerCiGateIndexAutoVectorizationGate.Acceptance.validationRulesAcceptanceReason")
        );
        assertEquals(
                "rejected/blockingResultsPresent",
                fields.get("optimizerCiGateIndexOptimizerGate.Acceptance.validationRulesAcceptanceReason")
        );
    }

    @Test
    void canAlsoExportOptimizerGateSnapshotFieldsForCiConsumers() {
        GpuIrOptimizationValidationProductionEnablementReadinessRunner runner =
                new GpuIrOptimizationValidationProductionEnablementReadinessRunner();

        GpuIrOptimizerGateSnapshot snapshot = runner.runOptimizerGateSnapshot(validationReport("readinessGateKernel"));
        Map<String, String> fields = runner.runOptimizerGateSnapshotFields(validationReport("readinessGateKernel"));

        assertEquals("none", snapshot.explanation().source());
        assertEquals("false", fields.get("optimizerGateBlocked"));
        assertEquals("none", fields.get("optimizerGateSource"));
        assertEquals("none", fields.get("optimizerGateFamily"));
        assertEquals("consistent", fields.get("optimizerGateConsistencyVerdict"));
        assertEquals("true", fields.get("optimizerGateConsistencyConsistent"));
        assertEquals("accepted", fields.get("optimizerGateAcceptanceVerdict"));
        assertEquals("false", fields.get("optimizerGateAcceptanceFailBuild"));
    }

    @Test
    void canStoreAndCompareOptimizerBlockerBaselineFieldsForCiConsumers() {
        GpuIrOptimizationValidationProductionEnablementReadinessRunner runner =
                new GpuIrOptimizationValidationProductionEnablementReadinessRunner();
        Map<String, String> baselineFields = runner.runOptimizerBlockerBaselineSnapshotFields(
                validationReport("optimizerBlockerBaselineKernel")
        );

        Map<String, String> comparisonFields = runner.runOptimizerBlockerBaselineComparisonFields(
                baselineFields,
                validationReport("optimizerBlockerBaselineKernel")
        );

        assertEquals("optimizerBlockerBaselineKernel", baselineFields.get("optimizerBlockerBaselineSnapshotMethod"));
        assertEquals("blocked", baselineFields.get("optimizerBlockerBaselineSnapshotVerdict"));
        assertEquals("autoVectorization", baselineFields.get("optimizerBlockerBaselineSnapshotSource"));
        assertEquals("candidateDiscovery.noRewriteCandidates", baselineFields.get("optimizerBlockerBaselineSnapshotFamily"));
        assertEquals("optimizerBlockerBaselineKernel", comparisonFields.get("optimizerBlockerBaselineComparisonMethod"));
        assertEquals("unchanged", comparisonFields.get("optimizerBlockerBaselineComparisonOutcome"));
        assertEquals("3", comparisonFields.get("optimizerBlockerBaselineComparisonBaselineScore"));
        assertEquals("3", comparisonFields.get("optimizerBlockerBaselineComparisonCurrentScore"));
        assertEquals("0", comparisonFields.get("optimizerBlockerBaselineComparisonScoreDelta"));
        assertEquals("autoVectorization->autoVectorization", comparisonFields.get("optimizerBlockerBaselineComparisonSourceTransition"));
    }

    @Test
    void canCompareOptimizerBlockerBaselineRegressionForCiConsumers() {
        GpuIrOptimizationValidationProductionEnablementReadinessRunner runner =
                new GpuIrOptimizationValidationProductionEnablementReadinessRunner();
        GpuIrOptimizationValidationOptimizerBlockerBaselineSnapshot baseline = new GpuIrOptimizationValidationOptimizerBlockerBaselineSnapshot(
                "optimizerBlockerRegressionKernel",
                "blocked",
                "autoVectorization",
                "candidateDiscovery.noRewriteCandidates",
                "collectRewriteCandidates"
        );

        Map<String, String> fields = runner.runOptimizerBlockerBaselineComparisonFields(
                baseline,
                cseBlockedReport("optimizerBlockerRegressionKernel")
        );

        assertEquals("optimizerBlockerRegressionKernel", fields.get("optimizerBlockerBaselineComparisonMethod"));
        assertEquals("regressed", fields.get("optimizerBlockerBaselineComparisonOutcome"));
        assertEquals("3", fields.get("optimizerBlockerBaselineComparisonBaselineScore"));
        assertEquals("2", fields.get("optimizerBlockerBaselineComparisonCurrentScore"));
        assertEquals("-1", fields.get("optimizerBlockerBaselineComparisonScoreDelta"));
        assertEquals("autoVectorization->cseRewritePolicy", fields.get("optimizerBlockerBaselineComparisonSourceTransition"));
        assertEquals("source", fields.get("optimizerBlockerBaselineComparisonFirstChangedDimension"));
    }

    @Test
    void canExportCombinedReadinessAndBlockerBaselineCiFieldsForCiConsumers() {
        GpuIrOptimizationValidationProductionEnablementReadinessRunner runner =
                new GpuIrOptimizationValidationProductionEnablementReadinessRunner();
        GpuIrOptimizationValidationReport baselineReport = validationReport("optimizerCombinedBaselineKernel");
        Map<String, String> readinessBaselineFields = runner.runOptimizerLayerReadinessBaselineSnapshotFields(
                baselineReport
        );
        Map<String, String> blockerBaselineFields = runner.runOptimizerBlockerBaselineSnapshotFields(
                baselineReport
        );

        Map<String, String> fields = runner.runOptimizerReadinessAndBlockerBaselineCiFields(
                readinessBaselineFields,
                blockerBaselineFields,
                validationReport("optimizerCombinedBaselineKernel")
        );

        assertEquals("optimizerCombinedBaselineKernel", fields.get("optimizerLayerReadinessBaselineCiMethod"));
        assertEquals("unchanged", fields.get("optimizerLayerReadinessBaselineCiOutcome"));
        assertEquals("accepted/noRegression", fields.get("optimizerLayerReadinessBaselineCiDecision"));
        assertEquals("false", fields.get("optimizerLayerReadinessBaselineCiFailBuild"));
        assertEquals("optimizerCombinedBaselineKernel", fields.get("optimizerBlockerBaselineSnapshotMethod"));
        assertEquals("blocked", fields.get("optimizerBlockerBaselineSnapshotVerdict"));
        assertEquals("autoVectorization", fields.get("optimizerBlockerBaselineSnapshotSource"));
        assertEquals("optimizerCombinedBaselineKernel", fields.get("optimizerBlockerBaselineComparisonMethod"));
        assertEquals("unchanged", fields.get("optimizerBlockerBaselineComparisonOutcome"));
        assertEquals("3", fields.get("optimizerBlockerBaselineComparisonBaselineScore"));
        assertEquals("3", fields.get("optimizerBlockerBaselineComparisonCurrentScore"));
        assertEquals("0", fields.get("optimizerBlockerBaselineComparisonScoreDelta"));
        assertEquals("autoVectorization->autoVectorization", fields.get("optimizerBlockerBaselineComparisonSourceTransition"));
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
        assertEquals(
                "readinessBaselineCiKernel",
                fields.get("optimizerLayerReadinessBaselineCiComparison.optimizerLayerReadinessBaselineComparisonMethod")
        );
        assertEquals(
                "unchanged",
                fields.get("optimizerLayerReadinessBaselineCiComparison.optimizerLayerReadinessBaselineComparisonOutcome")
        );
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
        assertEquals(
                "readinessRegressionCiKernel",
                fields.get("optimizerLayerReadinessRegressionCiRegression.optimizerLayerReadinessRegressionMethod")
        );
        assertEquals(
                "optimizer.layerReadinessRegressionGate",
                fields.get("optimizerLayerReadinessRegressionCiRuleArtifact.validationRulesRegistryResult.0.RuleId")
        );
        assertEquals(
                "accepted/pass",
                fields.get("optimizerLayerReadinessRegressionCiAcceptance.validationRulesAcceptanceReason")
        );
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
        assertThrows(NullPointerException.class, () -> runner.runOptimizerLayerReadinessRuleArtifact(null));
        assertThrows(NullPointerException.class, () -> runner.runOptimizerLayerReadinessRuleArtifactFields(null));
        assertThrows(NullPointerException.class, () -> runner.runOptimizerLayerReadinessRuleArtifactAcceptance(null));
        assertThrows(NullPointerException.class, () -> runner.runOptimizerLayerReadinessRuleArtifactAcceptanceFields(null));
        assertThrows(NullPointerException.class, () -> runner.runOptimizerLayerReadinessCiFields(null));
        assertThrows(NullPointerException.class, () -> runner.runCseLayerReadiness(null));
        assertThrows(NullPointerException.class, () -> runner.runCseLayerReadinessFields(null));
        assertThrows(NullPointerException.class, () -> runner.runCseLayerReadinessRuleArtifact(null));
        assertThrows(NullPointerException.class, () -> runner.runCseLayerReadinessRuleArtifactFields(null));
        assertThrows(NullPointerException.class, () -> runner.runCseLayerReadinessRuleArtifactAcceptance(null));
        assertThrows(NullPointerException.class, () -> runner.runCseLayerReadinessRuleArtifactAcceptanceFields(null));
        assertThrows(NullPointerException.class, () -> runner.runCseLayerReadinessCiFields(null));
        assertThrows(NullPointerException.class, () -> runner.runAutoVectorizationReadiness(null));
        assertThrows(NullPointerException.class, () -> runner.runAutoVectorizationReadinessFields(null));
        assertThrows(NullPointerException.class, () -> runner.runAutoVectorizationReadinessRuleArtifact(null));
        assertThrows(NullPointerException.class, () -> runner.runAutoVectorizationReadinessRuleArtifactFields(null));
        assertThrows(NullPointerException.class, () -> runner.runAutoVectorizationReadinessRuleArtifactAcceptance(null));
        assertThrows(NullPointerException.class, () -> runner.runAutoVectorizationReadinessRuleArtifactAcceptanceFields(null));
        assertThrows(NullPointerException.class, () -> runner.runAutoVectorizationReadinessCiFields(null));
        assertThrows(NullPointerException.class, () -> runner.runCiGateIndex(null));
        assertThrows(NullPointerException.class, () -> runner.runCiGateIndexFields(null));
        assertThrows(NullPointerException.class, () -> runner.runOptimizerGateSnapshot(null));
        assertThrows(NullPointerException.class, () -> runner.runOptimizerGateSnapshotFields(null));
        assertThrows(NullPointerException.class, () -> runner.runOptimizerBlockerIndex(null));
        assertThrows(NullPointerException.class, () -> runner.runOptimizerBlockerIndexFields(null));
        assertThrows(NullPointerException.class, () -> runner.runOptimizerBlockerBaselineSnapshot(null));
        assertThrows(NullPointerException.class, () -> runner.runOptimizerBlockerBaselineSnapshotFields(null));
        assertThrows(NullPointerException.class, () -> runner.runOptimizerBlockerBaselineComparison((GpuIrOptimizationValidationOptimizerBlockerBaselineSnapshot) null, validationReport("nullBlockerBaselineComparisonKernel")));
        assertThrows(NullPointerException.class, () -> runner.runOptimizerBlockerBaselineComparison((Map<String, String>) null, validationReport("nullBlockerBaselineComparisonFieldsKernel")));
        assertThrows(NullPointerException.class, () -> runner.runOptimizerBlockerBaselineComparisonFields((GpuIrOptimizationValidationOptimizerBlockerBaselineSnapshot) null, validationReport("nullBlockerBaselineComparisonResultKernel")));
        assertThrows(NullPointerException.class, () -> runner.runOptimizerBlockerBaselineComparisonFields((Map<String, String>) null, validationReport("nullBlockerBaselineComparisonResultFieldsKernel")));
        assertThrows(NullPointerException.class, () -> runner.runOptimizerReadinessAndBlockerBaselineCiFields((GpuIrOptimizationValidationOptimizerLayerReadinessBaselineSnapshot) null, runner.runOptimizerBlockerBaselineSnapshot(validationReport("nullCombinedBlockerBaselineKernel")), validationReport("nullCombinedReadinessBaselineKernel")));
        assertThrows(NullPointerException.class, () -> runner.runOptimizerReadinessAndBlockerBaselineCiFields(runner.runOptimizerLayerReadinessBaselineSnapshot(validationReport("nullCombinedReadinessBaselineKernel")), (GpuIrOptimizationValidationOptimizerBlockerBaselineSnapshot) null, validationReport("nullCombinedBlockerBaselineKernel")));
        assertThrows(NullPointerException.class, () -> runner.runOptimizerReadinessAndBlockerBaselineCiFields(runner.runOptimizerLayerReadinessBaselineSnapshot(validationReport("nullCombinedReadinessBaselineKernel")), runner.runOptimizerBlockerBaselineSnapshot(validationReport("nullCombinedBlockerBaselineKernel")), null));
        assertThrows(NullPointerException.class, () -> runner.runOptimizerReadinessAndBlockerBaselineCiFields((Map<String, String>) null, runner.runOptimizerBlockerBaselineSnapshotFields(validationReport("nullCombinedBlockerBaselineFieldsKernel")), validationReport("nullCombinedReadinessBaselineFieldsKernel")));
        assertThrows(NullPointerException.class, () -> runner.runOptimizerReadinessAndBlockerBaselineCiFields(runner.runOptimizerLayerReadinessBaselineSnapshotFields(validationReport("nullCombinedReadinessBaselineFieldsKernel")), (Map<String, String>) null, validationReport("nullCombinedBlockerBaselineFieldsKernel")));
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

    private static GpuIrOptimizationValidationReport cseBlockedReport(String methodName) {
        return validate(new GpuIrMethod(methodName, List.of(
                new GpuIrVariableDeclaration("int", "z", new GpuIrVariableRef("x")),
                new GpuIrVariableDeclaration("int", "first", new GpuIrBinary("+", new GpuIrVariableRef("z"), new GpuIrLiteral("1"))),
                new GpuIrAssignment(new GpuIrVariableRef("z"), new GpuIrLiteral("7")),
                new GpuIrVariableDeclaration("int", "second", new GpuIrBinary("+", new GpuIrVariableRef("z"), new GpuIrLiteral("1"))),
                new GpuIrReturn(null)
        )));
    }
}
