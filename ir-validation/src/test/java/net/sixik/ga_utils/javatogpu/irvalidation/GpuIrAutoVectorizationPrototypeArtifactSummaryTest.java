package net.sixik.ga_utils.javatogpu.irvalidation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuIrAutoVectorizationPrototypeArtifactSummaryTest {
    @Test
    void buildsCompactSummaryLineWithoutDiagnosticSuffixWhenClean() {
        GpuIrAutoVectorizationPrototypeArtifactSummary summary = new GpuIrAutoVectorizationPrototypeArtifactSummary(
                "kernel",
                true,
                1,
                true,
                2,
                1,
                0,
                "",
                "{laneCopy=1,unaryLaneOp=0,binaryLaneOp=0,laneLiteralBinaryOp=0}"
        );

        assertFalse(summary.hasDiagnostics());
        assertTrue(summary.summaryLine().contains("method=kernel"));
        assertTrue(summary.summaryLine().contains("runtimeEquivalenceSuccessful=true"));
        assertFalse(summary.summaryLine().contains("firstDiagnostic="));
    }

    @Test
    void includesFirstDiagnosticWhenPresent() {
        GpuIrAutoVectorizationPrototypeArtifactSummary summary = new GpuIrAutoVectorizationPrototypeArtifactSummary(
                "kernel",
                false,
                1,
                false,
                1,
                1,
                1,
                "case a output out differs",
                "{laneCopy=1,unaryLaneOp=0,binaryLaneOp=0,laneLiteralBinaryOp=0}"
        );

        assertTrue(summary.hasDiagnostics());
        assertTrue(summary.summaryLine().contains("firstDiagnostic=case a output out differs"));
    }

    @Test
    void rejectsInvalidSummaryMetadata() {
        assertThrows(IllegalArgumentException.class, () -> new GpuIrAutoVectorizationPrototypeArtifactSummary(
                "",
                true,
                0,
                true,
                0,
                0,
                0,
                "",
                "{}"
        ));
        assertThrows(IllegalArgumentException.class, () -> new GpuIrAutoVectorizationPrototypeArtifactSummary(
                "kernel",
                true,
                -1,
                true,
                0,
                0,
                0,
                "",
                "{}"
        ));
    }
}
