package net.sixik.ga_utils.javatogpu.irvalidation;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static net.sixik.ga_utils.javatogpu.irvalidation.GpuIrOptimizationValidationRuleTestFixtures.rule;
import static net.sixik.ga_utils.javatogpu.irvalidation.GpuIrOptimizationValidationRuleTestFixtures.validationReport;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuIrOptimizationValidationRuleArtifactContractTest {
    @Test
    void defaultArtifactKeepsStableTopLevelExportKeys() {
        GpuIrOptimizationValidationRuleArtifactReport report = new GpuIrOptimizationValidationRuleArtifactRunner()
                .run(validationReport("contractKernel"));

        Map<String, String> fields = report.artifactFields();

        // These keys are the compact contract that external CI/report tooling can rely on first.
        assertContainsKeys(fields, Set.of(
                "validationRulesMethod",
                "validationRulesVerdict",
                "validationRulesPassed",
                "validationRulesRules",
                "validationRulesResults",
                "validationRulesFailed",
                "validationRulesHasFailures",
                "validationRulesWarnings",
                "validationRulesHasWarnings",
                "validationRulesBlocking",
                "validationRulesHasBlockingResults",
                "validationRulesFailedRuleIds",
                "validationRulesWarningRuleIds",
                "validationRulesBlockingRuleIds",
                "validationRulesRuleIndex",
                "validationRulesWarningRuleIndex",
                "validationRulesBlockingRuleIndex",
                "validationRulesStatusCounts",
                "validationRulesRuleFamilyCounts",
                "validationRulesWarningRuleFamilyCounts",
                "validationRulesBlockingRuleFamilyCounts",
                "validationRulesFailedRuleFamilyCounts",
                "validationRulesSummary",
                "validationRulesCiSummaryLine"
        ));
        assertContainsKeys(fields, Set.of(
                "validationRulesRegistryPassed",
                "validationRulesRegistryRuleIndex",
                "validationRulesRegistryWarningRuleIndex",
                "validationRulesRegistryBlockingRuleIndex",
                "validationRulesRegistryResult.0.RuleId",
                "validationRulesRegistryResult.0.Passed",
                "validationRulesRegistryResult.0.Status",
                "validationRulesRegistryResult.0.Blocking",
                "validationRulesRegistryResult.0.MetadataCount",
                "validationRulesRegistryResult.0.MetadataPresent",
                "validationRulesConsistency.Consistent",
                "validationRulesConsistency.Checks",
                "validationRulesConsistency.FailedChecks",
                "validationRulesConsistency.FailedCheckList",
                "validationRulesConsistency.CiSummaryLine"
        ));

        assertEquals("contractKernel", fields.get("validationRulesMethod"));
        assertEquals("pass", fields.get("validationRulesVerdict"));
        assertEquals("true", fields.get("validationRulesPassed"));
        assertEquals(
                "[safety.clean=pass,optimizer.noBlockingDiagnostics=pass,optimizer.advisoryDiagnostics=pass]",
                fields.get("validationRulesRuleIndex")
        );
        assertEquals("true", fields.get("validationRulesConsistency.Consistent"));
        assertEquals("2", fields.get("validationRulesRegistryResult.0.MetadataCount"));
        assertEquals("true", fields.get("validationRulesRegistryResult.0.MetadataPresent"));
        assertFalse(fields.containsKey("validationRulesFirstFailedRuleId"));
        assertFalse(fields.containsKey("validationRulesFirstWarningRuleId"));
        assertFalse(fields.containsKey("validationRulesFirstBlockingRuleId"));
    }

    @Test
    void warningAndBlockingArtifactsKeepStableFirstResultExportKeys() {
        GpuIrOptimizationValidationRuleRegistry registry = GpuIrOptimizationValidationRuleRegistry.of(List.of(
                rule("optimizer.warning", context -> GpuIrOptimizationValidationRuleResult.warned(
                        "optimizer.warning",
                        "non-blocking optimizer signal"
                )),
                rule("safety.failure", context -> GpuIrOptimizationValidationRuleResult.failed(
                        "safety.failure",
                        "blocking safety issue",
                        Map.of("family", "safety")
                ))
        ));

        GpuIrOptimizationValidationRuleArtifactReport report = GpuIrOptimizationValidationRuleArtifactReport.evaluate(
                validationReport("mixedContractKernel"),
                registry
        );
        Map<String, String> fields = report.artifactFields();

        assertEquals("fail", fields.get("validationRulesVerdict"));
        assertEquals("false", fields.get("validationRulesPassed"));
        assertEquals("optimizer.warning", fields.get("validationRulesWarningRuleIds"));
        assertEquals("safety.failure", fields.get("validationRulesFailedRuleIds"));
        assertEquals("safety.failure", fields.get("validationRulesBlockingRuleIds"));
        assertEquals("optimizer.warning", fields.get("validationRulesFirstWarningRuleId"));
        assertEquals("warn", fields.get("validationRulesFirstWarningStatus"));
        assertEquals("false", fields.get("validationRulesFirstWarningBlocking"));
        assertEquals("non-blocking optimizer signal", fields.get("validationRulesFirstWarningMessage"));
        assertEquals("safety.failure", fields.get("validationRulesFirstFailedRuleId"));
        assertEquals("fail", fields.get("validationRulesFirstFailedStatus"));
        assertEquals("true", fields.get("validationRulesFirstFailedBlocking"));
        assertEquals("blocking safety issue", fields.get("validationRulesFirstFailedMessage"));
        assertEquals("safety.failure", fields.get("validationRulesFirstBlockingRuleId"));
        assertEquals("fail", fields.get("validationRulesFirstBlockingStatus"));
        assertEquals("true", fields.get("validationRulesFirstBlockingBlocking"));
        assertEquals("blocking safety issue", fields.get("validationRulesFirstBlockingMessage"));
        assertEquals("[optimizer.warning=warn]", fields.get("validationRulesWarningRuleIndex"));
        assertEquals("[safety.failure=fail]", fields.get("validationRulesBlockingRuleIndex"));
        assertEquals("{warn=1,fail=1}", fields.get("validationRulesStatusCounts"));
        assertEquals("{optimizer=1}", fields.get("validationRulesWarningRuleFamilyCounts"));
        assertEquals("{safety=1}", fields.get("validationRulesBlockingRuleFamilyCounts"));
        assertEquals("true", fields.get("validationRulesConsistency.Consistent"));
    }

    @Test
    void combinedFieldsWithAcceptanceKeepStableCiConsumptionKeys() {
        GpuIrOptimizationValidationRuleArtifactReport report = new GpuIrOptimizationValidationRuleArtifactRunner()
                .run(validationReport("combinedContractKernel"));

        Map<String, String> fields = GpuIrOptimizationValidationRuleArtifactFields.fieldsWithAcceptance(report);

        // External CI consumers should be able to read one merged map without parsing summaries.
        assertContainsKeys(fields, Set.of(
                "validationRulesMethod",
                "validationRulesVerdict",
                "validationRulesPassed",
                "validationRulesCiSummaryLine",
                "validationRulesConsistency.Consistent",
                "validationRulesConsistency.FailedChecks",
                "validationRulesRegistryPassed",
                "validationRulesRegistryResult.0.RuleId",
                "validationRulesRegistryResult.0.MetadataCount",
                "validationRulesRegistryResult.0.MetadataPresent",
                "validationRulesAcceptanceMethod",
                "validationRulesAcceptanceVerdict",
                "validationRulesAcceptanceAccepted",
                "validationRulesAcceptanceRejected",
                "validationRulesAcceptanceAcceptedWithWarnings",
                "validationRulesAcceptanceReason",
                "validationRulesAcceptanceCiSummaryLine"
        ));
        assertEquals("combinedContractKernel", fields.get("validationRulesMethod"));
        assertEquals("combinedContractKernel", fields.get("validationRulesAcceptanceMethod"));
        assertEquals("pass", fields.get("validationRulesVerdict"));
        assertEquals("pass", fields.get("validationRulesAcceptanceVerdict"));
        assertEquals("true", fields.get("validationRulesPassed"));
        assertEquals("true", fields.get("validationRulesAcceptanceAccepted"));
        assertEquals("false", fields.get("validationRulesAcceptanceRejected"));
        assertEquals("accepted/pass", fields.get("validationRulesAcceptanceReason"));
        assertEquals("true", fields.get("validationRulesConsistency.Consistent"));
        assertEquals("0", fields.get("validationRulesConsistency.FailedChecks"));
    }

    @Test
    void combinedFieldsWithExplicitConsistencyReportRejectDriftedArtifactsFailClosed() {
        GpuIrOptimizationValidationRuleArtifactReport report = new GpuIrOptimizationValidationRuleArtifactRunner()
                .run(validationReport("driftedCombinedContractKernel"));
        GpuIrOptimizationValidationRuleArtifactConsistencyReport driftedConsistencyReport =
                new GpuIrOptimizationValidationRuleArtifactConsistencyReport(
                        "driftedCombinedContractKernel",
                        "inconsistent",
                        false,
                        14,
                        1,
                        List.of("summaryVerdict")
                );

        Map<String, String> fields = GpuIrOptimizationValidationRuleArtifactFields.fieldsWithAcceptance(
                report,
                driftedConsistencyReport
        );

        // The rule artifact remains exported, but acceptance fails closed on the explicit drift report.
        assertContainsKeys(fields, Set.of(
                "validationRulesMethod",
                "validationRulesVerdict",
                "validationRulesPassed",
                "validationRulesConsistency.Consistent",
                "validationRulesAcceptanceAccepted",
                "validationRulesAcceptanceRejected",
                "validationRulesAcceptanceReason",
                "validationRulesAcceptanceCiSummaryLine"
        ));
        assertEquals("driftedCombinedContractKernel", fields.get("validationRulesMethod"));
        assertEquals("pass", fields.get("validationRulesVerdict"));
        assertEquals("true", fields.get("validationRulesPassed"));
        assertEquals("true", fields.get("validationRulesConsistency.Consistent"));
        assertEquals("false", fields.get("validationRulesAcceptanceAccepted"));
        assertEquals("true", fields.get("validationRulesAcceptanceRejected"));
        assertEquals("rejected/inconsistentArtifact", fields.get("validationRulesAcceptanceReason"));
        assertTrue(fields.get("validationRulesAcceptanceCiSummaryLine").contains("consistency=false"));
        assertTrue(fields.get("validationRulesAcceptanceCiSummaryLine").contains("consistencyFailedChecks=1"));
        assertTrue(fields.get("validationRulesAcceptanceCiSummaryLine").contains("firstConsistencyFailedCheck=summaryVerdict"));
    }

    @Test
    void combinedFieldsWithAcceptanceAndHandoffKeepStableCiConsumptionKeys() {
        GpuIrOptimizationValidationRuleArtifactReport report = new GpuIrOptimizationValidationRuleArtifactRunner()
                .run(validationReport("handoffContractKernel"));

        Map<String, String> fields = GpuIrOptimizationValidationRuleArtifactFields.fieldsWithAcceptanceAndHandoff(report);

        assertContainsKeys(fields, Set.of(
                "validationRulesMethod",
                "validationRulesAcceptanceAccepted",
                "validationRulesAcceptanceReason",
                "optimizerReadinessHandoffMethod",
                "optimizerReadinessHandoffVerdict",
                "optimizerReadinessHandoffReadyForOptimizerEnablement",
                "optimizerReadinessHandoffRuleArtifactAccepted",
                "optimizerReadinessHandoffAcceptanceReason",
                "optimizerReadinessHandoffBlockingReasonCount",
                "optimizerReadinessHandoffRemainingWorkCount",
                "optimizerReadinessHandoffCiSummaryLine"
        ));
        assertEquals("handoffContractKernel", fields.get("optimizerReadinessHandoffMethod"));
        assertEquals("readyForOptimizerEnablementReview", fields.get("optimizerReadinessHandoffVerdict"));
        assertEquals("true", fields.get("optimizerReadinessHandoffReadyForOptimizerEnablement"));
        assertEquals("true", fields.get("optimizerReadinessHandoffRuleArtifactAccepted"));
        assertEquals("accepted/pass", fields.get("optimizerReadinessHandoffAcceptanceReason"));
    }

    @Test
    void combinedFieldsWithAcceptanceHandoffAndPolicyKeepStableCiConsumptionKeys() {
        GpuIrOptimizationValidationRuleArtifactReport report = new GpuIrOptimizationValidationRuleArtifactRunner()
                .run(validationReport("policyContractKernel"));

        Map<String, String> fields = GpuIrOptimizationValidationRuleArtifactFields.fieldsWithAcceptanceHandoffAndPolicy(report);

        assertContainsKeys(fields, Set.of(
                "validationRulesMethod",
                "validationRulesAcceptanceAccepted",
                "optimizerReadinessHandoffReadyForOptimizerEnablement",
                "optimizerEnablementPolicyMethod",
                "optimizerEnablementPolicyVerdict",
                "optimizerEnablementPolicyAllowOptimizerEnablementReview",
                "optimizerEnablementPolicyProductionMutationEnabled",
                "optimizerEnablementPolicyHandoffVerdict",
                "optimizerEnablementPolicyFirstRemainingWork",
                "optimizerEnablementPolicyCiSummaryLine"
        ));
        assertEquals("policyContractKernel", fields.get("optimizerEnablementPolicyMethod"));
        assertEquals("reviewAllowed/productionMutationDisabled", fields.get("optimizerEnablementPolicyVerdict"));
        assertEquals("true", fields.get("optimizerEnablementPolicyAllowOptimizerEnablementReview"));
        assertEquals("false", fields.get("optimizerEnablementPolicyProductionMutationEnabled"));
        assertEquals("readyForOptimizerEnablementReview", fields.get("optimizerEnablementPolicyHandoffVerdict"));
        assertEquals("enableProductionMutationPolicy", fields.get("optimizerEnablementPolicyFirstRemainingWork"));
    }

    @Test
    void optimizerEnablementGateKeepsStableCiConsumptionKeys() {
        GpuIrOptimizationValidationReport report = validationReport("gateContractKernel");

        Map<String, String> fields = new GpuIrOptimizationValidationOptimizerEnablementArtifactRunner()
                .runGateFields(report);

        assertContainsKeys(fields, Set.of(
                "optimizerEnablementGateMethod",
                "optimizerEnablementGateVerdict",
                "optimizerEnablementGateReadyForProductionMutation",
                "optimizerEnablementGateCseReadyForProductionMutation",
                "optimizerEnablementGateCseReadyForEnablementReview",
                "optimizerEnablementGateAutoVectorizationReadyForPrototypeRewrite",
                "optimizerEnablementGateOptimizerEnablementReviewAllowed",
                "optimizerEnablementGateProductionMutationEnabled",
                "optimizerEnablementGateCseVerdict",
                "optimizerEnablementGateAutoVectorizationVerdict",
                "optimizerEnablementGateOptimizerEnablementPolicyVerdict",
                "optimizerEnablementGateBlockingReasons",
                "optimizerEnablementGateBlockingReasonCount",
                "optimizerEnablementGateRemainingWork",
                "optimizerEnablementGateRemainingWorkCount",
                "optimizerEnablementGateFirstBlockingReason",
                "optimizerEnablementGateFirstRemainingWork",
                "optimizerEnablementGateCiSummaryLine"
        ));
        assertEquals("gateContractKernel", fields.get("optimizerEnablementGateMethod"));
        assertEquals("notReady/cseBlocked", fields.get("optimizerEnablementGateVerdict"));
        assertEquals("false", fields.get("optimizerEnablementGateReadyForProductionMutation"));
        assertEquals("false", fields.get("optimizerEnablementGateCseReadyForProductionMutation"));
        assertEquals("false", fields.get("optimizerEnablementGateCseReadyForEnablementReview"));
        assertEquals("false", fields.get("optimizerEnablementGateAutoVectorizationReadyForPrototypeRewrite"));
        assertEquals("true", fields.get("optimizerEnablementGateOptimizerEnablementReviewAllowed"));
        assertEquals("false", fields.get("optimizerEnablementGateProductionMutationEnabled"));
        assertEquals("cseLiteralPromotionNotReady", fields.get("optimizerEnablementGateFirstBlockingReason"));
        assertEquals("collectPreviewCandidates", fields.get("optimizerEnablementGateFirstRemainingWork"));
    }

    @Test
    void optimizerValidationBundleKeepsStableCiConsumptionKeys() {
        GpuIrOptimizationValidationReport report = validationReport("bundleContractKernel");

        Map<String, String> fields = new GpuIrOptimizationValidationOptimizerEnablementArtifactRunner()
                .runBundleFields(report);

        assertContainsKeys(fields, Set.of(
                "optimizerValidationBundleMethod",
                "optimizerValidationBundleVerdict",
                "optimizerValidationBundleReadyForProductionMutation",
                "optimizerValidationBundleProductionMutationEnabled",
                "optimizerValidationBundleValidationHasSafetyError",
                "optimizerValidationBundleValidationHasOptimizerDiagnostics",
                "optimizerValidationBundleRuleArtifactVerdict",
                "optimizerValidationBundleRuleArtifactAccepted",
                "optimizerValidationBundleEnablementPolicyVerdict",
                "optimizerValidationBundleGateFirstBlockingReason",
                "optimizerValidationBundleGateFirstRemainingWork",
                "optimizerValidationBundleCiSummaryLine",
                "validationRulesMethod",
                "validationRulesAcceptanceAccepted",
                "optimizerEnablementPolicyProductionMutationEnabled",
                "optimizerEnablementGateVerdict",
                "optimizerEnablementGateReadyForProductionMutation",
                "optimizerProductionPreflightVerdict",
                "optimizerProductionPreflightBlocked",
                "optimizerProductionPreflightFirstBlockingReason",
                "optimizerProductionSwitchVerdict",
                "optimizerProductionSwitchEligibleForSwitchReview",
                "optimizerProductionSwitchRequiredEvidenceCount",
                "optimizerProductionSwitchFirstBlockingReason",
                "optimizerPromotionConfidenceVerdict",
                "optimizerPromotionConfidencePromotionAllowed",
                "optimizerPromotionConfidenceRuntimeConfidenceStable",
                "optimizerPromotionConfidenceFirstBlockingReason"
        ));
        assertEquals("bundleContractKernel", fields.get("optimizerValidationBundleMethod"));
        assertEquals("notReady/cseBlocked", fields.get("optimizerValidationBundleVerdict"));
        assertEquals("false", fields.get("optimizerValidationBundleReadyForProductionMutation"));
        assertEquals("false", fields.get("optimizerValidationBundleProductionMutationEnabled"));
        assertEquals("pass", fields.get("optimizerValidationBundleRuleArtifactVerdict"));
        assertEquals("true", fields.get("optimizerValidationBundleRuleArtifactAccepted"));
        assertEquals("reviewAllowed/productionMutationDisabled", fields.get("optimizerValidationBundleEnablementPolicyVerdict"));
        assertEquals("cseLiteralPromotionNotReady", fields.get("optimizerValidationBundleGateFirstBlockingReason"));
        assertEquals("collectPreviewCandidates", fields.get("optimizerValidationBundleGateFirstRemainingWork"));
        assertEquals("blocked/optimizerValidationBundleNotReady", fields.get("optimizerProductionPreflightVerdict"));
        assertEquals("true", fields.get("optimizerProductionPreflightBlocked"));
        assertEquals("cseLiteralPromotionNotReady", fields.get("optimizerProductionPreflightFirstBlockingReason"));
        assertEquals("blocked/preflightNotReady", fields.get("optimizerProductionSwitchVerdict"));
        assertEquals("false", fields.get("optimizerProductionSwitchEligibleForSwitchReview"));
        assertEquals("4", fields.get("optimizerProductionSwitchRequiredEvidenceCount"));
        assertEquals("productionPreflightNotReviewReady", fields.get("optimizerProductionSwitchFirstBlockingReason"));
        assertEquals("blocked/productionSwitchNotReady", fields.get("optimizerPromotionConfidenceVerdict"));
        assertEquals("false", fields.get("optimizerPromotionConfidencePromotionAllowed"));
        assertEquals("false", fields.get("optimizerPromotionConfidenceRuntimeConfidenceStable"));
        assertEquals("productionMutationSwitchNotReady", fields.get("optimizerPromotionConfidenceFirstBlockingReason"));
    }

    @Test
    void optimizerPromotionConfidenceKeepsStableCiConsumptionKeys() {
        GpuIrOptimizationValidationReport report = validationReport("promotionContractKernel");

        Map<String, String> fields = new GpuIrOptimizationValidationOptimizerEnablementArtifactRunner()
                .runOptimizerPromotionConfidenceContractFields(report);

        assertContainsKeys(fields, Set.of(
                "optimizerPromotionConfidenceMethod",
                "optimizerPromotionConfidenceVerdict",
                "optimizerPromotionConfidencePromotionAllowed",
                "optimizerPromotionConfidenceProductionMutationEnabled",
                "optimizerPromotionConfidenceSwitchVerdict",
                "optimizerPromotionConfidenceSwitchReviewEligible",
                "optimizerPromotionConfidenceRuntimeConfidenceStable",
                "optimizerPromotionConfidenceRequiredConfidenceEvidence",
                "optimizerPromotionConfidenceRequiredConfidenceEvidenceCount",
                "optimizerPromotionConfidenceBlockingReasons",
                "optimizerPromotionConfidenceBlockingReasonCount",
                "optimizerPromotionConfidenceRemainingWork",
                "optimizerPromotionConfidenceRemainingWorkCount",
                "optimizerPromotionConfidenceFirstBlockingReason",
                "optimizerPromotionConfidenceFirstRemainingWork",
                "optimizerPromotionConfidenceCiSummaryLine"
        ));
        assertEquals("promotionContractKernel", fields.get("optimizerPromotionConfidenceMethod"));
        assertEquals("blocked/productionSwitchNotReady", fields.get("optimizerPromotionConfidenceVerdict"));
        assertEquals("false", fields.get("optimizerPromotionConfidencePromotionAllowed"));
        assertEquals("false", fields.get("optimizerPromotionConfidenceProductionMutationEnabled"));
        assertEquals("blocked/preflightNotReady", fields.get("optimizerPromotionConfidenceSwitchVerdict"));
        assertEquals("false", fields.get("optimizerPromotionConfidenceSwitchReviewEligible"));
        assertEquals("false", fields.get("optimizerPromotionConfidenceRuntimeConfidenceStable"));
        assertEquals("5", fields.get("optimizerPromotionConfidenceRequiredConfidenceEvidenceCount"));
        assertEquals("productionMutationSwitchNotReady", fields.get("optimizerPromotionConfidenceFirstBlockingReason"));
        assertEquals("collectPreviewCandidates", fields.get("optimizerPromotionConfidenceFirstRemainingWork"));
    }

    @Test
    void optimizerProductionMutationSwitchKeepsStableCiConsumptionKeys() {
        GpuIrOptimizationValidationReport report = validationReport("switchContractKernel");

        Map<String, String> fields = new GpuIrOptimizationValidationOptimizerEnablementArtifactRunner()
                .runProductionMutationSwitchContractFields(report);

        assertContainsKeys(fields, Set.of(
                "optimizerProductionSwitchMethod",
                "optimizerProductionSwitchVerdict",
                "optimizerProductionSwitchEligibleForSwitchReview",
                "optimizerProductionSwitchProductionMutationEnabled",
                "optimizerProductionSwitchPreflightVerdict",
                "optimizerProductionSwitchPreflightReviewReady",
                "optimizerProductionSwitchPreflightReadyForProductionMutation",
                "optimizerProductionSwitchRequiredEvidence",
                "optimizerProductionSwitchRequiredEvidenceCount",
                "optimizerProductionSwitchBlockingReasons",
                "optimizerProductionSwitchBlockingReasonCount",
                "optimizerProductionSwitchRemainingWork",
                "optimizerProductionSwitchRemainingWorkCount",
                "optimizerProductionSwitchFirstBlockingReason",
                "optimizerProductionSwitchFirstRemainingWork",
                "optimizerProductionSwitchCiSummaryLine"
        ));
        assertEquals("switchContractKernel", fields.get("optimizerProductionSwitchMethod"));
        assertEquals("blocked/preflightNotReady", fields.get("optimizerProductionSwitchVerdict"));
        assertEquals("false", fields.get("optimizerProductionSwitchEligibleForSwitchReview"));
        assertEquals("false", fields.get("optimizerProductionSwitchProductionMutationEnabled"));
        assertEquals("blocked/optimizerValidationBundleNotReady", fields.get("optimizerProductionSwitchPreflightVerdict"));
        assertEquals("false", fields.get("optimizerProductionSwitchPreflightReviewReady"));
        assertEquals("false", fields.get("optimizerProductionSwitchPreflightReadyForProductionMutation"));
        assertEquals("4", fields.get("optimizerProductionSwitchRequiredEvidenceCount"));
        assertEquals("productionPreflightNotReviewReady", fields.get("optimizerProductionSwitchFirstBlockingReason"));
        assertEquals("collectPreviewCandidates", fields.get("optimizerProductionSwitchFirstRemainingWork"));
    }

    @Test
    void optimizerProductionPreflightKeepsStableCiConsumptionKeys() {
        GpuIrOptimizationValidationReport report = validationReport("preflightContractKernel");

        Map<String, String> fields = new GpuIrOptimizationValidationOptimizerEnablementArtifactRunner()
                .runProductionPreflightFields(report);

        assertContainsKeys(fields, Set.of(
                "optimizerProductionPreflightMethod",
                "optimizerProductionPreflightVerdict",
                "optimizerProductionPreflightBlocked",
                "optimizerProductionPreflightReviewReady",
                "optimizerProductionPreflightReadyForProductionMutation",
                "optimizerProductionPreflightProductionMutationEnabled",
                "optimizerProductionPreflightBundleVerdict",
                "optimizerProductionPreflightFirstBlockingReason",
                "optimizerProductionPreflightFirstRemainingWork",
                "optimizerProductionPreflightCiSummaryLine"
        ));
        assertEquals("preflightContractKernel", fields.get("optimizerProductionPreflightMethod"));
        assertEquals("blocked/optimizerValidationBundleNotReady", fields.get("optimizerProductionPreflightVerdict"));
        assertEquals("true", fields.get("optimizerProductionPreflightBlocked"));
        assertEquals("false", fields.get("optimizerProductionPreflightReviewReady"));
        assertEquals("false", fields.get("optimizerProductionPreflightReadyForProductionMutation"));
        assertEquals("false", fields.get("optimizerProductionPreflightProductionMutationEnabled"));
        assertEquals("notReady/cseBlocked", fields.get("optimizerProductionPreflightBundleVerdict"));
        assertEquals("cseLiteralPromotionNotReady", fields.get("optimizerProductionPreflightFirstBlockingReason"));
        assertEquals("collectPreviewCandidates", fields.get("optimizerProductionPreflightFirstRemainingWork"));
    }

    @Test
    void productionEnablementReadinessRunnerKeepsStableCiConsumptionKeys() {
        GpuIrOptimizationValidationReport report = validationReport("readinessRunnerContractKernel");

        Map<String, String> fields = new GpuIrOptimizationValidationProductionEnablementReadinessRunner()
                .runFields(report);

        assertContainsKeys(fields, Set.of(
                "optimizerProductionPreflightMethod",
                "optimizerProductionPreflightVerdict",
                "optimizerProductionPreflightBlocked",
                "optimizerProductionPreflightReviewReady",
                "optimizerProductionPreflightReadyForProductionMutation",
                "optimizerProductionPreflightProductionMutationEnabled",
                "optimizerProductionPreflightBundleVerdict",
                "optimizerProductionPreflightFirstBlockingReason",
                "optimizerProductionPreflightFirstRemainingWork",
                "optimizerProductionPreflightCiSummaryLine"
        ));
        assertEquals("readinessRunnerContractKernel", fields.get("optimizerProductionPreflightMethod"));
        assertEquals("blocked/optimizerValidationBundleNotReady", fields.get("optimizerProductionPreflightVerdict"));
        assertEquals("true", fields.get("optimizerProductionPreflightBlocked"));
        assertEquals("cseLiteralPromotionNotReady", fields.get("optimizerProductionPreflightFirstBlockingReason"));
    }

    @Test
    void optimizerProductionReadinessArtifactKeepsStableCiConsumptionKeys() {
        GpuIrOptimizationValidationReport report = validationReport("productionReadinessContractKernel");

        Map<String, String> fields = new GpuIrOptimizationValidationProductionEnablementReadinessRunner()
                .runProductionReadinessArtifactFields(report);

        assertContainsKeys(fields, Set.of(
                "optimizerProductionReadinessMethod",
                "optimizerProductionReadinessVerdict",
                "optimizerProductionReadinessBlocked",
                "optimizerProductionReadinessReviewReady",
                "optimizerProductionReadinessProductionMutationEnabled",
                "optimizerProductionReadinessPromotionAllowed",
                "optimizerProductionReadinessBundleVerdict",
                "optimizerProductionReadinessPreflightVerdict",
                "optimizerProductionReadinessSwitchVerdict",
                "optimizerProductionReadinessPromotionConfidenceVerdict",
                "optimizerProductionReadinessBlockingReasons",
                "optimizerProductionReadinessBlockingReasonCount",
                "optimizerProductionReadinessRemainingWork",
                "optimizerProductionReadinessRemainingWorkCount",
                "optimizerProductionReadinessFirstBlockingReason",
                "optimizerProductionReadinessFirstRemainingWork",
                "optimizerProductionReadinessConsistent",
                "optimizerProductionReadinessCiSummaryLine",
                "optimizerProductionReadinessSummary",
                "optimizerProductionReadinessConsistency.Method",
                "optimizerProductionReadinessConsistency.Consistent",
                "optimizerProductionReadinessConsistency.Checks",
                "optimizerProductionReadinessConsistency.FailedChecks",
                "optimizerProductionReadinessConsistency.FailedCheckList",
                "optimizerProductionReadinessConsistency.CiSummaryLine",
                "optimizerProductionReadinessAcceptance.Method",
                "optimizerProductionReadinessAcceptance.Verdict",
                "optimizerProductionReadinessAcceptance.Accepted",
                "optimizerProductionReadinessAcceptance.Rejected",
                "optimizerProductionReadinessAcceptance.Reason",
                "optimizerProductionReadinessAcceptance.FirstBlockingReason",
                "optimizerProductionReadinessAcceptance.FirstRemainingWork",
                "optimizerProductionReadinessAcceptance.FirstConsistencyFailedCheck",
                "optimizerProductionReadinessAcceptance.CiSummaryLine",
                "optimizerValidationBundleVerdict",
                "optimizerProductionPreflightVerdict",
                "optimizerProductionSwitchVerdict",
                "optimizerPromotionConfidenceVerdict"
        ));
        assertEquals("productionReadinessContractKernel", fields.get("optimizerProductionReadinessMethod"));
        assertEquals("blocked/productionSwitchNotReady", fields.get("optimizerProductionReadinessVerdict"));
        assertEquals("true", fields.get("optimizerProductionReadinessBlocked"));
        assertEquals("false", fields.get("optimizerProductionReadinessReviewReady"));
        assertEquals("false", fields.get("optimizerProductionReadinessProductionMutationEnabled"));
        assertEquals("false", fields.get("optimizerProductionReadinessPromotionAllowed"));
        assertEquals("notReady/cseBlocked", fields.get("optimizerProductionReadinessBundleVerdict"));
        assertEquals("blocked/optimizerValidationBundleNotReady", fields.get("optimizerProductionReadinessPreflightVerdict"));
        assertEquals("blocked/preflightNotReady", fields.get("optimizerProductionReadinessSwitchVerdict"));
        assertEquals("blocked/productionSwitchNotReady", fields.get("optimizerProductionReadinessPromotionConfidenceVerdict"));
        assertEquals("productionMutationSwitchNotReady", fields.get("optimizerProductionReadinessFirstBlockingReason"));
        assertEquals("collectPreviewCandidates", fields.get("optimizerProductionReadinessFirstRemainingWork"));
        assertEquals("true", fields.get("optimizerProductionReadinessConsistent"));
        assertEquals("true", fields.get("optimizerProductionReadinessConsistency.Consistent"));
        assertEquals("0", fields.get("optimizerProductionReadinessConsistency.FailedChecks"));
        assertEquals("false", fields.get("optimizerProductionReadinessAcceptance.Accepted"));
        assertEquals("true", fields.get("optimizerProductionReadinessAcceptance.Rejected"));
        assertEquals("rejected/productionReadinessBlocked", fields.get("optimizerProductionReadinessAcceptance.Reason"));
        assertEquals(
                "productionMutationSwitchNotReady",
                fields.get("optimizerProductionReadinessAcceptance.FirstBlockingReason")
        );
        assertTrue(fields.get("optimizerProductionReadinessRemainingWork").contains("stabilizeA1A2RuntimeEquivalenceHistory"));
    }

    private static void assertContainsKeys(Map<String, String> fields, Set<String> expectedKeys) {
        assertTrue(fields.keySet().containsAll(expectedKeys), () -> "missing artifact keys: "
                + expectedKeys.stream()
                .filter(key -> !fields.containsKey(key))
                .toList());
    }
}
