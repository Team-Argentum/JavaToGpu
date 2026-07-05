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
    void mergesDefaultValidationRuleAndAcceptanceFieldsIntoExistingMap() {
        GpuIrOptimizationValidationRuleArtifactReport report = new GpuIrOptimizationValidationRuleArtifactRunner()
                .run(validationReport("combinedKernel"));
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("existing", "preserved");

        GpuIrOptimizationValidationRuleArtifactFields.putFieldsWithAcceptance(fields, report);

        assertEquals("preserved", fields.get("existing"));
        assertEquals("combinedKernel", fields.get("validationRulesMethod"));
        assertEquals("pass", fields.get("validationRulesVerdict"));
        assertEquals("true", fields.get("validationRulesPassed"));
        assertEquals("combinedKernel", fields.get("validationRulesAcceptanceMethod"));
        assertEquals("pass", fields.get("validationRulesAcceptanceVerdict"));
        assertEquals("true", fields.get("validationRulesAcceptanceAccepted"));
        assertEquals("false", fields.get("validationRulesAcceptanceRejected"));
        assertEquals("accepted/pass", fields.get("validationRulesAcceptanceReason"));
        assertTrue(fields.get("validationRulesAcceptanceCiSummaryLine").contains("accepted=true"));
    }

    @Test
    void returnsImmutableCombinedFieldsWithCustomPrefixes() {
        GpuIrOptimizationValidationRuleArtifactReport report = new GpuIrOptimizationValidationRuleArtifactRunner()
                .run(validationReport("customCombinedKernel"));

        Map<String, String> fields = GpuIrOptimizationValidationRuleArtifactFields.fieldsWithAcceptance(
                "rules.",
                "rulesAcceptance.",
                report
        );

        assertEquals("customCombinedKernel", fields.get("rules.Method"));
        assertEquals("true", fields.get("rules.Passed"));
        assertEquals("customCombinedKernel", fields.get("rulesAcceptance.Method"));
        assertEquals("true", fields.get("rulesAcceptance.Accepted"));
        assertEquals("accepted/pass", fields.get("rulesAcceptance.Reason"));
        assertFalse(fields.containsKey("validationRulesPassed"));
        assertFalse(fields.containsKey("validationRulesAcceptanceAccepted"));
        assertThrows(UnsupportedOperationException.class, () -> fields.put("x", "y"));
    }

    @Test
    void returnsImmutableAcceptanceOnlyFields() {
        GpuIrOptimizationValidationRuleArtifactReport report = new GpuIrOptimizationValidationRuleArtifactRunner()
                .run(validationReport("acceptanceOnlyKernel"));

        Map<String, String> fields = GpuIrOptimizationValidationRuleArtifactFields.acceptanceFields(report);

        assertEquals("acceptanceOnlyKernel", fields.get("validationRulesAcceptanceMethod"));
        assertEquals("true", fields.get("validationRulesAcceptanceAccepted"));
        assertEquals("accepted/pass", fields.get("validationRulesAcceptanceReason"));
        assertFalse(fields.containsKey("validationRulesPassed"));
        assertThrows(UnsupportedOperationException.class, () -> fields.put("x", "y"));
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
        assertThrows(NullPointerException.class, () -> GpuIrOptimizationValidationRuleArtifactFields.putAcceptanceFields(null, report));
        assertThrows(NullPointerException.class, () -> GpuIrOptimizationValidationRuleArtifactFields.putAcceptanceFields(fields, null));
        assertThrows(IllegalArgumentException.class, () -> GpuIrOptimizationValidationRuleArtifactFields.putAcceptanceFields(fields, "", report));
        assertThrows(IllegalArgumentException.class, () -> GpuIrOptimizationValidationRuleArtifactFields.acceptanceFields(" ", report));
        assertThrows(NullPointerException.class, () -> GpuIrOptimizationValidationRuleArtifactFields.putFieldsWithAcceptance(null, report));
        assertThrows(NullPointerException.class, () -> GpuIrOptimizationValidationRuleArtifactFields.putFieldsWithAcceptance(fields, null));
        assertThrows(IllegalArgumentException.class, () -> GpuIrOptimizationValidationRuleArtifactFields.putFieldsWithAcceptance(fields, "", "acceptance.", report));
        assertThrows(IllegalArgumentException.class, () -> GpuIrOptimizationValidationRuleArtifactFields.putFieldsWithAcceptance(fields, "rules.", "", report));
        assertThrows(IllegalArgumentException.class, () -> GpuIrOptimizationValidationRuleArtifactFields.fieldsWithAcceptance("", "acceptance.", report));
        assertThrows(IllegalArgumentException.class, () -> GpuIrOptimizationValidationRuleArtifactFields.fieldsWithAcceptance("rules.", "", report));
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
