package net.sixik.ga_utils.javatogpu.irvalidation;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static net.sixik.ga_utils.javatogpu.irvalidation.GpuIrOptimizationValidationRuleTestFixtures.validationReport;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuIrOptimizationValidationOptimizerLayerReadinessCiSummaryTest {
    @Test
    void exportsRegressionCiSummaryWithNestedSourceArtifacts() {
        GpuIrOptimizationValidationOptimizerEnablementArtifactRunner runner =
                new GpuIrOptimizationValidationOptimizerEnablementArtifactRunner();
        GpuIrOptimizationValidationOptimizerLayerReadinessSummaryReport baseline =
                runner.runOptimizerLayerReadiness(validationReport("layerRegressionSummaryKernel"));
        GpuIrOptimizationValidationOptimizerLayerReadinessRegressionReport regression =
                runner.runOptimizerLayerReadinessRegression(
                        baseline,
                        validationReport("layerRegressionSummaryKernel")
                );
        GpuIrOptimizationValidationRuleArtifactReport ruleArtifact =
                runner.runOptimizerLayerReadinessRegressionRuleArtifact(
                        baseline,
                        validationReport("layerRegressionSummaryKernel")
                );

        GpuIrOptimizationValidationOptimizerLayerReadinessRegressionCiSummary summary =
                GpuIrOptimizationValidationOptimizerLayerReadinessRegressionCiSummary.from(
                        regression,
                        ruleArtifact
                );
        Map<String, String> fields = summary.artifactFields();

        assertEquals("layerRegressionSummaryKernel", summary.methodName());
        assertEquals("unchanged", summary.outcome());
        assertEquals("pass", summary.ruleVerdict());
        assertEquals("accepted/pass", summary.acceptanceReason());
        assertTrue(summary.accepted());
        assertFalse(summary.rejected());
        assertFalse(summary.acceptedWithWarnings());
        assertEquals("layerRegressionSummaryKernel", fields.get("optimizerLayerReadinessRegressionCiMethod"));
        assertEquals("unchanged", fields.get("optimizerLayerReadinessRegressionCiOutcome"));
        assertEquals("pass", fields.get("optimizerLayerReadinessRegressionCiRuleVerdict"));
        assertEquals("accepted/pass", fields.get("optimizerLayerReadinessRegressionCiAcceptanceReason"));
        assertEquals(
                "layerRegressionSummaryKernel",
                fields.get("optimizerLayerReadinessRegressionCiRegression.optimizerLayerReadinessRegressionMethod")
        );
        assertEquals(
                "unchanged",
                fields.get("optimizerLayerReadinessRegressionCiRegression.optimizerLayerReadinessRegressionOutcome")
        );
        assertEquals(
                "optimizer.layerReadinessRegressionGate",
                fields.get("optimizerLayerReadinessRegressionCiRuleArtifact.validationRulesRegistryResult.0.RuleId")
        );
        assertEquals(
                "accepted/pass",
                fields.get("optimizerLayerReadinessRegressionCiAcceptance.validationRulesAcceptanceReason")
        );
        assertTrue(fields.get("optimizerLayerReadinessRegressionCiCiSummaryLine")
                .contains("outcome=unchanged"));
        assertThrows(UnsupportedOperationException.class, () -> fields.put("x", "y"));
    }

    @Test
    void exportsBaselineCiSummaryWithNestedComparisonArtifact() {
        GpuIrOptimizationValidationOptimizerEnablementArtifactRunner runner =
                new GpuIrOptimizationValidationOptimizerEnablementArtifactRunner();
        GpuIrOptimizationValidationOptimizerLayerReadinessBaselineSnapshot baseline =
                runner.runOptimizerLayerReadinessBaselineSnapshot(validationReport("layerBaselineSummaryKernel"));
        GpuIrOptimizationValidationOptimizerLayerReadinessBaselineComparisonReport comparison =
                runner.runOptimizerLayerReadinessBaselineComparison(
                        baseline,
                        validationReport("layerBaselineSummaryKernel")
                );

        GpuIrOptimizationValidationOptimizerLayerReadinessBaselineCiSummary summary =
                new GpuIrOptimizationValidationOptimizerLayerReadinessBaselineCiSummary(comparison);
        Map<String, String> fields = summary.artifactFields();

        assertEquals("layerBaselineSummaryKernel", summary.methodName());
        assertEquals("unchanged", summary.outcome());
        assertEquals("accepted/noRegression", summary.decision());
        assertTrue(summary.accepted());
        assertFalse(summary.rejected());
        assertFalse(summary.failBuild());
        assertEquals("layerBaselineSummaryKernel", fields.get("optimizerLayerReadinessBaselineCiMethod"));
        assertEquals("unchanged", fields.get("optimizerLayerReadinessBaselineCiOutcome"));
        assertEquals("accepted/noRegression", fields.get("optimizerLayerReadinessBaselineCiDecision"));
        assertEquals("false", fields.get("optimizerLayerReadinessBaselineCiFailBuild"));
        assertEquals(
                "layerBaselineSummaryKernel",
                fields.get("optimizerLayerReadinessBaselineCiComparison.optimizerLayerReadinessBaselineComparisonMethod")
        );
        assertEquals(
                "unchanged",
                fields.get("optimizerLayerReadinessBaselineCiComparison.optimizerLayerReadinessBaselineComparisonOutcome")
        );
        assertTrue(fields.get("optimizerLayerReadinessBaselineCiCiSummaryLine")
                .contains("decision=accepted/noRegression"));
        assertThrows(UnsupportedOperationException.class, () -> fields.put("x", "y"));
    }

    @Test
    void failClosesInvalidBaselineArtifactFields() {
        Map<String, String> fields = GpuIrOptimizationValidationOptimizerLayerReadinessBaselineCiSummary
                .invalidBaselineArtifactFields(
                        "optimizerLayerReadinessBaselineCi",
                        "currentKernel",
                        "baselineKernel",
                        new IllegalArgumentException("missing LayerOrder")
                );

        assertEquals("currentKernel", fields.get("optimizerLayerReadinessBaselineCiMethod"));
        assertEquals("currentKernel", fields.get("optimizerLayerReadinessBaselineCiCurrentMethod"));
        assertEquals("baselineKernel", fields.get("optimizerLayerReadinessBaselineCiBaselineSourceMethod"));
        assertEquals("invalidBaseline", fields.get("optimizerLayerReadinessBaselineCiOutcome"));
        assertEquals("fail/invalidBaseline", fields.get("optimizerLayerReadinessBaselineCiDecision"));
        assertEquals("false", fields.get("optimizerLayerReadinessBaselineCiAccepted"));
        assertEquals("true", fields.get("optimizerLayerReadinessBaselineCiRejected"));
        assertEquals("true", fields.get("optimizerLayerReadinessBaselineCiFailBuild"));
        assertEquals("true", fields.get("optimizerLayerReadinessBaselineCiInvalidBaseline"));
        assertEquals("IllegalArgumentException", fields.get("optimizerLayerReadinessBaselineCiFailureType"));
        assertEquals("missing LayerOrder", fields.get("optimizerLayerReadinessBaselineCiFailureMessage"));
        assertTrue(fields.get("optimizerLayerReadinessBaselineCiCiSummaryLine")
                .contains("outcome=invalidBaseline"));
        assertThrows(UnsupportedOperationException.class, () -> fields.put("x", "y"));
    }

    @Test
    void rejectsInvalidInputsAndPrefixes() {
        GpuIrOptimizationValidationOptimizerEnablementArtifactRunner runner =
                new GpuIrOptimizationValidationOptimizerEnablementArtifactRunner();
        GpuIrOptimizationValidationOptimizerLayerReadinessSummaryReport baseline =
                runner.runOptimizerLayerReadiness(validationReport("invalidSummaryKernel"));
        GpuIrOptimizationValidationOptimizerLayerReadinessRegressionReport regression =
                runner.runOptimizerLayerReadinessRegression(
                        baseline,
                        validationReport("invalidSummaryKernel")
                );
        GpuIrOptimizationValidationRuleArtifactReport ruleArtifact =
                runner.runOptimizerLayerReadinessRegressionRuleArtifact(
                        baseline,
                        validationReport("invalidSummaryKernel")
                );
        GpuIrOptimizationValidationOptimizerLayerReadinessRegressionCiSummary regressionSummary =
                GpuIrOptimizationValidationOptimizerLayerReadinessRegressionCiSummary.from(
                        regression,
                        ruleArtifact
                );
        GpuIrOptimizationValidationOptimizerLayerReadinessBaselineCiSummary baselineSummary =
                new GpuIrOptimizationValidationOptimizerLayerReadinessBaselineCiSummary(
                        runner.runOptimizerLayerReadinessBaselineComparison(
                                runner.runOptimizerLayerReadinessBaselineSnapshot(validationReport("invalidBaselineSummaryKernel")),
                                validationReport("invalidBaselineSummaryKernel")
                        )
                );

        assertThrows(NullPointerException.class, () -> GpuIrOptimizationValidationOptimizerLayerReadinessRegressionCiSummary.from(
                null,
                ruleArtifact
        ));
        assertThrows(NullPointerException.class, () -> GpuIrOptimizationValidationOptimizerLayerReadinessRegressionCiSummary.from(
                regression,
                null
        ));
        assertThrows(IllegalArgumentException.class, () -> regressionSummary.artifactFields(""));
        assertThrows(NullPointerException.class, () -> new GpuIrOptimizationValidationOptimizerLayerReadinessBaselineCiSummary(null));
        assertThrows(IllegalArgumentException.class, () -> baselineSummary.artifactFields(""));
        assertThrows(IllegalArgumentException.class, () -> GpuIrOptimizationValidationOptimizerLayerReadinessBaselineCiSummary
                .invalidBaselineArtifactFields("", "kernel", new IllegalArgumentException("broken")));
        assertThrows(NullPointerException.class, () -> GpuIrOptimizationValidationOptimizerLayerReadinessBaselineCiSummary
                .invalidBaselineArtifactFields("kernel", null));
    }
}
