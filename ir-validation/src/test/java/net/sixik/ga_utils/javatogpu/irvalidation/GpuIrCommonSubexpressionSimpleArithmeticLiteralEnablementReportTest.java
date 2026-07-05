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

class GpuIrCommonSubexpressionSimpleArithmeticLiteralEnablementReportTest {
    @Test
    void reportsNoPreviewVerdict() {
        GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationReport canonicalization =
                emptyCanonicalizationReport();

        GpuIrCommonSubexpressionSimpleArithmeticLiteralEnablementReport report = evidence(canonicalization, false, List.of()).enablement();

        assertEquals("notReady/noPreviewCandidates", report.verdict());
        assertFalse(report.hasPreviewCandidates());
        assertEquals(List.of("noPreviewCandidates", "fingerprintDecisionNotReadyForProduction"), report.blockers());
    }

    @Test
    void reportsRuntimeMissingBeforeExplicitEvidenceRuns() {
        GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationReport canonicalization = canonicalizationReport();

        GpuIrCommonSubexpressionSimpleArithmeticLiteralEnablementReport report = evidence(canonicalization, false, List.of()).enablement();
        Map<String, String> fields = report.artifactFields("literalEnablement");

        assertEquals("notReady/runtimeMissing", report.verdict());
        assertTrue(report.hasPreviewCandidates());
        assertTrue(report.hasPreviewOnlyKeys());
        assertEquals("notReady/runtimeMissing", fields.get("literalEnablementVerdict"));
        assertEquals("blockedPreview", fields.get("literalEnablementDecisionReadiness"));
        assertEquals("previewOnly", fields.get("literalEnablementParityReadiness"));
        assertEquals("runtimeEquivalenceNotProven", fields.get("literalEnablementFirstBlocker"));
        assertThrows(UnsupportedOperationException.class, () -> fields.put("x", "y"));
    }

    @Test
    void reportsPreviewOnlyBlastRadiusAfterRuntimeEvidenceCompletes() {
        GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationReport canonicalization = canonicalizationReport();

        GpuIrCommonSubexpressionSimpleArithmeticLiteralEnablementReport report = evidence(canonicalization, true, List.of()).enablement();

        assertEquals("notReady/previewOnlyBlastRadius", report.verdict());
        assertTrue(report.evidenceComplete());
        assertEquals(2, report.previewOnlyKeyCount());
        assertTrue(report.blockers().contains("previewOnlyKeysNotInProductionFingerprints"));
        assertTrue(report.summary().contains("verdict=notReady/previewOnlyBlastRadius"));
    }

    @Test
    void reportsDisabledWhenEvidenceCompleteAndParityHasNoPreviewOnlyKeys() {
        GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationReport canonicalization = canonicalizationReport();
        GpuIrCommonSubexpressionSimpleArithmeticLiteralEnablementReport report = evidence(canonicalization, true, List.of(
                "literal_assoc_preview(plus:int,int,int;literals=1)",
                "literal_assoc_preview(plus:int,int,int;literals=2)"
        )).enablement();

        assertEquals("evidenceCompleteButDisabled", report.verdict());
        assertTrue(report.evidenceComplete());
        assertFalse(report.hasPreviewOnlyKeys());
        assertEquals(List.of("productionFingerprintIntegrationDisabled", "fingerprintDecisionNotReadyForProduction"), report.blockers());
    }

    @Test
    void rejectsInvalidMetadata() {
        assertThrows(IllegalArgumentException.class, () -> new GpuIrCommonSubexpressionSimpleArithmeticLiteralEnablementReport(
                "",
                "notReady/noPreviewCandidates",
                0,
                0,
                false,
                false,
                false,
                false,
                "none",
                "none",
                0,
                List.of()
        ));
        assertThrows(IllegalArgumentException.class, () -> new GpuIrCommonSubexpressionSimpleArithmeticLiteralEnablementReport(
                "kernel",
                "",
                0,
                0,
                false,
                false,
                false,
                false,
                "none",
                "none",
                0,
                List.of()
        ));
    }

}
