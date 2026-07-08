package net.sixik.ga_utils.javatogpu.irvalidation;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuIrOptimizationValidationRuleResultTest {
    @Test
    void exportsStablePassWarnFailArtifactFields() {
        GpuIrOptimizationValidationRuleResult passed = GpuIrOptimizationValidationRuleResult.passed(
                "safety.clean",
                "clean",
                Map.of("method", "kernel")
        );
        GpuIrOptimizationValidationRuleResult warned = GpuIrOptimizationValidationRuleResult.warned(
                "optimizer.advisoryDiagnostics",
                "advisory",
                Map.of("opportunities", "2")
        );
        GpuIrOptimizationValidationRuleResult failed = GpuIrOptimizationValidationRuleResult.failed(
                "optimizer.noBlockingDiagnostics",
                "blocked",
                Map.of("diagnostics", "1")
        );

        assertTrue(passed.passed());
        assertEquals(GpuIrOptimizationValidationRuleStatus.PASS, passed.status());
        assertFalse(passed.blocking());
        assertTrue(warned.passed());
        assertEquals(GpuIrOptimizationValidationRuleStatus.WARN, warned.status());
        assertFalse(warned.blocking());
        assertFalse(failed.passed());
        assertEquals(GpuIrOptimizationValidationRuleStatus.FAIL, failed.status());
        assertTrue(failed.blocking());

        Map<String, String> fields = warned.artifactFields("rule.");
        assertEquals("optimizer.advisoryDiagnostics", fields.get("rule.RuleId"));
        assertEquals("true", fields.get("rule.Passed"));
        assertEquals("warn", fields.get("rule.Status"));
        assertEquals("false", fields.get("rule.Blocking"));
        assertEquals("advisory", fields.get("rule.Message"));
        assertEquals("1", fields.get("rule.MetadataCount"));
        assertEquals("true", fields.get("rule.MetadataPresent"));
        assertEquals("2", fields.get("rule.Metadata.opportunities"));
    }

    @Test
    void marksEmptyMetadataAsAbsentInArtifactFields() {
        GpuIrOptimizationValidationRuleResult result = GpuIrOptimizationValidationRuleResult.passed(
                "safety.clean",
                "clean"
        );

        Map<String, String> fields = result.artifactFields("rule.");

        assertEquals("0", fields.get("rule.MetadataCount"));
        assertEquals("false", fields.get("rule.MetadataPresent"));
    }

    @Test
    void keepsMetadataSnapshotImmutable() {
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("method", "kernel");

        GpuIrOptimizationValidationRuleResult result = GpuIrOptimizationValidationRuleResult.passed(
                "safety.clean",
                "clean",
                metadata
        );
        metadata.put("method", "changed");
        Map<String, String> fields = result.artifactFields("rule.");

        assertEquals("kernel", result.metadata().get("method"));
        assertEquals("kernel", fields.get("rule.Metadata.method"));
        assertEquals("1", fields.get("rule.MetadataCount"));
        assertEquals("true", fields.get("rule.MetadataPresent"));
        assertThrows(UnsupportedOperationException.class, () -> result.metadata().put("x", "y"));
        assertThrows(UnsupportedOperationException.class, () -> fields.put("x", "y"));
    }

    @Test
    void rejectsInvalidResultContracts() {
        assertThrows(IllegalArgumentException.class, () -> GpuIrOptimizationValidationRuleResult.passed("", "message"));
        assertThrows(NullPointerException.class, () -> GpuIrOptimizationValidationRuleResult.passed("rule", null));
        assertThrows(NullPointerException.class, () -> GpuIrOptimizationValidationRuleResult.passed("rule", "message", null));
        assertThrows(IllegalArgumentException.class, () -> GpuIrOptimizationValidationRuleResult.passed(
                "rule",
                "message",
                Map.of("", "value")
        ));
        assertThrows(NullPointerException.class, () -> GpuIrOptimizationValidationRuleResult.passed(
                "rule",
                "message",
                java.util.Collections.singletonMap("key", null)
        ));
        assertThrows(IllegalArgumentException.class, () -> new GpuIrOptimizationValidationRuleResult(
                "rule",
                true,
                GpuIrOptimizationValidationRuleStatus.FAIL,
                "message",
                Map.of()
        ));
        assertThrows(IllegalArgumentException.class, () -> new GpuIrOptimizationValidationRuleResult(
                "rule",
                false,
                GpuIrOptimizationValidationRuleStatus.WARN,
                "message",
                Map.of()
        ));
        assertThrows(IllegalArgumentException.class, () -> GpuIrOptimizationValidationRuleResult
                .passed("rule", "message")
                .artifactFields(""));
    }
}
