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
                linkedCounts("autoVectorization", 1L, "cse", 2L),
                linkedCounts("guard.memoryAddressSpace", 1L, "cse.CONTROL_FLOW_BOUNDARY", 2L)
        );

        Map<String, String> fields = snapshot.artifactFields("gate");

        assertEquals("true", fields.get("gateBlocked"));
        assertEquals("autoVectorization", fields.get("gateSource"));
        assertEquals("guard.memoryAddressSpace", fields.get("gateFamily"));
        assertEquals("constant memory blocks rewrite", fields.get("gateSummary"));
        assertEquals(snapshot.compactSummary(), fields.get("gateCompactSummary"));
        assertEquals("{autoVectorization=1,cse=2}", fields.get("gateSourceCounts"));
        assertEquals("1", fields.get("gateSourceCount.autoVectorization"));
        assertEquals("2", fields.get("gateSourceCount.cse"));
        assertEquals("{guard.memoryAddressSpace=1,cse.CONTROL_FLOW_BOUNDARY=2}", fields.get("gateFamilyCounts"));
        assertEquals("1", fields.get("gateFamilyCount.guard.memoryAddressSpace"));
        assertEquals("2", fields.get("gateFamilyCount.cse.CONTROL_FLOW_BOUNDARY"));
        assertTrue(snapshot.compactSummary().contains("sourceCounts={autoVectorization=1,cse=2}"));
        assertTrue(snapshot.compactSummary().contains("familyCounts={guard.memoryAddressSpace=1,cse.CONTROL_FLOW_BOUNDARY=2}"));
        assertThrows(UnsupportedOperationException.class, () -> fields.put("x", "y"));
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
        assertThrows(UnsupportedOperationException.class, () -> snapshot.sourceCounts().put("cse", 1L));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.familyCounts().put("cse.CONTROL_FLOW_BOUNDARY", 1L));
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
