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

class GpuIrCommonSubexpressionSimpleArithmeticLiteralRewritePreflightReportTest {
    @Test
    void reportsNoPreviewCandidatesAsInactivePreflight() {
        GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationReport canonicalization =
                emptyCanonicalizationReport();

        GpuIrCommonSubexpressionSimpleArithmeticLiteralRewritePreflightReport report = evidence(
                canonicalization,
                false,
                List.of()
        ).preflight();
        Map<String, String> fields = report.artifactFields("literalRewritePreflight");

        assertEquals("none", report.readiness());
        assertFalse(report.hasCandidates());
        assertFalse(report.hasEligibleCandidates());
        assertTrue(report.hasBlockedCandidates());
        assertEquals(1, report.blockedCandidateCount());
        assertEquals(List.of("noPreviewCandidates"), report.firstBlockedCandidate().orElseThrow().blockers());
        assertEquals("noPreviewCandidates", report.firstBlockedCandidate().orElseThrow().firstBlocker().orElseThrow());
        assertEquals("none", fields.get("literalRewritePreflightReadiness"));
        assertEquals("1", fields.get("literalRewritePreflightBlockedCandidates"));
        assertEquals("noPreviewCandidates", fields.get("literalRewritePreflightFirstBlockedReason"));
        assertThrows(UnsupportedOperationException.class, () -> fields.put("x", "y"));
    }

    @Test
    void blocksCandidatesWhenRuntimeEvidenceIsMissing() {
        GpuIrCommonSubexpressionSimpleArithmeticLiteralRewritePreflightReport report = evidence(
                canonicalizationReport(),
                false,
                List.of()
        ).preflight();

        assertEquals("blocked", report.readiness());
        assertEquals(2, report.candidateCount());
        assertEquals(0, report.eligibleCandidateCount());
        assertEquals(2, report.blockedCandidateCount());
        assertTrue(report.blockerCounts().containsKey("runtimeEquivalenceNotProven"));
        assertTrue(report.blockerCounts().containsKey("previewOnlyBlastRadius"));
        assertTrue(report.blockerCounts().containsKey("productionFingerprintIntegrationDisabled"));
        assertTrue(report.summary().contains("readiness=blocked"));
    }

    @Test
    void keepsPreviewOnlyBlastRadiusVisibleAfterRuntimeEvidenceCompletes() {
        GpuIrCommonSubexpressionSimpleArithmeticLiteralRewritePreflightReport report = evidence(
                canonicalizationReport(),
                true,
                List.of()
        ).preflight();

        assertEquals("blocked", report.readiness());
        assertTrue(report.runtimeEquivalenceSuccessful());
        assertTrue(report.evidenceComplete());
        assertTrue(report.previewOnlyBlastRadius());
        assertTrue(report.blockerCounts().containsKey("previewOnlyBlastRadius"));
        assertTrue(report.blockerCounts().containsKey("productionFingerprintIntegrationDisabled"));
        assertFalse(report.blockerCounts().containsKey("runtimeEquivalenceNotProven"));
    }

    @Test
    void blocksProductionDisabledStateWhenEvidenceAndParityAreComplete() {
        GpuIrCommonSubexpressionSimpleArithmeticLiteralRewritePreflightReport report = evidence(
                canonicalizationReport(),
                true,
                List.of(
                        "literal_assoc_preview(plus:int,int,int;literals=1)",
                        "literal_assoc_preview(plus:int,int,int;literals=2)"
                )
        ).preflight();

        assertEquals("blocked", report.readiness());
        assertFalse(report.previewOnlyBlastRadius());
        assertEquals("evidenceCompleteButDisabled", report.enablementVerdict());
        assertEquals(Map.of("productionFingerprintIntegrationDisabled", 2L), report.blockerCounts());
        assertEquals("productionFingerprintIntegrationDisabled", report.firstBlockedCandidate().orElseThrow().firstBlocker().orElseThrow());
    }

    @Test
    void rejectsInvalidMetadata() {
        assertThrows(IllegalArgumentException.class, () -> new GpuIrCommonSubexpressionSimpleArithmeticLiteralRewritePreflightReport(
                "",
                "none",
                0,
                0,
                0,
                0,
                false,
                false,
                false,
                false,
                "notReady/noPreviewCandidates",
                List.of(),
                List.of()
        ));
        assertThrows(IllegalArgumentException.class, () -> new GpuIrCommonSubexpressionSimpleArithmeticLiteralRewritePreflightReport(
                "kernel",
                "",
                0,
                0,
                0,
                0,
                false,
                false,
                false,
                false,
                "notReady/noPreviewCandidates",
                List.of(),
                List.of()
        ));
        assertThrows(IllegalArgumentException.class, () -> new GpuIrCommonSubexpressionSimpleArithmeticLiteralRewritePreflightReport.Candidate(
                "",
                "key",
                "plus:int,int,int",
                List.of("1")
        ));
        assertThrows(IllegalArgumentException.class, () -> new GpuIrCommonSubexpressionSimpleArithmeticLiteralRewritePreflightReport.BlockedCandidate(
                "stmt[0].initializer",
                "key",
                "plus:int,int,int",
                List.of("blocker"),
                ""
        ));
    }

}
