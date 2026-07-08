package net.sixik.ga_utils.javatogpu.irvalidation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuIrCommonSubexpressionArtifactSummaryTest {
    @Test
    void buildsCompactSummaryLineWithoutDiagnosticSuffixWhenClean() {
        GpuIrCommonSubexpressionArtifactSummary summary = new GpuIrCommonSubexpressionArtifactSummary(
                true,
                1,
                2,
                0,
                true,
                3,
                1,
                0,
                "",
                "{}"
        );

        assertFalse(summary.hasDiagnostics());
        assertFalse(summary.hasSkippedCandidates());
        assertTrue(summary.summaryLine().contains("successful=true"));
        assertTrue(summary.summaryLine().contains("runtimeEquivalenceSuccessful=true"));
        assertFalse(summary.summaryLine().contains("firstDiagnostic="));
    }

    @Test
    void includesFirstDiagnosticWhenPresent() {
        GpuIrCommonSubexpressionArtifactSummary summary = new GpuIrCommonSubexpressionArtifactSummary(
                false,
                0,
                0,
                1,
                false,
                1,
                1,
                1,
                "case 0 output differs",
                "{requiresLocalExpressionDominance=1}"
        );

        assertTrue(summary.hasDiagnostics());
        assertTrue(summary.hasSkippedCandidates());
        assertTrue(summary.summaryLine().contains("firstDiagnostic=case 0 output differs"));
        assertTrue(summary.summaryLine().contains("skippedDominanceStatusCounts={requiresLocalExpressionDominance=1}"));
    }

    @Test
    void rejectsInvalidSummaryMetadata() {
        assertThrows(IllegalArgumentException.class, () -> new GpuIrCommonSubexpressionArtifactSummary(
                true,
                -1,
                0,
                0,
                true,
                0,
                0,
                0,
                "",
                "{}"
        ));
        assertThrows(NullPointerException.class, () -> new GpuIrCommonSubexpressionArtifactSummary(
                true,
                0,
                0,
                0,
                true,
                0,
                0,
                0,
                null,
                "{}"
        ));
    }
}
