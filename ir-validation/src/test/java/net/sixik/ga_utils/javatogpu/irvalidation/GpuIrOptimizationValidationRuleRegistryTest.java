package net.sixik.ga_utils.javatogpu.irvalidation;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static net.sixik.ga_utils.javatogpu.irvalidation.GpuIrOptimizationValidationRuleTestFixtures.rule;
import static net.sixik.ga_utils.javatogpu.irvalidation.GpuIrOptimizationValidationRuleTestFixtures.validationReport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuIrOptimizationValidationRuleRegistryTest {
    @Test
    void evaluatesRulesInRegistrationOrderAndExportsStableFields() {
        GpuIrOptimizationValidationReport report = validationReport("kernel");
        GpuIrOptimizationValidationRuleRegistry registry = GpuIrOptimizationValidationRuleRegistry.of(List.of(
                rule("safety.clean", context -> GpuIrOptimizationValidationRuleResult.passed(
                        "safety.clean",
                        "method has no safety error",
                        Map.of("method", context.methodName())
                )),
                rule("optimizer.clean", context -> GpuIrOptimizationValidationRuleResult.failed(
                        "optimizer.clean",
                        "optimizer diagnostics are present",
                        Map.of("hasOptimizerDiagnostics", Boolean.toString(context.hasOptimizerDiagnostics()))
                ))
        ));

        List<GpuIrOptimizationValidationRuleResult> results = registry.evaluate(
                new GpuIrOptimizationValidationRuleContext(report)
        );
        Map<String, String> fields = registry.artifactFields("ruleRegistry", results);

        assertEquals(2, results.size());
        assertEquals("safety.clean", results.get(0).ruleId());
        assertEquals("optimizer.clean", results.get(1).ruleId());
        assertTrue(results.get(0).passed());
        assertFalse(results.get(1).passed());
        assertEquals("2", fields.get("ruleRegistryRules"));
        assertEquals("2", fields.get("ruleRegistryResults"));
        assertEquals("false", fields.get("ruleRegistryPassed"));
        assertEquals("[safety.clean=pass,optimizer.clean=fail]", fields.get("ruleRegistryRuleIndex"));
        assertEquals("[optimizer.clean=fail]", fields.get("ruleRegistryBlockingRuleIndex"));
        assertEquals("safety.clean", fields.get("ruleRegistryResult.0.RuleId"));
        assertEquals("true", fields.get("ruleRegistryResult.0.Passed"));
        assertEquals("pass", fields.get("ruleRegistryResult.0.Status"));
        assertEquals("false", fields.get("ruleRegistryResult.0.Blocking"));
        assertEquals("1", fields.get("ruleRegistryResult.0.MetadataCount"));
        assertEquals("true", fields.get("ruleRegistryResult.0.MetadataPresent"));
        assertEquals("kernel", fields.get("ruleRegistryResult.0.Metadata.method"));
        assertEquals("optimizer.clean", fields.get("ruleRegistryResult.1.RuleId"));
        assertEquals("false", fields.get("ruleRegistryResult.1.Passed"));
        assertEquals("fail", fields.get("ruleRegistryResult.1.Status"));
        assertEquals("true", fields.get("ruleRegistryResult.1.Blocking"));
        assertEquals("1", fields.get("ruleRegistryResult.1.MetadataCount"));
        assertEquals("true", fields.get("ruleRegistryResult.1.MetadataPresent"));
        assertEquals("false", fields.get("ruleRegistryResult.1.Metadata.hasOptimizerDiagnostics"));
        assertEquals("[safety.clean=pass,optimizer.clean=fail]", registry.ruleIndex(results));
        assertEquals("[optimizer.clean=fail]", registry.blockingRuleIndex(results));
    }

    @Test
    void rejectsDuplicateRuleIdsAndInvalidArtifactPrefix() {
        GpuIrOptimizationValidationRule duplicate = rule("duplicate.rule", context -> GpuIrOptimizationValidationRuleResult.passed(
                "duplicate.rule",
                "ok"
        ));

        assertThrows(IllegalArgumentException.class, () -> GpuIrOptimizationValidationRuleRegistry.of(List.of(
                duplicate,
                duplicate
        )));
        assertThrows(IllegalArgumentException.class, () -> GpuIrOptimizationValidationRuleRegistry.of(List.of(duplicate))
                .artifactFields("", List.of()));
    }

    @Test
    void rejectsInvalidRuleIds() {
        assertThrows(IllegalArgumentException.class, () -> GpuIrOptimizationValidationRuleRegistry.of(List.of(
                rule("", context -> GpuIrOptimizationValidationRuleResult.passed("", "bad"))
        )));
        assertThrows(IllegalArgumentException.class, () -> GpuIrOptimizationValidationRuleRegistry.of(List.of(
                rule(" ", context -> GpuIrOptimizationValidationRuleResult.passed(" ", "bad"))
        )));
        assertThrows(IllegalArgumentException.class, () -> GpuIrOptimizationValidationRuleRegistry.of(List.of(
                rule(null, context -> GpuIrOptimizationValidationRuleResult.passed("null.id", "bad"))
        )));
    }

    @Test
    void rejectsMismatchedArtifactResults() {
        GpuIrOptimizationValidationRuleRegistry registry = GpuIrOptimizationValidationRuleRegistry.of(List.of(
                rule("expected.result", ignored -> GpuIrOptimizationValidationRuleResult.passed(
                        "expected.result",
                        "ok"
                ))
        ));

        assertThrows(NullPointerException.class, () -> registry.artifactFields("ruleRegistry", null));
        assertThrows(IllegalArgumentException.class, () -> registry.artifactFields("ruleRegistry", java.util.Arrays.asList(
                GpuIrOptimizationValidationRuleResult.passed("expected.result", "ok"),
                null
        )));
        assertThrows(IllegalArgumentException.class, () -> registry.artifactFields("ruleRegistry", List.of()));
        assertThrows(IllegalArgumentException.class, () -> registry.artifactFields("ruleRegistry", List.of(
                GpuIrOptimizationValidationRuleResult.passed("different.result", "wrong result id")
        )));
        assertThrows(IllegalArgumentException.class, () -> registry.ruleIndex(List.of(
                GpuIrOptimizationValidationRuleResult.passed("different.result", "wrong result id")
        )));
        assertThrows(IllegalArgumentException.class, () -> registry.warningRuleIndex(List.of(
                GpuIrOptimizationValidationRuleResult.warned("different.result", "wrong result id")
        )));
        assertThrows(IllegalArgumentException.class, () -> registry.blockingRuleIndex(List.of(
                GpuIrOptimizationValidationRuleResult.failed("different.result", "wrong result id")
        )));
    }

    @Test
    void rejectsInvalidRuleEvaluationResults() {
        GpuIrOptimizationValidationRuleContext context = new GpuIrOptimizationValidationRuleContext(validationReport("kernel"));

        GpuIrOptimizationValidationRuleRegistry nullResultRegistry = GpuIrOptimizationValidationRuleRegistry.of(List.of(
                rule("null.result", ignored -> null)
        ));
        assertThrows(NullPointerException.class, () -> nullResultRegistry.evaluate(context));

        GpuIrOptimizationValidationRuleRegistry mismatchedResultRegistry = GpuIrOptimizationValidationRuleRegistry.of(List.of(
                rule("expected.result", ignored -> GpuIrOptimizationValidationRuleResult.passed(
                        "different.result",
                        "wrong result id"
                ))
        ));
        assertThrows(IllegalStateException.class, () -> mismatchedResultRegistry.evaluate(context));
    }

    @Test
    void ruleResultMetadataIsDefensivelyCopied() {
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("before", "one");

        GpuIrOptimizationValidationRuleResult result = GpuIrOptimizationValidationRuleResult.passed(
                "metadata.copy",
                "ok",
                metadata
        );
        metadata.put("after", "two");

        assertEquals(Map.of("before", "one"), result.metadata());
        assertEquals(GpuIrOptimizationValidationRuleStatus.PASS, result.status());
        assertFalse(result.blocking());
        assertThrows(UnsupportedOperationException.class, () -> result.metadata().put("x", "y"));
        assertThrows(IllegalArgumentException.class, () -> GpuIrOptimizationValidationRuleResult.passed("", "bad"));
        assertThrows(IllegalArgumentException.class, () -> new GpuIrOptimizationValidationRuleResult(
                "bad.status",
                false,
                GpuIrOptimizationValidationRuleStatus.WARN,
                "bad",
                Map.of()
        ));
        Map<String, String> blankKeyMetadata = new LinkedHashMap<>();
        blankKeyMetadata.put("", "bad");
        assertThrows(IllegalArgumentException.class, () -> GpuIrOptimizationValidationRuleResult.passed(
                "metadata.blankKey",
                "bad",
                blankKeyMetadata
        ));
        Map<String, String> nullValueMetadata = new LinkedHashMap<>();
        nullValueMetadata.put("bad", null);
        assertThrows(NullPointerException.class, () -> GpuIrOptimizationValidationRuleResult.passed(
                "metadata.nullValue",
                "bad",
                nullValueMetadata
        ));
        assertThrows(IllegalArgumentException.class, () -> result.artifactFields(""));
    }

    @Test
    void warningRuleResultIsPassingButNonBlocking() {
        GpuIrOptimizationValidationRuleResult result = GpuIrOptimizationValidationRuleResult.warned(
                "optimizer.advisory",
                "optimizer advisory warning",
                Map.of("family", "optimizer")
        );
        Map<String, String> fields = result.artifactFields("rule.");

        assertTrue(result.passed());
        assertEquals(GpuIrOptimizationValidationRuleStatus.WARN, result.status());
        assertFalse(result.blocking());
        assertEquals("optimizer.advisory", fields.get("rule.RuleId"));
        assertEquals("true", fields.get("rule.Passed"));
        assertEquals("warn", fields.get("rule.Status"));
        assertEquals("false", fields.get("rule.Blocking"));
        assertEquals("1", fields.get("rule.MetadataCount"));
        assertEquals("true", fields.get("rule.MetadataPresent"));
        assertEquals("optimizer", fields.get("rule.Metadata.family"));
    }

}
