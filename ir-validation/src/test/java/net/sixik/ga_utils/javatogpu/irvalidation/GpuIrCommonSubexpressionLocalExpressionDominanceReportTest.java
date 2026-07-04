package net.sixik.ga_utils.javatogpu.irvalidation;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuIrCommonSubexpressionLocalExpressionDominanceReportTest {
    @Test
    void artifactFieldsExposeProvenAndBlockedLocalExpressionProofs() {
        GpuIrCommonSubexpressionLocalExpressionDominanceReport report = new GpuIrCommonSubexpressionLocalExpressionDominanceReport(
                new GpuIrCommonSubexpressionRewritePreview(
                        List.of(new GpuIrCommonSubexpressionRewriteInsertion(
                                "__gpu_cse_0",
                                "binary(*,var(x),var(y))",
                                0,
                                "stmt[0].value.left"
                        )),
                        List.of(new GpuIrCommonSubexpressionRewriteEdit(
                                "__gpu_cse_0",
                                "binary(*,var(x),var(y))",
                                0,
                                "stmt[0].value.left",
                                "stmt[0].value.right"
                        )),
                        List.of(blockedLocalDiagnostic())
                )
        );

        Map<String, String> fields = report.artifactFields();

        assertEquals(1, report.provenCandidateCount());
        assertEquals(1, report.provenReplacementCount());
        assertEquals(1, report.blockedCandidateCount());
        assertTrue(report.hasLocalExpressionEvidence());
        assertEquals("1", fields.get("cseLocalExpressionProvenCandidates"));
        assertEquals("1", fields.get("cseLocalExpressionProvenReplacements"));
        assertEquals("1", fields.get("cseLocalExpressionBlockedCandidates"));
        assertEquals("true", fields.get("cseLocalExpressionHasEvidence"));
        assertEquals("NO_DOMINATING_FIRST_OCCURRENCE", fields.get("cseLocalExpressionFirstBlockedReason"));
        assertEquals("requiresLocalExpressionDominance", fields.get("cseLocalExpressionFirstBlockedDominanceStatus"));
        assertTrue(fields.get("cseLocalExpressionFirstBlockedSummary").contains("requiresLocalExpressionDominance"));
        assertTrue(report.summary().contains("provenCandidates=1"));
        assertTrue(report.summary().contains("blockedCandidates=1"));
        assertThrows(UnsupportedOperationException.class, () -> fields.put("x", "y"));
    }

    @Test
    void ignoresTopLevelDownstreamRewritesAsLocalExpressionProofs() {
        GpuIrCommonSubexpressionLocalExpressionDominanceReport report = new GpuIrCommonSubexpressionLocalExpressionDominanceReport(
                new GpuIrCommonSubexpressionRewritePreview(
                        List.of(new GpuIrCommonSubexpressionRewriteInsertion(
                                "__gpu_cse_0",
                                "binary(+,var(x),var(y))",
                                0,
                                "stmt[0].value"
                        )),
                        List.of(new GpuIrCommonSubexpressionRewriteEdit(
                                "__gpu_cse_0",
                                "binary(+,var(x),var(y))",
                                0,
                                "stmt[0].value",
                                "stmt[1].value"
                        )),
                        List.of()
                )
        );

        assertEquals(0, report.provenCandidateCount());
        assertEquals(0, report.provenReplacementCount());
        assertEquals(0, report.blockedCandidateCount());
        assertFalse(report.hasLocalExpressionEvidence());
        assertEquals("false", report.artifactFields().get("cseLocalExpressionHasEvidence"));
    }

    @Test
    void handlesEmptyPreview() {
        GpuIrCommonSubexpressionLocalExpressionDominanceReport report = new GpuIrCommonSubexpressionLocalExpressionDominanceReport(
                new GpuIrCommonSubexpressionRewritePreview(List.of(), List.of(), List.of())
        );

        Map<String, String> fields = report.artifactFields("local.");

        assertEquals("0", fields.get("local.ProvenCandidates"));
        assertEquals("0", fields.get("local.ProvenReplacements"));
        assertEquals("0", fields.get("local.BlockedCandidates"));
        assertEquals("false", fields.get("local.HasEvidence"));
        assertFalse(fields.containsKey("local.FirstBlockedReason"));
        assertThrows(NullPointerException.class, () -> new GpuIrCommonSubexpressionLocalExpressionDominanceReport(null));
    }

    private GpuIrCommonSubexpressionSkippedDiagnostic blockedLocalDiagnostic() {
        return new GpuIrCommonSubexpressionSkippedDiagnostic(
                "binary(*,var(x),var(y))",
                2,
                List.of("stmt[0].value.right", "stmt[0].value.left"),
                GpuIrCommonSubexpressionKind.LOCAL_REUSE,
                GpuIrCommonSubexpressionScope.STRAIGHT_LINE,
                GpuIrCommonSubexpressionSkipReason.NO_DOMINATING_FIRST_OCCURRENCE,
                GpuIrCommonSubexpressionDominanceStatus.REQUIRES_LOCAL_EXPRESSION_DOMINANCE
        );
    }
}
