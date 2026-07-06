package net.sixik.ga_utils.javatogpu.irvalidation;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static net.sixik.ga_utils.javatogpu.irvalidation.GpuIrCommonSubexpressionSimpleArithmeticLiteralTestFixtures.canonicalizationReport;
import static net.sixik.ga_utils.javatogpu.irvalidation.GpuIrOptimizationValidationRuleTestFixtures.reportWithLiteralEvidence;
import static net.sixik.ga_utils.javatogpu.irvalidation.GpuIrOptimizationValidationRuleTestFixtures.validationReport;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuIrOptimizationValidationOptimizerEnablementGateReportTest {
    @Test
    void blocksOnCseReadinessBeforeAutoVectorizationAndPolicyCanMatter() {
        GpuIrOptimizationValidationReport validationReport = validationReport("aggregateGateKernel");
        GpuIrOptimizationValidationOptimizerEnablementArtifact enablementArtifact =
                new GpuIrOptimizationValidationOptimizerEnablementArtifactRunner().run(validationReport);

        GpuIrOptimizationValidationOptimizerEnablementGateReport gate =
                GpuIrOptimizationValidationOptimizerEnablementGateReport.from(validationReport, enablementArtifact);
        Map<String, String> fields = gate.artifactFields();

        assertEquals("notReady/cseBlocked", gate.verdict());
        assertFalse(gate.readyForProductionMutation());
        assertFalse(gate.cseReadyForProductionMutation());
        assertFalse(gate.cseReadyForEnablementReview());
        assertFalse(gate.autoVectorizationReadyForPrototypeRewrite());
        assertTrue(gate.optimizerEnablementReviewAllowed());
        assertFalse(gate.productionMutationEnabled());
        assertEquals("cseLiteralPromotionNotReady", gate.firstBlockingReason().orElseThrow());
        assertEquals("aggregateGateKernel", fields.get("optimizerEnablementGateMethod"));
        assertEquals("notReady/cseBlocked", fields.get("optimizerEnablementGateVerdict"));
        assertEquals("false", fields.get("optimizerEnablementGateReadyForProductionMutation"));
        assertEquals("false", fields.get("optimizerEnablementGateProductionMutationEnabled"));
        assertEquals("cseLiteralPromotionNotReady", fields.get("optimizerEnablementGateFirstBlockingReason"));
        assertTrue(fields.get("optimizerEnablementGateRemainingWork").contains("clearCseLiteralPromotionReadiness"));
        assertThrows(UnsupportedOperationException.class, () -> fields.put("x", "y"));
    }

    @Test
    void blocksOnAutoVectorizationWhenCseIsReadyButPrototypeRewriteIsNotReady() {
        GpuIrCommonSubexpressionSimpleArithmeticLiteralPromotionReadinessSummaryReport cseReady = cseReady();
        GpuIrAutoVectorizationReadinessSummaryReport autoBlocked = autoBlocked();
        GpuIrOptimizationValidationOptimizerEnablementPolicyDecision policy = reviewAllowedPolicy();

        GpuIrOptimizationValidationOptimizerEnablementGateReport gate =
                GpuIrOptimizationValidationOptimizerEnablementGateReport.from(cseReady, autoBlocked, policy);

        assertEquals("notReady/autoVectorizationBlocked", gate.verdict());
        assertTrue(gate.cseReadyForProductionMutation());
        assertTrue(gate.cseReadyForEnablementReview());
        assertFalse(gate.autoVectorizationReadyForPrototypeRewrite());
        assertEquals("autoVectorizationNotReady", gate.firstBlockingReason().orElseThrow());
        assertTrue(gate.remainingWork().contains("clearAutoVectorizationReadiness"));
        assertTrue(gate.remainingWork().contains("enableProductionMutationPolicy"));
    }

    @Test
    void reportsReviewReadyButProductionDisabledWhenReadinessAndReviewAreClear() {
        GpuIrOptimizationValidationOptimizerEnablementGateReport gate =
                GpuIrOptimizationValidationOptimizerEnablementGateReport.from(
                        cseReady(),
                        autoReady(),
                        reviewAllowedPolicy()
                );

        assertEquals("reviewReady/productionMutationDisabled", gate.verdict());
        assertFalse(gate.readyForProductionMutation());
        assertTrue(gate.cseReadyForProductionMutation());
        assertTrue(gate.cseReadyForEnablementReview());
        assertTrue(gate.autoVectorizationReadyForPrototypeRewrite());
        assertTrue(gate.optimizerEnablementReviewAllowed());
        assertFalse(gate.productionMutationEnabled());
        assertEquals(List.of("productionMutationDisabled"), gate.blockingReasons());
        assertEquals("enableProductionMutationPolicy", gate.firstRemainingWork().orElseThrow());
    }

    @Test
    void treatsRuntimeEquivalentCseEvidenceAsReviewReadyWhileProductionMutationStaysDisabled() {
        GpuIrOptimizationValidationReport report = reportWithLiteralEvidence(
                canonicalizationReport(),
                true,
                List.of(
                        "literal_assoc_preview(plus:int,int,int;literals=1)",
                        "literal_assoc_preview(plus:int,int,int;literals=2)"
                )
        );

        GpuIrOptimizationValidationOptimizerEnablementGateReport gate =
                GpuIrOptimizationValidationOptimizerEnablementGateReport.from(
                        report.commonSubexpressionLiteralPromotionReadinessSummaryReport(),
                        autoReady(report.methodName()),
                        reviewAllowedPolicy(report.methodName())
                );
        Map<String, String> fields = gate.artifactFields();

        assertEquals("reviewReady/productionMutationDisabled", gate.verdict());
        assertFalse(gate.readyForProductionMutation());
        assertFalse(gate.cseReadyForProductionMutation());
        assertTrue(gate.cseReadyForEnablementReview());
        assertTrue(gate.autoVectorizationReadyForPrototypeRewrite());
        assertFalse(gate.productionMutationEnabled());
        assertEquals("evidenceCompleteButProductionDisabled", gate.cseVerdict());
        assertEquals(List.of("productionMutationDisabled"), gate.blockingReasons());
        assertEquals("enableProductionMutationPolicy", gate.firstRemainingWork().orElseThrow());
        assertEquals("true", fields.get("optimizerEnablementGateCseReadyForEnablementReview"));
    }

    @Test
    void canRepresentFutureReadyContractOnlyWhenProductionMutationIsEnabled() {
        GpuIrOptimizationValidationOptimizerEnablementGateReport gate =
                new GpuIrOptimizationValidationOptimizerEnablementGateReport(
                        "kernel",
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

        assertTrue(gate.readyForProductionMutation());
        assertEquals(0, gate.blockingReasonCount());
        assertEquals("optimizer enablement gate ready method=kernel", gate.ciSummaryLine());
    }

    @Test
    void rejectsInvalidInputsAndMismatchedMethods() {
        assertThrows(NullPointerException.class, () -> GpuIrOptimizationValidationOptimizerEnablementGateReport.from(
                null,
                autoReady(),
                reviewAllowedPolicy()
        ));
        assertThrows(IllegalArgumentException.class, () -> GpuIrOptimizationValidationOptimizerEnablementGateReport.from(
                cseReady(),
                autoReady("otherKernel"),
                reviewAllowedPolicy()
        ));
        GpuIrOptimizationValidationOptimizerEnablementGateReport gate =
                GpuIrOptimizationValidationOptimizerEnablementGateReport.from(cseReady(), autoReady(), reviewAllowedPolicy());
        assertThrows(IllegalArgumentException.class, () -> gate.artifactFields(""));
        assertThrows(IllegalArgumentException.class, () -> new GpuIrOptimizationValidationOptimizerEnablementGateReport(
                "kernel",
                "readyForProductionMutation",
                false,
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
        ));
    }

    private static GpuIrCommonSubexpressionSimpleArithmeticLiteralPromotionReadinessSummaryReport cseReady() {
        return new GpuIrCommonSubexpressionSimpleArithmeticLiteralPromotionReadinessSummaryReport(
                "kernel",
                "readyForProductionMutation",
                1,
                1,
                0,
                0,
                0,
                true,
                true,
                true,
                true,
                true,
                "clear",
                "proven",
                "productionParity",
                "readyForProductionMutation",
                List.of(),
                List.of()
        );
    }

    private static GpuIrAutoVectorizationReadinessSummaryReport autoReady() {
        return autoReady("kernel");
    }

    private static GpuIrAutoVectorizationReadinessSummaryReport autoReady(String methodName) {
        return new GpuIrAutoVectorizationReadinessSummaryReport(
                methodName,
                "readyForPrototypeRewrite",
                1,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                2,
                true,
                true,
                true,
                true,
                true,
                true,
                "ready",
                "allow",
                "ready",
                "ready",
                List.of(),
                List.of()
        );
    }

    private static GpuIrAutoVectorizationReadinessSummaryReport autoBlocked() {
        return new GpuIrAutoVectorizationReadinessSummaryReport(
                "kernel",
                "notReady/noCandidates",
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                false,
                true,
                true,
                false,
                false,
                false,
                "none",
                "allow",
                "none",
                "skipped",
                List.of("noRewriteCandidates", "rewritePolicyBlocksRewrite", "dryRunNotReady"),
                List.of("collectRewriteCandidates", "enableRewritePolicy", "fixRewriteDryRun")
        );
    }

    private static GpuIrOptimizationValidationOptimizerEnablementPolicyDecision reviewAllowedPolicy() {
        return reviewAllowedPolicy("kernel");
    }

    private static GpuIrOptimizationValidationOptimizerEnablementPolicyDecision reviewAllowedPolicy(String methodName) {
        return new GpuIrOptimizationValidationOptimizerEnablementPolicyDecision(
                methodName,
                "reviewAllowed/productionMutationDisabled",
                true,
                false,
                "readyForOptimizerEnablementReview",
                true,
                "",
                "enableProductionMutationPolicy",
                "summary"
        );
    }
}
