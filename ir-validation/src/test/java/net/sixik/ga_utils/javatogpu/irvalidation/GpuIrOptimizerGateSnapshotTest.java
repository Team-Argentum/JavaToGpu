package net.sixik.ga_utils.javatogpu.irvalidation;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuIrOptimizerGateSnapshotTest {
    @Test
    void artifactFieldsExposeExplanationAndGroupedCounts() {
        GpuIrOptimizerGateSnapshot snapshot = new GpuIrOptimizerGateSnapshot(
                GpuIrOptimizerGateExplanation.blocked("autoVectorization", "guard.memoryAddressSpace", "constant memory blocks rewrite"),
                linkedCounts("autoVectorization", 1L, "cseRewritePolicy", 2L),
                linkedCounts("guard.memoryAddressSpace", 1L, "cseRewritePolicy.skipReason.CONTROL_FLOW_BOUNDARY", 2L)
        );

        Map<String, String> fields = snapshot.artifactFields("gate");

        assertEquals("true", fields.get("gateBlocked"));
        assertEquals("autoVectorization", fields.get("gateSource"));
        assertEquals("guard.memoryAddressSpace", fields.get("gateFamily"));
        assertEquals("constant memory blocks rewrite", fields.get("gateSummary"));
        assertEquals(snapshot.compactSummary(), fields.get("gateCompactSummary"));
        assertEquals("{autoVectorization=1,cseRewritePolicy=2}", fields.get("gateSourceCounts"));
        assertEquals("1", fields.get("gateSourceCount.autoVectorization"));
        assertEquals("2", fields.get("gateSourceCount.cseRewritePolicy"));
        assertEquals("{guard.memoryAddressSpace=1,cseRewritePolicy.skipReason.CONTROL_FLOW_BOUNDARY=2}", fields.get("gateFamilyCounts"));
        assertEquals("1", fields.get("gateFamilyCount.guard.memoryAddressSpace"));
        assertEquals("2", fields.get("gateFamilyCount.cseRewritePolicy.skipReason.CONTROL_FLOW_BOUNDARY"));
        assertEquals("consistent", fields.get("gateConsistencyVerdict"));
        assertEquals("true", fields.get("gateConsistencyConsistent"));
        assertEquals("0", fields.get("gateConsistencyFailedChecks"));
        assertEquals("accepted", fields.get("gateAcceptanceVerdict"));
        assertEquals("true", fields.get("gateAcceptanceAccepted"));
        assertEquals("false", fields.get("gateAcceptanceRejected"));
        assertEquals("false", fields.get("gateAcceptanceFailBuild"));
        assertEquals("accepted/consistentArtifact", fields.get("gateAcceptanceReason"));
        assertTrue(snapshot.compactSummary().contains("sourceCounts={autoVectorization=1,cseRewritePolicy=2}"));
        assertTrue(snapshot.compactSummary().contains("familyCounts={guard.memoryAddressSpace=1,cseRewritePolicy.skipReason.CONTROL_FLOW_BOUNDARY=2}"));
        assertThrows(UnsupportedOperationException.class, () -> fields.put("x", "y"));
    }

    @Test
    void consistencyReportValidatesBlockedGateCounters() {
        GpuIrOptimizerGateSnapshot consistentSnapshot = new GpuIrOptimizerGateSnapshot(
                GpuIrOptimizerGateExplanation.blocked("safety", "safety.helperArgumentTypeMismatch", "type mismatch"),
                linkedCounts("safety", 1L),
                linkedCounts("safety.helperArgumentTypeMismatch", 1L)
        );
        GpuIrOptimizerGateSnapshot inconsistentSnapshot = new GpuIrOptimizerGateSnapshot(
                GpuIrOptimizerGateExplanation.blocked("safety", "safety.helperArgumentTypeMismatch", "type mismatch"),
                linkedCounts("safety", 1L),
                linkedCounts("safety.error", 1L)
        );

        GpuIrOptimizerGateConsistencyReport consistentReport = consistentSnapshot.consistencyReport();
        GpuIrOptimizerGateConsistencyReport inconsistentReport = inconsistentSnapshot.consistencyReport();

        assertTrue(consistentReport.consistent());
        assertEquals("consistent", consistentReport.verdict());
        assertEquals(0, consistentReport.failedCheckCount());
        assertEquals("true", consistentReport.artifactFields("gateConsistency").get("gateConsistencyConsistent"));
        assertEquals("inconsistent", inconsistentReport.verdict());
        assertEquals("false", inconsistentReport.artifactFields("gateConsistency").get("gateConsistencyConsistent"));
        assertEquals("blockedFamilyPresent", inconsistentReport.firstFailedCheck().orElseThrow());
        assertEquals("blocked gate family must appear in optimizer gate family counts", inconsistentReport.firstFailureExplanation().orElseThrow());
        assertTrue(inconsistentReport.ciSummaryLine().contains("failed"));
    }

    @Test
    void acceptanceFailsClosedForInconsistentGateArtifacts() {
        GpuIrOptimizerGateSnapshot consistentSnapshot = new GpuIrOptimizerGateSnapshot(
                GpuIrOptimizerGateExplanation.blocked("safety", "safety.helperArgumentTypeMismatch", "type mismatch"),
                linkedCounts("safety", 1L),
                linkedCounts("safety.helperArgumentTypeMismatch", 1L)
        );
        GpuIrOptimizerGateSnapshot inconsistentSnapshot = new GpuIrOptimizerGateSnapshot(
                GpuIrOptimizerGateExplanation.blocked("safety", "safety.helperArgumentTypeMismatch", "type mismatch"),
                linkedCounts("safety", 1L),
                linkedCounts("safety.error", 1L)
        );

        GpuIrOptimizerGateArtifactAcceptance accepted = consistentSnapshot.acceptance();
        GpuIrOptimizerGateArtifactAcceptance rejected = inconsistentSnapshot.acceptance();

        assertTrue(accepted.accepted());
        assertEquals("accepted", accepted.verdict());
        assertEquals("accepted/consistentArtifact", accepted.reason());
        assertEquals("false", accepted.artifactFields("gateAcceptance").get("gateAcceptanceFailBuild"));
        assertTrue(rejected.rejected());
        assertTrue(rejected.failBuild());
        assertEquals("rejected", rejected.verdict());
        assertEquals("rejected/inconsistentArtifact", rejected.reason());
        assertEquals("blockedFamilyPresent", rejected.firstConsistencyFailedCheck());
        assertEquals("true", rejected.artifactFields("gateAcceptance").get("gateAcceptanceFailBuild"));
        assertTrue(rejected.ciSummaryLine().contains("firstConsistencyFailedCheck=blockedFamilyPresent"));
    }

    @Test
    void constructorKeepsDefensiveImmutableCopies() {
        Map<String, Long> sourceCounts = linkedCounts("safety", 1L);
        Map<String, Long> familyCounts = linkedCounts("safety.error", 1L);

        GpuIrOptimizerGateSnapshot snapshot = new GpuIrOptimizerGateSnapshot(
                GpuIrOptimizerGateExplanation.blocked("safety", "safety.error", "unknown variable reference"),
                sourceCounts,
                familyCounts
        );
        sourceCounts.put("autoVectorization", 99L);
        familyCounts.put("guard.alias", 99L);

        assertEquals("{safety=1}", snapshot.sourceCountsSummary());
        assertEquals("{safety.error=1}", snapshot.familyCountsSummary());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.sourceCounts().put("cseRewritePolicy", 1L));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.familyCounts().put("cseRewritePolicy.skipReason.CONTROL_FLOW_BOUNDARY", 1L));
    }

    @Test
    void constructorRejectsInvalidCounters() {
        GpuIrOptimizerGateExplanation explanation = GpuIrOptimizerGateExplanation.allowed();

        assertThrows(NullPointerException.class, () -> new GpuIrOptimizerGateSnapshot(null, Map.of(), Map.of()));
        assertThrows(NullPointerException.class, () -> new GpuIrOptimizerGateSnapshot(explanation, null, Map.of()));
        assertThrows(NullPointerException.class, () -> new GpuIrOptimizerGateSnapshot(explanation, Map.of(), null));
        assertThrows(IllegalArgumentException.class, () -> new GpuIrOptimizerGateSnapshot(explanation, linkedCounts("", 1L), Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new GpuIrOptimizerGateSnapshot(explanation, linkedCounts("safety", -1L), Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new GpuIrOptimizerGateSnapshot(explanation, linkedCounts("safety", null), Map.of()));
    }

    private Map<String, Long> linkedCounts(String firstKey, Long firstValue, Object... rest) {
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put(firstKey, firstValue);
        for (int i = 0; i < rest.length; i += 2) {
            counts.put((String) rest[i], (Long) rest[i + 1]);
        }
        return counts;
    }
}
