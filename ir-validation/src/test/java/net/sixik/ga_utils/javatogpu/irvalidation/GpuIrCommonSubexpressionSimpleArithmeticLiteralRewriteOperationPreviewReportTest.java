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

class GpuIrCommonSubexpressionSimpleArithmeticLiteralRewriteOperationPreviewReportTest {
    @Test
    void reportsNoPreviewCandidatesAsInactiveOperationPreview() {
        GpuIrCommonSubexpressionSimpleArithmeticLiteralRewriteOperationPreviewReport report =
                evidence(
                        emptyCanonicalizationReport(),
                        false,
                        List.of()
                ).operationPreview();
        Map<String, String> fields = report.artifactFields("literalOperationPreview");

        assertEquals("none", report.readiness());
        assertFalse(report.hasEligibleOperations());
        assertTrue(report.hasBlockedOperations());
        assertEquals(0, report.candidateCount());
        assertEquals(0, report.eligibleOperationCount());
        assertEquals(1, report.blockedOperationCount());
        assertEquals("literalArithmeticCse(none)", report.firstBlockedOperation().orElseThrow().operationShape());
        assertEquals("noPreviewCandidates", report.firstBlockedOperation().orElseThrow().firstBlocker().orElseThrow());
        assertEquals("none", fields.get("literalOperationPreviewReadiness"));
        assertEquals("1", fields.get("literalOperationPreviewBlockedOperations"));
        assertEquals("{literalArithmeticCse(none)=1}", fields.get("literalOperationPreviewBlockedOperationShapeCounts"));
        assertEquals("noPreviewCandidates", fields.get("literalOperationPreviewFirstBlockedReason"));
        assertThrows(UnsupportedOperationException.class, () -> fields.put("x", "y"));
    }

    @Test
    void reportsBlockedOperationShapesWhenPreflightIsBlocked() {
        GpuIrCommonSubexpressionSimpleArithmeticLiteralRewriteOperationPreviewReport report =
                evidence(
                        canonicalizationReport(),
                        false,
                        List.of()
                ).operationPreview();

        assertEquals("blockedPreview", report.readiness());
        assertEquals(2, report.candidateCount());
        assertEquals(0, report.eligibleOperationCount());
        assertEquals(2, report.blockedOperationCount());
        assertEquals(Map.of("literalArithmeticCse(plus:int,int,int)", 2L), report.blockedOperationShapeCounts());
        assertTrue(report.blockerCounts().containsKey("runtimeEquivalenceNotProven"));
        assertTrue(report.blockerCounts().containsKey("previewOnlyBlastRadius"));
        assertTrue(report.blockerCounts().containsKey("productionFingerprintIntegrationDisabled"));
        assertEquals("runtimeEquivalenceNotProven", report.firstBlockedOperation().orElseThrow().firstBlocker().orElseThrow());
        assertTrue(report.summary().contains("blockedOperationShapes={literalArithmeticCse(plus:int,int,int)=2}"));
    }

    @Test
    void reportsEligibleOperationShapesWhenPreflightIsEligible() {
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

        GpuIrCommonSubexpressionSimpleArithmeticLiteralRewriteOperationPreviewReport report =
                GpuIrCommonSubexpressionSimpleArithmeticLiteralRewriteOperationPreviewReport.from(preflight);

        assertEquals("readyPreview", report.readiness());
        assertTrue(report.hasEligibleOperations());
        assertFalse(report.hasBlockedOperations());
        assertEquals(Map.of("literalArithmeticCse(plus:int,int,int)", 1L), report.operationShapeCounts());
        assertEquals("literalArithmeticCse(plus:int,int,int)", report.firstEligibleOperation().orElseThrow().operationShape());
    }

    @Test
    void rejectsInvalidMetadata() {
        assertThrows(IllegalArgumentException.class, () -> new GpuIrCommonSubexpressionSimpleArithmeticLiteralRewriteOperationPreviewReport(
                "",
                "none",
                0,
                0,
                0,
                0,
                List.of(),
                List.of()
        ));
        assertThrows(IllegalArgumentException.class, () -> new GpuIrCommonSubexpressionSimpleArithmeticLiteralRewriteOperationPreviewReport(
                "kernel",
                "",
                0,
                0,
                0,
                0,
                List.of(),
                List.of()
        ));
        assertThrows(IllegalArgumentException.class, () -> new GpuIrCommonSubexpressionSimpleArithmeticLiteralRewriteOperationPreviewReport.Operation(
                "",
                "key",
                "plus:int,int,int",
                "literalArithmeticCse(plus:int,int,int)",
                List.of("1")
        ));
        assertThrows(IllegalArgumentException.class, () -> new GpuIrCommonSubexpressionSimpleArithmeticLiteralRewriteOperationPreviewReport.BlockedOperation(
                "stmt[0].initializer",
                "key",
                "plus:int,int,int",
                "literalArithmeticCse(plus:int,int,int)",
                List.of("blocker"),
                ""
        ));
    }

}
