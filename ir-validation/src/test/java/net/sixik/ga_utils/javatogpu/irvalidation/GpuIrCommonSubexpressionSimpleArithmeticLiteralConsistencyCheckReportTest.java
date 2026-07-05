package net.sixik.ga_utils.javatogpu.irvalidation;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static net.sixik.ga_utils.javatogpu.irvalidation.GpuIrCommonSubexpressionSimpleArithmeticLiteralTestFixtures.canonicalizationReport;
import static net.sixik.ga_utils.javatogpu.irvalidation.GpuIrCommonSubexpressionSimpleArithmeticLiteralTestFixtures.evidence;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuIrCommonSubexpressionSimpleArithmeticLiteralConsistencyCheckReportTest {
    @Test
    void reportsConsistentEvidenceStack() {
        GpuIrCommonSubexpressionSimpleArithmeticLiteralTestFixtures.EvidenceStack evidence = evidence(
                canonicalizationReport(),
                false,
                List.of()
        );

        GpuIrCommonSubexpressionSimpleArithmeticLiteralConsistencyCheckReport report =
                GpuIrCommonSubexpressionSimpleArithmeticLiteralConsistencyCheckReport.from(
                        evidence.enablement(),
                        evidence.preflight(),
                        evidence.operationPreview(),
                        evidence.promotionChecklist()
                );
        Map<String, String> fields = report.artifactFields("literalConsistency");

        assertEquals("consistent", report.verdict());
        assertTrue(report.consistent());
        assertFalse(report.hasFailures());
        assertEquals(12, report.checkCount());
        assertEquals(0, report.failedCheckCount());
        assertEquals(List.of(), report.failedChecks());
        assertEquals("consistent", fields.get("literalConsistencyVerdict"));
        assertEquals("true", fields.get("literalConsistencyConsistent"));
        assertEquals("12", fields.get("literalConsistencyChecks"));
        assertEquals("0", fields.get("literalConsistencyFailedChecks"));
        assertEquals("[]", fields.get("literalConsistencyFailedCheckList"));
        assertEquals("literal consistency check passed: 12 checks", report.ciSummaryLine());
        assertEquals("literal consistency check passed: 12 checks", fields.get("literalConsistencyCiSummaryLine"));
        assertThrows(UnsupportedOperationException.class, () -> fields.put("x", "y"));
    }

    @Test
    void reportsCandidateCountDriftAcrossArtifacts() {
        GpuIrCommonSubexpressionSimpleArithmeticLiteralTestFixtures.EvidenceStack evidence = evidence(
                canonicalizationReport(),
                false,
                List.of()
        );
        GpuIrCommonSubexpressionSimpleArithmeticLiteralEnablementReport driftedEnablement = new GpuIrCommonSubexpressionSimpleArithmeticLiteralEnablementReport(
                "kernel",
                evidence.enablement().verdict(),
                1,
                evidence.enablement().uniqueCanonicalKeyCount(),
                evidence.enablement().numericSemanticsFullyProven(),
                evidence.enablement().runtimeEquivalenceSuccessful(),
                evidence.enablement().evidenceComplete(),
                evidence.enablement().readyForProduction(),
                evidence.enablement().decisionReadiness(),
                evidence.enablement().parityReadiness(),
                evidence.enablement().previewOnlyKeyCount(),
                evidence.enablement().blockers()
        );

        GpuIrCommonSubexpressionSimpleArithmeticLiteralConsistencyCheckReport report =
                GpuIrCommonSubexpressionSimpleArithmeticLiteralConsistencyCheckReport.from(
                        driftedEnablement,
                        evidence.preflight(),
                        evidence.operationPreview(),
                        evidence.promotionChecklist()
                );

        assertEquals("inconsistent", report.verdict());
        assertFalse(report.consistent());
        assertTrue(report.hasFailures());
        assertTrue(report.failedChecks().contains("enablementPreflightCandidateCount"));
        assertTrue(report.failedChecks().contains("enablementOperationPreviewCandidateCount"));
        assertTrue(report.failedChecks().contains("enablementChecklistCandidateCount"));
        assertEquals("enablementPreflightCandidateCount", report.firstFailedCheck().orElseThrow());
        assertEquals(
                "enablement and preflight disagree on preview candidate count",
                report.firstFailureExplanation().orElseThrow()
        );
        assertEquals(
                "literal consistency check failed: 3/12 checks failed; first=enablementPreflightCandidateCount; explanation=enablement and preflight disagree on preview candidate count",
                report.ciSummaryLine()
        );

        Map<String, String> fields = report.artifactFields("literalConsistency");
        assertEquals("enablementPreflightCandidateCount", fields.get("literalConsistencyFirstFailedCheck"));
        assertEquals(
                "enablement and preflight disagree on preview candidate count",
                fields.get("literalConsistencyFirstFailureExplanation")
        );
        assertEquals(report.ciSummaryLine(), fields.get("literalConsistencyCiSummaryLine"));
    }

    @Test
    void rejectsInvalidMetadata() {
        assertThrows(IllegalArgumentException.class, () -> new GpuIrCommonSubexpressionSimpleArithmeticLiteralConsistencyCheckReport(
                "",
                "consistent",
                true,
                12,
                0,
                List.of()
        ));
        assertThrows(IllegalArgumentException.class, () -> new GpuIrCommonSubexpressionSimpleArithmeticLiteralConsistencyCheckReport(
                "kernel",
                "consistent",
                true,
                12,
                1,
                List.of()
        ));
        assertThrows(IllegalArgumentException.class, () -> new GpuIrCommonSubexpressionSimpleArithmeticLiteralConsistencyCheckReport(
                "kernel",
                "consistent",
                true,
                -1,
                0,
                List.of()
        ));
    }
}
