package net.sixik.ga_utils.javatogpu.irvalidation;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static net.sixik.ga_utils.javatogpu.irvalidation.GpuIrCommonSubexpressionSimpleArithmeticLiteralTestFixtures.canonicalizationReport;
import static net.sixik.ga_utils.javatogpu.irvalidation.GpuIrCommonSubexpressionSimpleArithmeticLiteralTestFixtures.emptyCanonicalizationReport;
import static net.sixik.ga_utils.javatogpu.irvalidation.GpuIrCommonSubexpressionSimpleArithmeticLiteralTestFixtures.evidence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuIrCommonSubexpressionSimpleArithmeticLiteralPromotionReadinessSummaryReportTest {
    @Test
    void reportsNoPreviewCandidatesAsNotReady() {
        GpuIrCommonSubexpressionSimpleArithmeticLiteralPromotionReadinessSummaryReport report = report(
                emptyCanonicalizationReport(),
                false,
                List.of()
        );
        Map<String, String> fields = report.artifactFields("literalPromotionReadiness");

        assertEquals("notReady/noPreviewCandidates", report.verdict());
        assertFalse(report.readyForProductionMutation());
        assertEquals(0, report.previewCandidateCount());
        assertEquals(0, report.uniqueCanonicalKeyCount());
        assertEquals(0, report.typedNumericBlockedCandidateCount());
        assertEquals(1, report.runtimeEquivalenceDiagnosticCount());
        assertEquals(0, report.previewOnlyKeyCount());
        assertEquals(List.of(
                "noPreviewCandidates",
                "runtimeEquivalenceNotProven",
                "productionFingerprintIntegrationDisabled",
                "productionMutationDisabled"
        ), report.blockingReasons());
        assertEquals("noPreviewCandidates", report.firstBlockingReason().orElseThrow());
        assertEquals("collectPreviewCandidates", report.firstRemainingWork().orElseThrow());
        assertEquals("notReady/noPreviewCandidates", fields.get("literalPromotionReadinessVerdict"));
        assertEquals("false", fields.get("literalPromotionReadinessReadyForProductionMutation"));
        assertEquals("4", fields.get("literalPromotionReadinessBlockingReasonCount"));
        assertEquals("{noPreviewCandidates=1,runtimeEquivalenceNotProven=1,productionFingerprintIntegrationDisabled=1,productionMutationDisabled=1}", fields.get("literalPromotionReadinessBlockingReasonCounts"));
        assertEquals("literal promotion readiness notReady/noPreviewCandidates blockers=4 first=noPreviewCandidates", fields.get("literalPromotionReadinessCiSummaryLine"));
        assertThrows(UnsupportedOperationException.class, () -> fields.put("x", "y"));
    }

    @Test
    void reportsRuntimeMissingBeforeExplicitEvidenceRuns() {
        GpuIrCommonSubexpressionSimpleArithmeticLiteralPromotionReadinessSummaryReport report = report(
                canonicalizationReport(),
                false,
                List.of()
        );

        assertEquals("notReady/runtimeMissing", report.verdict());
        assertTrue(report.typedNumericBlockersClear());
        assertFalse(report.runtimeEquivalenceSuccessful());
        assertFalse(report.previewOnlyBlastRadiusClear());
        assertFalse(report.productionFingerprintIntegrationEnabled());
        assertFalse(report.productionMutationEnabled());
        assertEquals("clear", report.typedNumericReadiness());
        assertEquals("notProven", report.runtimeEquivalenceReadiness());
        assertEquals("previewOnly", report.fingerprintParityReadiness());
        assertEquals("notReady/evidenceIncomplete", report.promotionChecklistVerdict());
        assertEquals(List.of(
                "runtimeEquivalenceNotProven",
                "previewOnlyKeysNotInProductionFingerprints",
                "productionFingerprintIntegrationDisabled",
                "productionMutationDisabled"
        ), report.blockingReasons());
        assertTrue(report.remainingWork().contains("runRuntimeEquivalenceEvidence"));
        assertTrue(report.remainingWork().contains("resolvePreviewOnlyFingerprintBlastRadius"));
        assertTrue(report.remainingWork().contains("enableProductionFingerprintIntegration"));
        assertTrue(report.remainingWork().contains("enableProductionMutation"));
    }

    @Test
    void reportsProductionDisabledWhenEvidenceAndParityAreClear() {
        GpuIrCommonSubexpressionSimpleArithmeticLiteralPromotionReadinessSummaryReport report = report(
                canonicalizationReport(),
                true,
                List.of(
                        "literal_assoc_preview(plus:int,int,int;literals=1)",
                        "literal_assoc_preview(plus:int,int,int;literals=2)"
                )
        );

        assertEquals("evidenceCompleteButProductionDisabled", report.verdict());
        assertTrue(report.typedNumericBlockersClear());
        assertTrue(report.runtimeEquivalenceSuccessful());
        assertTrue(report.previewOnlyBlastRadiusClear());
        assertFalse(report.productionFingerprintIntegrationEnabled());
        assertFalse(report.productionMutationEnabled());
        assertEquals(List.of(
                "productionFingerprintIntegrationDisabled",
                "productionMutationDisabled"
        ), report.blockingReasons());
        assertEquals(List.of(
                "clearRewritePreflightBlockers",
                "clearRewriteOperationPreviewBlockers",
                "enableProductionFingerprintIntegration",
                "enableProductionMutation"
        ), report.remainingWork());
    }

    @Test
    void reportsReadyWhenAllInputsAreEnabled() {
        GpuIrCommonSubexpressionSimpleArithmeticLiteralPromotionReadinessSummaryReport report = new GpuIrCommonSubexpressionSimpleArithmeticLiteralPromotionReadinessSummaryReport(
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

        assertTrue(report.readyForProductionMutation());
        assertEquals(0, report.blockingReasonCount());
        assertEquals(0, report.remainingWorkCount());
        assertEquals("literal promotion readiness ready", report.ciSummaryLine());
    }

    @Test
    void rejectsInvalidMetadata() {
        assertThrows(IllegalArgumentException.class, () -> new GpuIrCommonSubexpressionSimpleArithmeticLiteralPromotionReadinessSummaryReport(
                "",
                "notReady/noPreviewCandidates",
                0,
                0,
                0,
                0,
                0,
                true,
                false,
                true,
                false,
                false,
                "clear",
                "none",
                "none",
                "notReady/noPreviewCandidates",
                List.of(),
                List.of()
        ));
        assertThrows(IllegalArgumentException.class, () -> new GpuIrCommonSubexpressionSimpleArithmeticLiteralPromotionReadinessSummaryReport(
                "kernel",
                "",
                0,
                0,
                0,
                0,
                0,
                true,
                false,
                true,
                false,
                false,
                "clear",
                "none",
                "none",
                "notReady/noPreviewCandidates",
                List.of(),
                List.of()
        ));
    }

    private GpuIrCommonSubexpressionSimpleArithmeticLiteralPromotionReadinessSummaryReport report(
            GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationReport canonicalization,
            boolean runtimeSuccessful,
            List<String> productionFingerprints
    ) {
        return evidence(canonicalization, runtimeSuccessful, productionFingerprints).promotionReadiness();
    }
}
