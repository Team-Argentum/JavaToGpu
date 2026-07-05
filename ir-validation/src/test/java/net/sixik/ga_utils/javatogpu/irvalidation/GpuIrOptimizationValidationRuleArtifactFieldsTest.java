package net.sixik.ga_utils.javatogpu.irvalidation;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static net.sixik.ga_utils.javatogpu.irvalidation.GpuIrOptimizationValidationRuleTestFixtures.validationReport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuIrOptimizationValidationRuleArtifactFieldsTest {
    @Test
    void mergesDefaultValidationRuleFieldsIntoExistingMap() {
        GpuIrOptimizationValidationRuleArtifactReport report = new GpuIrOptimizationValidationRuleArtifactRunner()
                .run(validationReport("cleanKernel"));
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("existing", "preserved");

        GpuIrOptimizationValidationRuleArtifactFields.putFields(fields, report);

        assertEquals("preserved", fields.get("existing"));
        assertEquals("cleanKernel", fields.get("validationRulesMethod"));
        assertEquals("true", fields.get("validationRulesPassed"));
        assertEquals("3", fields.get("validationRulesRules"));
        assertEquals("3", fields.get("validationRulesResults"));
        assertEquals("false", fields.get("validationRulesHasFailures"));
        assertEquals("false", fields.get("validationRulesHasWarnings"));
        assertEquals("true", fields.get("validationRulesRegistryPassed"));
        assertEquals("true", fields.get("validationRulesConsistency.Consistent"));
        assertEquals("0", fields.get("validationRulesConsistency.FailedChecks"));
        assertEquals("[]", fields.get("validationRulesConsistency.FailedCheckList"));
        assertEquals("safety.clean", fields.get("validationRulesRegistryResult.0.RuleId"));
        assertEquals(report.ciSummaryLine(), fields.get("validationRulesCiSummaryLine"));
    }

    @Test
    void returnsImmutableFieldsWithCustomPrefix() {
        GpuIrOptimizationValidationRuleArtifactReport report = new GpuIrOptimizationValidationRuleArtifactRunner()
                .run(validationReport("customKernel"));

        Map<String, String> fields = GpuIrOptimizationValidationRuleArtifactFields.fields("customRules.", report);

        assertEquals("customKernel", fields.get("customRules.Method"));
        assertEquals("true", fields.get("customRules.Passed"));
        assertEquals("3", fields.get("customRules.Rules"));
        assertEquals("true", fields.get("customRules.RegistryPassed"));
        assertEquals("true", fields.get("customRules.Consistency.Consistent"));
        assertEquals("0", fields.get("customRules.Consistency.FailedChecks"));
        assertEquals(report.ciSummaryLine(), fields.get("customRules.CiSummaryLine"));
        assertFalse(fields.containsKey("validationRulesPassed"));
        assertThrows(UnsupportedOperationException.class, () -> fields.put("x", "y"));
    }

    @Test
    void rejectsInvalidMergeInputs() {
        GpuIrOptimizationValidationRuleArtifactReport report = new GpuIrOptimizationValidationRuleArtifactRunner()
                .run(validationReport("cleanKernel"));
        Map<String, String> fields = new LinkedHashMap<>();

        assertThrows(NullPointerException.class, () -> GpuIrOptimizationValidationRuleArtifactFields.putFields(null, report));
        assertThrows(NullPointerException.class, () -> GpuIrOptimizationValidationRuleArtifactFields.putFields(fields, null));
        assertThrows(IllegalArgumentException.class, () -> GpuIrOptimizationValidationRuleArtifactFields.putFields(fields, "", report));
        assertThrows(IllegalArgumentException.class, () -> GpuIrOptimizationValidationRuleArtifactFields.fields(" ", report));
    }

    @Test
    void defaultFieldsUseValidationRulesPrefix() {
        GpuIrOptimizationValidationRuleArtifactReport report = new GpuIrOptimizationValidationRuleArtifactRunner()
                .run(validationReport("defaultKernel"));

        Map<String, String> fields = GpuIrOptimizationValidationRuleArtifactFields.fields(report);

        assertTrue(fields.containsKey("validationRulesPassed"));
        assertTrue(fields.containsKey("validationRulesConsistency.Consistent"));
        assertEquals("defaultKernel", fields.get("validationRulesMethod"));
    }
}
