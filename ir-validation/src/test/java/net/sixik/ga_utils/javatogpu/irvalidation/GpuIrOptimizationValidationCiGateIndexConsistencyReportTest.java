package net.sixik.ga_utils.javatogpu.irvalidation;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static net.sixik.ga_utils.javatogpu.irvalidation.GpuIrOptimizationValidationRuleTestFixtures.validationReport;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuIrOptimizationValidationCiGateIndexConsistencyReportTest {
    @Test
    void reportsConsistentGateIndexArtifacts() {
        GpuIrOptimizationValidationCiGateIndex index = new GpuIrOptimizationValidationOptimizerEnablementArtifactRunner()
                .runCiGateIndex(validationReport("consistentGateIndexKernel"));

        GpuIrOptimizationValidationCiGateIndexConsistencyReport report =
                GpuIrOptimizationValidationCiGateIndexConsistencyReport.from(index);
        Map<String, String> fields = report.artifactFields();

        assertEquals("consistentGateIndexKernel", report.methodName());
        assertEquals("consistent", report.verdict());
        assertTrue(report.consistent());
        assertFalse(report.hasFailures());
        assertEquals(10, report.checkCount());
        assertEquals(0, report.failedCheckCount());
        assertEquals(List.of(), report.failedChecks());
        assertEquals("consistentGateIndexKernel", fields.get("optimizerCiGateIndexConsistencyMethod"));
        assertEquals("consistent", fields.get("optimizerCiGateIndexConsistencyVerdict"));
        assertEquals("true", fields.get("optimizerCiGateIndexConsistencyConsistent"));
        assertEquals("10", fields.get("optimizerCiGateIndexConsistencyChecks"));
        assertEquals("0", fields.get("optimizerCiGateIndexConsistencyFailedChecks"));
        assertEquals("[]", fields.get("optimizerCiGateIndexConsistencyFailedCheckList"));
        assertEquals("{}", fields.get("optimizerCiGateIndexConsistencyFailedCheckCounts"));
        assertTrue(fields.get("optimizerCiGateIndexConsistencyCiSummaryLine")
                .contains("optimizer ci gate index consistency check passed"));
        assertThrows(UnsupportedOperationException.class, () -> fields.put("x", "y"));
    }

    @Test
    void canRepresentInconsistentGateIndexArtifactShape() {
        GpuIrOptimizationValidationCiGateIndexConsistencyReport report =
                new GpuIrOptimizationValidationCiGateIndexConsistencyReport(
                        "driftedGateIndexKernel",
                        "inconsistent",
                        false,
                        10,
                        2,
                        List.of("acceptedMatchesGateFlags", "firstRejectedGateMatchesPriority")
                );
        Map<String, String> fields = report.artifactFields("indexConsistency.");

        assertEquals("inconsistent", report.verdict());
        assertFalse(report.consistent());
        assertTrue(report.hasFailures());
        assertEquals("acceptedMatchesGateFlags", report.firstFailedCheck().orElseThrow());
        assertEquals(
                "index accepted flag must match all nested gate accepted flags",
                report.firstFailureExplanation().orElseThrow()
        );
        assertEquals("false", fields.get("indexConsistency.Consistent"));
        assertEquals("2", fields.get("indexConsistency.FailedChecks"));
        assertEquals("[acceptedMatchesGateFlags,firstRejectedGateMatchesPriority]", fields.get("indexConsistency.FailedCheckList"));
        assertEquals("{acceptedMatchesGateFlags=1,firstRejectedGateMatchesPriority=1}", fields.get("indexConsistency.FailedCheckCounts"));
        assertEquals("acceptedMatchesGateFlags", fields.get("indexConsistency.FirstFailedCheck"));
        assertEquals(
                "index accepted flag must match all nested gate accepted flags",
                fields.get("indexConsistency.FirstFailureExplanation")
        );
        assertTrue(fields.get("indexConsistency.CiSummaryLine").contains("optimizer ci gate index consistency check failed"));
    }

    @Test
    void rejectsInvalidMetadataAndPrefixes() {
        GpuIrOptimizationValidationCiGateIndexConsistencyReport report =
                new GpuIrOptimizationValidationCiGateIndexConsistencyReport(
                        "kernel",
                        "consistent",
                        true,
                        10,
                        0,
                        List.of()
                );

        assertThrows(NullPointerException.class, () -> GpuIrOptimizationValidationCiGateIndexConsistencyReport.from(null));
        assertThrows(IllegalArgumentException.class, () -> report.artifactFields(""));
        assertThrows(IllegalArgumentException.class, () -> new GpuIrOptimizationValidationCiGateIndexConsistencyReport(
                "",
                "consistent",
                true,
                10,
                0,
                List.of()
        ));
        assertThrows(IllegalArgumentException.class, () -> new GpuIrOptimizationValidationCiGateIndexConsistencyReport(
                "kernel",
                "",
                true,
                10,
                0,
                List.of()
        ));
        assertThrows(IllegalArgumentException.class, () -> new GpuIrOptimizationValidationCiGateIndexConsistencyReport(
                "kernel",
                "consistent",
                true,
                -1,
                0,
                List.of()
        ));
        assertThrows(IllegalArgumentException.class, () -> new GpuIrOptimizationValidationCiGateIndexConsistencyReport(
                "kernel",
                "consistent",
                true,
                10,
                1,
                List.of()
        ));
        assertThrows(IllegalArgumentException.class, () -> new GpuIrOptimizationValidationCiGateIndexConsistencyReport(
                "kernel",
                "inconsistent",
                true,
                10,
                1,
                List.of("acceptedMatchesGateFlags")
        ));
    }
}
