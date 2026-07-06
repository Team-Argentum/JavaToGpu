package net.sixik.ga_utils.javatogpu.irvalidation;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuIrOptimizerGateArtifactContractTest {
    @Test
    void reportsConsistentBlockedGateSnapshot() {
        GpuIrOptimizerGateSnapshot snapshot = new GpuIrOptimizerGateSnapshot(
                GpuIrOptimizerGateExplanation.blocked(
                        "autoVectorization",
                        "warning.nonCanonicalVectorShape",
                        "vector shape needs review"
                ),
                Map.of("autoVectorization", 2L),
                Map.of("warning.nonCanonicalVectorShape", 2L)
        );

        GpuIrOptimizerGateConsistencyReport consistency = snapshot.consistencyReport();
        GpuIrOptimizerGateArtifactAcceptance acceptance = snapshot.acceptance();
        Map<String, String> fields = snapshot.artifactFields();

        assertEquals("consistent", consistency.verdict());
        assertTrue(consistency.consistent());
        assertFalse(consistency.hasFailures());
        assertEquals(6, consistency.checkCount());
        assertEquals(0, consistency.failedCheckCount());
        assertEquals(List.of(), consistency.failedChecks());
        assertEquals("accepted", acceptance.verdict());
        assertTrue(acceptance.accepted());
        assertFalse(acceptance.rejected());
        assertFalse(acceptance.failBuild());
        assertEquals("accepted/consistentArtifact", acceptance.reason());
        assertEquals("true", fields.get("optimizerGateBlocked"));
        assertEquals("autoVectorization", fields.get("optimizerGateSource"));
        assertEquals("warning.nonCanonicalVectorShape", fields.get("optimizerGateFamily"));
        assertEquals("{autoVectorization=2}", fields.get("optimizerGateSourceCounts"));
        assertEquals("{warning.nonCanonicalVectorShape=2}", fields.get("optimizerGateFamilyCounts"));
        assertEquals("consistent", fields.get("optimizerGateConsistencyVerdict"));
        assertEquals("accepted", fields.get("optimizerGateAcceptanceVerdict"));
        assertEquals("accepted/consistentArtifact", fields.get("optimizerGateAcceptanceReason"));
        assertThrows(UnsupportedOperationException.class, () -> fields.put("x", "y"));
    }

    @Test
    void reportsInconsistentGateSnapshotDrift() {
        GpuIrOptimizerGateSnapshot snapshot = new GpuIrOptimizerGateSnapshot(
                GpuIrOptimizerGateExplanation.blocked(
                        "autoVectorization",
                        "warning.missingFamilyCounter",
                        "counter drift"
                ),
                Map.of(),
                Map.of("warning.missingFamilyCounter", 0L)
        );

        GpuIrOptimizerGateConsistencyReport consistency = snapshot.consistencyReport();
        GpuIrOptimizerGateArtifactAcceptance acceptance = snapshot.acceptance();
        Map<String, String> consistencyFields = consistency.artifactFields("gateConsistency.");
        Map<String, String> acceptanceFields = acceptance.artifactFields("gateAcceptance.");

        assertEquals("inconsistent", consistency.verdict());
        assertFalse(consistency.consistent());
        assertTrue(consistency.hasFailures());
        assertEquals(2, consistency.failedCheckCount());
        assertEquals(List.of("blockedSourcePresent", "familyCountsPositive"), consistency.failedChecks());
        assertEquals("blockedSourcePresent", consistency.firstFailedCheck().orElseThrow());
        assertEquals(
                "blocked gate source must appear in optimizer gate source counts",
                consistency.firstFailureExplanation().orElseThrow()
        );
        assertEquals("rejected", acceptance.verdict());
        assertFalse(acceptance.accepted());
        assertTrue(acceptance.rejected());
        assertTrue(acceptance.failBuild());
        assertEquals("rejected/inconsistentArtifact", acceptance.reason());
        assertEquals("blockedSourcePresent", acceptance.firstConsistencyFailedCheck());
        assertEquals("[blockedSourcePresent,familyCountsPositive]", consistencyFields.get("gateConsistency.FailedCheckList"));
        assertEquals("{blockedSourcePresent=1,familyCountsPositive=1}", consistencyFields.get("gateConsistency.FailedCheckCounts"));
        assertTrue(consistencyFields.get("gateConsistency.CiSummaryLine").contains("optimizer gate consistency check failed"));
        assertEquals("rejected/inconsistentArtifact", acceptanceFields.get("gateAcceptance.Reason"));
        assertEquals("blockedSourcePresent", acceptanceFields.get("gateAcceptance.FirstConsistencyFailedCheck"));
        assertTrue(acceptanceFields.get("gateAcceptance.CiSummaryLine").contains("consistent=false"));
    }

    @Test
    void acceptsUnblockedEmptyGateSnapshot() {
        GpuIrOptimizerGateSnapshot snapshot = new GpuIrOptimizerGateSnapshot(
                GpuIrOptimizerGateExplanation.allowed(),
                Map.of(),
                Map.of()
        );

        GpuIrOptimizerGateConsistencyReport consistency = snapshot.consistencyReport();
        GpuIrOptimizerGateArtifactAcceptance acceptance = GpuIrOptimizerGateArtifactAcceptance.from(consistency);

        assertEquals("consistent", consistency.verdict());
        assertTrue(consistency.consistent());
        assertTrue(acceptance.accepted());
        assertFalse(acceptance.failBuild());
        assertEquals("", acceptance.firstConsistencyFailedCheck());
    }

    @Test
    void rejectsInvalidInputsAndPrefixes() {
        GpuIrOptimizerGateConsistencyReport consistency = new GpuIrOptimizerGateConsistencyReport(
                "consistent",
                true,
                6,
                0,
                List.of()
        );
        GpuIrOptimizerGateArtifactAcceptance acceptance = GpuIrOptimizerGateArtifactAcceptance.from(consistency);

        assertThrows(NullPointerException.class, () -> GpuIrOptimizerGateConsistencyReport.from(null));
        assertThrows(NullPointerException.class, () -> GpuIrOptimizerGateArtifactAcceptance.from(
                (GpuIrOptimizerGateSnapshot) null
        ));
        assertThrows(NullPointerException.class, () -> GpuIrOptimizerGateArtifactAcceptance.from(
                (GpuIrOptimizerGateConsistencyReport) null
        ));
        assertThrows(IllegalArgumentException.class, () -> consistency.artifactFields(""));
        assertThrows(IllegalArgumentException.class, () -> acceptance.artifactFields(""));
        assertThrows(IllegalArgumentException.class, () -> new GpuIrOptimizerGateConsistencyReport(
                "",
                true,
                6,
                0,
                List.of()
        ));
        assertThrows(IllegalArgumentException.class, () -> new GpuIrOptimizerGateConsistencyReport(
                "consistent",
                true,
                -1,
                0,
                List.of()
        ));
        assertThrows(IllegalArgumentException.class, () -> new GpuIrOptimizerGateConsistencyReport(
                "consistent",
                true,
                6,
                1,
                List.of()
        ));
        assertThrows(IllegalArgumentException.class, () -> new GpuIrOptimizerGateConsistencyReport(
                "inconsistent",
                true,
                6,
                1,
                List.of("blockedSourcePresent")
        ));
        assertThrows(IllegalArgumentException.class, () -> new GpuIrOptimizerGateArtifactAcceptance(
                "accepted",
                true,
                true,
                "accepted/consistentArtifact",
                "",
                "summary"
        ));
        assertThrows(IllegalArgumentException.class, () -> new GpuIrOptimizerGateArtifactAcceptance(
                "accepted",
                true,
                false,
                "rejected/inconsistentArtifact",
                "blockedSourcePresent",
                "summary"
        ));
        assertThrows(IllegalArgumentException.class, () -> new GpuIrOptimizerGateArtifactAcceptance(
                "rejected",
                false,
                true,
                "accepted/consistentArtifact",
                "",
                "summary"
        ));
    }
}
