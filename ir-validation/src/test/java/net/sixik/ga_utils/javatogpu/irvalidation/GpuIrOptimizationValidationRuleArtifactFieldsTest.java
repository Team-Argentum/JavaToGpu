package net.sixik.ga_utils.javatogpu.irvalidation;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
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
    void combinedFieldsCanRejectInconsistentArtifactsWithExplicitConsistencyReport() {
        GpuIrOptimizationValidationRuleArtifactReport report = new GpuIrOptimizationValidationRuleArtifactRunner()
                .run(validationReport("inconsistentFieldsKernel"));
        GpuIrOptimizationValidationRuleArtifactConsistencyReport consistencyReport =
                new GpuIrOptimizationValidationRuleArtifactConsistencyReport(
                        "inconsistentFieldsKernel",
                        "inconsistent",
                        false,
                        14,
                        1,
                        List.of("summaryVerdict")
                );
        Map<String, String> fields = GpuIrOptimizationValidationRuleArtifactFields.fieldsWithAcceptance(
                report,
                consistencyReport
        );

        assertEquals("pass", fields.get("validationRulesVerdict"));
        assertEquals("true", fields.get("validationRulesPassed"));
        assertEquals("true", fields.get("validationRulesConsistency.Consistent"));
        assertEquals("false", fields.get("validationRulesAcceptanceAccepted"));
        assertEquals("true", fields.get("validationRulesAcceptanceRejected"));
        assertEquals("rejected/inconsistentArtifact", fields.get("validationRulesAcceptanceReason"));
        assertTrue(fields.get("validationRulesAcceptanceCiSummaryLine").contains("consistency=false"));
        assertTrue(fields.get("validationRulesAcceptanceCiSummaryLine").contains("firstConsistencyFailedCheck=summaryVerdict"));
    }

    @Test
    void defaultCombinedFieldsUseReportConsistencyAndAcceptCleanArtifacts() {
        GpuIrOptimizationValidationRuleArtifactReport report = new GpuIrOptimizationValidationRuleArtifactRunner()
                .run(validationReport("defaultConsistencyKernel"));

        Map<String, String> fields = GpuIrOptimizationValidationRuleArtifactFields.fieldsWithAcceptance(report);

        assertEquals("true", fields.get("validationRulesConsistency.Consistent"));
        assertEquals("0", fields.get("validationRulesConsistency.FailedChecks"));
        assertEquals("true", fields.get("validationRulesAcceptanceAccepted"));
        assertEquals("false", fields.get("validationRulesAcceptanceRejected"));
        assertEquals("accepted/pass", fields.get("validationRulesAcceptanceReason"));
        assertFalse(fields.get("validationRulesAcceptanceCiSummaryLine").contains("consistency=false"));
    }

    @Test
    void combinedFieldsCanAlsoExportOptimizerReadinessHandoffFields() {
        GpuIrOptimizationValidationRuleArtifactReport report = new GpuIrOptimizationValidationRuleArtifactRunner()
                .run(validationReport("handoffKernel"));
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("existing", "preserved");

        GpuIrOptimizationValidationRuleArtifactFields.putFieldsWithAcceptanceAndHandoff(fields, report);

        assertEquals("preserved", fields.get("existing"));
        assertEquals("handoffKernel", fields.get("validationRulesMethod"));
        assertEquals("true", fields.get("validationRulesAcceptanceAccepted"));
        assertEquals("handoffKernel", fields.get("optimizerReadinessHandoffMethod"));
        assertEquals("readyForOptimizerEnablementReview", fields.get("optimizerReadinessHandoffVerdict"));
        assertEquals("true", fields.get("optimizerReadinessHandoffReadyForOptimizerEnablement"));
        assertEquals("0", fields.get("optimizerReadinessHandoffBlockingReasonCount"));
    }

    @Test
    void combinedFieldsWithHandoffCanRejectExplicitConsistencyDrift() {
        GpuIrOptimizationValidationRuleArtifactReport report = new GpuIrOptimizationValidationRuleArtifactRunner()
                .run(validationReport("handoffDriftKernel"));
        GpuIrOptimizationValidationRuleArtifactConsistencyReport consistencyReport =
                new GpuIrOptimizationValidationRuleArtifactConsistencyReport(
                        "handoffDriftKernel",
                        "inconsistent",
                        false,
                        14,
                        1,
                        List.of("summaryVerdict")
                );

        Map<String, String> fields = GpuIrOptimizationValidationRuleArtifactFields.fieldsWithAcceptanceAndHandoff(
                report,
                consistencyReport
        );

        assertEquals("true", fields.get("validationRulesPassed"));
        assertEquals("false", fields.get("validationRulesAcceptanceAccepted"));
        assertEquals("rejected/inconsistentArtifact", fields.get("validationRulesAcceptanceReason"));
        assertEquals("notReady/ruleArtifactRejected", fields.get("optimizerReadinessHandoffVerdict"));
        assertEquals("false", fields.get("optimizerReadinessHandoffReadyForOptimizerEnablement"));
        assertEquals("[ruleArtifactRejected,ruleArtifactInconsistent]", fields.get("optimizerReadinessHandoffBlockingReasons"));
    }

    @Test
    void combinedFieldsCanAlsoExportOptimizerEnablementPolicyFields() {
        GpuIrOptimizationValidationRuleArtifactReport report = new GpuIrOptimizationValidationRuleArtifactRunner()
                .run(validationReport("policyKernel"));
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("existing", "preserved");

        GpuIrOptimizationValidationRuleArtifactFields.putFieldsWithAcceptanceHandoffAndPolicy(fields, report);

        assertEquals("preserved", fields.get("existing"));
        assertEquals("policyKernel", fields.get("validationRulesMethod"));
        assertEquals("true", fields.get("validationRulesAcceptanceAccepted"));
        assertEquals("true", fields.get("optimizerReadinessHandoffReadyForOptimizerEnablement"));
        assertEquals("reviewAllowed/productionMutationDisabled", fields.get("optimizerEnablementPolicyVerdict"));
        assertEquals("true", fields.get("optimizerEnablementPolicyAllowOptimizerEnablementReview"));
        assertEquals("false", fields.get("optimizerEnablementPolicyProductionMutationEnabled"));
        assertEquals("enableProductionMutationPolicy", fields.get("optimizerEnablementPolicyFirstRemainingWork"));
    }

    @Test
    void optimizerEnablementArtifactFieldsExposeSameDefaultCombinedSurface() {
        GpuIrOptimizationValidationRuleArtifactReport report = new GpuIrOptimizationValidationRuleArtifactRunner()
                .run(validationReport("policyArtifactAliasKernel"));

        Map<String, String> aliasFields = GpuIrOptimizationValidationRuleArtifactFields
                .optimizerEnablementArtifactFields(report);
        Map<String, String> combinedFields = GpuIrOptimizationValidationRuleArtifactFields
                .fieldsWithAcceptanceHandoffAndPolicy(report);

        assertEquals(combinedFields, aliasFields);
        assertEquals("policyArtifactAliasKernel", aliasFields.get("validationRulesMethod"));
        assertEquals("true", aliasFields.get("validationRulesAcceptanceAccepted"));
        assertEquals("true", aliasFields.get("optimizerReadinessHandoffReadyForOptimizerEnablement"));
        assertEquals("reviewAllowed/productionMutationDisabled", aliasFields.get("optimizerEnablementPolicyVerdict"));
    }

    @Test
    void combinedFieldsWithPolicyCanRejectExplicitConsistencyDrift() {
        GpuIrOptimizationValidationRuleArtifactReport report = new GpuIrOptimizationValidationRuleArtifactRunner()
                .run(validationReport("policyDriftKernel"));
        GpuIrOptimizationValidationRuleArtifactConsistencyReport consistencyReport =
                new GpuIrOptimizationValidationRuleArtifactConsistencyReport(
                        "policyDriftKernel",
                        "inconsistent",
                        false,
                        14,
                        1,
                        List.of("summaryVerdict")
                );

        Map<String, String> fields = GpuIrOptimizationValidationRuleArtifactFields.fieldsWithAcceptanceHandoffAndPolicy(
                report,
                consistencyReport
        );

        assertEquals("false", fields.get("validationRulesAcceptanceAccepted"));
        assertEquals("notReady/ruleArtifactRejected", fields.get("optimizerReadinessHandoffVerdict"));
        assertEquals("blocked/handoffNotReady", fields.get("optimizerEnablementPolicyVerdict"));
        assertEquals("false", fields.get("optimizerEnablementPolicyAllowOptimizerEnablementReview"));
        assertEquals("false", fields.get("optimizerEnablementPolicyProductionMutationEnabled"));
        assertEquals("ruleArtifactRejected", fields.get("optimizerEnablementPolicyFirstBlockingReason"));
        assertEquals("produceAcceptedRuleArtifact", fields.get("optimizerEnablementPolicyFirstRemainingWork"));
    }

    @Test
    void acceptanceOnlyFieldsCanRejectInconsistentArtifactsWithExplicitConsistencyReport() {
        GpuIrOptimizationValidationRuleArtifactReport report = new GpuIrOptimizationValidationRuleArtifactRunner()
                .run(validationReport("inconsistentAcceptanceOnlyKernel"));
        GpuIrOptimizationValidationRuleArtifactConsistencyReport consistencyReport =
                new GpuIrOptimizationValidationRuleArtifactConsistencyReport(
                        "inconsistentAcceptanceOnlyKernel",
                        "inconsistent",
                        false,
                        14,
                        1,
                        List.of("summaryPassed")
                );
        Map<String, String> fields = GpuIrOptimizationValidationRuleArtifactFields.acceptanceFields(
                report,
                consistencyReport
        );

        assertEquals("inconsistentAcceptanceOnlyKernel", fields.get("validationRulesAcceptanceMethod"));
        assertEquals("false", fields.get("validationRulesAcceptanceAccepted"));
        assertEquals("true", fields.get("validationRulesAcceptanceRejected"));
        assertEquals("rejected/inconsistentArtifact", fields.get("validationRulesAcceptanceReason"));
        assertFalse(fields.containsKey("validationRulesPassed"));
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
        assertThrows(NullPointerException.class, () -> GpuIrOptimizationValidationRuleArtifactFields.putFieldsWithAcceptance(fields, report, null));
        assertThrows(NullPointerException.class, () -> GpuIrOptimizationValidationRuleArtifactFields.putAcceptanceFields(fields, report, null));
        assertThrows(NullPointerException.class, () -> GpuIrOptimizationValidationRuleArtifactFields.putHandoffFields(fields, report, null));
        assertThrows(NullPointerException.class, () -> GpuIrOptimizationValidationRuleArtifactFields.putFieldsWithAcceptanceAndHandoff(fields, report, null));
        assertThrows(NullPointerException.class, () -> GpuIrOptimizationValidationRuleArtifactFields.putEnablementPolicyFields(fields, report, null));
        assertThrows(NullPointerException.class, () -> GpuIrOptimizationValidationRuleArtifactFields.putFieldsWithAcceptanceHandoffAndPolicy(fields, report, null));
        assertThrows(IllegalArgumentException.class, () -> GpuIrOptimizationValidationRuleArtifactFields.putFieldsWithAcceptance(fields, "", "acceptance.", report));
        assertThrows(IllegalArgumentException.class, () -> GpuIrOptimizationValidationRuleArtifactFields.putFieldsWithAcceptance(fields, "rules.", "", report));
        assertThrows(IllegalArgumentException.class, () -> GpuIrOptimizationValidationRuleArtifactFields.fieldsWithAcceptance("", "acceptance.", report));
        assertThrows(IllegalArgumentException.class, () -> GpuIrOptimizationValidationRuleArtifactFields.fieldsWithAcceptance("rules.", "", report));
        assertThrows(IllegalArgumentException.class, () -> GpuIrOptimizationValidationRuleArtifactFields.fieldsWithAcceptanceAndHandoff("", "acceptance.", "handoff.", report));
        assertThrows(IllegalArgumentException.class, () -> GpuIrOptimizationValidationRuleArtifactFields.fieldsWithAcceptanceAndHandoff("rules.", "", "handoff.", report));
        assertThrows(IllegalArgumentException.class, () -> GpuIrOptimizationValidationRuleArtifactFields.fieldsWithAcceptanceAndHandoff("rules.", "acceptance.", "", report));
        assertThrows(IllegalArgumentException.class, () -> GpuIrOptimizationValidationRuleArtifactFields.fieldsWithAcceptanceHandoffAndPolicy("", "acceptance.", "handoff.", "policy.", report));
        assertThrows(IllegalArgumentException.class, () -> GpuIrOptimizationValidationRuleArtifactFields.fieldsWithAcceptanceHandoffAndPolicy("rules.", "", "handoff.", "policy.", report));
        assertThrows(IllegalArgumentException.class, () -> GpuIrOptimizationValidationRuleArtifactFields.fieldsWithAcceptanceHandoffAndPolicy("rules.", "acceptance.", "", "policy.", report));
        assertThrows(IllegalArgumentException.class, () -> GpuIrOptimizationValidationRuleArtifactFields.fieldsWithAcceptanceHandoffAndPolicy("rules.", "acceptance.", "handoff.", "", report));
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
