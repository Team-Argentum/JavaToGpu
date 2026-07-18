package net.sixik.ga_utils.javatogpu.runtime;

import java.util.Properties;

/**
 * Compact runtime-facing summary of production-promotion explainability evidence.
 *
 * <p>The explainability artifact remains the detailed gate output. This record gives reports, validation history, and CI
 * workflows one stable scalar summary surface instead of reparsing the full production-promotion contract each time.</p>
 */
public record GpuProductionPromotionExplainabilitySummary(
        String status,
        boolean contractValid,
        String firstViolation,
        String decisionMode,
        String productionSourceSwitchingAllowed,
        String productionSourceSwitchingEnabled,
        int productionSourceSwitchingEnabledCount,
        String productionSourceSwitchingEnabledAll,
        int productionPromotionDecisionEnabledCount,
        String productionPromotionDecisionEnabledAll,
        int productionPromotionOperatorAcceptedCount,
        String productionPromotionOperatorAcceptedAll,
        int productionSourceDecisionCount,
        String productionSourceDecisionAll,
        String productionMutationAllowed,
        String productionMutationEnabled,
        int kernelCount,
        int blockerCount,
        String firstBlocker,
        int i3ReviewReadyCount,
        int i3BlockedCount,
        int i3SourceReadyCount,
        String i3SourceReadyAll,
        int optimizerFamilyCount,
        int optimizerFamilyPromotionReadyCount,
        String optimizerFamilySummary,
        int optimizerReplacementPlanCompleteCount,
        int optimizerReplacementPlanPartialCount,
        String optimizerReplacementPlanFirstBlockers,
        int optimizerReplacementPlanValidationCount,
        int optimizerReplacementPlanValidationValidCount,
        int optimizerReplacementPlanValidationInvalidCount,
        String optimizerReplacementPlanValidationFirstBlockers,
        int optimizerRewriteSketchCount,
        int optimizerRewriteSketchReadyCount,
        int optimizerRewriteSketchBlockedCount,
        String optimizerRewriteSketchFirstBlockers,
        int optimizerRewriteSketchConflictCount,
        String optimizerRewriteSketchConflictFirstBlockers,
        String optimizerRewriteSelectionStatuses,
        String optimizerRewriteSelectionFirstBlockers,
        String optimizerRewriteProofStatuses,
        String optimizerRewriteProofFirstBlockers,
        String optimizerRewriteReviewPackageStatuses,
        String optimizerRewriteReviewPackageFirstBlockers,
        int optimizerRuleCount,
        String optimizerRuleSummary,
        String optimizerRuleDetails,
        int optimizerFamilyPayloadCompleteCount,
        String optimizerFamilyPayloadCompleteAll,
        String optimizerFamilyRuntimeEquivalenceHistoryBaselineReady,
        String optimizerFamilyPromotionPreflightReady,
        String backendPromotionArtifactSupportComplete,
        int backendPromotionArtifactSupportMissingCount,
        String controlledProductionSourceSwitchingStatus,
        int controlledProductionSourceSwitchingKernelCount,
        int controlledProductionSourceSwitchingRealWorkloadCoveredCount,
        int controlledProductionSourceSwitchingRealWorkloadTotalCount,
        int controlledProductionSourceSwitchingRealWorkloadUncoveredCount,
        String controlledProductionSourceSwitchingRealWorkloadCoveredAll,
        String controlledProductionMutationStatus,
        String controlledProductionMutationReviewReady,
        String controlledProductionMutationProductionMutation,
        String controlledProductionMutationDefaultProductionMutation,
        int controlledProductionMutationRealWorkloadCoveredCount,
        int controlledProductionMutationRealWorkloadTotalCount,
        int controlledProductionMutationRealWorkloadUncoveredCount,
        String controlledProductionMutationRealWorkloadCoveredAll,
        String controlledProductionMutationPassed,
        String controlledProductionActivationTokenSmokeStatus,
        String controlledProductionActivationTokenLoaded,
        String controlledProductionActivationTokenApprovedKernelExecuted,
        int controlledProductionActivationTokenRealWorkloadCoveredCount,
        int controlledProductionActivationTokenRealWorkloadTotalCount,
        int controlledProductionActivationTokenRealWorkloadUncoveredCount,
        String controlledProductionActivationTokenRealWorkloadCoveredAll,
        String controlledProductionActivationTokenSafeDefaults,
        String controlledProductionActivationTokenSmokePassed,
        String controlledProductionActivationTokenNegativeStatus,
        String controlledProductionActivationTokenDigestMismatchRejected,
        String controlledProductionActivationTokenUnapprovedKernelRejected,
        String controlledProductionActivationTokenNegativeOutputUnchanged,
        String controlledProductionActivationTokenNegativeSafeDefaults,
        String controlledProductionActivationTokenNegativePassed,
        int readinessChecklistReadyCount,
        int readinessChecklistBlockedCount,
        String readinessChecklistReadyAll,
        String readinessChecklistFirstBlocked
) {

    public static GpuProductionPromotionExplainabilitySummary notRecorded() {
        return new GpuProductionPromotionExplainabilitySummary(
                "not-recorded",
                false,
                "not-recorded",
                GpuProductionPromotionDecision.DIAGNOSTIC_ONLY,
                "false",
                "false",
                0,
                "false",
                0,
                "false",
                0,
                "false",
                0,
                "false",
                "false",
                "false",
                0,
                0,
                "none",
                0,
                0,
                0,
                "false",
                0,
                0,
                "none",
                0,
                0,
                "",
                0,
                0,
                0,
                "",
                0,
                0,
                0,
                "",
                0,
                "",
                "none",
                "none",
                "none",
                "none",
                "none",
                "none",
                0,
                "none",
                "none",
                0,
                "false",
                "false",
                "true",
                "unknown",
                0,
                "not-recorded",
                0,
                0,
                0,
                0,
                "false",
                "not-recorded",
                "false",
                "disabled",
                "unknown",
                0,
                0,
                0,
                "false",
                "false",
                "not-recorded",
                "false",
                "false",
                0,
                0,
                0,
                "false",
                "false",
                "false",
                "not-recorded",
                "false",
                "false",
                "false",
                "false",
                "false",
                0,
                0,
                "false",
                "none"
        );
    }

    public static GpuProductionPromotionExplainabilitySummary fromProperties(Properties properties) {
        if (properties == null || properties.isEmpty()) {
            return notRecorded();
        }
        GpuProductionPromotionExplainabilityValidation.Result contract =
                GpuProductionPromotionExplainabilityValidation.validate(properties);
        int blockerCount = parsePositiveInt(properties.getProperty("blocker.count", "0"));
        return new GpuProductionPromotionExplainabilitySummary(
                properties.getProperty("status", contract.status()),
                contract.valid(),
                contract.firstViolation(),
                properties.getProperty("decision.mode", "unknown"),
                properties.getProperty("productionSourceSwitchingAllowed", Boolean.toString(contract.sourceSwitchingAllowed())),
                properties.getProperty("productionSourceSwitchingEnabled", Boolean.toString(contract.sourceSwitchingEnabled())),
                contract.productionSourceSwitchingEnabledCount(),
                Boolean.toString(contract.allSourceSwitchingEnabled()),
                contract.productionPromotionDecisionEnabledCount(),
                Boolean.toString(contract.allPromotionDecisionsEnabled()),
                contract.productionPromotionOperatorAcceptedCount(),
                Boolean.toString(contract.allPromotionOperatorsAccepted()),
                contract.productionSourceDecisionCount(),
                Boolean.toString(contract.allProductionSourceDecisions()),
                properties.getProperty("productionMutationAllowed", Boolean.toString(contract.mutationAllowed())),
                properties.getProperty("productionMutationEnabled", Boolean.toString(contract.mutationEnabled())),
                contract.kernelCount(),
                blockerCount,
                blockerCount == 0 ? "none" : properties.getProperty("blocker.0", "unknown"),
                parsePositiveInt(properties.getProperty("i3ReviewReady.count", Integer.toString(contract.i3ReviewReadyCount()))),
                parsePositiveInt(properties.getProperty("i3Blocked.count", Integer.toString(contract.i3BlockedCount()))),
                parsePositiveInt(properties.getProperty("i3SourceReady.count", Integer.toString(contract.i3SourceReadyCount()))),
                properties.getProperty("i3SourceReady.all", "false"),
                parsePositiveInt(properties.getProperty("optimizerFamily.count", "0")),
                parsePositiveInt(properties.getProperty("optimizerFamily.promotionReady.count", "0")),
                properties.getProperty("optimizerFamily.summary", "none"),
                parsePositiveInt(properties.getProperty("optimizerReplacementPlan.complete.count", "0")),
                parsePositiveInt(properties.getProperty("optimizerReplacementPlan.partial.count", "0")),
                properties.getProperty("optimizerReplacementPlan.firstBlockers", ""),
                parsePositiveInt(properties.getProperty("optimizerReplacementPlan.validation.count", "0")),
                parsePositiveInt(properties.getProperty("optimizerReplacementPlan.validation.valid.count", "0")),
                parsePositiveInt(properties.getProperty("optimizerReplacementPlan.validation.invalid.count", "0")),
                properties.getProperty("optimizerReplacementPlan.validation.firstBlockers", ""),
                parsePositiveInt(properties.getProperty("optimizerRewriteSketch.count", "0")),
                parsePositiveInt(properties.getProperty("optimizerRewriteSketch.ready.count", "0")),
                parsePositiveInt(properties.getProperty("optimizerRewriteSketch.blocked.count", "0")),
                properties.getProperty("optimizerRewriteSketch.firstBlockers", ""),
                parsePositiveInt(properties.getProperty("optimizerRewriteSketch.conflict.count", "0")),
                properties.getProperty("optimizerRewriteSketch.conflict.firstBlockers", ""),
                properties.getProperty("optimizerRewriteSelection.statuses", "none"),
                properties.getProperty("optimizerRewriteSelection.firstBlockers", "none"),
                properties.getProperty("optimizerRewriteProof.statuses", "none"),
                properties.getProperty("optimizerRewriteProof.firstBlockers", "none"),
                properties.getProperty("optimizerRewriteReviewPackage.statuses", "none"),
                properties.getProperty("optimizerRewriteReviewPackage.firstBlockers", "none"),
                parsePositiveInt(properties.getProperty("optimizerRule.count", "0")),
                properties.getProperty("optimizerRule.summary", "none"),
                properties.getProperty("optimizerRule.details", "none"),
                parsePositiveInt(properties.getProperty("optimizerFamilyPayload.complete.count", "0")),
                properties.getProperty("optimizerFamilyPayload.complete.all", "false"),
                properties.getProperty("optimizerFamily.runtimeEquivalenceHistoryBaselineReady", "false"),
                properties.getProperty("optimizerFamily.promotionPreflightReady", "true"),
                properties.getProperty("backendPromotionArtifactSupport.complete", "unknown"),
                parsePositiveInt(properties.getProperty("backendPromotionArtifactSupport.missing.count", "0")),
                firstProperty(
                        properties,
                        "not-recorded",
                        "runtime.production.sourceSwitching.controlled.status",
                        "controlledProductionSourceSwitching.status"
                ),
                parsePositiveInt(firstProperty(
                        properties,
                        "0",
                        "runtime.production.sourceSwitching.controlled.kernel.count",
                        "controlledProductionSourceSwitching.kernel.count"
                )),
                parsePositiveInt(firstProperty(
                        properties,
                        "0",
                        "runtime.production.sourceSwitching.controlled.realWorkload.covered.count",
                        "controlledProductionSourceSwitching.realWorkload.covered.count"
                )),
                parsePositiveInt(firstProperty(
                        properties,
                        "0",
                        "runtime.production.sourceSwitching.controlled.realWorkload.total.count",
                        "controlledProductionSourceSwitching.realWorkload.total.count"
                )),
                parsePositiveInt(firstProperty(
                        properties,
                        "0",
                        "runtime.production.sourceSwitching.controlled.realWorkload.uncovered.count",
                        "controlledProductionSourceSwitching.realWorkload.uncovered.count"
                )),
                firstProperty(
                        properties,
                        "false",
                        "runtime.production.sourceSwitching.controlled.realWorkload.covered.all",
                        "controlledProductionSourceSwitching.realWorkload.covered.all"
                ),
                firstProperty(
                        properties,
                        "not-recorded",
                        "runtime.production.mutation.controlled.status",
                        "controlledProductionMutation.status"
                ),
                firstProperty(
                        properties,
                        "false",
                        "runtime.production.mutation.controlled.reviewReady",
                        "controlledProductionMutation.reviewReady"
                ),
                firstProperty(
                        properties,
                        "disabled",
                        "runtime.production.mutation.controlled.productionMutation",
                        "controlledProductionMutation.productionMutation"
                ),
                firstProperty(
                        properties,
                        "unknown",
                        "runtime.production.mutation.controlled.defaultProductionMutation",
                        "controlledProductionMutation.defaultProductionMutation"
                ),
                parsePositiveInt(firstProperty(
                        properties,
                        "0",
                        "runtime.production.mutation.controlled.realWorkload.covered.count",
                        "controlledProductionMutation.realWorkload.covered.count"
                )),
                parsePositiveInt(firstProperty(
                        properties,
                        "0",
                        "runtime.production.mutation.controlled.realWorkload.total.count",
                        "controlledProductionMutation.realWorkload.total.count"
                )),
                parsePositiveInt(firstProperty(
                        properties,
                        "0",
                        "runtime.production.mutation.controlled.realWorkload.uncovered.count",
                        "controlledProductionMutation.realWorkload.uncovered.count"
                )),
                firstProperty(
                        properties,
                        "false",
                        "runtime.production.mutation.controlled.realWorkload.covered.all",
                        "controlledProductionMutation.realWorkload.covered.all"
                ),
                firstProperty(
                        properties,
                        "false",
                        "runtime.production.mutation.controlled.passed",
                        "controlledProductionMutation.passed"
                ),
                firstProperty(
                        properties,
                        "not-recorded",
                        "runtime.production.activationToken.smoke.status",
                        "controlledProductionActivationTokenSmoke.status"
                ),
                firstProperty(
                        properties,
                        "false",
                        "runtime.production.activationToken.smoke.tokenLoaded",
                        "controlledProductionActivationTokenSmoke.tokenLoaded"
                ),
                firstProperty(
                        properties,
                        "false",
                        "runtime.production.activationToken.smoke.approvedKernelExecuted",
                        "controlledProductionActivationTokenSmoke.approvedKernelExecuted"
                ),
                parsePositiveInt(firstProperty(
                        properties,
                        "0",
                        "runtime.production.activationToken.smoke.realWorkload.covered.count",
                        "controlledProductionActivationTokenSmoke.realWorkload.covered.count"
                )),
                parsePositiveInt(firstProperty(
                        properties,
                        "0",
                        "runtime.production.activationToken.smoke.realWorkload.total.count",
                        "controlledProductionActivationTokenSmoke.realWorkload.total.count"
                )),
                parsePositiveInt(firstProperty(
                        properties,
                        "0",
                        "runtime.production.activationToken.smoke.realWorkload.uncovered.count",
                        "controlledProductionActivationTokenSmoke.realWorkload.uncovered.count"
                )),
                firstProperty(
                        properties,
                        "false",
                        "runtime.production.activationToken.smoke.realWorkload.covered.all",
                        "controlledProductionActivationTokenSmoke.realWorkload.covered.all"
                ),
                firstProperty(
                        properties,
                        "false",
                        "runtime.production.activationToken.smoke.safeDefaults",
                        "controlledProductionActivationTokenSmoke.safeDefaults"
                ),
                firstProperty(
                        properties,
                        "false",
                        "runtime.production.activationToken.smoke.passed",
                        "controlledProductionActivationTokenSmoke.passed"
                ),
                firstProperty(
                        properties,
                        "not-recorded",
                        "runtime.production.activationToken.negative.status",
                        "controlledProductionActivationTokenNegative.status"
                ),
                firstProperty(
                        properties,
                        "false",
                        "runtime.production.activationToken.negative.digestMismatchRejected",
                        "controlledProductionActivationTokenNegative.digestMismatchRejected"
                ),
                firstProperty(
                        properties,
                        "false",
                        "runtime.production.activationToken.negative.unapprovedKernelRejected",
                        "controlledProductionActivationTokenNegative.unapprovedKernelRejected"
                ),
                firstProperty(
                        properties,
                        "false",
                        "runtime.production.activationToken.negative.outputUnchanged",
                        "controlledProductionActivationTokenNegative.outputUnchanged"
                ),
                firstProperty(
                        properties,
                        "false",
                        "runtime.production.activationToken.negative.safeDefaults",
                        "controlledProductionActivationTokenNegative.safeDefaults"
                ),
                firstProperty(
                        properties,
                        "false",
                        "runtime.production.activationToken.negative.passed",
                        "controlledProductionActivationTokenNegative.passed"
                ),
                parsePositiveInt(properties.getProperty("readinessChecklist.ready.count", "0")),
                parsePositiveInt(properties.getProperty("readinessChecklist.blocked.count", "0")),
                properties.getProperty("readinessChecklist.ready.all", "false"),
                properties.getProperty("readinessChecklist.firstBlocked", "none")
        );
    }

    public String contractStatus() {
        return contractValid ? "valid" : "invalid";
    }

    public String contractViolationText() {
        return contractValid ? "" : ", violation=" + firstViolation;
    }

    public String firstBlockerText() {
        return blockerCount == 0 ? "" : ", first=" + firstBlocker;
    }

    public String historyStatus() {
        if ("not-recorded".equals(status)) {
            return "not recorded";
        }
        return status
                + " (contract=" + contractStatus()
                + contractViolationText()
                + ", decisionMode=" + decisionMode
                + ", sourceSwitchingAllowed=" + productionSourceSwitchingAllowed
                + ", sourceSwitchingEnabled=" + productionSourceSwitchingEnabled
                + ", sourceSwitchingEnabledCount=" + productionSourceSwitchingEnabledCount
                + ", sourceSwitchingEnabledAll=" + productionSourceSwitchingEnabledAll
                + ", productionPromotionDecisionEnabled=" + productionPromotionDecisionEnabledCount
                + ", productionPromotionDecisionEnabledAll=" + productionPromotionDecisionEnabledAll
                + ", operatorAccepted=" + productionPromotionOperatorAcceptedCount
                + ", operatorAcceptedAll=" + productionPromotionOperatorAcceptedAll
                + ", productionSourceDecisions=" + productionSourceDecisionCount
                + ", productionSourceDecisionAll=" + productionSourceDecisionAll
                + ", mutationAllowed=" + productionMutationAllowed
                + ", mutationEnabled=" + productionMutationEnabled
                + ", blockers=" + blockerCount
                + firstBlockerText()
                + ", i3ReviewReady=" + i3ReviewReadyCount
                + ", i3Blocked=" + i3BlockedCount
                + ", i3SourceReady=" + i3SourceReadyCount
                + ", i3SourceReadyAll=" + i3SourceReadyAll
                + ", optimizerFamilies=" + optimizerFamilyCount
                + ", optimizerPromotionReadyFamilies=" + optimizerFamilyPromotionReadyCount
                + ", optimizerPayloadCompleteFamilies=" + optimizerFamilyPayloadCompleteCount
                + ", optimizerPayloadCompleteAll=" + optimizerFamilyPayloadCompleteAll
                + ", optimizerRuntimeEquivalenceHistoryBaselineReady="
                + optimizerFamilyRuntimeEquivalenceHistoryBaselineReady
                + ", optimizerPromotionPreflightReady="
                + optimizerFamilyPromotionPreflightReady
                + optimizerFamilySummaryText()
                + optimizerReplacementPlanSummaryText()
                + optimizerRewriteSketchSummaryText()
                + optimizerRuleSummaryText()
                + ", backendPromotionArtifactSupportComplete=" + backendPromotionArtifactSupportComplete
                + ", backendPromotionArtifactSupportMissing=" + backendPromotionArtifactSupportMissingCount
                + ", controlledSourceSwitching=" + controlledProductionSourceSwitchingStatus
                + ", controlledSourceSwitchingKernels=" + controlledProductionSourceSwitchingKernelCount
                + ", controlledRealWorkloadCoverage=" + controlledProductionSourceSwitchingRealWorkloadCoveredCount
                + "/" + controlledProductionSourceSwitchingRealWorkloadTotalCount
                + ", controlledRealWorkloadUncovered=" + controlledProductionSourceSwitchingRealWorkloadUncoveredCount
                + ", controlledRealWorkloadCoverageAll=" + controlledProductionSourceSwitchingRealWorkloadCoveredAll
                + ", controlledProductionMutation=" + controlledProductionMutationStatus
                + ", controlledProductionMutationReviewReady=" + controlledProductionMutationReviewReady
                + ", controlledProductionMutationMode=" + controlledProductionMutationProductionMutation
                + ", controlledProductionMutationDefault=" + controlledProductionMutationDefaultProductionMutation
                + ", controlledProductionMutationRealWorkloadCoverage="
                + controlledProductionMutationRealWorkloadCoveredCount
                + "/" + controlledProductionMutationRealWorkloadTotalCount
                + ", controlledProductionMutationRealWorkloadUncovered="
                + controlledProductionMutationRealWorkloadUncoveredCount
                + ", controlledProductionMutationRealWorkloadCoverageAll="
                + controlledProductionMutationRealWorkloadCoveredAll
                + ", controlledProductionMutationPassed=" + controlledProductionMutationPassed
                + ", controlledActivationTokenSmoke=" + controlledProductionActivationTokenSmokeStatus
                + ", controlledActivationTokenLoaded=" + controlledProductionActivationTokenLoaded
                + ", controlledActivationTokenApprovedKernelExecuted="
                + controlledProductionActivationTokenApprovedKernelExecuted
                + ", controlledActivationTokenRealWorkloadCoverage="
                + controlledProductionActivationTokenRealWorkloadCoveredCount
                + "/" + controlledProductionActivationTokenRealWorkloadTotalCount
                + ", controlledActivationTokenRealWorkloadUncovered="
                + controlledProductionActivationTokenRealWorkloadUncoveredCount
                + ", controlledActivationTokenRealWorkloadCoverageAll="
                + controlledProductionActivationTokenRealWorkloadCoveredAll
                + ", controlledActivationTokenSafeDefaults=" + controlledProductionActivationTokenSafeDefaults
                + ", controlledActivationTokenSmokePassed=" + controlledProductionActivationTokenSmokePassed
                + ", controlledActivationTokenNegative=" + controlledProductionActivationTokenNegativeStatus
                + ", controlledActivationTokenDigestMismatchRejected="
                + controlledProductionActivationTokenDigestMismatchRejected
                + ", controlledActivationTokenUnapprovedKernelRejected="
                + controlledProductionActivationTokenUnapprovedKernelRejected
                + ", controlledActivationTokenNegativeOutputUnchanged="
                + controlledProductionActivationTokenNegativeOutputUnchanged
                + ", controlledActivationTokenNegativeSafeDefaults="
                + controlledProductionActivationTokenNegativeSafeDefaults
                + ", controlledActivationTokenNegativePassed="
                + controlledProductionActivationTokenNegativePassed
                + ", readinessChecklistReady=" + readinessChecklistReadyCount
                + ", readinessChecklistBlocked=" + readinessChecklistBlockedCount
                + ", readinessChecklistReadyAll=" + readinessChecklistReadyAll
                + ", readinessChecklistFirstBlocked=" + readinessChecklistFirstBlocked
                + ")";
    }

    private String optimizerFamilySummaryText() {
        return optimizerFamilySummary == null || optimizerFamilySummary.isBlank() || "none".equals(optimizerFamilySummary)
                ? ""
                : ", optimizerFamilySummary=" + optimizerFamilySummary;
    }

    private String optimizerRuleSummaryText() {
        if (optimizerRuleCount == 0
                && (optimizerRuleSummary == null || optimizerRuleSummary.isBlank() || "none".equals(optimizerRuleSummary))
                && (optimizerRuleDetails == null || optimizerRuleDetails.isBlank() || "none".equals(optimizerRuleDetails))) {
            return "";
        }
        StringBuilder builder = new StringBuilder(", optimizerRules=").append(optimizerRuleCount);
        if (optimizerRuleSummary != null && !optimizerRuleSummary.isBlank() && !"none".equals(optimizerRuleSummary)) {
            builder.append(", optimizerRuleSummary=").append(optimizerRuleSummary);
        }
        if (optimizerRuleDetails != null && !optimizerRuleDetails.isBlank() && !"none".equals(optimizerRuleDetails)) {
            builder.append(", optimizerRuleDetails=").append(optimizerRuleDetails);
        }
        return builder.toString();
    }

    private String optimizerReplacementPlanSummaryText() {
        if (optimizerReplacementPlanCompleteCount == 0
                && optimizerReplacementPlanPartialCount == 0
                && optimizerReplacementPlanValidationCount == 0
                && optimizerReplacementPlanValidationValidCount == 0
                && optimizerReplacementPlanValidationInvalidCount == 0
                && (optimizerReplacementPlanFirstBlockers == null || optimizerReplacementPlanFirstBlockers.isBlank())
                && (optimizerReplacementPlanValidationFirstBlockers == null
                || optimizerReplacementPlanValidationFirstBlockers.isBlank())) {
            return "";
        }
        StringBuilder builder = new StringBuilder(", optimizerReplacementPlans=complete=")
                .append(optimizerReplacementPlanCompleteCount)
                .append("/partial=")
                .append(optimizerReplacementPlanPartialCount)
                .append("/validation=valid=")
                .append(optimizerReplacementPlanValidationValidCount)
                .append("/total=")
                .append(optimizerReplacementPlanValidationCount)
                .append("/invalid=")
                .append(optimizerReplacementPlanValidationInvalidCount);
        if (optimizerReplacementPlanFirstBlockers != null && !optimizerReplacementPlanFirstBlockers.isBlank()) {
            builder.append("/firstBlockers=").append(optimizerReplacementPlanFirstBlockers);
        }
        if (optimizerReplacementPlanValidationFirstBlockers != null
                && !optimizerReplacementPlanValidationFirstBlockers.isBlank()) {
            builder.append("/validationFirstBlockers=").append(optimizerReplacementPlanValidationFirstBlockers);
        }
        return builder.toString();
    }

    private String optimizerRewriteSketchSummaryText() {
        if (optimizerRewriteSketchCount == 0
                && optimizerRewriteSketchReadyCount == 0
                && optimizerRewriteSketchBlockedCount == 0
                && optimizerRewriteSketchConflictCount == 0
                && (optimizerRewriteSketchFirstBlockers == null || optimizerRewriteSketchFirstBlockers.isBlank())
                && (optimizerRewriteSketchConflictFirstBlockers == null
                || optimizerRewriteSketchConflictFirstBlockers.isBlank())
                && (optimizerRewriteSelectionStatuses == null
                || optimizerRewriteSelectionStatuses.isBlank()
                || "none".equals(optimizerRewriteSelectionStatuses))
                && (optimizerRewriteSelectionFirstBlockers == null
                || optimizerRewriteSelectionFirstBlockers.isBlank()
                || "none".equals(optimizerRewriteSelectionFirstBlockers))
                && (optimizerRewriteProofStatuses == null
                || optimizerRewriteProofStatuses.isBlank()
                || "none".equals(optimizerRewriteProofStatuses))
                && (optimizerRewriteProofFirstBlockers == null
                || optimizerRewriteProofFirstBlockers.isBlank()
                || "none".equals(optimizerRewriteProofFirstBlockers))
                && (optimizerRewriteReviewPackageStatuses == null
                || optimizerRewriteReviewPackageStatuses.isBlank()
                || "none".equals(optimizerRewriteReviewPackageStatuses))
                && (optimizerRewriteReviewPackageFirstBlockers == null
                || optimizerRewriteReviewPackageFirstBlockers.isBlank()
                || "none".equals(optimizerRewriteReviewPackageFirstBlockers))) {
            return "";
        }
        StringBuilder builder = new StringBuilder(", optimizerRewriteSketches=ready=")
                .append(optimizerRewriteSketchReadyCount)
                .append("/total=")
                .append(optimizerRewriteSketchCount)
                .append("/blocked=")
                .append(optimizerRewriteSketchBlockedCount)
                .append("/conflicts=")
                .append(optimizerRewriteSketchConflictCount)
                .append("/rewriteBuilderImplemented=false")
                .append("/mutationAllowed=false")
                .append("/selectedIrReplacement=false");
        if (optimizerRewriteSketchFirstBlockers != null && !optimizerRewriteSketchFirstBlockers.isBlank()) {
            builder.append("/firstBlockers=").append(optimizerRewriteSketchFirstBlockers);
        }
        if (optimizerRewriteSketchConflictFirstBlockers != null
                && !optimizerRewriteSketchConflictFirstBlockers.isBlank()) {
            builder.append("/conflictFirstBlockers=").append(optimizerRewriteSketchConflictFirstBlockers);
        }
        if (optimizerRewriteSelectionStatuses != null
                && !optimizerRewriteSelectionStatuses.isBlank()
                && !"none".equals(optimizerRewriteSelectionStatuses)) {
            builder.append("/selectionStatus=").append(optimizerRewriteSelectionStatuses);
        }
        if (optimizerRewriteSelectionFirstBlockers != null
                && !optimizerRewriteSelectionFirstBlockers.isBlank()
                && !"none".equals(optimizerRewriteSelectionFirstBlockers)) {
            builder.append("/selectionFirstBlockers=").append(optimizerRewriteSelectionFirstBlockers);
        }
        builder.append("/selectionApplied=false");
        if (optimizerRewriteProofStatuses != null
                && !optimizerRewriteProofStatuses.isBlank()
                && !"none".equals(optimizerRewriteProofStatuses)) {
            builder.append("/proofStatus=").append(optimizerRewriteProofStatuses);
        }
        if (optimizerRewriteProofFirstBlockers != null
                && !optimizerRewriteProofFirstBlockers.isBlank()
                && !"none".equals(optimizerRewriteProofFirstBlockers)) {
            builder.append("/proofFirstBlockers=").append(optimizerRewriteProofFirstBlockers);
        }
        builder.append("/proofAccepted=false")
                .append("/runtimeEquivalencePayloadComplete=false")
                .append("/rollbackClean=false");
        if (optimizerRewriteReviewPackageStatuses != null
                && !optimizerRewriteReviewPackageStatuses.isBlank()
                && !"none".equals(optimizerRewriteReviewPackageStatuses)) {
            builder.append("/reviewPackageStatus=").append(optimizerRewriteReviewPackageStatuses);
        }
        if (optimizerRewriteReviewPackageFirstBlockers != null
                && !optimizerRewriteReviewPackageFirstBlockers.isBlank()
                && !"none".equals(optimizerRewriteReviewPackageFirstBlockers)) {
            builder.append("/reviewPackageFirstBlockers=").append(optimizerRewriteReviewPackageFirstBlockers);
        }
        builder.append("/reviewPackageComplete=false")
                .append("/reviewPackageManualReviewOnly=true");
        return builder.toString();
    }

    private static int parsePositiveInt(String value) {
        try {
            return Math.max(0, Integer.parseInt(value == null ? "0" : value.trim()));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static String firstProperty(Properties properties, String fallback, String... keys) {
        return GpuRuntimeArtifactProperties.first(properties, fallback, keys);
    }
}
