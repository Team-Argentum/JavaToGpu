package net.sixik.ga_utils.javatogpu.irvalidation;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuIrOptimizationValidationOptimizerBlockerBaselineComparisonReportTest {
    @Test
    void reportsUnchangedWhenCurrentBlockerMatchesBaseline() {
        GpuIrOptimizationValidationReport current = GpuIrOptimizationValidationRuleTestFixtures.validationReport("kernel");
        GpuIrOptimizationValidationOptimizerBlockerBaselineSnapshot baseline =
                GpuIrOptimizationValidationOptimizerBlockerBaselineSnapshot.from(current);

        GpuIrOptimizationValidationOptimizerBlockerBaselineComparisonReport comparison =
                GpuIrOptimizationValidationOptimizerBlockerBaselineComparisonReport.from(
                        baseline,
                        current.optimizerBlockerIndex()
                );
        Map<String, String> fields = comparison.artifactFields();

        assertEquals("unchanged", comparison.outcome());
        assertEquals(3, comparison.baselineScore());
        assertEquals(3, comparison.currentScore());
        assertEquals(0, comparison.scoreDelta());
        assertEquals("false", fields.get("optimizerBlockerBaselineComparisonChanged"));
        assertEquals("3", fields.get("optimizerBlockerBaselineComparisonBaselineScore"));
        assertEquals("3", fields.get("optimizerBlockerBaselineComparisonCurrentScore"));
        assertEquals("0", fields.get("optimizerBlockerBaselineComparisonScoreDelta"));
        assertEquals("autoVectorization->autoVectorization", fields.get("optimizerBlockerBaselineComparisonSourceTransition"));
        assertEquals("candidateDiscovery.noRewriteCandidates->candidateDiscovery.noRewriteCandidates", fields.get("optimizerBlockerBaselineComparisonFamilyTransition"));
        assertTrue(comparison.ciSummaryLine().contains("scoreDelta=0"));
    }

    @Test
    void reportsRegressionWhenFirstBlockerMovesFromAutoVectorizationBackToCse() {
        GpuIrOptimizationValidationOptimizerBlockerBaselineSnapshot baseline = new GpuIrOptimizationValidationOptimizerBlockerBaselineSnapshot(
                "kernel",
                "blocked",
                "autoVectorization",
                "candidateDiscovery.noRewriteCandidates",
                "collectRewriteCandidates"
        );
        GpuIrOptimizationValidationOptimizerBlockerIndex current = new GpuIrOptimizationValidationOptimizerBlockerIndex(
                "kernel",
                "blocked",
                "cseRewritePolicy",
                "skipReason.NOT_LOCAL_REUSE",
                "NOT_LOCAL_REUSE",
                "proveLocalReuseOrKeepExpressionInline",
                "hint",
                "skipReason.NOT_LOCAL_REUSE",
                "candidateDiscovery.noRewriteCandidates"
        );

        GpuIrOptimizationValidationOptimizerBlockerBaselineComparisonReport comparison =
                GpuIrOptimizationValidationOptimizerBlockerBaselineComparisonReport.from(baseline, current);

        assertEquals("regressed", comparison.outcome());
        assertTrue(comparison.regressed());
        assertEquals(3, comparison.baselineScore());
        assertEquals(2, comparison.currentScore());
        assertEquals(-1, comparison.scoreDelta());
        assertEquals("source", comparison.firstChangedDimension().orElseThrow());
        assertTrue(comparison.summary().contains("scoreDelta=-1"));
        assertTrue(comparison.summary().contains("sourceTransition=autoVectorization->cseRewritePolicy"));
    }

    @Test
    void reportsImprovementWhenCurrentBlockerBecomesReady() {
        GpuIrOptimizationValidationOptimizerBlockerBaselineSnapshot baseline = new GpuIrOptimizationValidationOptimizerBlockerBaselineSnapshot(
                "kernel",
                "blocked",
                "autoVectorization",
                "candidateDiscovery.noRewriteCandidates",
                "collectRewriteCandidates"
        );
        GpuIrOptimizationValidationOptimizerBlockerIndex current = new GpuIrOptimizationValidationOptimizerBlockerIndex(
                "kernel",
                "ready",
                "none",
                "none",
                "none",
                "none",
                "clear",
                "none",
                "none"
        );

        GpuIrOptimizationValidationOptimizerBlockerBaselineComparisonReport comparison =
                GpuIrOptimizationValidationOptimizerBlockerBaselineComparisonReport.from(baseline, current);

        assertEquals("improved", comparison.outcome());
        assertTrue(comparison.improved());
        assertEquals(3, comparison.baselineScore());
        assertEquals(4, comparison.currentScore());
        assertEquals(1, comparison.scoreDelta());
        assertEquals("verdict", comparison.firstChangedDimension().orElseThrow());
    }

    @Test
    void reportsChangedWhenScoreIsStableButBlockerFamilyMoves() {
        GpuIrOptimizationValidationOptimizerBlockerBaselineSnapshot baseline = new GpuIrOptimizationValidationOptimizerBlockerBaselineSnapshot(
                "kernel",
                "blocked",
                "autoVectorization",
                "candidateDiscovery.noRewriteCandidates",
                "collectRewriteCandidates"
        );
        GpuIrOptimizationValidationOptimizerBlockerIndex current = new GpuIrOptimizationValidationOptimizerBlockerIndex(
                "kernel",
                "blocked",
                "autoVectorization",
                "proof.proofDecisionBlocksRewrite",
                "proofDecisionBlocksRewrite",
                "completeProofLayersBeforeRewriteReview",
                "hint",
                "proof.proofDecisionBlocksRewrite",
                "candidateDiscovery.noRewriteCandidates"
        );

        GpuIrOptimizationValidationOptimizerBlockerBaselineComparisonReport comparison =
                GpuIrOptimizationValidationOptimizerBlockerBaselineComparisonReport.from(baseline, current);
        Map<String, String> fields = comparison.artifactFields();

        assertEquals("changed", comparison.outcome());
        assertEquals(0, comparison.scoreDelta());
        assertEquals("family", comparison.firstChangedDimension().orElseThrow());
        assertEquals("changed", fields.get("optimizerBlockerBaselineComparisonOutcome"));
        assertEquals("0", fields.get("optimizerBlockerBaselineComparisonScoreDelta"));
    }

    @Test
    void rejectsMismatchedMethods() {
        GpuIrOptimizationValidationOptimizerBlockerBaselineSnapshot baseline =
                GpuIrOptimizationValidationOptimizerBlockerBaselineSnapshot.from(
                        GpuIrOptimizationValidationRuleTestFixtures.validationReport("baseline")
                );
        GpuIrOptimizationValidationOptimizerBlockerIndex current =
                GpuIrOptimizationValidationRuleTestFixtures.validationReport("current").optimizerBlockerIndex();

        assertThrows(IllegalArgumentException.class, () ->
                GpuIrOptimizationValidationOptimizerBlockerBaselineComparisonReport.from(baseline, current));
    }
}
