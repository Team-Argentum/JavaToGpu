package net.sixik.ga_utils.javatogpu.irvalidation;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuIrOptimizationValidationOptimizerBlockerBaselineSnapshotTest {
    @Test
    void exportsAndRestoresStableOptimizerBlockerBaselineFields() {
        GpuIrOptimizationValidationOptimizerBlockerBaselineSnapshot snapshot =
                GpuIrOptimizationValidationOptimizerBlockerBaselineSnapshot.from(
                        GpuIrOptimizationValidationRuleTestFixtures.validationReport("kernel")
                );

        Map<String, String> fields = snapshot.artifactFields();
        GpuIrOptimizationValidationOptimizerBlockerBaselineSnapshot restored =
                GpuIrOptimizationValidationOptimizerBlockerBaselineSnapshot.fromArtifactFields(fields);

        assertEquals("kernel", fields.get("optimizerBlockerBaselineSnapshotMethod"));
        assertEquals("blocked", fields.get("optimizerBlockerBaselineSnapshotVerdict"));
        assertEquals("autoVectorization", fields.get("optimizerBlockerBaselineSnapshotSource"));
        assertEquals("candidateDiscovery.noRewriteCandidates", fields.get("optimizerBlockerBaselineSnapshotFamily"));
        assertEquals("collectRewriteCandidates", fields.get("optimizerBlockerBaselineSnapshotRemainingWork"));
        assertEquals(snapshot, restored);
        assertTrue(fields.get("optimizerBlockerBaselineSnapshotCiSummaryLine").contains("source=autoVectorization"));
        assertThrows(UnsupportedOperationException.class, () -> fields.put("x", "y"));
    }

    @Test
    void rejectsInvalidSnapshotFields() {
        assertThrows(IllegalArgumentException.class, () -> new GpuIrOptimizationValidationOptimizerBlockerBaselineSnapshot(
                "",
                "blocked",
                "autoVectorization",
                "candidateDiscovery.noRewriteCandidates",
                "collectRewriteCandidates"
        ));
        assertThrows(IllegalArgumentException.class, () -> new GpuIrOptimizationValidationOptimizerBlockerBaselineSnapshot(
                "kernel",
                "maybe",
                "autoVectorization",
                "candidateDiscovery.noRewriteCandidates",
                "collectRewriteCandidates"
        ));
        assertThrows(IllegalArgumentException.class, () -> GpuIrOptimizationValidationOptimizerBlockerBaselineSnapshot
                .fromArtifactFields(Map.of()));
    }
}
