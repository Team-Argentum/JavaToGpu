package net.sixik.ga_utils.javatogpu.irvalidation;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static net.sixik.ga_utils.javatogpu.irvalidation.GpuIrOptimizationValidationRuleTestFixtures.validationReport;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuIrOptimizationValidationCurrentReadinessCiSummaryTest {
    @Test
    void exportsCseCurrentReadinessCiSummary() {
        GpuIrOptimizationValidationOptimizerEnablementArtifactRunner runner =
                new GpuIrOptimizationValidationOptimizerEnablementArtifactRunner();
        GpuIrOptimizationValidationReport report = validationReport("currentCseCiSummaryKernel");
        GpuIrCommonSubexpressionLayerReadinessSummaryReport readiness = runner.runCseLayerReadiness(report);
        GpuIrOptimizationValidationRuleArtifactReport ruleArtifact = runner.runCseLayerReadinessRuleArtifact(report);
        GpuIrOptimizationValidationRuleArtifactAcceptance acceptance =
                GpuIrOptimizationValidationRuleArtifactAcceptance.from(ruleArtifact);

        GpuIrCommonSubexpressionLayerReadinessCiSummary summary =
                new GpuIrCommonSubexpressionLayerReadinessCiSummary(readiness, ruleArtifact, acceptance);
        Map<String, String> fields = summary.artifactFields();

        assertEquals("currentCseCiSummaryKernel", summary.methodName());
        assertEquals("noRewriteWork", summary.verdict());
        assertEquals("pass", summary.ruleVerdict());
        assertEquals("accepted/pass", summary.acceptanceReason());
        assertTrue(summary.accepted());
        assertFalse(summary.rejected());
        assertFalse(summary.acceptedWithWarnings());
        assertEquals("currentCseCiSummaryKernel", fields.get("cseLayerReadinessCiMethod"));
        assertEquals("noRewriteWork", fields.get("cseLayerReadinessCiVerdict"));
        assertEquals("pass", fields.get("cseLayerReadinessCiRuleVerdict"));
        assertEquals("accepted/pass", fields.get("cseLayerReadinessCiAcceptanceReason"));
        assertEquals("none", fields.get("cseLayerReadinessCiFirstBlockingLayer"));
        assertTrue(fields.get("cseLayerReadinessCiCiSummaryLine").contains("accepted=true"));
        assertThrows(UnsupportedOperationException.class, () -> fields.put("x", "y"));
    }

    @Test
    void exportsAutoVectorizationCurrentReadinessCiSummary() {
        GpuIrOptimizationValidationOptimizerEnablementArtifactRunner runner =
                new GpuIrOptimizationValidationOptimizerEnablementArtifactRunner();
        GpuIrOptimizationValidationReport report = validationReport("currentAutoCiSummaryKernel");
        GpuIrAutoVectorizationReadinessSummaryReport readiness = runner.runAutoVectorizationReadiness(report);
        GpuIrOptimizationValidationRuleArtifactReport ruleArtifact = runner.runAutoVectorizationReadinessRuleArtifact(report);
        GpuIrOptimizationValidationRuleArtifactAcceptance acceptance =
                GpuIrOptimizationValidationRuleArtifactAcceptance.from(ruleArtifact);

        GpuIrAutoVectorizationReadinessCiSummary summary =
                new GpuIrAutoVectorizationReadinessCiSummary(readiness, ruleArtifact, acceptance);
        Map<String, String> fields = summary.artifactFields();

        assertEquals("currentAutoCiSummaryKernel", summary.methodName());
        assertEquals("notReady/noCandidates", summary.verdict());
        assertEquals("fail", summary.ruleVerdict());
        assertEquals("rejected/blockingResultsPresent", summary.acceptanceReason());
        assertFalse(summary.accepted());
        assertTrue(summary.rejected());
        assertFalse(summary.acceptedWithWarnings());
        assertEquals("currentAutoCiSummaryKernel", fields.get("autoVectorizationReadinessCiMethod"));
        assertEquals("notReady/noCandidates", fields.get("autoVectorizationReadinessCiVerdict"));
        assertEquals("fail", fields.get("autoVectorizationReadinessCiRuleVerdict"));
        assertEquals("rejected/blockingResultsPresent", fields.get("autoVectorizationReadinessCiAcceptanceReason"));
        assertEquals("false", fields.get("autoVectorizationReadinessCiReadyForPrototypeRewrite"));
        assertEquals("noRewriteCandidates", fields.get("autoVectorizationReadinessCiFirstBlockingReason"));
        assertTrue(fields.get("autoVectorizationReadinessCiCiSummaryLine").contains("accepted=false"));
        assertThrows(UnsupportedOperationException.class, () -> fields.put("x", "y"));
    }

    @Test
    void exportsOptimizerCurrentReadinessCiSummary() {
        GpuIrOptimizationValidationOptimizerEnablementArtifactRunner runner =
                new GpuIrOptimizationValidationOptimizerEnablementArtifactRunner();
        GpuIrOptimizationValidationReport report = validationReport("currentOptimizerCiSummaryKernel");
        GpuIrOptimizationValidationOptimizerLayerReadinessSummaryReport readiness =
                runner.runOptimizerLayerReadiness(report);
        GpuIrOptimizationValidationRuleArtifactReport ruleArtifact =
                runner.runOptimizerLayerReadinessRuleArtifact(report);
        GpuIrOptimizationValidationRuleArtifactAcceptance acceptance =
                GpuIrOptimizationValidationRuleArtifactAcceptance.from(ruleArtifact);

        GpuIrOptimizationValidationOptimizerLayerReadinessCiSummary summary =
                new GpuIrOptimizationValidationOptimizerLayerReadinessCiSummary(readiness, ruleArtifact, acceptance);
        Map<String, String> fields = summary.artifactFields();

        assertEquals("currentOptimizerCiSummaryKernel", summary.methodName());
        assertEquals("blocked", summary.verdict());
        assertEquals("fail", summary.ruleVerdict());
        assertEquals("rejected/blockingResultsPresent", summary.acceptanceReason());
        assertFalse(summary.accepted());
        assertTrue(summary.rejected());
        assertFalse(summary.acceptedWithWarnings());
        assertEquals("currentOptimizerCiSummaryKernel", fields.get("optimizerLayerReadinessCiMethod"));
        assertEquals("blocked", fields.get("optimizerLayerReadinessCiVerdict"));
        assertEquals("fail", fields.get("optimizerLayerReadinessCiRuleVerdict"));
        assertEquals("rejected/blockingResultsPresent", fields.get("optimizerLayerReadinessCiAcceptanceReason"));
        assertEquals("cseLiteralPromotion", fields.get("optimizerLayerReadinessCiFirstBlockingLayer"));
        assertTrue(fields.get("optimizerLayerReadinessCiCiSummaryLine").contains("accepted=false"));
        assertThrows(UnsupportedOperationException.class, () -> fields.put("x", "y"));
    }

    @Test
    void rejectsInvalidInputsAndPrefixes() {
        GpuIrOptimizationValidationOptimizerEnablementArtifactRunner runner =
                new GpuIrOptimizationValidationOptimizerEnablementArtifactRunner();
        GpuIrOptimizationValidationReport report = validationReport("invalidCurrentCiSummaryKernel");
        GpuIrCommonSubexpressionLayerReadinessSummaryReport cseReadiness = runner.runCseLayerReadiness(report);
        GpuIrOptimizationValidationRuleArtifactReport cseRuleArtifact = runner.runCseLayerReadinessRuleArtifact(report);
        GpuIrOptimizationValidationRuleArtifactAcceptance cseAcceptance =
                GpuIrOptimizationValidationRuleArtifactAcceptance.from(cseRuleArtifact);
        GpuIrAutoVectorizationReadinessSummaryReport autoReadiness = runner.runAutoVectorizationReadiness(report);
        GpuIrOptimizationValidationRuleArtifactReport autoRuleArtifact =
                runner.runAutoVectorizationReadinessRuleArtifact(report);
        GpuIrOptimizationValidationRuleArtifactAcceptance autoAcceptance =
                GpuIrOptimizationValidationRuleArtifactAcceptance.from(autoRuleArtifact);
        GpuIrOptimizationValidationOptimizerLayerReadinessSummaryReport optimizerReadiness =
                runner.runOptimizerLayerReadiness(report);
        GpuIrOptimizationValidationRuleArtifactReport optimizerRuleArtifact =
                runner.runOptimizerLayerReadinessRuleArtifact(report);
        GpuIrOptimizationValidationRuleArtifactAcceptance optimizerAcceptance =
                GpuIrOptimizationValidationRuleArtifactAcceptance.from(optimizerRuleArtifact);

        assertThrows(NullPointerException.class, () -> new GpuIrCommonSubexpressionLayerReadinessCiSummary(
                null,
                cseRuleArtifact,
                cseAcceptance
        ));
        assertThrows(NullPointerException.class, () -> new GpuIrAutoVectorizationReadinessCiSummary(
                autoReadiness,
                null,
                autoAcceptance
        ));
        assertThrows(NullPointerException.class, () -> new GpuIrOptimizationValidationOptimizerLayerReadinessCiSummary(
                optimizerReadiness,
                optimizerRuleArtifact,
                null
        ));
        assertThrows(IllegalArgumentException.class, () -> new GpuIrAutoVectorizationReadinessCiSummary(
                autoReadiness,
                autoRuleArtifact,
                new GpuIrOptimizationValidationRuleArtifactAcceptance(
                        "differentKernel",
                        "pass",
                        true,
                        "accepted/pass",
                        "",
                        "",
                        "",
                        "summary"
                )
        ));
        assertThrows(IllegalArgumentException.class, () -> new GpuIrCommonSubexpressionLayerReadinessCiSummary(
                cseReadiness,
                cseRuleArtifact,
                cseAcceptance
        ).artifactFields(""));
        assertThrows(IllegalArgumentException.class, () -> new GpuIrAutoVectorizationReadinessCiSummary(
                autoReadiness,
                autoRuleArtifact,
                autoAcceptance
        ).artifactFields(""));
        assertThrows(IllegalArgumentException.class, () -> new GpuIrOptimizationValidationOptimizerLayerReadinessCiSummary(
                optimizerReadiness,
                optimizerRuleArtifact,
                optimizerAcceptance
        ).artifactFields(""));
    }
}
