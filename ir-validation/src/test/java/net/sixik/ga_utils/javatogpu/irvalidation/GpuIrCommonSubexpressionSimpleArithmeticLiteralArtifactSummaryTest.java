package net.sixik.ga_utils.javatogpu.irvalidation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuIrCommonSubexpressionSimpleArithmeticLiteralArtifactSummaryTest {
    @Test
    void buildsCompactSummaryLineWithoutDiagnosticSuffixWhenClean() {
        GpuIrCommonSubexpressionSimpleArithmeticLiteralArtifactSummary summary =
                new GpuIrCommonSubexpressionSimpleArithmeticLiteralArtifactSummary(
                        true,
                        2,
                        2,
                        true,
                        true,
                        3,
                        2,
                        0,
                        "",
                        "blockedPreview",
                        "[fingerprintIntegrationDisabled]"
                );

        assertFalse(summary.hasDiagnostics());
        assertTrue(summary.hasPreviewCandidates());
        assertTrue(summary.summaryLine().contains("successful=true"));
        assertTrue(summary.summaryLine().contains("previewCandidates=2"));
        assertTrue(summary.summaryLine().contains("runtimeEquivalenceSuccessful=true"));
        assertTrue(summary.summaryLine().contains("gateBlockingReasons=[fingerprintIntegrationDisabled]"));
        assertFalse(summary.summaryLine().contains("firstDiagnostic="));
    }

    @Test
    void includesFirstDiagnosticWhenPresent() {
        GpuIrCommonSubexpressionSimpleArithmeticLiteralArtifactSummary summary =
                new GpuIrCommonSubexpressionSimpleArithmeticLiteralArtifactSummary(
                        false,
                        1,
                        1,
                        true,
                        false,
                        1,
                        1,
                        1,
                        "case case-a output out differs",
                        "blockedPreview",
                        "[runtimeEquivalenceNotProven,fingerprintIntegrationDisabled]"
                );

        assertTrue(summary.hasDiagnostics());
        assertTrue(summary.hasPreviewCandidates());
        assertTrue(summary.summaryLine().contains("firstDiagnostic=case case-a output out differs"));
        assertTrue(summary.summaryLine().contains("gateReadiness=blockedPreview"));
    }

    @Test
    void rejectsInvalidSummaryMetadata() {
        assertThrows(IllegalArgumentException.class, () -> new GpuIrCommonSubexpressionSimpleArithmeticLiteralArtifactSummary(
                true,
                -1,
                0,
                true,
                true,
                0,
                0,
                0,
                "",
                "none",
                "[]"
        ));
        assertThrows(NullPointerException.class, () -> new GpuIrCommonSubexpressionSimpleArithmeticLiteralArtifactSummary(
                true,
                0,
                0,
                true,
                true,
                0,
                0,
                0,
                null,
                "none",
                "[]"
        ));
    }
}
