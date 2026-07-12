package net.sixik.ga_utils.javatogpu.runtime;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Command-line entrypoint for CI summaries of production-promotion explainability artifacts. */
public final class GpuProductionPromotionExplainabilitySummaryCli {

    private GpuProductionPromotionExplainabilitySummaryCli() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 1 || args.length > 2 || args[0] == null || args[0].isBlank()) {
            throw new IllegalArgumentException(
                    "Expected path to production-promotion explainability artifact and optional output properties path"
            );
        }
        Path artifactPath = Path.of(args[0]);
        if (!Files.isRegularFile(artifactPath)) {
            throw new IllegalStateException("Missing production-promotion explainability artifact: " + artifactPath);
        }

        Properties properties = new Properties();
        try (InputStream inputStream = Files.newInputStream(artifactPath)) {
            properties.load(inputStream);
        }

        GpuProductionPromotionExplainabilitySummary summary =
                GpuProductionPromotionExplainabilitySummary.fromProperties(properties);
        String formatted = format(summary);
        if (args.length == 2 && args[1] != null && !args[1].isBlank()) {
            Path outputPath = Path.of(args[1]);
            Path parent = outputPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(outputPath, formatted, StandardCharsets.UTF_8);
        }
        System.out.println("Production-promotion explainability summary: " + summary.historyStatus());
    }

    public static String format(GpuProductionPromotionExplainabilitySummary summary) {
        StringBuilder builder = new StringBuilder();
        builder.append("status=").append(summary.status()).append('\n');
        builder.append("contract.status=").append(summary.contractStatus()).append('\n');
        builder.append("contract.valid=").append(summary.contractValid()).append('\n');
        builder.append("contract.violation.0=").append(summary.firstViolation()).append('\n');
        builder.append("decision.mode=").append(summary.decisionMode()).append('\n');
        builder.append("productionSourceSwitchingAllowed=").append(summary.productionSourceSwitchingAllowed()).append('\n');
        builder.append("productionSourceSwitchingEnabled=").append(summary.productionSourceSwitchingEnabled()).append('\n');
        builder.append("productionMutationAllowed=").append(summary.productionMutationAllowed()).append('\n');
        builder.append("productionMutationEnabled=").append(summary.productionMutationEnabled()).append('\n');
        builder.append("blocker.count=").append(summary.blockerCount()).append('\n');
        builder.append("blocker.0=").append(summary.firstBlocker()).append('\n');
        builder.append("i3ReviewReady.count=").append(summary.i3ReviewReadyCount()).append('\n');
        builder.append("i3Blocked.count=").append(summary.i3BlockedCount()).append('\n');
        builder.append("i3SourceReady.count=").append(summary.i3SourceReadyCount()).append('\n');
        builder.append("i3SourceReady.all=").append(summary.i3SourceReadyAll()).append('\n');
        builder.append("optimizerFamily.count=").append(summary.optimizerFamilyCount()).append('\n');
        builder.append("optimizerFamily.promotionReady.count=")
                .append(summary.optimizerFamilyPromotionReadyCount())
                .append('\n');
        builder.append("optimizerFamily.summary=").append(summary.optimizerFamilySummary()).append('\n');
        builder.append("optimizerReplacementPlan.complete.count=")
                .append(summary.optimizerReplacementPlanCompleteCount())
                .append('\n');
        builder.append("optimizerReplacementPlan.partial.count=")
                .append(summary.optimizerReplacementPlanPartialCount())
                .append('\n');
        builder.append("optimizerReplacementPlan.firstBlockers=")
                .append(summary.optimizerReplacementPlanFirstBlockers())
                .append('\n');
        builder.append("optimizerReplacementPlan.validation.count=")
                .append(summary.optimizerReplacementPlanValidationCount())
                .append('\n');
        builder.append("optimizerReplacementPlan.validation.valid.count=")
                .append(summary.optimizerReplacementPlanValidationValidCount())
                .append('\n');
        builder.append("optimizerReplacementPlan.validation.invalid.count=")
                .append(summary.optimizerReplacementPlanValidationInvalidCount())
                .append('\n');
        builder.append("optimizerReplacementPlan.validation.firstBlockers=")
                .append(summary.optimizerReplacementPlanValidationFirstBlockers())
                .append('\n');
        builder.append("optimizerRewriteSketch.count=").append(summary.optimizerRewriteSketchCount()).append('\n');
        builder.append("optimizerRewriteSketch.ready.count=").append(summary.optimizerRewriteSketchReadyCount()).append('\n');
        builder.append("optimizerRewriteSketch.blocked.count=").append(summary.optimizerRewriteSketchBlockedCount()).append('\n');
        builder.append("optimizerRewriteSketch.firstBlockers=")
                .append(summary.optimizerRewriteSketchFirstBlockers())
                .append('\n');
        builder.append("optimizerRewriteSketch.conflict.count=")
                .append(summary.optimizerRewriteSketchConflictCount())
                .append('\n');
        builder.append("optimizerRewriteSketch.conflict.firstBlockers=")
                .append(summary.optimizerRewriteSketchConflictFirstBlockers())
                .append('\n');
        builder.append("optimizerRewriteSketch.conflict.conflictResolutionImplemented=false\n");
        builder.append("optimizerRewriteSketch.conflict.selectionApplied=false\n");
        builder.append("optimizerRewriteSketch.conflict.selectedIrReplacement=false\n");
        builder.append("optimizerRewriteSketch.rewriteBuilderImplemented=false\n");
        builder.append("optimizerRewriteSketch.mutationAllowed=false\n");
        builder.append("optimizerRewriteSketch.selectedIrReplacement=false\n");
        builder.append("optimizerRewriteSelection.statuses=")
                .append(summary.optimizerRewriteSelectionStatuses())
                .append('\n');
        builder.append("optimizerRewriteSelection.firstBlockers=")
                .append(summary.optimizerRewriteSelectionFirstBlockers())
                .append('\n');
        builder.append("optimizerRewriteSelection.rewriteBuilderImplemented=false\n");
        builder.append("optimizerRewriteSelection.conflictResolutionImplemented=false\n");
        builder.append("optimizerRewriteSelection.mutationAllowed=false\n");
        builder.append("optimizerRewriteSelection.selectionApplied=false\n");
        builder.append("optimizerRewriteSelection.selectedIrReplacement=false\n");
        builder.append("optimizerRule.count=").append(summary.optimizerRuleCount()).append('\n');
        builder.append("optimizerRule.summary=").append(summary.optimizerRuleSummary()).append('\n');
        builder.append("optimizerRule.details=").append(summary.optimizerRuleDetails()).append('\n');
        builder.append("optimizerFamilyPayload.complete.count=")
                .append(summary.optimizerFamilyPayloadCompleteCount())
                .append('\n');
        builder.append("optimizerFamilyPayload.complete.all=")
                .append(summary.optimizerFamilyPayloadCompleteAll())
                .append('\n');
        builder.append("optimizerFamily.runtimeEquivalenceHistoryBaselineReady=")
                .append(summary.optimizerFamilyRuntimeEquivalenceHistoryBaselineReady())
                .append('\n');
        builder.append("optimizerFamily.promotionPreflightReady=")
                .append(summary.optimizerFamilyPromotionPreflightReady())
                .append('\n');
        builder.append("backendPromotionArtifactSupport.complete=")
                .append(summary.backendPromotionArtifactSupportComplete())
                .append('\n');
        builder.append("backendPromotionArtifactSupport.missing.count=")
                .append(summary.backendPromotionArtifactSupportMissingCount())
                .append('\n');
        builder.append("controlledProductionSourceSwitching.status=")
                .append(summary.controlledProductionSourceSwitchingStatus())
                .append('\n');
        builder.append("controlledProductionSourceSwitching.kernel.count=")
                .append(summary.controlledProductionSourceSwitchingKernelCount())
                .append('\n');
        builder.append("controlledProductionSourceSwitching.realWorkload.covered.count=")
                .append(summary.controlledProductionSourceSwitchingRealWorkloadCoveredCount())
                .append('\n');
        builder.append("controlledProductionSourceSwitching.realWorkload.total.count=")
                .append(summary.controlledProductionSourceSwitchingRealWorkloadTotalCount())
                .append('\n');
        builder.append("controlledProductionSourceSwitching.realWorkload.uncovered.count=")
                .append(summary.controlledProductionSourceSwitchingRealWorkloadUncoveredCount())
                .append('\n');
        builder.append("controlledProductionSourceSwitching.realWorkload.covered.all=")
                .append(summary.controlledProductionSourceSwitchingRealWorkloadCoveredAll())
                .append('\n');
        builder.append("controlledProductionMutation.status=")
                .append(summary.controlledProductionMutationStatus())
                .append('\n');
        builder.append("controlledProductionMutation.reviewReady=")
                .append(summary.controlledProductionMutationReviewReady())
                .append('\n');
        builder.append("controlledProductionMutation.productionMutation=")
                .append(summary.controlledProductionMutationProductionMutation())
                .append('\n');
        builder.append("controlledProductionMutation.defaultProductionMutation=")
                .append(summary.controlledProductionMutationDefaultProductionMutation())
                .append('\n');
        builder.append("controlledProductionMutation.realWorkload.covered.count=")
                .append(summary.controlledProductionMutationRealWorkloadCoveredCount())
                .append('\n');
        builder.append("controlledProductionMutation.realWorkload.total.count=")
                .append(summary.controlledProductionMutationRealWorkloadTotalCount())
                .append('\n');
        builder.append("controlledProductionMutation.realWorkload.uncovered.count=")
                .append(summary.controlledProductionMutationRealWorkloadUncoveredCount())
                .append('\n');
        builder.append("controlledProductionMutation.realWorkload.covered.all=")
                .append(summary.controlledProductionMutationRealWorkloadCoveredAll())
                .append('\n');
        builder.append("controlledProductionMutation.passed=")
                .append(summary.controlledProductionMutationPassed())
                .append('\n');
        builder.append("controlledProductionActivationTokenSmoke.status=")
                .append(summary.controlledProductionActivationTokenSmokeStatus())
                .append('\n');
        builder.append("controlledProductionActivationTokenSmoke.tokenLoaded=")
                .append(summary.controlledProductionActivationTokenLoaded())
                .append('\n');
        builder.append("controlledProductionActivationTokenSmoke.approvedKernelExecuted=")
                .append(summary.controlledProductionActivationTokenApprovedKernelExecuted())
                .append('\n');
        builder.append("controlledProductionActivationTokenSmoke.realWorkload.covered.count=")
                .append(summary.controlledProductionActivationTokenRealWorkloadCoveredCount())
                .append('\n');
        builder.append("controlledProductionActivationTokenSmoke.realWorkload.total.count=")
                .append(summary.controlledProductionActivationTokenRealWorkloadTotalCount())
                .append('\n');
        builder.append("controlledProductionActivationTokenSmoke.realWorkload.uncovered.count=")
                .append(summary.controlledProductionActivationTokenRealWorkloadUncoveredCount())
                .append('\n');
        builder.append("controlledProductionActivationTokenSmoke.realWorkload.covered.all=")
                .append(summary.controlledProductionActivationTokenRealWorkloadCoveredAll())
                .append('\n');
        builder.append("controlledProductionActivationTokenSmoke.safeDefaults=")
                .append(summary.controlledProductionActivationTokenSafeDefaults())
                .append('\n');
        builder.append("controlledProductionActivationTokenSmoke.passed=")
                .append(summary.controlledProductionActivationTokenSmokePassed())
                .append('\n');
        builder.append("controlledProductionActivationTokenNegative.status=")
                .append(summary.controlledProductionActivationTokenNegativeStatus())
                .append('\n');
        builder.append("controlledProductionActivationTokenNegative.digestMismatchRejected=")
                .append(summary.controlledProductionActivationTokenDigestMismatchRejected())
                .append('\n');
        builder.append("controlledProductionActivationTokenNegative.unapprovedKernelRejected=")
                .append(summary.controlledProductionActivationTokenUnapprovedKernelRejected())
                .append('\n');
        builder.append("controlledProductionActivationTokenNegative.outputUnchanged=")
                .append(summary.controlledProductionActivationTokenNegativeOutputUnchanged())
                .append('\n');
        builder.append("controlledProductionActivationTokenNegative.safeDefaults=")
                .append(summary.controlledProductionActivationTokenNegativeSafeDefaults())
                .append('\n');
        builder.append("controlledProductionActivationTokenNegative.passed=")
                .append(summary.controlledProductionActivationTokenNegativePassed())
                .append('\n');
        builder.append("readinessChecklist.ready.count=").append(summary.readinessChecklistReadyCount()).append('\n');
        builder.append("readinessChecklist.blocked.count=").append(summary.readinessChecklistBlockedCount()).append('\n');
        builder.append("readinessChecklist.ready.all=").append(summary.readinessChecklistReadyAll()).append('\n');
        builder.append("readinessChecklist.firstBlocked=").append(summary.readinessChecklistFirstBlocked()).append('\n');
        builder.append("historyStatus=").append(summary.historyStatus()).append('\n');
        return builder.toString();
    }
}
