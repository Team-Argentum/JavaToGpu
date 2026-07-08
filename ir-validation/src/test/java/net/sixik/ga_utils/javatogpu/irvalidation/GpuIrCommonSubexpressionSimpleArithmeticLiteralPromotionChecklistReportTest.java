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

class GpuIrCommonSubexpressionSimpleArithmeticLiteralPromotionChecklistReportTest {
    @Test
    void reportsNoPreviewCandidatesAsNotReady() {
        GpuIrCommonSubexpressionSimpleArithmeticLiteralPromotionChecklistReport report = report(
                emptyCanonicalizationReport(),
                false,
                List.of()
        );
        Map<String, String> fields = report.artifactFields("literalPromotionChecklist");

        assertEquals("notReady/noPreviewCandidates", report.verdict());
        assertFalse(report.readyForProductionMutation());
        assertEquals(0, report.candidateCount());
        assertEquals(6, report.remainingWorkCount());
        assertEquals("collectPreviewCandidates", report.firstRemainingWork().orElseThrow());
        assertEquals("notReady/noPreviewCandidates", fields.get("literalPromotionChecklistVerdict"));
        assertEquals("false", fields.get("literalPromotionChecklistReadyForProductionMutation"));
        assertEquals("collectPreviewCandidates", fields.get("literalPromotionChecklistFirstRemainingWork"));
        assertThrows(UnsupportedOperationException.class, () -> fields.put("x", "y"));
    }

    @Test
    void reportsIncompleteEvidenceBeforeRuntimeEquivalenceRuns() {
        GpuIrCommonSubexpressionSimpleArithmeticLiteralPromotionChecklistReport report = report(
                canonicalizationReport(),
                false,
                List.of()
        );

        assertEquals("notReady/evidenceIncomplete", report.verdict());
        assertEquals(2, report.candidateCount());
        assertEquals(0, report.eligibleCandidateCount());
        assertEquals(2, report.blockedCandidateCount());
        assertEquals(0, report.eligibleOperationCount());
        assertEquals(2, report.blockedOperationCount());
        assertTrue(report.remainingWork().contains("runRuntimeEquivalenceEvidence"));
        assertTrue(report.remainingWork().contains("resolvePreviewOnlyFingerprintBlastRadius"));
        assertTrue(report.remainingWork().contains("clearRewritePreflightBlockers"));
        assertTrue(report.remainingWork().contains("clearRewriteOperationPreviewBlockers"));
        assertTrue(report.remainingWork().contains("enableProductionFingerprintIntegration"));
    }

    @Test
    void reportsPreflightBlockedWhenEvidenceIsCompleteButProductionDisabled() {
        GpuIrCommonSubexpressionSimpleArithmeticLiteralPromotionChecklistReport report = report(
                canonicalizationReport(),
                true,
                List.of(
                        "literal_assoc_preview(plus:int,int,int;literals=1)",
                        "literal_assoc_preview(plus:int,int,int;literals=2)"
                )
        );

        assertEquals("notReady/rewritePreflightBlocked", report.verdict());
        assertTrue(report.evidenceComplete());
        assertFalse(report.readyForProduction());
        assertEquals("evidenceCompleteButDisabled", report.enablementVerdict());
        assertEquals("blocked", report.preflightReadiness());
        assertEquals("blockedPreview", report.operationPreviewReadiness());
        assertEquals(List.of(
                "clearRewritePreflightBlockers",
                "clearRewriteOperationPreviewBlockers",
                "enableProductionFingerprintIntegration"
        ), report.remainingWork());
    }

    @Test
    void reportsReadyWhenAllChecklistInputsAreClear() {
        GpuIrCommonSubexpressionSimpleArithmeticLiteralEnablementReport enablement = new GpuIrCommonSubexpressionSimpleArithmeticLiteralEnablementReport(
                "kernel",
                "readyForProduction",
                1,
                1,
                true,
                true,
                true,
                true,
                "readyForProduction",
                "productionOverlap",
                0,
                List.of()
        );
        GpuIrCommonSubexpressionSimpleArithmeticLiteralRewritePreflightReport preflight = new GpuIrCommonSubexpressionSimpleArithmeticLiteralRewritePreflightReport(
                "kernel",
                "eligible",
                1,
                1,
                0,
                1,
                true,
                true,
                true,
                false,
                "readyForProduction",
                List.of(new GpuIrCommonSubexpressionSimpleArithmeticLiteralRewritePreflightReport.Candidate(
                        "stmt[0].initializer",
                        "literal_assoc_preview(plus:int,int,int;literals=1)",
                        "plus:int,int,int",
                        List.of("1")
                )),
                List.of()
        );
        GpuIrCommonSubexpressionSimpleArithmeticLiteralRewriteOperationPreviewReport operationPreview =
                GpuIrCommonSubexpressionSimpleArithmeticLiteralRewriteOperationPreviewReport.from(preflight);

        GpuIrCommonSubexpressionSimpleArithmeticLiteralPromotionChecklistReport report =
                GpuIrCommonSubexpressionSimpleArithmeticLiteralPromotionChecklistReport.from(enablement, preflight, operationPreview);

        assertEquals("readyForProductionMutation", report.verdict());
        assertTrue(report.readyForProductionMutation());
        assertEquals(0, report.remainingWorkCount());
        assertTrue(report.remainingWork().isEmpty());
    }

    @Test
    void rejectsInvalidMetadata() {
        assertThrows(IllegalArgumentException.class, () -> new GpuIrCommonSubexpressionSimpleArithmeticLiteralPromotionChecklistReport(
                "",
                "notReady/noPreviewCandidates",
                0,
                0,
                0,
                0,
                0,
                0,
                false,
                false,
                "notReady/noPreviewCandidates",
                "none",
                "none",
                List.of()
        ));
        assertThrows(IllegalArgumentException.class, () -> new GpuIrCommonSubexpressionSimpleArithmeticLiteralPromotionChecklistReport(
                "kernel",
                "",
                0,
                0,
                0,
                0,
                0,
                0,
                false,
                false,
                "notReady/noPreviewCandidates",
                "none",
                "none",
                List.of()
        ));
    }

    private GpuIrCommonSubexpressionSimpleArithmeticLiteralPromotionChecklistReport report(
            GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationReport canonicalization,
            boolean runtimeSuccessful,
            List<String> productionFingerprints
    ) {
        return evidence(canonicalization, runtimeSuccessful, productionFingerprints).promotionChecklist();
    }
}
