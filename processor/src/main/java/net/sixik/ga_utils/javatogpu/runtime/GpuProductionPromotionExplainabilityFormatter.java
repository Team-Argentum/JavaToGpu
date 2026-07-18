package net.sixik.ga_utils.javatogpu.runtime;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

/**
 * Formats backend-neutral production-promotion explainability artifacts.
 *
 * <p>The formatter consumes workload source-promotion evidence plus I3 readiness evidence and emits a compact
 * properties artifact that CI, validation history, and future backend implementations can share.</p>
 */
public final class GpuProductionPromotionExplainabilityFormatter {

    private GpuProductionPromotionExplainabilityFormatter() {
    }

    public static String format(Properties workloadGate, Properties i3Summary) {
        return format(workloadGate, i3Summary, completePromotionArtifactSupport());
    }

    public static String format(
            Properties workloadGate,
            Properties i3Summary,
            Properties backendPromotionArtifactSupport
    ) {
        return format(workloadGate, i3Summary, backendPromotionArtifactSupport, new Properties());
    }

    public static String format(
            Properties workloadGate,
            Properties i3Summary,
            Properties backendPromotionArtifactSupport,
            Properties controlledProductionSourceSwitchingValidation
    ) {
        return format(
                workloadGate,
                i3Summary,
                backendPromotionArtifactSupport,
                controlledProductionSourceSwitchingValidation,
                new Properties()
        );
    }

    public static String format(
            Properties workloadGate,
            Properties i3Summary,
            Properties backendPromotionArtifactSupport,
            Properties controlledProductionSourceSwitchingValidation,
            Properties controlledProductionActivationTokenSmoke
    ) {
        return format(
                workloadGate,
                i3Summary,
                backendPromotionArtifactSupport,
                controlledProductionSourceSwitchingValidation,
                new Properties(),
                controlledProductionActivationTokenSmoke,
                new Properties()
        );
    }

    public static String format(
            Properties workloadGate,
            Properties i3Summary,
            Properties backendPromotionArtifactSupport,
            Properties controlledProductionSourceSwitchingValidation,
            Properties controlledProductionActivationTokenSmoke,
            Properties controlledProductionActivationTokenNegative
    ) {
        return format(
                workloadGate,
                i3Summary,
                backendPromotionArtifactSupport,
                controlledProductionSourceSwitchingValidation,
                new Properties(),
                controlledProductionActivationTokenSmoke,
                controlledProductionActivationTokenNegative
        );
    }

    public static String format(
            Properties workloadGate,
            Properties i3Summary,
            Properties backendPromotionArtifactSupport,
            Properties controlledProductionSourceSwitchingValidation,
            Properties controlledProductionMutationValidation,
            Properties controlledProductionActivationTokenSmoke,
            Properties controlledProductionActivationTokenNegative
    ) {
        Properties gate = workloadGate == null ? new Properties() : workloadGate;
        Properties readiness = i3Summary == null ? new Properties() : i3Summary;
        Properties promotionSupport = backendPromotionArtifactSupport == null
                ? new Properties()
                : backendPromotionArtifactSupport;
        Properties controlledSourceSwitching = controlledProductionSourceSwitchingValidation == null
                ? new Properties()
                : controlledProductionSourceSwitchingValidation;
        Properties controlledMutation = controlledProductionMutationValidation == null
                ? new Properties()
                : controlledProductionMutationValidation;
        Properties activationTokenSmoke = controlledProductionActivationTokenSmoke == null
                ? new Properties()
                : controlledProductionActivationTokenSmoke;
        Properties activationTokenNegative = controlledProductionActivationTokenNegative == null
                ? new Properties()
                : controlledProductionActivationTokenNegative;
        boolean backendPromotionArtifactSupportComplete = propertyIsTrue(
                promotionSupport,
                "complete",
                false
        );
        if (gate.isEmpty()) {
            return appendContractFields("status=blocked\n"
                    + "productionSourceSwitchingAllowed=false\n"
                    + "productionMutationAllowed=false\n"
                    + "backendPromotionArtifactSupport.complete=" + backendPromotionArtifactSupportComplete + "\n"
                    + "kernel.count=0\n"
                    + "blocker.count=1\n"
                    + "blocker.0=workload-promotion-gate-not-recorded\n"
                    + "diagnostic.0=production promotion is blocked because workload promotion evidence was not recorded\n");
        }

        int kernelCount = parsePositiveInt(gate.getProperty("kernel.count", "0"));
        boolean gateReviewReady = "true".equals(gate.getProperty("reviewReady", "false"));
        boolean sourceParityMatched = "true".equals(gate.getProperty("sourceParityMatched", "false"));
        boolean runtimeEquivalencePassed = "true".equals(gate.getProperty("runtimeEquivalencePassed", "false"));
        boolean productionSourceSwitchingEnabled = "true".equals(gate.getProperty("productionSourceSwitching", "false"))
                || "enabled".equals(gate.getProperty("productionSourceSwitching", "false"));
        boolean productionMutationEnabled = "true".equals(readiness.getProperty("productionMutationEnabled", "false"));
        int productionSourceSwitchingEnabledCount = parsePositiveInt(
                GpuRuntimeArtifactProperties.first(
                        gate,
                        productionSourceSwitchingEnabled ? Integer.toString(kernelCount) : "0",
                        "runtime.backend.source.productionSwitchingEnabled.count",
                        "productionSourceSwitchingEnabled.count"
                )
        );
        boolean allProductionSourceSwitchingEnabled = propertyIsTrue(
                gate,
                "runtime.backend.source.productionSwitchingEnabled.all",
                "productionSourceSwitchingEnabled.all",
                productionSourceSwitchingEnabled && productionSourceSwitchingEnabledCount == kernelCount
        );
        int productionPromotionDecisionEnabledCount = parsePositiveInt(
                GpuRuntimeArtifactProperties.first(
                        gate,
                        productionSourceSwitchingEnabled ? Integer.toString(kernelCount) : "0",
                        "runtime.backend.source.productionPromotionDecisionMode.productionEnabled.count",
                        "productionPromotionDecisionMode.productionEnabled.count"
                )
        );
        boolean allProductionPromotionDecisionsEnabled = propertyIsTrue(
                gate,
                "runtime.backend.source.productionPromotionDecisionMode.productionEnabled.all",
                "productionPromotionDecisionMode.productionEnabled.all",
                productionPromotionDecisionEnabledCount == kernelCount && productionPromotionDecisionEnabledCount > 0
        );
        int productionPromotionOperatorAcceptedCount = parsePositiveInt(
                GpuRuntimeArtifactProperties.first(
                        gate,
                        "0",
                        "runtime.backend.source.productionPromotionOperatorAccepted.count",
                        "productionPromotionOperatorAccepted.count"
                )
        );
        boolean allProductionPromotionOperatorAccepted = propertyIsTrue(
                gate,
                "runtime.backend.source.productionPromotionOperatorAccepted.all",
                "productionPromotionOperatorAccepted.all",
                productionPromotionOperatorAcceptedCount == kernelCount && productionPromotionOperatorAcceptedCount > 0
        );
        int productionSourceDecisionCount = parsePositiveInt(
                GpuRuntimeArtifactProperties.first(
                        gate,
                        productionSourceSwitchingEnabled ? Integer.toString(kernelCount) : "0",
                        "runtime.backend.source.productionDecision.count",
                        "sourceSwitching.productionDecision.count"
                )
        );
        boolean allProductionSourceDecisions = propertyIsTrue(
                gate,
                "runtime.backend.source.productionDecision.all",
                "sourceSwitching.productionDecision.all",
                productionSourceDecisionCount == kernelCount && productionSourceDecisionCount > 0
        );
        int i3ReviewReadyCount = parsePositiveInt(readiness.getProperty("reviewReady.count", "0"));
        int i3BlockedCount = parsePositiveInt(readiness.getProperty("blocked.count", Integer.toString(kernelCount)));
        int i3SourceReadyCount = parsePositiveInt(readiness.getProperty("sourceReady.count", "0"));
        boolean allKernelsI3ReviewReady = kernelCount > 0 && i3ReviewReadyCount == kernelCount && i3BlockedCount == 0;
        boolean allKernelsSourceReady = kernelCount > 0 && i3SourceReadyCount == kernelCount;
        int optimizerFamilyCount = parsePositiveInt(gate.getProperty(
                "optimizerFamily.count",
                Integer.toString(sumKernelProperty(gate, kernelCount, "runtimeOptimizerDrift.optimizerFamily.count"))
        ));
        int optimizerFamilyPromotionReadyCount = parsePositiveInt(
                gate.getProperty(
                        "optimizerFamily.promotionReady.count",
                        Integer.toString(sumKernelProperty(
                                gate,
                                kernelCount,
                                "runtimeOptimizerDrift.optimizerFamily.promotionReady.count"
                        ))
                )
        );
        String optimizerFamilySummary = gate.getProperty(
                "optimizerFamily.summary",
                summarizeKernelProperty(gate, kernelCount, "runtimeOptimizerDrift.optimizerFamily.summary")
        );
        int optimizerReplacementPlanCompleteCount = parsePositiveInt(gate.getProperty(
                "optimizerReplacementPlan.complete.count",
                Integer.toString(sumKernelProperty(gate, kernelCount, "runtimeOptimizerDrift.replacementPlan.complete.count"))
        ));
        int optimizerReplacementPlanPartialCount = parsePositiveInt(gate.getProperty(
                "optimizerReplacementPlan.partial.count",
                Integer.toString(sumKernelProperty(gate, kernelCount, "runtimeOptimizerDrift.replacementPlan.partial.count"))
        ));
        String optimizerReplacementPlanFirstBlockers = gate.getProperty(
                "optimizerReplacementPlan.firstBlockers",
                summarizeKernelProperty(gate, kernelCount, "runtimeOptimizerDrift.replacementPlan.firstBlocker")
        );
        int optimizerReplacementPlanValidationCount = parsePositiveInt(gate.getProperty(
                "optimizerReplacementPlan.validation.count",
                Integer.toString(sumKernelProperty(gate, kernelCount, "runtimeOptimizerDrift.replacementPlan.validation.count"))
        ));
        int optimizerReplacementPlanValidationValidCount = parsePositiveInt(gate.getProperty(
                "optimizerReplacementPlan.validation.valid.count",
                Integer.toString(sumKernelProperty(gate, kernelCount, "runtimeOptimizerDrift.replacementPlan.validation.valid.count"))
        ));
        int optimizerReplacementPlanValidationInvalidCount = parsePositiveInt(gate.getProperty(
                "optimizerReplacementPlan.validation.invalid.count",
                Integer.toString(sumKernelProperty(gate, kernelCount, "runtimeOptimizerDrift.replacementPlan.validation.invalid.count"))
        ));
        String optimizerReplacementPlanValidationFirstBlockers = gate.getProperty(
                "optimizerReplacementPlan.validation.firstBlockers",
                summarizeKernelProperty(gate, kernelCount, "runtimeOptimizerDrift.replacementPlan.validation.firstBlocker")
        );
        int optimizerRewriteSketchCount = parsePositiveInt(gate.getProperty(
                "optimizerRewriteSketch.count",
                Integer.toString(sumKernelProperty(gate, kernelCount, "runtimeOptimizerDrift.rewriteSketch.count"))
        ));
        int optimizerRewriteSketchReadyCount = parsePositiveInt(gate.getProperty(
                "optimizerRewriteSketch.ready.count",
                Integer.toString(sumKernelProperty(gate, kernelCount, "runtimeOptimizerDrift.rewriteSketch.ready.count"))
        ));
        int optimizerRewriteSketchBlockedCount = parsePositiveInt(gate.getProperty(
                "optimizerRewriteSketch.blocked.count",
                Integer.toString(sumKernelProperty(gate, kernelCount, "runtimeOptimizerDrift.rewriteSketch.blocked.count"))
        ));
        String optimizerRewriteSketchFirstBlockers = gate.getProperty(
                "optimizerRewriteSketch.firstBlockers",
                summarizeKernelProperty(gate, kernelCount, "runtimeOptimizerDrift.rewriteSketch.firstBlocker")
        );
        int optimizerRewriteSketchConflictCount = parsePositiveInt(gate.getProperty(
                "optimizerRewriteSketch.conflict.count",
                Integer.toString(sumKernelProperty(gate, kernelCount, "runtimeOptimizerDrift.rewriteSketch.conflict.count"))
        ));
        String optimizerRewriteSketchConflictFirstBlockers = gate.getProperty(
                "optimizerRewriteSketch.conflict.firstBlockers",
                summarizeKernelProperty(gate, kernelCount, "runtimeOptimizerDrift.rewriteSketch.conflict.firstBlocker")
        );
        String optimizerRewriteSelectionStatuses = gate.getProperty(
                "optimizerRewriteSelection.statuses",
                summarizeRewriteSelectionStatuses(gate, kernelCount)
        );
        String optimizerRewriteSelectionFirstBlockers = gate.getProperty(
                "optimizerRewriteSelection.firstBlockers",
                summarizeRewriteSelectionFirstBlockers(gate, kernelCount)
        );
        String optimizerRewriteProofStatuses = gate.getProperty(
                "optimizerRewriteProof.statuses",
                summarizeRewriteProofStatuses(gate, kernelCount)
        );
        String optimizerRewriteProofFirstBlockers = gate.getProperty(
                "optimizerRewriteProof.firstBlockers",
                summarizeRewriteProofFirstBlockers(gate, kernelCount)
        );
        String optimizerRewriteReviewPackageStatuses = gate.getProperty(
                "optimizerRewriteReviewPackage.statuses",
                summarizeRewriteReviewPackageStatuses(gate, kernelCount)
        );
        String optimizerRewriteReviewPackageFirstBlockers = gate.getProperty(
                "optimizerRewriteReviewPackage.firstBlockers",
                summarizeRewriteReviewPackageFirstBlockers(gate, kernelCount)
        );
        int optimizerRuleCount = parsePositiveInt(gate.getProperty(
                "optimizerRule.count",
                Integer.toString(sumKernelProperty(gate, kernelCount, "runtimeOptimizerDrift.optimizerRule.count"))
        ));
        String optimizerRuleSummary = gate.getProperty(
                "optimizerRule.summary",
                summarizeKernelProperty(gate, kernelCount, "runtimeOptimizerDrift.optimizerRule.summary")
        );
        String optimizerRuleDetails = gate.getProperty(
                "optimizerRule.details",
                summarizeOptimizerRules(gate, kernelCount)
        );
        int optimizerFamilyPayloadCompleteCount = parsePositiveInt(
                gate.getProperty(
                        "optimizerFamilyPayload.complete.count",
                        Integer.toString(sumKernelProperty(
                                gate,
                                kernelCount,
                                "optimizerFamilyPayload.family.complete.count"
                        ))
                )
        );
        String optimizerFamilyPayloadCompleteAll = gate.getProperty(
                "optimizerFamilyPayload.complete.all",
                allKernelBooleanPropertyWhenCountPresent(
                        gate,
                        kernelCount,
                        "optimizerFamilyPayload.family.count",
                        "optimizerFamilyPayload.family.complete.count",
                        "optimizerFamilyPayload.family.complete.all"
                )
        );
        boolean optimizerFamilyRuntimeEquivalenceHistoryBaselineReady = propertyIsTrue(
                gate,
                "optimizerFamily.runtimeEquivalenceHistoryBaselineReady",
                false
        );
        boolean optimizerFamilyPromotionPreflightReady = optimizerFamilyPromotionReadyCount == 0
                || optimizerFamilyRuntimeEquivalenceHistoryBaselineReady;
        String controlledSourceSwitchingStatus = controlledSourceSwitching.getProperty("status", "not-recorded");
        int controlledSourceSwitchingKernelCount = parsePositiveInt(
                controlledSourceSwitching.getProperty("kernel.count", "0")
        );
        ControlledSourceSwitchingCoverage controlledSourceSwitchingCoverage =
                controlledSourceSwitchingCoverage(gate, controlledSourceSwitching);
        ControlledMutationEvidence controlledMutationEvidence = controlledMutationEvidence(
                gate,
                controlledMutation,
                controlledSourceSwitchingCoverage
        );
        boolean activationTokenLoaded = propertyIsTrue(activationTokenSmoke, "token.loaded", false);
        boolean activationTokenSafeDefaults = "false".equals(
                activationTokenSmoke.getProperty("defaultRuntimeActivation", "unknown")
        ) && "disabled".equals(
                activationTokenSmoke.getProperty("defaultProductionSourceSwitching", "unknown")
        ) && "disabled".equals(
                activationTokenSmoke.getProperty("productionMutation", "unknown")
        );
        ControlledSourceSwitchingCoverage activationTokenSmokeCoverage =
                controlledActivationTokenSmokeCoverage(gate, activationTokenSmoke);
        boolean activationTokenApprovedKernelExecuted = !activationTokenSmokeCoverage.coveredResources().isEmpty();
        boolean activationTokenSmokePassed = "passed".equals(activationTokenSmoke.getProperty("status", "not-recorded"))
                && "controlled-production-activation-token-smoke".equals(
                activationTokenSmoke.getProperty("scope", "unknown")
        )
                && activationTokenLoaded
                && activationTokenSafeDefaults
                && activationTokenSmokeCoverage.allCovered()
                && "OPENCL".equals(activationTokenSmoke.getProperty("token.backendTarget", "UNKNOWN"))
                && GpuBackendSourcePromotionActivationGate.ACTIVATION_SCOPE.equals(
                activationTokenSmoke.getProperty("token.activationScope", "unknown")
        );
        boolean activationTokenNegativeSafeDefaults = "false".equals(
                activationTokenNegative.getProperty("defaultRuntimeActivation", "unknown")
        ) && "disabled".equals(
                activationTokenNegative.getProperty("defaultProductionSourceSwitching", "unknown")
        ) && "disabled".equals(
                activationTokenNegative.getProperty("productionMutation", "unknown")
        );
        boolean activationTokenDigestMismatchRejected = propertyIsTrue(
                activationTokenNegative,
                "digestMismatchRejected",
                false
        );
        boolean activationTokenUnapprovedKernelRejected = propertyIsTrue(
                activationTokenNegative,
                "unapprovedKernelRejected",
                false
        );
        boolean activationTokenNegativeOutputUnchanged = propertyIsTrue(
                activationTokenNegative,
                "outputUnchanged",
                false
        );
        boolean activationTokenNegativePassed = "passed".equals(
                activationTokenNegative.getProperty("status", "not-recorded")
        ) && "controlled-production-activation-token-negative".equals(
                activationTokenNegative.getProperty("scope", "unknown")
        )
                && activationTokenDigestMismatchRejected
                && activationTokenUnapprovedKernelRejected
                && activationTokenNegativeOutputUnchanged
                && activationTokenNegativeSafeDefaults
                && propertyIsTrue(activationTokenNegative, "passed", false);

        boolean activationGatedSourceSwitchingEvidence = gateReviewReady
                && sourceParityMatched
                && runtimeEquivalencePassed
                && allKernelsI3ReviewReady
                && allKernelsSourceReady
                && controlledSourceSwitchingCoverage.allCovered()
                && activationTokenSmokePassed
                && activationTokenNegativePassed
                && backendPromotionArtifactSupportComplete
                && optimizerFamilyPromotionPreflightReady;
        boolean effectiveProductionSourceSwitchingEnabled = productionSourceSwitchingEnabled
                || activationGatedSourceSwitchingEvidence;
        int effectiveProductionSourceSwitchingEnabledCount = productionSourceSwitchingEnabledCount;
        if (activationGatedSourceSwitchingEvidence
                && effectiveProductionSourceSwitchingEnabledCount < kernelCount) {
            effectiveProductionSourceSwitchingEnabledCount = kernelCount;
        }
        boolean effectiveAllProductionSourceSwitchingEnabled = allProductionSourceSwitchingEnabled
                || (activationGatedSourceSwitchingEvidence && effectiveProductionSourceSwitchingEnabledCount == kernelCount);
        int effectiveProductionPromotionDecisionEnabledCount = productionPromotionDecisionEnabledCount;
        if (activationGatedSourceSwitchingEvidence
                && effectiveProductionPromotionDecisionEnabledCount < kernelCount) {
            effectiveProductionPromotionDecisionEnabledCount = kernelCount;
        }
        boolean effectiveAllProductionPromotionDecisionsEnabled = allProductionPromotionDecisionsEnabled
                || (activationGatedSourceSwitchingEvidence && effectiveProductionPromotionDecisionEnabledCount == kernelCount);
        int effectiveProductionPromotionOperatorAcceptedCount = productionPromotionOperatorAcceptedCount;
        if (activationGatedSourceSwitchingEvidence
                && effectiveProductionPromotionOperatorAcceptedCount < kernelCount) {
            effectiveProductionPromotionOperatorAcceptedCount = kernelCount;
        }
        boolean effectiveAllProductionPromotionOperatorAccepted = allProductionPromotionOperatorAccepted
                || (activationGatedSourceSwitchingEvidence && effectiveProductionPromotionOperatorAcceptedCount == kernelCount);
        int effectiveProductionSourceDecisionCount = productionSourceDecisionCount;
        if (activationGatedSourceSwitchingEvidence
                && effectiveProductionSourceDecisionCount < kernelCount) {
            effectiveProductionSourceDecisionCount = kernelCount;
        }
        boolean effectiveAllProductionSourceDecisions = allProductionSourceDecisions
                || (activationGatedSourceSwitchingEvidence && effectiveProductionSourceDecisionCount == kernelCount);

        List<ReadinessChecklistItem> readinessChecklist = List.of(
                new ReadinessChecklistItem(
                        "workload-gate-review-ready",
                        gateReviewReady,
                        "real workload source-promotion gate is review-ready",
                        "real workload source-promotion gate is not review-ready"
                ),
                new ReadinessChecklistItem(
                        "source-parity-matched",
                        sourceParityMatched,
                        "generated source and packaged IrGpu source match",
                        "generated source and packaged IrGpu source do not match"
                ),
                new ReadinessChecklistItem(
                        "runtime-equivalence-passed",
                        runtimeEquivalencePassed,
                        "runtime equivalence evidence passed",
                        "runtime equivalence evidence has not passed"
                ),
                new ReadinessChecklistItem(
                        "i3-source-ready",
                        allKernelsSourceReady,
                        "all workload kernels are I3 source-ready",
                        "one or more workload kernels are not I3 source-ready"
                ),
                new ReadinessChecklistItem(
                        "controlled-source-switching-covered",
                        controlledSourceSwitchingCoverage.allCovered(),
                        "controlled production source-switching lane covers all real workload resources",
                        "controlled production source-switching lane does not cover every real workload resource"
                ),
                new ReadinessChecklistItem(
                        "activation-token-hardware-smoke-passed",
                        activationTokenSmokePassed,
                        "controlled activation token loaded its exact artifact and executed an approved hardware workload",
                        "controlled activation-token hardware smoke is missing or incomplete"
                ),
                new ReadinessChecklistItem(
                        "activation-token-negative-controls-passed",
                        activationTokenNegativePassed,
                        "controlled activation-token negative controls reject digest and kernel mismatches before execution",
                        "controlled activation-token negative controls are missing or incomplete"
                ),
                new ReadinessChecklistItem(
                        "promotion-artifacts-complete",
                        backendPromotionArtifactSupportComplete,
                        "backend promotion artifact support is complete",
                        "backend promotion artifact support is incomplete"
                ),
                new ReadinessChecklistItem(
                        "optimizer-family-runtime-equivalence-history-baseline",
                        optimizerFamilyPromotionPreflightReady,
                        "optimizer family promotion candidates have A1/A2 runtime-equivalence history baseline evidence",
                        "optimizer family promotion candidates require A1/A2 runtime-equivalence history baseline evidence"
                ),
                new ReadinessChecklistItem(
                        "production-source-switching-enabled",
                        effectiveProductionSourceSwitchingEnabled
                                && effectiveAllProductionSourceSwitchingEnabled
                                && effectiveAllProductionPromotionDecisionsEnabled
                                && effectiveAllProductionSourceDecisions,
                        "production source switching is enabled for all workload kernels",
                        "production source switching remains disabled or incomplete"
                ),
                new ReadinessChecklistItem(
                        "production-mutation-enabled",
                        productionMutationEnabled || controlledMutationEvidence.passed(),
                        "production mutation is enabled or covered by controlled mutation readiness evidence",
                        "production mutation remains disabled and controlled mutation readiness evidence is incomplete"
                )
        );

        List<String> readinessBlockers = new ArrayList<>();
        if (!gateReviewReady) {
            readinessBlockers.add("workload-source-promotion-gate-not-review-ready");
        }
        if (!sourceParityMatched) {
            readinessBlockers.add("source-parity-not-matched");
        }
        if (!runtimeEquivalencePassed) {
            readinessBlockers.add("runtime-equivalence-not-passed");
        }
        if (!allKernelsI3ReviewReady) {
            readinessBlockers.add("i3-workload-readiness-not-review-ready");
        }
        if (!allKernelsSourceReady) {
            readinessBlockers.add("i3-source-readiness-not-complete");
        }
        if (!effectiveProductionSourceSwitchingEnabled) {
            readinessBlockers.add("production-source-switching-disabled");
        }
        if (!effectiveAllProductionSourceSwitchingEnabled) {
            readinessBlockers.add("production-source-switching-not-enabled-for-all-kernels");
        }
        if (!effectiveAllProductionPromotionDecisionsEnabled) {
            readinessBlockers.add("production-promotion-decision-not-enabled-for-all-kernels");
        }
        if (!effectiveAllProductionSourceDecisions) {
            readinessBlockers.add("production-source-decision-not-compiled-for-all-kernels");
        }
        if (!backendPromotionArtifactSupportComplete) {
            readinessBlockers.add("backend-promotion-artifact-support-incomplete");
        }
        if (!optimizerFamilyPromotionPreflightReady) {
            readinessBlockers.add("optimizer-family-runtime-equivalence-history-baseline-missing");
        }
        boolean sourceSwitchingAllowed = readinessBlockers.isEmpty();
        boolean effectiveProductionMutationEnabled = (productionMutationEnabled || controlledMutationEvidence.passed())
                && sourceSwitchingAllowed;

        List<String> blockers = new ArrayList<>(readinessBlockers);
        if (!effectiveProductionMutationEnabled) {
            blockers.add("production-mutation-disabled");
        }

        StringBuilder builder = new StringBuilder();
        builder.append("status=").append(blockers.isEmpty() ? "production-ready" : "blocked").append('\n');
        builder.append("gateStatus=").append(gate.getProperty("status", "unknown")).append('\n');
        builder.append("gateReviewReady=").append(gateReviewReady).append('\n');
        builder.append("sourceParityMatched=").append(sourceParityMatched).append('\n');
        builder.append("runtimeEquivalencePassed=").append(runtimeEquivalencePassed).append('\n');
        builder.append("realWorkloadEvidence=").append(gate.getProperty("realWorkloadEvidence", "not-wired")).append('\n');
        builder.append("kernel.count=").append(kernelCount).append('\n');
        builder.append("i3ReviewReady.count=").append(i3ReviewReadyCount).append('\n');
        builder.append("i3Blocked.count=").append(i3BlockedCount).append('\n');
        builder.append("i3SourceReady.count=").append(i3SourceReadyCount).append('\n');
        builder.append("i3SourceReady.all=").append(allKernelsSourceReady).append('\n');
        builder.append("optimizerFamily.count=").append(optimizerFamilyCount).append('\n');
        builder.append("optimizerFamily.promotionReady.count=").append(optimizerFamilyPromotionReadyCount).append('\n');
        builder.append("optimizerFamily.summary=").append(optimizerFamilySummary).append('\n');
        builder.append("optimizerReplacementPlan.complete.count=").append(optimizerReplacementPlanCompleteCount).append('\n');
        builder.append("optimizerReplacementPlan.partial.count=").append(optimizerReplacementPlanPartialCount).append('\n');
        builder.append("optimizerReplacementPlan.firstBlockers=").append(optimizerReplacementPlanFirstBlockers).append('\n');
        builder.append("optimizerReplacementPlan.validation.count=").append(optimizerReplacementPlanValidationCount).append('\n');
        builder.append("optimizerReplacementPlan.validation.valid.count=").append(optimizerReplacementPlanValidationValidCount).append('\n');
        builder.append("optimizerReplacementPlan.validation.invalid.count=").append(optimizerReplacementPlanValidationInvalidCount).append('\n');
        builder.append("optimizerReplacementPlan.validation.firstBlockers=").append(optimizerReplacementPlanValidationFirstBlockers).append('\n');
        builder.append("optimizerRewriteSketch.count=").append(optimizerRewriteSketchCount).append('\n');
        builder.append("optimizerRewriteSketch.ready.count=").append(optimizerRewriteSketchReadyCount).append('\n');
        builder.append("optimizerRewriteSketch.blocked.count=").append(optimizerRewriteSketchBlockedCount).append('\n');
        builder.append("optimizerRewriteSketch.firstBlockers=").append(optimizerRewriteSketchFirstBlockers).append('\n');
        builder.append("optimizerRewriteSketch.conflict.count=").append(optimizerRewriteSketchConflictCount).append('\n');
        builder.append("optimizerRewriteSketch.conflict.firstBlockers=").append(optimizerRewriteSketchConflictFirstBlockers).append('\n');
        builder.append("optimizerRewriteSketch.conflict.conflictResolutionImplemented=false\n");
        builder.append("optimizerRewriteSketch.conflict.selectionApplied=false\n");
        builder.append("optimizerRewriteSketch.conflict.selectedIrReplacement=false\n");
        builder.append("optimizerRewriteSketch.rewriteBuilderImplemented=false\n");
        builder.append("optimizerRewriteSketch.mutationAllowed=false\n");
        builder.append("optimizerRewriteSketch.selectedIrReplacement=false\n");
        builder.append("optimizerRewriteSelection.statuses=").append(optimizerRewriteSelectionStatuses).append('\n');
        builder.append("optimizerRewriteSelection.firstBlockers=").append(optimizerRewriteSelectionFirstBlockers).append('\n');
        builder.append("optimizerRewriteSelection.rewriteBuilderImplemented=false\n");
        builder.append("optimizerRewriteSelection.conflictResolutionImplemented=false\n");
        builder.append("optimizerRewriteSelection.mutationAllowed=false\n");
        builder.append("optimizerRewriteSelection.selectionApplied=false\n");
        builder.append("optimizerRewriteSelection.selectedIrReplacement=false\n");
        builder.append("optimizerRewriteProof.statuses=").append(optimizerRewriteProofStatuses).append('\n');
        builder.append("optimizerRewriteProof.firstBlockers=").append(optimizerRewriteProofFirstBlockers).append('\n');
        builder.append("optimizerRewriteProof.proofAccepted=false\n");
        builder.append("optimizerRewriteProof.runtimeEquivalencePayload.complete=false\n");
        builder.append("optimizerRewriteProof.rollbackClean=false\n");
        builder.append("optimizerRewriteProof.approvalAccepted=false\n");
        builder.append("optimizerRewriteProof.mutationAllowed=false\n");
        builder.append("optimizerRewriteProof.selectedIrReplacement=false\n");
        builder.append("optimizerRewriteReviewPackage.statuses=").append(optimizerRewriteReviewPackageStatuses).append('\n');
        builder.append("optimizerRewriteReviewPackage.firstBlockers=").append(optimizerRewriteReviewPackageFirstBlockers).append('\n');
        builder.append("optimizerRewriteReviewPackage.complete=false\n");
        builder.append("optimizerRewriteReviewPackage.proofAccepted=false\n");
        builder.append("optimizerRewriteReviewPackage.runtimeEquivalencePayload.complete=false\n");
        builder.append("optimizerRewriteReviewPackage.rollbackClean=false\n");
        builder.append("optimizerRewriteReviewPackage.approvalAccepted=false\n");
        builder.append("optimizerRewriteReviewPackage.mutationAllowed=false\n");
        builder.append("optimizerRewriteReviewPackage.selectionApplied=false\n");
        builder.append("optimizerRewriteReviewPackage.selectedIrReplacement=false\n");
        builder.append("optimizerRewriteReviewPackage.manualReviewOnly=true\n");
        builder.append("optimizerRule.count=").append(optimizerRuleCount).append('\n');
        builder.append("optimizerRule.summary=").append(optimizerRuleSummary).append('\n');
        builder.append("optimizerRule.details=").append(optimizerRuleDetails).append('\n');
        builder.append("optimizerFamilyPayload.complete.count=").append(optimizerFamilyPayloadCompleteCount).append('\n');
        builder.append("optimizerFamilyPayload.complete.all=").append(optimizerFamilyPayloadCompleteAll).append('\n');
        builder.append("optimizerFamily.runtimeEquivalenceHistoryBaselineReady=")
                .append(optimizerFamilyRuntimeEquivalenceHistoryBaselineReady)
                .append('\n');
        builder.append("optimizerFamily.promotionPreflightReady=")
                .append(optimizerFamilyPromotionPreflightReady)
                .append('\n');
        builder.append("productionSourceSwitchingAllowed=").append(sourceSwitchingAllowed).append('\n');
        builder.append("productionSourceSwitchingEnabled=").append(effectiveProductionSourceSwitchingEnabled).append('\n');
        builder.append("runtime.backend.source.productionSwitchingEnabled.count=").append(effectiveProductionSourceSwitchingEnabledCount).append('\n');
        builder.append("runtime.backend.source.productionSwitchingEnabled.all=").append(effectiveAllProductionSourceSwitchingEnabled).append('\n');
        builder.append("productionSourceSwitchingEnabled.count=").append(effectiveProductionSourceSwitchingEnabledCount).append('\n');
        builder.append("productionSourceSwitchingEnabled.all=").append(effectiveAllProductionSourceSwitchingEnabled).append('\n');
        builder.append("runtime.backend.source.productionPromotionDecisionMode.productionEnabled.count=").append(effectiveProductionPromotionDecisionEnabledCount).append('\n');
        builder.append("runtime.backend.source.productionPromotionDecisionMode.productionEnabled.all=").append(effectiveAllProductionPromotionDecisionsEnabled).append('\n');
        builder.append("productionPromotionDecisionMode.productionEnabled.count=").append(effectiveProductionPromotionDecisionEnabledCount).append('\n');
        builder.append("productionPromotionDecisionMode.productionEnabled.all=").append(effectiveAllProductionPromotionDecisionsEnabled).append('\n');
        builder.append("runtime.backend.source.productionPromotionOperatorAccepted.count=").append(effectiveProductionPromotionOperatorAcceptedCount).append('\n');
        builder.append("runtime.backend.source.productionPromotionOperatorAccepted.all=").append(effectiveAllProductionPromotionOperatorAccepted).append('\n');
        builder.append("productionPromotionOperatorAccepted.count=").append(effectiveProductionPromotionOperatorAcceptedCount).append('\n');
        builder.append("productionPromotionOperatorAccepted.all=").append(effectiveAllProductionPromotionOperatorAccepted).append('\n');
        builder.append("runtime.backend.source.productionDecision.count=").append(effectiveProductionSourceDecisionCount).append('\n');
        builder.append("runtime.backend.source.productionDecision.all=").append(effectiveAllProductionSourceDecisions).append('\n');
        builder.append("sourceSwitching.productionDecision.count=").append(effectiveProductionSourceDecisionCount).append('\n');
        builder.append("sourceSwitching.productionDecision.all=").append(effectiveAllProductionSourceDecisions).append('\n');
        builder.append("productionMutationAllowed=").append(effectiveProductionMutationEnabled && blockers.isEmpty()).append('\n');
        builder.append("productionMutationEnabled=").append(effectiveProductionMutationEnabled).append('\n');
        builder.append("backendPromotionArtifactSupport.complete=").append(backendPromotionArtifactSupportComplete).append('\n');
        builder.append("backendPromotionArtifactSupport.supported.count=").append(parsePositiveInt(
                promotionSupport.getProperty("supported.count", "0")
        )).append('\n');
        builder.append("backendPromotionArtifactSupport.missing.count=").append(parsePositiveInt(
                promotionSupport.getProperty("missing.count", "0")
        )).append('\n');
        appendControlledProductionSourceSwitchingFields(
                builder,
                "runtime.production.sourceSwitching.controlled",
                controlledSourceSwitching,
                controlledSourceSwitchingStatus,
                controlledSourceSwitchingKernelCount,
                controlledSourceSwitchingCoverage
        );
        appendControlledProductionSourceSwitchingFields(
                builder,
                "controlledProductionSourceSwitching",
                controlledSourceSwitching,
                controlledSourceSwitchingStatus,
                controlledSourceSwitchingKernelCount,
                controlledSourceSwitchingCoverage
        );
        appendControlledProductionMutationFields(
                builder,
                "runtime.production.mutation.controlled",
                controlledMutation,
                controlledMutationEvidence
        );
        appendControlledProductionMutationFields(
                builder,
                "controlledProductionMutation",
                controlledMutation,
                controlledMutationEvidence
        );
        appendActivationTokenSmokeFields(
                builder,
                "runtime.production.activationToken.smoke",
                activationTokenSmoke,
                activationTokenLoaded,
                activationTokenApprovedKernelExecuted,
                activationTokenSmokeCoverage,
                activationTokenSafeDefaults,
                activationTokenSmokePassed
        );
        appendActivationTokenSmokeFields(
                builder,
                "controlledProductionActivationTokenSmoke",
                activationTokenSmoke,
                activationTokenLoaded,
                activationTokenApprovedKernelExecuted,
                activationTokenSmokeCoverage,
                activationTokenSafeDefaults,
                activationTokenSmokePassed
        );
        appendActivationTokenNegativeFields(
                builder,
                "runtime.production.activationToken.negative",
                activationTokenNegative,
                activationTokenDigestMismatchRejected,
                activationTokenUnapprovedKernelRejected,
                activationTokenNegativeOutputUnchanged,
                activationTokenNegativeSafeDefaults,
                activationTokenNegativePassed
        );
        appendActivationTokenNegativeFields(
                builder,
                "controlledProductionActivationTokenNegative",
                activationTokenNegative,
                activationTokenDigestMismatchRejected,
                activationTokenUnapprovedKernelRejected,
                activationTokenNegativeOutputUnchanged,
                activationTokenNegativeSafeDefaults,
                activationTokenNegativePassed
        );
        appendReadinessChecklist(builder, readinessChecklist);
        builder.append("blocker.count=").append(blockers.size()).append('\n');
        for (int index = 0; index < blockers.size(); index++) {
            builder.append("blocker.").append(index).append('=').append(blockers.get(index)).append('\n');
        }
        builder.append("diagnostic.0=").append(diagnostic(blockers, i3ReviewReadyCount, i3BlockedCount)).append('\n');
        return appendContractFields(builder.toString());
    }

    private static ControlledSourceSwitchingCoverage controlledActivationTokenSmokeCoverage(
            Properties workloadGate,
            Properties activationTokenSmoke
    ) {
        List<String> realWorkloadResources = resources(workloadGate, "sourceKernelResource");
        Set<String> passedResources = new LinkedHashSet<>();
        int kernelCount = parsePositiveInt(activationTokenSmoke.getProperty("kernel.count", "0"));
        for (int index = 0; index < kernelCount; index++) {
            String prefix = "kernel." + index + ".";
            if ("passed".equals(activationTokenSmoke.getProperty(prefix + "status"))) {
                passedResources.add(activationTokenSmoke.getProperty(prefix + "resource", "unknown"));
            }
        }
        List<String> coveredResources = new ArrayList<>();
        List<String> uncoveredResources = new ArrayList<>();
        for (String resource : realWorkloadResources) {
            if (passedResources.contains(resource)) {
                coveredResources.add(resource);
            } else {
                uncoveredResources.add(resource);
            }
        }
        return new ControlledSourceSwitchingCoverage(realWorkloadResources, coveredResources, uncoveredResources);
    }

    private static ControlledSourceSwitchingCoverage controlledSourceSwitchingCoverage(
            Properties workloadGate,
            Properties controlledSourceSwitching
    ) {
        List<String> realWorkloadResources = resources(workloadGate, "sourceKernelResource");
        Set<String> controlledResources = new LinkedHashSet<>(resources(controlledSourceSwitching, "resource"));
        List<String> coveredResources = new ArrayList<>();
        List<String> uncoveredResources = new ArrayList<>();
        for (String resource : realWorkloadResources) {
            if (controlledResources.contains(resource)) {
                coveredResources.add(resource);
            } else {
                uncoveredResources.add(resource);
            }
        }
        return new ControlledSourceSwitchingCoverage(realWorkloadResources, coveredResources, uncoveredResources);
    }

    private static ControlledMutationEvidence controlledMutationEvidence(
            Properties workloadGate,
            Properties controlledMutation,
            ControlledSourceSwitchingCoverage controlledSourceSwitchingCoverage
    ) {
        ControlledSourceSwitchingCoverage coverage = controlledMutationCoverage(workloadGate, controlledMutation);
        boolean reviewReady = propertyIsTrue(controlledMutation, "reviewReady", false);
        boolean passed = "passed".equals(controlledMutation.getProperty("status", "not-recorded"))
                && "controlled-production-mutation-readiness".equals(controlledMutation.getProperty("scope", "unknown"))
                && reviewReady
                && "enabled".equals(controlledMutation.getProperty("productionMutation", "disabled"))
                && "disabled".equals(controlledMutation.getProperty("defaultProductionMutation", "unknown"))
                && "enabled".equals(controlledMutation.getProperty("productionSourceSwitching", "disabled"))
                && propertyIsTrue(controlledMutation, "controlledSourceSwitchingPassed", false)
                && controlledSourceSwitchingCoverage.allCovered()
                && coverage.allCovered();
        return new ControlledMutationEvidence(reviewReady, coverage, passed);
    }

    private static ControlledSourceSwitchingCoverage controlledMutationCoverage(
            Properties workloadGate,
            Properties controlledMutation
    ) {
        List<String> realWorkloadResources = resources(workloadGate, "sourceKernelResource");
        int coveredCount = parsePositiveInt(controlledMutation.getProperty("realWorkload.covered.count", "0"));
        int totalCount = parsePositiveInt(controlledMutation.getProperty("realWorkload.total.count", "0"));
        int uncoveredCount = parsePositiveInt(controlledMutation.getProperty("realWorkload.uncovered.count", "0"));
        boolean reportedAllCovered = propertyIsTrue(controlledMutation, "realWorkload.covered.all", false);
        if (realWorkloadResources.isEmpty()) {
            return new ControlledSourceSwitchingCoverage(List.of(), List.of(), List.of());
        }
        if (reportedAllCovered
                && coveredCount == realWorkloadResources.size()
                && totalCount == realWorkloadResources.size()
                && uncoveredCount == 0) {
            return new ControlledSourceSwitchingCoverage(realWorkloadResources, realWorkloadResources, List.of());
        }
        return new ControlledSourceSwitchingCoverage(realWorkloadResources, List.of(), realWorkloadResources);
    }

    private static List<String> resources(Properties properties, String suffix) {
        int kernelCount = parsePositiveInt(properties.getProperty("kernel.count", "0"));
        List<String> resources = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (int index = 0; index < kernelCount; index++) {
            String resource = properties.getProperty("kernel." + index + "." + suffix, "").trim();
            if (!resource.isBlank() && seen.add(resource)) {
                resources.add(resource);
            }
        }
        return resources;
    }

    private static void appendIndexedResources(StringBuilder builder, String prefix, List<String> resources) {
        for (int index = 0; index < resources.size(); index++) {
            builder.append(prefix).append('.').append(index).append(".resource=")
                    .append(resources.get(index))
                    .append('\n');
        }
    }

    private static void appendControlledProductionSourceSwitchingFields(
            StringBuilder builder,
            String prefix,
            Properties controlledSourceSwitching,
            String controlledSourceSwitchingStatus,
            int controlledSourceSwitchingKernelCount,
            ControlledSourceSwitchingCoverage controlledSourceSwitchingCoverage
    ) {
        builder.append(prefix).append(".status=")
                .append(controlledSourceSwitchingStatus)
                .append('\n');
        builder.append(prefix).append(".kernel.count=")
                .append(controlledSourceSwitchingKernelCount)
                .append('\n');
        builder.append(prefix).append(".reviewReady=")
                .append(controlledSourceSwitching.getProperty("reviewReady", "false"))
                .append('\n');
        builder.append(prefix).append(".productionSourceSwitching=")
                .append(controlledSourceSwitching.getProperty("productionSourceSwitching", "unknown"))
                .append('\n');
        builder.append(prefix).append(".productionPromotionDecisionMode=")
                .append(controlledSourceSwitching.getProperty(
                        "productionPromotionDecisionMode",
                        GpuProductionPromotionDecision.DIAGNOSTIC_ONLY
                ))
                .append('\n');
        builder.append(prefix).append(".realWorkload.covered.count=")
                .append(controlledSourceSwitchingCoverage.coveredResources().size())
                .append('\n');
        builder.append(prefix).append(".realWorkload.total.count=")
                .append(controlledSourceSwitchingCoverage.realWorkloadResources().size())
                .append('\n');
        builder.append(prefix).append(".realWorkload.uncovered.count=")
                .append(controlledSourceSwitchingCoverage.uncoveredResources().size())
                .append('\n');
        builder.append(prefix).append(".realWorkload.covered.all=")
                .append(controlledSourceSwitchingCoverage.allCovered())
                .append('\n');
        appendIndexedResources(builder, prefix + ".realWorkload.covered",
                controlledSourceSwitchingCoverage.coveredResources());
        appendIndexedResources(builder, prefix + ".realWorkload.uncovered",
                controlledSourceSwitchingCoverage.uncoveredResources());
    }

    private static void appendControlledProductionMutationFields(
            StringBuilder builder,
            String prefix,
            Properties controlledMutation,
            ControlledMutationEvidence controlledMutationEvidence
    ) {
        builder.append(prefix).append(".status=")
                .append(controlledMutation.getProperty("status", "not-recorded"))
                .append('\n');
        builder.append(prefix).append(".scope=")
                .append(controlledMutation.getProperty("scope", "unknown"))
                .append('\n');
        builder.append(prefix).append(".reviewReady=")
                .append(controlledMutationEvidence.reviewReady())
                .append('\n');
        builder.append(prefix).append(".productionMutation=")
                .append(controlledMutation.getProperty("productionMutation", "disabled"))
                .append('\n');
        builder.append(prefix).append(".defaultProductionMutation=")
                .append(controlledMutation.getProperty("defaultProductionMutation", "unknown"))
                .append('\n');
        builder.append(prefix).append(".realWorkload.covered.count=")
                .append(controlledMutationEvidence.coverage().coveredResources().size())
                .append('\n');
        builder.append(prefix).append(".realWorkload.total.count=")
                .append(controlledMutationEvidence.coverage().realWorkloadResources().size())
                .append('\n');
        builder.append(prefix).append(".realWorkload.uncovered.count=")
                .append(controlledMutationEvidence.coverage().uncoveredResources().size())
                .append('\n');
        builder.append(prefix).append(".realWorkload.covered.all=")
                .append(controlledMutationEvidence.coverage().allCovered())
                .append('\n');
        builder.append(prefix).append(".passed=")
                .append(controlledMutationEvidence.passed())
                .append('\n');
        appendIndexedResources(builder, prefix + ".realWorkload.covered",
                controlledMutationEvidence.coverage().coveredResources());
        appendIndexedResources(builder, prefix + ".realWorkload.uncovered",
                controlledMutationEvidence.coverage().uncoveredResources());
    }

    private static void appendActivationTokenSmokeFields(
            StringBuilder builder,
            String prefix,
            Properties activationTokenSmoke,
            boolean activationTokenLoaded,
            boolean activationTokenApprovedKernelExecuted,
            ControlledSourceSwitchingCoverage activationTokenSmokeCoverage,
            boolean activationTokenSafeDefaults,
            boolean activationTokenSmokePassed
    ) {
        builder.append(prefix).append(".status=")
                .append(activationTokenSmoke.getProperty("status", "not-recorded"))
                .append('\n');
        builder.append(prefix).append(".scope=")
                .append(activationTokenSmoke.getProperty("scope", "unknown"))
                .append('\n');
        builder.append(prefix).append(".tokenLoaded=")
                .append(activationTokenLoaded)
                .append('\n');
        builder.append(prefix).append(".artifactSha256=")
                .append(activationTokenSmoke.getProperty("token.artifactSha256", "missing"))
                .append('\n');
        builder.append(prefix).append(".approvalId=")
                .append(activationTokenSmoke.getProperty("token.approvalId", "approval:missing"))
                .append('\n');
        builder.append(prefix).append(".candidateGitSha=")
                .append(activationTokenSmoke.getProperty("token.candidateGitSha", "unknown"))
                .append('\n');
        builder.append(prefix).append(".deviceVendor=")
                .append(activationTokenSmoke.getProperty("token.deviceVendor", "unknown"))
                .append('\n');
        builder.append(prefix).append(".deviceLabel=")
                .append(activationTokenSmoke.getProperty("token.deviceLabel", "unknown"))
                .append('\n');
        builder.append(prefix).append(".driverVersion=")
                .append(activationTokenSmoke.getProperty("token.driverVersion", "unknown"))
                .append('\n');
        builder.append(prefix).append(".kernel.count=")
                .append(parsePositiveInt(activationTokenSmoke.getProperty("kernel.count", "0")))
                .append('\n');
        builder.append(prefix).append(".approvedKernelExecuted=")
                .append(activationTokenApprovedKernelExecuted)
                .append('\n');
        builder.append(prefix).append(".realWorkload.covered.count=")
                .append(activationTokenSmokeCoverage.coveredResources().size())
                .append('\n');
        builder.append(prefix).append(".realWorkload.total.count=")
                .append(activationTokenSmokeCoverage.realWorkloadResources().size())
                .append('\n');
        builder.append(prefix).append(".realWorkload.uncovered.count=")
                .append(activationTokenSmokeCoverage.uncoveredResources().size())
                .append('\n');
        builder.append(prefix).append(".realWorkload.covered.all=")
                .append(activationTokenSmokeCoverage.allCovered())
                .append('\n');
        appendIndexedResources(builder, prefix + ".realWorkload.covered",
                activationTokenSmokeCoverage.coveredResources());
        appendIndexedResources(builder, prefix + ".realWorkload.uncovered",
                activationTokenSmokeCoverage.uncoveredResources());
        builder.append(prefix).append(".safeDefaults=")
                .append(activationTokenSafeDefaults)
                .append('\n');
        builder.append(prefix).append(".passed=")
                .append(activationTokenSmokePassed)
                .append('\n');
    }

    private static void appendActivationTokenNegativeFields(
            StringBuilder builder,
            String prefix,
            Properties activationTokenNegative,
            boolean activationTokenDigestMismatchRejected,
            boolean activationTokenUnapprovedKernelRejected,
            boolean activationTokenNegativeOutputUnchanged,
            boolean activationTokenNegativeSafeDefaults,
            boolean activationTokenNegativePassed
    ) {
        builder.append(prefix).append(".status=")
                .append(activationTokenNegative.getProperty("status", "not-recorded"))
                .append('\n');
        builder.append(prefix).append(".scope=")
                .append(activationTokenNegative.getProperty("scope", "unknown"))
                .append('\n');
        builder.append(prefix).append(".digestMismatchRejected=")
                .append(activationTokenDigestMismatchRejected)
                .append('\n');
        builder.append(prefix).append(".unapprovedKernelRejected=")
                .append(activationTokenUnapprovedKernelRejected)
                .append('\n');
        builder.append(prefix).append(".outputUnchanged=")
                .append(activationTokenNegativeOutputUnchanged)
                .append('\n');
        builder.append(prefix).append(".safeDefaults=")
                .append(activationTokenNegativeSafeDefaults)
                .append('\n');
        builder.append(prefix).append(".passed=")
                .append(activationTokenNegativePassed)
                .append('\n');
    }

    private static int sumKernelProperty(Properties properties, int kernelCount, String suffix) {
        int sum = 0;
        for (int index = 0; index < kernelCount; index++) {
            sum += parsePositiveInt(properties.getProperty("kernel." + index + "." + suffix, "0"));
        }
        return sum;
    }

    private static String allKernelBooleanPropertyWhenCountPresent(
            Properties properties,
            int kernelCount,
            String countSuffix,
            String fallbackCountSuffix,
            String booleanSuffix
    ) {
        if (kernelCount <= 0) {
            return "false";
        }
        boolean present = false;
        for (int index = 0; index < kernelCount; index++) {
            String prefix = "kernel." + index + ".";
            int count = parsePositiveInt(properties.getProperty(prefix + countSuffix, "0"));
            int fallbackCount = parsePositiveInt(properties.getProperty(prefix + fallbackCountSuffix, "0"));
            if (count == 0 && fallbackCount == 0) {
                continue;
            }
            String value = properties.getProperty(prefix + booleanSuffix, "");
            if (value.isBlank()) {
                return "false";
            }
            present = true;
            if (!"true".equals(value)) {
                return "false";
            }
        }
        return Boolean.toString(present);
    }

    private static String summarizeKernelProperty(Properties properties, int kernelCount, String suffix) {
        if (kernelCount <= 0) {
            return "none";
        }
        java.util.LinkedHashMap<String, Integer> counts = new java.util.LinkedHashMap<>();
        for (int index = 0; index < kernelCount; index++) {
            String value = properties.getProperty("kernel." + index + "." + suffix, "none");
            if (!value.isBlank() && !"none".equals(value)) {
                counts.merge(value, 1, Integer::sum);
            }
        }
        if (counts.isEmpty()) {
            return "none";
        }
        StringBuilder builder = new StringBuilder();
        for (java.util.Map.Entry<String, Integer> entry : counts.entrySet()) {
            if (!builder.isEmpty()) {
                builder.append(", ");
            }
            builder.append(entry.getKey()).append(" x").append(entry.getValue());
        }
        return builder.toString();
    }

    private static String summarizeRewriteSelectionStatuses(Properties properties, int kernelCount) {
        if (kernelCount <= 0) {
            return "none";
        }
        java.util.LinkedHashMap<String, Integer> counts = new java.util.LinkedHashMap<>();
        for (int index = 0; index < kernelCount; index++) {
            String value = properties.getProperty(
                    "kernel." + index + ".runtimeOptimizerDrift.rewriteSelection.status",
                    "not-required"
            );
            if (!value.isBlank() && !"unknown".equals(value) && !"not-required".equals(value)) {
                counts.merge(value, 1, Integer::sum);
            }
        }
        return summarizeCounts(counts);
    }

    private static String summarizeRewriteSelectionFirstBlockers(Properties properties, int kernelCount) {
        if (kernelCount <= 0) {
            return "none";
        }
        java.util.LinkedHashMap<String, Integer> counts = new java.util.LinkedHashMap<>();
        for (int index = 0; index < kernelCount; index++) {
            String value = properties.getProperty(
                    "kernel." + index + ".runtimeOptimizerDrift.rewriteSelection.firstBlocker",
                    "no-rewrite-sketches"
            );
            if (!value.isBlank()
                    && !"none".equals(value)
                    && !"unknown".equals(value)
                    && !"no-rewrite-sketches".equals(value)) {
                counts.merge(value, 1, Integer::sum);
            }
        }
        return summarizeCounts(counts);
    }

    private static String summarizeRewriteProofStatuses(Properties properties, int kernelCount) {
        if (kernelCount <= 0) {
            return "none";
        }
        java.util.LinkedHashMap<String, Integer> counts = new java.util.LinkedHashMap<>();
        for (int index = 0; index < kernelCount; index++) {
            String value = properties.getProperty(
                    "kernel." + index + ".runtimeOptimizerDrift.rewriteProof.status",
                    "not-required"
            );
            if (!value.isBlank() && !"unknown".equals(value) && !"not-required".equals(value)) {
                counts.merge(value, 1, Integer::sum);
            }
        }
        return summarizeCounts(counts);
    }

    private static String summarizeRewriteProofFirstBlockers(Properties properties, int kernelCount) {
        if (kernelCount <= 0) {
            return "none";
        }
        java.util.LinkedHashMap<String, Integer> counts = new java.util.LinkedHashMap<>();
        for (int index = 0; index < kernelCount; index++) {
            String value = properties.getProperty(
                    "kernel." + index + ".runtimeOptimizerDrift.rewriteProof.firstBlocker",
                    "no-proof-candidates"
            );
            if (!value.isBlank()
                    && !"none".equals(value)
                    && !"unknown".equals(value)
                    && !"no-proof-candidates".equals(value)) {
                counts.merge(value, 1, Integer::sum);
            }
        }
        return summarizeCounts(counts);
    }

    private static String summarizeRewriteReviewPackageStatuses(Properties properties, int kernelCount) {
        if (kernelCount <= 0) {
            return "none";
        }
        java.util.LinkedHashMap<String, Integer> counts = new java.util.LinkedHashMap<>();
        for (int index = 0; index < kernelCount; index++) {
            String value = properties.getProperty(
                    "kernel." + index + ".runtimeOptimizerDrift.rewriteReviewPackage.status",
                    "not-required"
            );
            if (!value.isBlank() && !"unknown".equals(value) && !"not-required".equals(value)) {
                counts.merge(value, 1, Integer::sum);
            }
        }
        return summarizeCounts(counts);
    }

    private static String summarizeRewriteReviewPackageFirstBlockers(Properties properties, int kernelCount) {
        if (kernelCount <= 0) {
            return "none";
        }
        java.util.LinkedHashMap<String, Integer> counts = new java.util.LinkedHashMap<>();
        for (int index = 0; index < kernelCount; index++) {
            String value = properties.getProperty(
                    "kernel." + index + ".runtimeOptimizerDrift.rewriteReviewPackage.firstBlocker",
                    "no-review-candidates"
            );
            if (!value.isBlank()
                    && !"none".equals(value)
                    && !"unknown".equals(value)
                    && !"no-review-candidates".equals(value)) {
                counts.merge(value, 1, Integer::sum);
            }
        }
        return summarizeCounts(counts);
    }

    private static String summarizeCounts(java.util.LinkedHashMap<String, Integer> counts) {
        if (counts.isEmpty()) {
            return "none";
        }
        StringBuilder builder = new StringBuilder();
        for (java.util.Map.Entry<String, Integer> entry : counts.entrySet()) {
            if (!builder.isEmpty()) {
                builder.append(", ");
            }
            builder.append(entry.getKey()).append(" x").append(entry.getValue());
        }
        return builder.toString();
    }

    private static String summarizeOptimizerRules(Properties properties, int kernelCount) {
        if (kernelCount <= 0) {
            return "none";
        }
        java.util.LinkedHashMap<String, OptimizerRuleAggregate> rules = new java.util.LinkedHashMap<>();
        for (int kernelIndex = 0; kernelIndex < kernelCount; kernelIndex++) {
            String basePrefix = "kernel." + kernelIndex + ".runtimeOptimizerDrift.optimizerRule.";
            int ruleCount = parsePositiveInt(properties.getProperty(basePrefix + "count", "0"));
            for (int ruleIndex = 0; ruleIndex < ruleCount; ruleIndex++) {
                String prefix = basePrefix + ruleIndex + ".";
                String id = properties.getProperty(prefix + "id", "");
                if (id.isBlank()) {
                    continue;
                }
                OptimizerRuleAggregate existing = rules.getOrDefault(id, OptimizerRuleAggregate.empty(id));
                rules.put(id, existing.add(
                        parsePositiveInt(properties.getProperty(prefix + "candidate.count", "0")),
                        parsePositiveInt(properties.getProperty(prefix + "proposal.count", "0")),
                        parsePositiveInt(properties.getProperty(prefix + "applied.count", "0")),
                        parsePositiveInt(properties.getProperty(prefix + "skipped.count", "0")),
                        parsePositiveInt(properties.getProperty(prefix + "blocked.count", "0")),
                        parsePositiveInt(properties.getProperty(prefix + "replacementPlan.count", "0")),
                        parsePositiveInt(properties.getProperty(prefix + "replacementPlan.complete.count", "0")),
                        parsePositiveInt(properties.getProperty(prefix + "replacementPlan.partial.count", "0")),
                        parsePositiveInt(properties.getProperty(prefix + "replacementPlan.validation.count", "0")),
                        parsePositiveInt(properties.getProperty(prefix + "replacementPlan.validation.valid.count", "0")),
                        parsePositiveInt(properties.getProperty(prefix + "replacementPlan.validation.invalid.count", "0")),
                        properties.getProperty(prefix + "replacementPlan.validation.firstBlocker", "none"),
                        parsePositiveInt(properties.getProperty(prefix + "rewriteSketch.count", "0")),
                        parsePositiveInt(properties.getProperty(prefix + "rewriteSketch.ready.count", "0")),
                        parsePositiveInt(properties.getProperty(prefix + "rewriteSketch.blocked.count", "0")),
                        properties.getProperty(prefix + "rewriteSketch.firstBlocker", "none"),
                        properties.getProperty(prefix + "rewriteSelection.status", "not-required"),
                        properties.getProperty(prefix + "rewriteSelection.firstBlocker", "no-rewrite-sketches"),
                        properties.getProperty(prefix + "rewriteProof.status", "not-required"),
                        properties.getProperty(prefix + "rewriteProof.firstBlocker", "no-proof-candidates"),
                        properties.getProperty(prefix + "firstBlocker", properties.getProperty(prefix + "replacementPlan.firstBlocker", "none"))
                ));
            }
        }
        if (rules.isEmpty()) {
            return "none";
        }
        StringBuilder builder = new StringBuilder();
        for (OptimizerRuleAggregate rule : rules.values()) {
            if (!builder.isEmpty()) {
                builder.append(", ");
            }
            builder.append(rule.id())
                    .append("[candidates=")
                    .append(rule.candidateCount())
                    .append(", proposals=")
                    .append(rule.proposalCount())
                    .append(", applied=")
                    .append(rule.appliedCount())
                    .append(", skipped=")
                    .append(rule.skippedCount())
                    .append(", blocked=")
                    .append(rule.blockedCount())
                    .append(", replacementPlans=")
                    .append(rule.replacementPlanCount())
                    .append(", completePlans=")
                    .append(rule.replacementPlanCompleteCount())
                    .append(", partialPlans=")
                    .append(rule.replacementPlanPartialCount())
                    .append(", planValidations=")
                    .append(rule.replacementPlanValidationCount())
                    .append(", invalidPlanValidations=")
                    .append(rule.replacementPlanValidationInvalidCount())
                    .append(", planValidationFirstBlocker=")
                    .append(rule.replacementPlanValidationFirstBlocker())
                    .append(", rewriteSketches=")
                    .append(rule.rewriteSketchCount())
                    .append(", readySketches=")
                    .append(rule.rewriteSketchReadyCount())
                    .append(", blockedSketches=")
                    .append(rule.rewriteSketchBlockedCount())
                    .append(", rewriteSketchFirstBlocker=")
                    .append(rule.rewriteSketchFirstBlocker())
                    .append(", rewriteSelectionStatus=")
                    .append(rule.rewriteSelectionStatus())
                    .append(", rewriteSelectionFirstBlocker=")
                    .append(rule.rewriteSelectionFirstBlocker())
                    .append(", rewriteProofStatus=")
                    .append(rule.rewriteProofStatus())
                    .append(", rewriteProofFirstBlocker=")
                    .append(rule.rewriteProofFirstBlocker())
                    .append(", firstBlocker=")
                    .append(rule.firstBlocker())
                    .append(']');
        }
        return builder.toString();
    }

    private static void appendReadinessChecklist(StringBuilder builder, List<ReadinessChecklistItem> checklist) {
        int readyCount = 0;
        List<ReadinessChecklistItem> blocked = new ArrayList<>();
        for (ReadinessChecklistItem item : checklist) {
            if (item.ready()) {
                readyCount++;
            } else {
                blocked.add(item);
            }
        }

        builder.append("readinessChecklist.item.count=").append(checklist.size()).append('\n');
        builder.append("readinessChecklist.ready.count=").append(readyCount).append('\n');
        builder.append("readinessChecklist.blocked.count=").append(blocked.size()).append('\n');
        builder.append("readinessChecklist.ready.all=").append(blocked.isEmpty()).append('\n');
        builder.append("readinessChecklist.firstBlocked=")
                .append(blocked.isEmpty() ? "none" : blocked.get(0).name())
                .append('\n');
        for (int index = 0; index < checklist.size(); index++) {
            ReadinessChecklistItem item = checklist.get(index);
            builder.append("readinessChecklist.item.").append(index).append(".name=")
                    .append(item.name())
                    .append('\n');
            builder.append("readinessChecklist.item.").append(index).append(".ready=")
                    .append(item.ready())
                    .append('\n');
            builder.append("readinessChecklist.item.").append(index).append(".diagnostic=")
                    .append(item.diagnostic())
                    .append('\n');
        }
    }

    private static String appendContractFields(String propertiesText) {
        try {
            Properties properties = new Properties();
            properties.load(new StringReader(propertiesText));
            GpuProductionPromotionExplainabilityValidation.Result contract =
                    GpuProductionPromotionExplainabilityValidation.validate(properties);
            StringBuilder builder = new StringBuilder(propertiesText);
            builder.append("contract.status=").append(contract.valid() ? "valid" : "invalid").append('\n');
            builder.append("contract.valid=").append(contract.valid()).append('\n');
            builder.append("contract.violation.count=").append(contract.violations().size()).append('\n');
            for (int index = 0; index < contract.violations().size(); index++) {
                builder.append("contract.violation.").append(index).append('=').append(contract.violations().get(index)).append('\n');
            }
            builder.append(GpuProductionPromotionDecision.fromExplainability(properties).toPropertiesText());
            return builder.toString();
        } catch (IOException failure) {
            return propertiesText
                    + "contract.status=invalid\n"
                    + "contract.valid=false\n"
                    + "contract.violation.count=1\n"
                    + "contract.violation.0=production-promotion explainability contract could not parse generated properties\n"
                    + "decision.mode=diagnostic-only\n"
                    + "decision.status=blocked\n"
                    + "decision.contractValid=false\n"
                    + "decision.productionSourceSwitchingAllowed=false\n"
                    + "decision.productionMutationAllowed=false\n"
                    + "decision.firstBlocker=none\n"
                    + "decision.firstViolation=production-promotion explainability contract could not parse generated properties\n"
                    + "decision.diagnostic=production promotion remains diagnostic-only because the explainability contract is invalid\n";
        }
    }

    private static String diagnostic(List<String> blockers, int i3ReviewReadyCount, int i3BlockedCount) {
        if (blockers.isEmpty()) {
            return "production promotion gates are ready for explicit operator review";
        }
        return "production promotion remains blocked: first=" + blockers.get(0)
                + ", i3ReviewReady=" + i3ReviewReadyCount
                + ", i3Blocked=" + i3BlockedCount;
    }

    private static boolean propertyIsTrue(Properties properties, String key, boolean fallback) {
        String value = properties.getProperty(key);
        return value == null || value.isBlank() ? fallback : "true".equals(value);
    }

    private static boolean propertyIsTrue(Properties properties, String primaryKey, String fallbackKey, boolean fallback) {
        String value = GpuRuntimeArtifactProperties.first(properties, null, primaryKey, fallbackKey);
        return value == null || value.isBlank() ? fallback : "true".equals(value);
    }

    private static Properties completePromotionArtifactSupport() {
        Properties properties = new Properties();
        properties.setProperty("complete", "true");
        properties.setProperty(
                "supported.count",
                Integer.toString(GpuPromotionArtifactRegistry.PROMOTION_ARTIFACTS.size())
        );
        properties.setProperty("missing.count", "0");
        return properties;
    }

    private static int parsePositiveInt(String value) {
        try {
            return Math.max(0, Integer.parseInt(value == null ? "0" : value.trim()));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private record OptimizerRuleAggregate(
            String id,
            int candidateCount,
            int proposalCount,
            int appliedCount,
            int skippedCount,
            int blockedCount,
            int replacementPlanCount,
            int replacementPlanCompleteCount,
            int replacementPlanPartialCount,
            int replacementPlanValidationCount,
            int replacementPlanValidationValidCount,
            int replacementPlanValidationInvalidCount,
            String replacementPlanValidationFirstBlocker,
            int rewriteSketchCount,
            int rewriteSketchReadyCount,
            int rewriteSketchBlockedCount,
            String rewriteSketchFirstBlocker,
            String rewriteSelectionStatus,
            String rewriteSelectionFirstBlocker,
            String rewriteProofStatus,
            String rewriteProofFirstBlocker,
            String firstBlocker
    ) {

        private static OptimizerRuleAggregate empty(String id) {
            return new OptimizerRuleAggregate(id, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, "none", 0, 0, 0, "none", "not-required", "no-rewrite-sketches", "not-required", "no-proof-candidates", "none");
        }

        private OptimizerRuleAggregate add(
                int candidateCount,
                int proposalCount,
                int appliedCount,
                int skippedCount,
                int blockedCount,
                int replacementPlanCount,
                int replacementPlanCompleteCount,
                int replacementPlanPartialCount,
                int replacementPlanValidationCount,
                int replacementPlanValidationValidCount,
                int replacementPlanValidationInvalidCount,
                String nextValidationBlocker,
                int rewriteSketchCount,
                int rewriteSketchReadyCount,
                int rewriteSketchBlockedCount,
                String nextRewriteSketchBlocker,
                String nextRewriteSelectionStatus,
                String nextRewriteSelectionBlocker,
                String nextRewriteProofStatus,
                String nextRewriteProofBlocker,
                String nextBlocker
        ) {
            String blocker = firstBlocker;
            String validationBlocker = replacementPlanValidationFirstBlocker;
            if ("none".equals(validationBlocker)
                    && nextValidationBlocker != null
                    && !nextValidationBlocker.isBlank()
                    && !"none".equals(nextValidationBlocker)) {
                validationBlocker = nextValidationBlocker;
            }
            String sketchBlocker = rewriteSketchFirstBlocker;
            if ("none".equals(sketchBlocker)
                    && nextRewriteSketchBlocker != null
                    && !nextRewriteSketchBlocker.isBlank()
                    && !"none".equals(nextRewriteSketchBlocker)) {
                sketchBlocker = nextRewriteSketchBlocker;
            }
            String selectionStatus = firstNonDefault(rewriteSelectionStatus, nextRewriteSelectionStatus, "not-required");
            String selectionBlocker = firstNonDefault(rewriteSelectionFirstBlocker, nextRewriteSelectionBlocker, "no-rewrite-sketches");
            String proofStatus = firstNonDefault(rewriteProofStatus, nextRewriteProofStatus, "not-required");
            String proofBlocker = firstNonDefault(rewriteProofFirstBlocker, nextRewriteProofBlocker, "no-proof-candidates");
            if ("none".equals(blocker)
                    && nextBlocker != null
                    && !nextBlocker.isBlank()
                    && !"none".equals(nextBlocker)) {
                blocker = nextBlocker;
            }
            return new OptimizerRuleAggregate(
                    id,
                    this.candidateCount + candidateCount,
                    this.proposalCount + proposalCount,
                    this.appliedCount + appliedCount,
                    this.skippedCount + skippedCount,
                    this.blockedCount + blockedCount,
                    this.replacementPlanCount + replacementPlanCount,
                    this.replacementPlanCompleteCount + replacementPlanCompleteCount,
                    this.replacementPlanPartialCount + replacementPlanPartialCount,
                    this.replacementPlanValidationCount + replacementPlanValidationCount,
                    this.replacementPlanValidationValidCount + replacementPlanValidationValidCount,
                    this.replacementPlanValidationInvalidCount + replacementPlanValidationInvalidCount,
                    validationBlocker,
                    this.rewriteSketchCount + rewriteSketchCount,
                    this.rewriteSketchReadyCount + rewriteSketchReadyCount,
                    this.rewriteSketchBlockedCount + rewriteSketchBlockedCount,
                    sketchBlocker,
                    selectionStatus,
                    selectionBlocker,
                    proofStatus,
                    proofBlocker,
                    blocker
            );
        }

        private static String firstNonDefault(String current, String next, String defaultValue) {
            if (current != null && !current.isBlank() && !defaultValue.equals(current) && !"unknown".equals(current)) {
                return current;
            }
            if (next != null && !next.isBlank() && !defaultValue.equals(next) && !"unknown".equals(next)) {
                return next;
            }
            return defaultValue;
        }
    }

    private record ControlledSourceSwitchingCoverage(
            List<String> realWorkloadResources,
            List<String> coveredResources,
            List<String> uncoveredResources
    ) {
        boolean allCovered() {
            return !realWorkloadResources.isEmpty() && uncoveredResources.isEmpty();
        }
    }

    private record ControlledMutationEvidence(
            boolean reviewReady,
            ControlledSourceSwitchingCoverage coverage,
            boolean passed
    ) {
    }

    private record ReadinessChecklistItem(
            String name,
            boolean ready,
            String readyDiagnostic,
            String blockedDiagnostic
    ) {
        String diagnostic() {
            return ready ? readyDiagnostic : blockedDiagnostic;
        }
    }
}
