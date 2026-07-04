package net.sixik.ga_utils.javatogpu.irvalidation;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuIrCommonSubexpressionRuntimeEquivalenceReportTest {
    @Test
    void exposesSuccessfulRuntimeEquivalenceArtifactFieldsForSameStatementCse() {
        GpuIrCommonSubexpressionRuntimeEquivalenceReport report =
                GpuIrCommonSubexpressionRuntimeEquivalenceReport.equivalent(
                        sameStatementRewritePlanReport(),
                        3,
                        List.of("outA")
                );

        Map<String, String> fields = report.artifactFields();

        assertTrue(report.successful());
        assertFalse(report.hasDiagnostics());
        assertEquals(1, report.comparedOutputCount());
        assertEquals(0, report.diagnosticCount());
        assertEquals("", report.firstDiagnostic());
        assertEquals("true", fields.get("cseRuntimeEquivalenceSuccessful"));
        assertEquals("true", fields.get("cseRuntimeEquivalenceEquivalent"));
        assertEquals("3", fields.get("cseRuntimeEquivalenceInputCases"));
        assertEquals("1", fields.get("cseRuntimeEquivalenceComparedOutputs"));
        assertEquals("outA", fields.get("cseRuntimeEquivalenceComparedOutputNames"));
        assertEquals("0", fields.get("cseRuntimeEquivalenceDiagnostics"));
        assertEquals("false", fields.get("cseRuntimeEquivalenceHasDiagnostics"));
        assertEquals("1", fields.get("cseRuntimeEquivalencePlans"));
        assertEquals("1", fields.get("cseRuntimeEquivalenceInsertions"));
        assertEquals("1", fields.get("cseRuntimeEquivalenceReplacements"));
        assertEquals("0", fields.get("cseRuntimeEquivalenceSkipped"));
        assertEquals("{}", fields.get("cseRuntimeEquivalenceSkippedDominanceStatusCounts"));
        assertFalse(fields.containsKey("cseRuntimeEquivalenceFirstDiagnostic"));
        assertTrue(report.summary().contains("successful=true"));
        assertTrue(report.summary().contains("replacements=1"));
    }

    @Test
    void exposesFailedRuntimeEquivalenceArtifactFields() {
        GpuIrCommonSubexpressionRuntimeEquivalenceReport report =
                GpuIrCommonSubexpressionRuntimeEquivalenceReport.failed(
                        skippedRewritePlanReport(),
                        2,
                        List.of("outA"),
                        List.of("case 1 output outA differs")
                );

        Map<String, String> fields = report.artifactFields("cseEquivalence.");

        assertFalse(report.successful());
        assertFalse(report.equivalent());
        assertTrue(report.hasDiagnostics());
        assertEquals(1, report.diagnosticCount());
        assertEquals("case 1 output outA differs", report.firstDiagnostic());
        assertEquals("false", fields.get("cseEquivalence.Successful"));
        assertEquals("false", fields.get("cseEquivalence.Equivalent"));
        assertEquals("2", fields.get("cseEquivalence.InputCases"));
        assertEquals("1", fields.get("cseEquivalence.ComparedOutputs"));
        assertEquals("outA", fields.get("cseEquivalence.ComparedOutputNames"));
        assertEquals("1", fields.get("cseEquivalence.Diagnostics"));
        assertEquals("true", fields.get("cseEquivalence.HasDiagnostics"));
        assertEquals("case 1 output outA differs", fields.get("cseEquivalence.FirstDiagnostic"));
        assertEquals("0", fields.get("cseEquivalence.Plans"));
        assertEquals("1", fields.get("cseEquivalence.Skipped"));
        assertEquals("{requiresLocalExpressionDominance=1}", fields.get("cseEquivalence.SkippedDominanceStatusCounts"));
        assertEquals("1", fields.get("cseEquivalence.SkippedDominanceStatus.requiresLocalExpressionDominance"));
        assertTrue(report.summary().contains("successful=false"));
        assertTrue(report.summary().contains("firstDiagnostic=case 1 output outA differs"));
    }

    @Test
    void treatsDiagnosticsAsUnsuccessfulEvenWhenEquivalentFlagIsTrue() {
        GpuIrCommonSubexpressionRuntimeEquivalenceReport report =
                new GpuIrCommonSubexpressionRuntimeEquivalenceReport(
                        sameStatementRewritePlanReport(),
                        true,
                        1,
                        List.of("outA"),
                        List.of("diagnostic emitted by external runner")
                );

        assertFalse(report.successful());
        assertTrue(report.equivalent());
        assertEquals("diagnostic emitted by external runner", report.firstDiagnostic());
    }

    @Test
    void returnsImmutableArtifactFieldsAndLists() {
        GpuIrCommonSubexpressionRuntimeEquivalenceReport report =
                GpuIrCommonSubexpressionRuntimeEquivalenceReport.equivalent(
                        sameStatementRewritePlanReport(),
                        1,
                        List.of("outA")
                );

        assertThrows(UnsupportedOperationException.class, () -> report.comparedOutputs().add("outB"));
        assertThrows(UnsupportedOperationException.class, () -> report.diagnostics().add("failure"));
        assertThrows(UnsupportedOperationException.class, () -> report.artifactFields().put("x", "y"));
    }

    @Test
    void rejectsInvalidRuntimeEquivalenceMetadata() {
        GpuIrCommonSubexpressionRewritePlanReport rewritePlanReport = sameStatementRewritePlanReport();

        assertThrows(
                IllegalArgumentException.class,
                () -> GpuIrCommonSubexpressionRuntimeEquivalenceReport.equivalent(
                        rewritePlanReport,
                        -1,
                        List.of("outA")
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> GpuIrCommonSubexpressionRuntimeEquivalenceReport.equivalent(
                        rewritePlanReport,
                        1,
                        List.of("")
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> GpuIrCommonSubexpressionRuntimeEquivalenceReport.failed(
                        rewritePlanReport,
                        1,
                        List.of("outA"),
                        java.util.Arrays.asList((String) null)
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> GpuIrCommonSubexpressionRuntimeEquivalenceReport.equivalent(
                        rewritePlanReport,
                        1,
                        List.of("outA")
                ).artifactFields("")
        );
    }

    private GpuIrCommonSubexpressionRewritePlanReport sameStatementRewritePlanReport() {
        return new GpuIrCommonSubexpressionRewritePlanReport(List.of(
                new GpuIrCommonSubexpressionRewritePlan(
                        "__gpu_cse_0",
                        "binary(*,var(x),var(y))",
                        1,
                        0,
                        "stmt[0].value.left",
                        List.of("stmt[0].value.left", "stmt[0].value.right")
                )
        ), List.of());
    }

    private GpuIrCommonSubexpressionRewritePlanReport skippedRewritePlanReport() {
        return new GpuIrCommonSubexpressionRewritePlanReport(List.of(), List.of(
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
