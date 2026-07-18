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
                properties.getProperty("controlledProductionSourceSwitching.status", "not-recorded"),
                parsePositiveInt(properties.getProperty("controlledProductionSourceSwitching.kernel.count", "0")),
                parsePositiveInt(properties.getProperty(
                        "controlledProductionSourceSwitching.realWorkload.covered.count",
                        "0"
                )),
                parsePositiveInt(properties.getProperty(
                        "controlledProductionSourceSwitching.realWorkload.total.count",
                        "0"
                )),
                parsePositiveInt(properties.getProperty(
                        "controlledProductionSourceSwitching.realWorkload.uncovered.count",
                        "0"
                )),
                properties.getProperty("controlledProductionSourceSwitching.realWorkload.covered.all", "false"),
                properties.getProperty("controlledProductionMutation.status", "not-recorded"),
                properties.getProperty("controlledProductionMutation.reviewReady", "false"),
                properties.getProperty("controlledProductionMutation.productionMutation", "disabled"),
                properties.getProperty("controlledProductionMutation.defaultProductionMutation", "unknown"),
                parsePositiveInt(properties.getProperty(
                        "controlledProductionMutation.realWorkload.covered.count",
                        "0"
                )),
                parsePositiveInt(properties.getProperty(
                        "controlledProductionMutation.realWorkload.total.count",
                        "0"
                )),
                parsePositiveInt(properties.getProperty(
                        "controlledProductionMutation.realWorkload.uncovered.count",
                        "0"
                )),
                properties.getProperty("controlledProductionMutation.realWorkload.covered.all", "false"),
                properties.getProperty("controlledProductionMutation.passed", "false"),
                properties.getProperty("controlledProductionActivationTokenSmoke.status", "not-recorded"),
                properties.getProperty("controlledProductionActivationTokenSmoke.tokenLoaded", "false"),
                properties.getProperty(
                        "controlledProductionActivationTokenSmoke.approvedKernelExecuted",
                        "false"
                ),
                parsePositiveInt(properties.getProperty(
                        "controlledProductionActivationTokenSmoke.realWorkload.covered.count",
                        "0"
                )),
                parsePositiveInt(properties.getProperty(
                        "controlledProductionActivationTokenSmoke.realWorkload.total.count",
                        "0"
                )),
                parsePositiveInt(properties.getProperty(
                        "controlledProductionActivationTokenSmoke.realWorkload.uncovered.count",
                        "0"
                )),
                properties.getProperty(
                        "controlledProductionActivationTokenSmoke.realWorkload.covered.all",
                        "false"
                ),
                properties.getProperty("controlledProductionActivationTokenSmoke.safeDefaults", "false"),
                properties.getProperty("controlledProductionActivationTokenSmoke.passed", "false"),
                properties.getProperty("controlledProductionActivationTokenNegative.status", "not-recorded"),
                properties.getProperty(
                        "controlledProductionActivationTokenNegative.digestMismatchRejected",
                        "false"
                ),
                properties.getProperty(
                        "controlledProductionActivationTokenNegative.unapprovedKernelRejected",
                        "false"
                ),
                properties.getProperty("controlledProductionActivationTokenNegative.outputUnchanged", "false"),
                properties.getProperty("controlledProductionActivationTokenNegative.safeDefaults", "false"),
                properties.getProperty("controlledProductionActivationTokenNegative.passed", "false"),
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
}
