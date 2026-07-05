package net.sixik.ga_utils.javatogpu.irvalidation;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuIrCommonSubexpressionArtifactReportTest {
    @Test
    void exposesCombinedCseArtifactFieldsForSuccessfulEquivalenceRun() {
        GpuIrCommonSubexpressionArtifactReport report = new GpuIrCommonSubexpressionArtifactReport(
                GpuIrCommonSubexpressionRuntimeEquivalenceReport.equivalent(
                        rewritePlanReport(),
                        3,
                        List.of("outA")
                )
        );

        Map<String, String> fields = report.artifactFields();

        assertTrue(report.successful());
        assertEquals(1, report.insertionCount());
        assertEquals(1, report.replacementCount());
        assertEquals(1, report.skippedCount());
        assertEquals(0, report.diagnosticCount());
        assertEquals("true", fields.get("cseArtifactSuccessful"));
        assertEquals("1", fields.get("cseArtifactInsertions"));
        assertEquals("1", fields.get("cseArtifactReplacements"));
        assertEquals("1", fields.get("cseArtifactSkipped"));
        assertEquals("true", fields.get("cseArtifactRuntimeEquivalenceSuccessful"));
        assertEquals("0", fields.get("cseArtifactRuntimeEquivalenceDiagnostics"));
        assertTrue(fields.get("cseArtifactSummary").contains("inputCases=3"));
        assertTrue(fields.get("cseArtifactSummary").contains("skippedDominanceStatusCounts={requiresLocalExpressionDominance=1}"));
        assertEquals("1", fields.get("cseArtifactSnapshot.Insertions"));
        assertEquals("1", fields.get("cseArtifactSnapshot.LocalExpressionProvenCandidates"));
        assertEquals("blockedBySkippedCandidate", fields.get("cseArtifactSnapshot.RewritePolicyReadiness"));
        assertEquals("false", fields.get("cseArtifactSnapshot.RewritePolicyCanRewrite"));
        assertEquals("true", fields.get("cseArtifactRuntimeEquivalence.Successful"));
        assertEquals("outA", fields.get("cseArtifactRuntimeEquivalence.ComparedOutputNames"));
        assertEquals("1", fields.get("cseArtifactRuntimeEquivalence.SkippedDominanceStatus.requiresLocalExpressionDominance"));
        assertTrue(report.summary().contains("successful=true"));
        assertTrue(report.summary().contains("runtimeEquivalenceSuccessful=true"));
    }

    @Test
    void exposesTypedArtifactSummaryForSuccessfulRun() {
        GpuIrCommonSubexpressionArtifactReport report = new GpuIrCommonSubexpressionArtifactReport(
                GpuIrCommonSubexpressionRuntimeEquivalenceReport.equivalent(
                        rewritePlanReport(),
                        3,
                        List.of("outA")
                )
        );

        GpuIrCommonSubexpressionArtifactSummary summary = report.artifactSummary();

        assertTrue(summary.successful());
        assertEquals(1, summary.insertionCount());
        assertEquals(1, summary.replacementCount());
        assertEquals(1, summary.skippedCount());
        assertTrue(summary.runtimeEquivalenceSuccessful());
        assertEquals(3, summary.inputCaseCount());
        assertEquals(1, summary.comparedOutputCount());
        assertEquals(0, summary.diagnosticCount());
        assertFalse(summary.hasDiagnostics());
        assertEquals("", summary.firstDiagnostic());
        assertEquals("{requiresLocalExpressionDominance=1}", summary.skippedDominanceStatusCounts());
        assertTrue(summary.summaryLine().contains("replacements=1"));
    }

    @Test
    void exposesCombinedCseArtifactFieldsForFailedEquivalenceRun() {
        GpuIrCommonSubexpressionArtifactReport report = new GpuIrCommonSubexpressionArtifactReport(
                GpuIrCommonSubexpressionRuntimeEquivalenceReport.failed(
                        rewritePlanReport(),
                        1,
                        List.of("outA"),
                        List.of("case 0 output outA differs")
                )
        );

        Map<String, String> fields = report.artifactFields("cseRun.");

        assertFalse(report.successful());
        assertEquals(1, report.diagnosticCount());
        assertEquals("false", fields.get("cseRun.Successful"));
        assertEquals("false", fields.get("cseRun.RuntimeEquivalenceSuccessful"));
        assertEquals("1", fields.get("cseRun.RuntimeEquivalenceDiagnostics"));
        assertTrue(fields.get("cseRun.Summary").contains("diagnostics=1"));
        assertEquals("false", fields.get("cseRun.RuntimeEquivalence.Successful"));
        assertEquals("case 0 output outA differs", fields.get("cseRun.RuntimeEquivalence.FirstDiagnostic"));
        assertTrue(report.summary().contains("successful=false"));
        assertTrue(report.summary().contains("firstDiagnostic=case 0 output outA differs"));
    }

    @Test
    void returnsImmutableArtifactFieldsAndRejectsInvalidMetadata() {
        GpuIrCommonSubexpressionArtifactReport report = new GpuIrCommonSubexpressionArtifactReport(
                GpuIrCommonSubexpressionRuntimeEquivalenceReport.equivalent(
                        rewritePlanReport(),
                        1,
                        List.of("outA")
                )
        );

        assertThrows(UnsupportedOperationException.class, () -> report.artifactFields().put("x", "y"));
        assertThrows(IllegalArgumentException.class, () -> report.artifactFields(""));
        assertThrows(NullPointerException.class, () -> new GpuIrCommonSubexpressionArtifactReport(null));
        assertThrows(NullPointerException.class, () -> new GpuIrCommonSubexpressionArtifactReport(
                new GpuIrCommonSubexpressionArtifactSnapshot(rewritePlanReport().preview()),
                null
        ));
    }

    private GpuIrCommonSubexpressionRewritePlanReport rewritePlanReport() {
        return new GpuIrCommonSubexpressionRewritePlanReport(List.of(
                new GpuIrCommonSubexpressionRewritePlan(
                        "__gpu_cse_0",
                        "binary(*,var(x),var(y))",
                        1,
                        0,
                        "stmt[0].value.left",
                        List.of("stmt[0].value.left", "stmt[0].value.right")
                )
        ), List.of(
                new GpuIrCommonSubexpressionSkippedCandidate(
                        new GpuIrCommonSubexpression(
                                "binary(*,var(x),var(y))",
                                2,
                                List.of("stmt[0].value.right", "stmt[0].value.left")
                        ),
                        GpuIrCommonSubexpressionKind.LOCAL_REUSE,
                        GpuIrCommonSubexpressionScope.STRAIGHT_LINE,
                        GpuIrCommonSubexpressionSkipReason.NO_DOMINATING_FIRST_OCCURRENCE,
                        GpuIrCommonSubexpressionDominanceStatus.REQUIRES_LOCAL_EXPRESSION_DOMINANCE
                )
        ));
    }
}
