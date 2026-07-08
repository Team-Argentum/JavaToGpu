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

class GpuIrOptimizationValidationOptimizerEnablementArtifactTest {
    @Test
    void bundlesCleanRuleArtifactThroughReadOnlyPolicyChain() {
        GpuIrOptimizationValidationRuleArtifactReport report = new GpuIrOptimizationValidationRuleArtifactRunner()
                .run(validationReport("enablementArtifactKernel"));

        GpuIrOptimizationValidationOptimizerEnablementArtifact artifact =
                GpuIrOptimizationValidationOptimizerEnablementArtifact.from(report);
        Map<String, String> fields = artifact.artifactFields();

        assertEquals("enablementArtifactKernel", artifact.methodName());
        assertTrue(artifact.accepted());
        assertTrue(artifact.readyForOptimizerEnablementReview());
        assertTrue(artifact.allowOptimizerEnablementReview());
        assertFalse(artifact.productionMutationEnabled());
        assertEquals("enablementArtifactKernel", fields.get("validationRulesMethod"));
        assertEquals("true", fields.get("validationRulesAcceptanceAccepted"));
        assertEquals("readyForOptimizerEnablementReview", fields.get("optimizerReadinessHandoffVerdict"));
        assertEquals("reviewAllowed/productionMutationDisabled", fields.get("optimizerEnablementPolicyVerdict"));
        assertEquals("false", fields.get("optimizerEnablementPolicyProductionMutationEnabled"));
        assertThrows(UnsupportedOperationException.class, () -> fields.put("x", "y"));
    }

    @Test
    void failClosesPolicyWhenExplicitConsistencyDriftIsProvided() {
        GpuIrOptimizationValidationRuleArtifactReport report = new GpuIrOptimizationValidationRuleArtifactRunner()
                .run(validationReport("enablementArtifactDriftKernel"));
        GpuIrOptimizationValidationRuleArtifactConsistencyReport consistencyReport =
                new GpuIrOptimizationValidationRuleArtifactConsistencyReport(
                        "enablementArtifactDriftKernel",
                        "inconsistent",
                        false,
                        14,
                        1,
                        List.of("summaryVerdict")
                );

        GpuIrOptimizationValidationOptimizerEnablementArtifact artifact =
                GpuIrOptimizationValidationOptimizerEnablementArtifact.from(report, consistencyReport);
        Map<String, String> fields = artifact.artifactFields();

        assertFalse(artifact.accepted());
        assertFalse(artifact.readyForOptimizerEnablementReview());
        assertFalse(artifact.allowOptimizerEnablementReview());
        assertFalse(artifact.productionMutationEnabled());
        assertEquals("rejected/inconsistentArtifact", fields.get("validationRulesAcceptanceReason"));
        assertEquals("notReady/ruleArtifactRejected", fields.get("optimizerReadinessHandoffVerdict"));
        assertEquals("blocked/handoffNotReady", fields.get("optimizerEnablementPolicyVerdict"));
        assertEquals("ruleArtifactRejected", fields.get("optimizerEnablementPolicyFirstBlockingReason"));
        assertEquals("produceAcceptedRuleArtifact", fields.get("optimizerEnablementPolicyFirstRemainingWork"));
    }

    @Test
    void canAppendCustomPrefixedFieldsIntoExistingMap() {
        GpuIrOptimizationValidationRuleArtifactReport report = new GpuIrOptimizationValidationRuleArtifactRunner()
                .run(validationReport("customEnablementArtifactKernel"));
        GpuIrOptimizationValidationOptimizerEnablementArtifact artifact =
                GpuIrOptimizationValidationOptimizerEnablementArtifact.from(report);
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("existing", "preserved");

        artifact.putArtifactFields(fields, "rules.", "acceptance.", "handoff.", "policy.");

        assertEquals("preserved", fields.get("existing"));
        assertEquals("customEnablementArtifactKernel", fields.get("rules.Method"));
        assertEquals("true", fields.get("acceptance.Accepted"));
        assertEquals("true", fields.get("handoff.ReadyForOptimizerEnablement"));
        assertEquals("true", fields.get("policy.AllowOptimizerEnablementReview"));
        assertEquals("false", fields.get("policy.ProductionMutationEnabled"));
    }

    @Test
    void rejectsInvalidInputsAndMismatchedContracts() {
        GpuIrOptimizationValidationRuleArtifactReport report = new GpuIrOptimizationValidationRuleArtifactRunner()
                .run(validationReport("invalidEnablementArtifactKernel"));
        GpuIrOptimizationValidationOptimizerEnablementArtifact artifact =
                GpuIrOptimizationValidationOptimizerEnablementArtifact.from(report);
        GpuIrOptimizationValidationRuleArtifactAcceptance mismatchedAcceptance =
                new GpuIrOptimizationValidationRuleArtifactAcceptance(
                        "otherKernel",
                        "pass",
                        true,
                        "accepted/pass",
                        "",
                        "",
                        "",
                        "validation rule artifact acceptance method=otherKernel verdict=pass accepted=true reason=accepted/pass warnings=0 blocking=0 failed=0"
                );

        assertThrows(NullPointerException.class, () -> GpuIrOptimizationValidationOptimizerEnablementArtifact.from(null));
        assertThrows(NullPointerException.class, () -> GpuIrOptimizationValidationOptimizerEnablementArtifact.from(report, null));
        assertThrows(NullPointerException.class, () -> artifact.putArtifactFields(null));
        assertThrows(IllegalArgumentException.class, () -> artifact.artifactFields("", "acceptance.", "handoff.", "policy."));
        assertThrows(IllegalArgumentException.class, () -> new GpuIrOptimizationValidationOptimizerEnablementArtifact(
                report,
                mismatchedAcceptance,
                GpuIrOptimizationValidationOptimizerReadinessHandoffReport.from(report),
                GpuIrOptimizationValidationOptimizerEnablementPolicyDecision.from(
                        GpuIrOptimizationValidationOptimizerReadinessHandoffReport.from(report)
                )
        ));
    }
}
