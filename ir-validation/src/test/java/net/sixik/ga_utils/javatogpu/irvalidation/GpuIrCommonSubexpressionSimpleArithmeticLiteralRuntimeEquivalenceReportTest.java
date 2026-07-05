package net.sixik.ga_utils.javatogpu.irvalidation;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static net.sixik.ga_utils.javatogpu.irvalidation.GpuIrCommonSubexpressionSimpleArithmeticLiteralTestFixtures.mixedCanonicalizationReport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuIrCommonSubexpressionSimpleArithmeticLiteralRuntimeEquivalenceReportTest {
    @Test
    void exposesSuccessfulRuntimeEquivalenceArtifactFields() {
        GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationReport canonicalizationReport = mixedCanonicalizationReport();
        GpuIrCommonSubexpressionSimpleArithmeticLiteralNumericSemanticsProofReport numericProof =
                GpuIrCommonSubexpressionSimpleArithmeticLiteralNumericSemanticsProofReport.from(canonicalizationReport);
        GpuIrCommonSubexpressionSimpleArithmeticLiteralRuntimeEquivalenceReport report =
                GpuIrCommonSubexpressionSimpleArithmeticLiteralRuntimeEquivalenceReport.equivalent(
                        canonicalizationReport,
                        numericProof,
                        3,
                        List.of("out")
                );

        Map<String, String> fields = report.artifactFields("literalRuntime");

        assertTrue(report.successful());
        assertTrue(report.equivalent());
        assertFalse(report.hasDiagnostics());
        assertEquals("proven", report.readiness());
        assertEquals(1, report.comparedOutputCount());
        assertEquals(0, report.diagnosticCount());
        assertTrue(report.firstDiagnostic().isEmpty());
        assertEquals("true", fields.get("literalRuntimeSuccessful"));
        assertEquals("true", fields.get("literalRuntimeEquivalent"));
        assertEquals("proven", fields.get("literalRuntimeReadiness"));
        assertEquals("3", fields.get("literalRuntimeInputCases"));
        assertEquals("1", fields.get("literalRuntimeComparedOutputs"));
        assertEquals("out", fields.get("literalRuntimeComparedOutputNames"));
        assertEquals("0", fields.get("literalRuntimeDiagnostics"));
        assertEquals("false", fields.get("literalRuntimeHasDiagnostics"));
        assertEquals("2", fields.get("literalRuntimePreviewCandidates"));
        assertEquals("2", fields.get("literalRuntimeUniqueCanonicalKeys"));
        assertEquals("true", fields.get("literalRuntimeNumericSemanticsFullyProven"));
        assertEquals("proven", fields.get("literalRuntimeNumericSemanticsReadiness"));
        assertTrue(fields.get("literalRuntimeCanonicalKeyCounts").contains("literal_assoc_preview(plus:int,int,int;literals=1)=1"));
        assertTrue(fields.get("literalRuntimeCanonicalKeyCounts").contains("literal_assoc_preview(times:int,int,int;literals=2)=1"));
        assertFalse(fields.containsKey("literalRuntimeFirstDiagnostic"));
        assertTrue(report.summary().contains("successful=true"));
        assertThrows(UnsupportedOperationException.class, () -> fields.put("x", "y"));
    }

    @Test
    void reportsNotRunEvidenceAsUnsuccessfulDiagnosticArtifact() {
        GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationReport canonicalizationReport = mixedCanonicalizationReport();
        GpuIrCommonSubexpressionSimpleArithmeticLiteralNumericSemanticsProofReport numericProof =
                GpuIrCommonSubexpressionSimpleArithmeticLiteralNumericSemanticsProofReport.from(canonicalizationReport);
        GpuIrCommonSubexpressionSimpleArithmeticLiteralRuntimeEquivalenceReport report =
                GpuIrCommonSubexpressionSimpleArithmeticLiteralRuntimeEquivalenceReport.notRun(canonicalizationReport, numericProof);

        Map<String, String> fields = report.artifactFields("literalRuntime");

        assertFalse(report.successful());
        assertFalse(report.equivalent());
        assertTrue(report.hasDiagnostics());
        assertEquals("notProven", report.readiness());
        assertEquals(1, report.diagnosticCount());
        assertEquals("literal canonicalization runtime equivalence not run", report.firstDiagnostic().orElseThrow());
        assertEquals("false", fields.get("literalRuntimeSuccessful"));
        assertEquals("false", fields.get("literalRuntimeEquivalent"));
        assertEquals("notProven", fields.get("literalRuntimeReadiness"));
        assertEquals("0", fields.get("literalRuntimeInputCases"));
        assertEquals("0", fields.get("literalRuntimeComparedOutputs"));
        assertEquals("1", fields.get("literalRuntimeDiagnostics"));
        assertEquals("true", fields.get("literalRuntimeHasDiagnostics"));
        assertEquals("literal canonicalization runtime equivalence not run", fields.get("literalRuntimeFirstDiagnostic"));
    }

    @Test
    void treatsDiagnosticsAsUnsuccessfulEvenWhenEquivalentFlagIsTrue() {
        GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationReport canonicalizationReport = mixedCanonicalizationReport();
        GpuIrCommonSubexpressionSimpleArithmeticLiteralNumericSemanticsProofReport numericProof =
                GpuIrCommonSubexpressionSimpleArithmeticLiteralNumericSemanticsProofReport.from(canonicalizationReport);
        GpuIrCommonSubexpressionSimpleArithmeticLiteralRuntimeEquivalenceReport report =
                new GpuIrCommonSubexpressionSimpleArithmeticLiteralRuntimeEquivalenceReport(
                        canonicalizationReport,
                        numericProof,
                        true,
                        1,
                        List.of("out"),
                        List.of("diagnostic from external literal runtime runner")
                );

        assertFalse(report.successful());
        assertTrue(report.equivalent());
        assertEquals("notProven", report.readiness());
        assertEquals("diagnostic from external literal runtime runner", report.firstDiagnostic().orElseThrow());
    }

    @Test
    void requiresNumericSemanticsProofForSuccessfulRuntimeEquivalence() {
        GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationReport canonicalizationReport = mixedCanonicalizationReport();
        GpuIrCommonSubexpressionSimpleArithmeticLiteralRuntimeEquivalenceReport report =
                GpuIrCommonSubexpressionSimpleArithmeticLiteralRuntimeEquivalenceReport.equivalent(
                        canonicalizationReport,
                        GpuIrCommonSubexpressionSimpleArithmeticLiteralNumericSemanticsProofReport.empty("kernel"),
                        1,
                        List.of("out")
                );

        assertFalse(report.successful());
        assertTrue(report.equivalent());
        assertEquals("notProven", report.readiness());
    }

    @Test
    void rejectsInvalidRuntimeEquivalenceMetadata() {
        GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationReport canonicalizationReport = mixedCanonicalizationReport();
        GpuIrCommonSubexpressionSimpleArithmeticLiteralNumericSemanticsProofReport numericProof =
                GpuIrCommonSubexpressionSimpleArithmeticLiteralNumericSemanticsProofReport.from(canonicalizationReport);

        assertThrows(IllegalArgumentException.class, () -> GpuIrCommonSubexpressionSimpleArithmeticLiteralRuntimeEquivalenceReport.equivalent(
                canonicalizationReport,
                numericProof,
                -1,
                List.of("out")
        ));
        assertThrows(IllegalArgumentException.class, () -> GpuIrCommonSubexpressionSimpleArithmeticLiteralRuntimeEquivalenceReport.equivalent(
                canonicalizationReport,
                numericProof,
                1,
                List.of("")
        ));
        assertThrows(IllegalArgumentException.class, () -> GpuIrCommonSubexpressionSimpleArithmeticLiteralRuntimeEquivalenceReport.failed(
                canonicalizationReport,
                numericProof,
                1,
                List.of("out"),
                java.util.Arrays.asList((String) null)
        ));
        assertThrows(IllegalArgumentException.class, () -> GpuIrCommonSubexpressionSimpleArithmeticLiteralRuntimeEquivalenceReport.equivalent(
                canonicalizationReport,
                numericProof,
                1,
                List.of("out")
        ).artifactFields(""));
    }

}
