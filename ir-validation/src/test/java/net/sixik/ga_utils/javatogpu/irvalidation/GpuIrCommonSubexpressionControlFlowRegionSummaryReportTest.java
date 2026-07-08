package net.sixik.ga_utils.javatogpu.irvalidation;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuIrCommonSubexpressionControlFlowRegionSummaryReportTest {
    @Test
    void summarizesControlFlowBoundarySkippedCandidates() {
        GpuIrCommonSubexpressionRewritePreview preview = new GpuIrCommonSubexpressionRewritePreview(
                List.of(),
                List.of(),
                List.of(new GpuIrCommonSubexpressionSkippedDiagnostic(
                        "call(clamp)",
                        2,
                        List.of("stmt[0].then.stmt[0].value", "stmt[0].else.stmt[0].value"),
                        GpuIrCommonSubexpressionKind.LOCAL_REUSE,
                        GpuIrCommonSubexpressionScope.CONTROL_FLOW_BOUNDARY,
                        GpuIrCommonSubexpressionSkipReason.CONTROL_FLOW_BOUNDARY,
                        GpuIrCommonSubexpressionDominanceStatus.REQUIRES_LOCAL_EXPRESSION_DOMINANCE
                ))
        );

        GpuIrCommonSubexpressionControlFlowRegionSummaryReport report =
                GpuIrCommonSubexpressionControlFlowRegionSummaryReport.from(preview);
        Map<String, String> fields = report.artifactFields();

        assertTrue(report.blocked());
        assertEquals("blocked", report.readiness());
        assertEquals(1, report.blockedCandidateCount());
        assertEquals("call(clamp)", report.firstBlockedFingerprint());
        assertEquals("requiresLocalExpressionDominance", report.firstBlockedDominanceStatus());
        assertEquals("stmt[0].then.stmt[0].value", report.firstBlockedLocation());
        assertEquals("splitControlFlowRegionsBeforeCse", report.firstRemainingWork());
        assertEquals("blocked", fields.get("cseControlFlowRegionReadiness"));
        assertEquals("true", fields.get("cseControlFlowRegionBlocked"));
        assertEquals("1", fields.get("cseControlFlowRegionBlockedCandidates"));
        assertEquals("{requiresLocalExpressionDominance=1}", fields.get("cseControlFlowRegionDominanceStatusCounts"));
        assertEquals("{thenBranch=1,elseBranch=1}", fields.get("cseControlFlowRegionLocationScopeCounts"));
        assertEquals("1", fields.get("cseControlFlowRegionLocationScope.thenBranch"));
        assertTrue(fields.get("cseControlFlowRegionCiSummaryLine").contains("blockedCandidates=1"));
        assertThrows(UnsupportedOperationException.class, () -> fields.put("x", "y"));
    }

    @Test
    void reportsClearWhenNoControlFlowSkippedCandidatesExist() {
        GpuIrCommonSubexpressionControlFlowRegionSummaryReport report =
                GpuIrCommonSubexpressionControlFlowRegionSummaryReport.from(
                        new GpuIrCommonSubexpressionRewritePreview(List.of(), List.of(), List.of())
                );
        Map<String, String> fields = report.artifactFields();

        assertEquals("clear", report.readiness());
        assertEquals(0, report.blockedCandidateCount());
        assertEquals("none", report.firstBlockedFingerprint());
        assertEquals("none", report.firstRemainingWork());
        assertEquals("clear", fields.get("cseControlFlowRegionReadiness"));
        assertEquals("false", fields.get("cseControlFlowRegionBlocked"));
        assertEquals("{}", fields.get("cseControlFlowRegionLocationScopeCounts"));
    }
}
