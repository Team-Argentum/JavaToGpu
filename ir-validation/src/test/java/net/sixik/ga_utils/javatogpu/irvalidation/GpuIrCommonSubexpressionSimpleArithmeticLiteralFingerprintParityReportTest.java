package net.sixik.ga_utils.javatogpu.irvalidation;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static net.sixik.ga_utils.javatogpu.irvalidation.GpuIrCommonSubexpressionSimpleArithmeticLiteralTestFixtures.canonicalizationReport;
import static net.sixik.ga_utils.javatogpu.irvalidation.GpuIrCommonSubexpressionSimpleArithmeticLiteralTestFixtures.emptyCanonicalizationReport;
import static net.sixik.ga_utils.javatogpu.irvalidation.GpuIrCommonSubexpressionSimpleArithmeticLiteralTestFixtures.evidence;
import static net.sixik.ga_utils.javatogpu.irvalidation.GpuIrCommonSubexpressionSimpleArithmeticLiteralTestFixtures.snapshot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuIrCommonSubexpressionSimpleArithmeticLiteralFingerprintParityReportTest {
    @Test
    void reportsNoPreviewCandidatesAsInactiveParity() {
        GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationReport canonicalization =
                emptyCanonicalizationReport();
        GpuIrCommonSubexpressionSimpleArithmeticLiteralFingerprintDecisionReport decision = evidence(canonicalization, false, List.of()).decision();

        GpuIrCommonSubexpressionSimpleArithmeticLiteralFingerprintParityReport report =
                GpuIrCommonSubexpressionSimpleArithmeticLiteralFingerprintParityReport.from(
                        snapshot(List.of()),
                        canonicalization,
                        decision
                );

        assertEquals("none", report.readiness());
        assertFalse(report.hasPreviewCandidates());
        assertFalse(report.hasPreviewOnlyKeys());
        assertEquals(List.of("noPreviewCandidates", "fingerprintDecisionNotReadyForProduction"), report.blockers());
        assertEquals("noPreviewCandidates", report.firstBlocker().orElseThrow());
    }

    @Test
    void reportsPreviewOnlyLiteralKeysAgainstCurrentProductionCse() {
        GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationReport canonicalization = canonicalizationReport();
        GpuIrCommonSubexpressionSimpleArithmeticLiteralFingerprintDecisionReport decision = evidence(canonicalization, false, List.of()).decision();

        GpuIrCommonSubexpressionSimpleArithmeticLiteralFingerprintParityReport report =
                GpuIrCommonSubexpressionSimpleArithmeticLiteralFingerprintParityReport.from(
                        snapshot(List.of("binary_assoc_simple(+,var(x),var(y),var(z))")),
                        canonicalization,
                        decision
                );
        Map<String, String> fields = report.artifactFields("literalParity");

        assertEquals("previewOnly", report.readiness());
        assertTrue(report.hasPreviewOnlyKeys());
        assertEquals(1, report.productionCandidateCount());
        assertEquals(0, report.productionCandidateOverlapCount());
        assertEquals(2, report.previewOnlyKeyCount());
        assertEquals("previewOnly", fields.get("literalParityReadiness"));
        assertEquals("2", fields.get("literalParityUniquePreviewKeys"));
        assertEquals("0", fields.get("literalParityProductionCandidateOverlaps"));
        assertEquals("[previewOnlyKeysNotInProductionFingerprints,fingerprintDecisionNotReadyForProduction]", fields.get("literalParityBlockers"));
        assertTrue(fields.get("literalParityFirstPreviewOnlyKey").startsWith("literal_assoc_preview("));
        assertThrows(UnsupportedOperationException.class, () -> fields.put("x", "y"));
    }

    @Test
    void reportsProductionOverlapWhenPreviewKeyAlreadyExistsInCseOutput() {
        GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationReport canonicalization = canonicalizationReport();
        GpuIrCommonSubexpressionSimpleArithmeticLiteralFingerprintDecisionReport decision = evidence(canonicalization, false, List.of()).decision();

        GpuIrCommonSubexpressionSimpleArithmeticLiteralFingerprintParityReport report =
                GpuIrCommonSubexpressionSimpleArithmeticLiteralFingerprintParityReport.from(
                        snapshot(List.of("literal_assoc_preview(plus:int,int,int;literals=1)")),
                        canonicalization,
                        decision
                );

        assertEquals("previewOnly", report.readiness());
        assertEquals(1, report.productionCandidateOverlapCount());
        assertEquals(1, report.previewOnlyKeyCount());
        assertTrue(report.summary().contains("productionOverlaps=1"));
    }

    @Test
    void rejectsInvalidMetadata() {
        assertThrows(IllegalArgumentException.class, () -> new GpuIrCommonSubexpressionSimpleArithmeticLiteralFingerprintParityReport(
                "",
                0,
                0,
                0,
                0,
                0,
                false,
                false,
                "none",
                List.of(),
                List.of(),
                List.of()
        ));
        assertThrows(IllegalArgumentException.class, () -> new GpuIrCommonSubexpressionSimpleArithmeticLiteralFingerprintParityReport(
                "kernel",
                -1,
                0,
                0,
                0,
                0,
                false,
                false,
                "none",
                List.of(),
                List.of(),
                List.of()
        ));
        assertThrows(NullPointerException.class, () -> GpuIrCommonSubexpressionSimpleArithmeticLiteralFingerprintParityReport.from(
                null,
                emptyCanonicalizationReport(),
                evidence(emptyCanonicalizationReport(), false, List.of()).decision()
        ));
    }
}
