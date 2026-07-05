package net.sixik.ga_utils.javatogpu.irvalidation;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static net.sixik.ga_utils.javatogpu.irvalidation.GpuIrCommonSubexpressionSimpleArithmeticLiteralTestFixtures.mixedCanonicalizationReport;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuIrCommonSubexpressionSimpleArithmeticLiteralArtifactConsistencyReportTest {
    @Test
    void reportsConsistentExplicitArtifactStack() {
        GpuIrCommonSubexpressionSimpleArithmeticLiteralRuntimeEquivalenceReport runtimeEquivalence = runtimeEquivalence(true);
        GpuIrCommonSubexpressionSimpleArithmeticLiteralArtifactReport artifact =
                new GpuIrCommonSubexpressionSimpleArithmeticLiteralArtifactReport(runtimeEquivalence);

        GpuIrCommonSubexpressionSimpleArithmeticLiteralArtifactConsistencyReport report =
                GpuIrCommonSubexpressionSimpleArithmeticLiteralArtifactConsistencyReport.from(artifact);
        Map<String, String> fields = report.artifactFields("literalArtifactConsistency");

        assertEquals("consistent", report.verdict());
        assertTrue(report.consistent());
        assertFalse(report.hasFailures());
        assertEquals(14, report.checkCount());
        assertEquals(0, report.failedCheckCount());
        assertEquals(List.of(), report.failedChecks());
        assertEquals("literal artifact consistency check passed: 14 checks", report.ciSummaryLine());
        assertEquals("consistent", fields.get("literalArtifactConsistencyVerdict"));
        assertEquals("true", fields.get("literalArtifactConsistencyConsistent"));
        assertEquals("14", fields.get("literalArtifactConsistencyChecks"));
        assertEquals("0", fields.get("literalArtifactConsistencyFailedChecks"));
        assertEquals("[]", fields.get("literalArtifactConsistencyFailedCheckList"));
        assertEquals("{}", fields.get("literalArtifactConsistencyFailedCheckCounts"));
        assertEquals(report.ciSummaryLine(), fields.get("literalArtifactConsistencyCiSummaryLine"));
        assertThrows(UnsupportedOperationException.class, () -> fields.put("x", "y"));
    }

    @Test
    void reportsDriftWhenRuntimeEvidenceIsNotTiedToArtifactCanonicalization() {
        GpuIrCommonSubexpressionSimpleArithmeticLiteralRuntimeEquivalenceReport runtimeEquivalence = runtimeEquivalence(true);
        GpuIrCommonSubexpressionSimpleArithmeticLiteralArtifactReport cleanArtifact =
                new GpuIrCommonSubexpressionSimpleArithmeticLiteralArtifactReport(runtimeEquivalence);
        GpuIrCommonSubexpressionSimpleArithmeticLiteralRuntimeEquivalenceReport driftedRuntimeEquivalence =
                GpuIrCommonSubexpressionSimpleArithmeticLiteralRuntimeEquivalenceReport.equivalent(
                        GpuIrCommonSubexpressionSimpleArithmeticLiteralTestFixtures.canonicalizationReport(),
                        cleanArtifact.numericSemanticsProofReport(),
                        2,
                        List.of("out")
                );
        GpuIrCommonSubexpressionSimpleArithmeticLiteralArtifactReport driftedArtifact =
                new GpuIrCommonSubexpressionSimpleArithmeticLiteralArtifactReport(
                        cleanArtifact.canonicalizationReport(),
                        cleanArtifact.numericSemanticsProofReport(),
                        driftedRuntimeEquivalence,
                        cleanArtifact.canonicalizationGate(),
                        cleanArtifact.fingerprintDecisionReport(),
                        cleanArtifact.fingerprintParityReport()
                );

        GpuIrCommonSubexpressionSimpleArithmeticLiteralArtifactConsistencyReport report =
                GpuIrCommonSubexpressionSimpleArithmeticLiteralArtifactConsistencyReport.from(driftedArtifact);
        Map<String, String> fields = report.artifactFields("literalArtifactConsistency");

        assertEquals("inconsistent", report.verdict());
        assertFalse(report.consistent());
        assertTrue(report.hasFailures());
        assertTrue(report.failedChecks().contains("runtimeCanonicalizationReference"));
        assertEquals("runtimeCanonicalizationReference", report.firstFailedCheck().orElseThrow());
        assertEquals(
                "runtime-equivalence report is not tied to the artifact canonicalization report",
                report.firstFailureExplanation().orElseThrow()
        );
        assertEquals("runtimeCanonicalizationReference", fields.get("literalArtifactConsistencyFirstFailedCheck"));
        assertEquals(
                "runtime-equivalence report is not tied to the artifact canonicalization report",
                fields.get("literalArtifactConsistencyFirstFailureExplanation")
        );
        assertTrue(fields.get("literalArtifactConsistencyCiSummaryLine").contains("literal artifact consistency check failed"));
    }

    @Test
    void rejectsInvalidMetadata() {
        assertThrows(IllegalArgumentException.class, () -> new GpuIrCommonSubexpressionSimpleArithmeticLiteralArtifactConsistencyReport(
                "",
                "consistent",
                true,
                12,
                0,
                List.of()
        ));
        assertThrows(IllegalArgumentException.class, () -> new GpuIrCommonSubexpressionSimpleArithmeticLiteralArtifactConsistencyReport(
                "kernel",
                "consistent",
                true,
                12,
                1,
                List.of()
        ));
        assertThrows(IllegalArgumentException.class, () -> new GpuIrCommonSubexpressionSimpleArithmeticLiteralArtifactConsistencyReport(
                "kernel",
                "consistent",
                true,
                -1,
                0,
                List.of()
        ));
    }

    private GpuIrCommonSubexpressionSimpleArithmeticLiteralRuntimeEquivalenceReport runtimeEquivalence(boolean equivalent) {
        if (equivalent) {
            return GpuIrCommonSubexpressionSimpleArithmeticLiteralRuntimeEquivalenceReport.equivalent(
                    mixedCanonicalizationReport(),
                    GpuIrCommonSubexpressionSimpleArithmeticLiteralNumericSemanticsProofReport.from(mixedCanonicalizationReport()),
                    2,
                    List.of("out")
            );
        }
        return GpuIrCommonSubexpressionSimpleArithmeticLiteralRuntimeEquivalenceReport.failed(
                mixedCanonicalizationReport(),
                GpuIrCommonSubexpressionSimpleArithmeticLiteralNumericSemanticsProofReport.from(mixedCanonicalizationReport()),
                2,
                List.of("out"),
                List.of("out differs")
        );
    }
}
