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
        Properties gate = workloadGate == null ? new Properties() : workloadGate;
        Properties readiness = i3Summary == null ? new Properties() : i3Summary;
        Properties promotionSupport = backendPromotionArtifactSupport == null
                ? new Properties()
                : backendPromotionArtifactSupport;
        Properties controlledSourceSwitching = controlledProductionSourceSwitchingValidation == null
                ? new Properties()
                : controlledProductionSourceSwitchingValidation;
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
                gate.getProperty("productionSourceSwitchingEnabled.count", productionSourceSwitchingEnabled ? Integer.toString(kernelCount) : "0")
        );
        boolean allProductionSourceSwitchingEnabled = propertyIsTrue(
                gate,
                "productionSourceSwitchingEnabled.all",
                productionSourceSwitchingEnabled && productionSourceSwitchingEnabledCount == kernelCount
        );
        int productionPromotionDecisionEnabledCount = parsePositiveInt(
                gate.getProperty("productionPromotionDecisionMode.productionEnabled.count", productionSourceSwitchingEnabled ? Integer.toString(kernelCount) : "0")
        );
        boolean allProductionPromotionDecisionsEnabled = propertyIsTrue(
                gate,
                "productionPromotionDecisionMode.productionEnabled.all",
                productionPromotionDecisionEnabledCount == kernelCount && productionPromotionDecisionEnabledCount > 0
        );
        int productionSourceDecisionCount = parsePositiveInt(
                gate.getProperty("sourceSwitching.productionDecision.count", productionSourceSwitchingEnabled ? Integer.toString(kernelCount) : "0")
        );
        boolean allProductionSourceDecisions = propertyIsTrue(
                gate,
                "sourceSwitching.productionDecision.all",
                productionSourceDecisionCount == kernelCount && productionSourceDecisionCount > 0
        );
        int i3ReviewReadyCount = parsePositiveInt(readiness.getProperty("reviewReady.count", "0"));
        int i3BlockedCount = parsePositiveInt(readiness.getProperty("blocked.count", Integer.toString(kernelCount)));
        int i3SourceReadyCount = parsePositiveInt(readiness.getProperty("sourceReady.count", "0"));
        boolean allKernelsI3ReviewReady = kernelCount > 0 && i3ReviewReadyCount == kernelCount && i3BlockedCount == 0;
        boolean allKernelsSourceReady = kernelCount > 0 && i3SourceReadyCount == kernelCount;
        int optimizerFamilyCount = parsePositiveInt(gate.getProperty("optimizerFamily.count", "0"));
        int optimizerFamilyPromotionReadyCount = parsePositiveInt(
                gate.getProperty("optimizerFamily.promotionReady.count", "0")
        );
        String optimizerFamilySummary = gate.getProperty("optimizerFamily.summary", "none");
        int optimizerFamilyPayloadCompleteCount = parsePositiveInt(
                gate.getProperty("optimizerFamilyPayload.complete.count", "0")
        );
        String optimizerFamilyPayloadCompleteAll = gate.getProperty("optimizerFamilyPayload.complete.all", "false");
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
                        productionSourceSwitchingEnabled
                                && allProductionSourceSwitchingEnabled
                                && allProductionPromotionDecisionsEnabled
                                && allProductionSourceDecisions,
                        "production source switching is enabled for all workload kernels",
                        "production source switching remains disabled or incomplete"
                ),
                new ReadinessChecklistItem(
                        "production-mutation-enabled",
                        productionMutationEnabled,
                        "production mutation is enabled",
                        "production mutation remains disabled"
                )
        );

        List<String> blockers = new ArrayList<>();
        if (!gateReviewReady) {
            blockers.add("workload-source-promotion-gate-not-review-ready");
        }
        if (!sourceParityMatched) {
            blockers.add("source-parity-not-matched");
        }
        if (!runtimeEquivalencePassed) {
            blockers.add("runtime-equivalence-not-passed");
        }
        if (!allKernelsI3ReviewReady) {
            blockers.add("i3-workload-readiness-not-review-ready");
        }
        if (!allKernelsSourceReady) {
            blockers.add("i3-source-readiness-not-complete");
        }
        if (!productionSourceSwitchingEnabled) {
            blockers.add("production-source-switching-disabled");
        }
        if (!allProductionSourceSwitchingEnabled) {
            blockers.add("production-source-switching-not-enabled-for-all-kernels");
        }
        if (!allProductionPromotionDecisionsEnabled) {
            blockers.add("production-promotion-decision-not-enabled-for-all-kernels");
        }
        if (!allProductionSourceDecisions) {
            blockers.add("production-source-decision-not-compiled-for-all-kernels");
        }
        if (!productionMutationEnabled) {
            blockers.add("production-mutation-disabled");
        }
        if (!backendPromotionArtifactSupportComplete) {
            blockers.add("backend-promotion-artifact-support-incomplete");
        }
        if (!optimizerFamilyPromotionPreflightReady) {
            blockers.add("optimizer-family-runtime-equivalence-history-baseline-missing");
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
        builder.append("optimizerFamilyPayload.complete.count=").append(optimizerFamilyPayloadCompleteCount).append('\n');
        builder.append("optimizerFamilyPayload.complete.all=").append(optimizerFamilyPayloadCompleteAll).append('\n');
        builder.append("optimizerFamily.runtimeEquivalenceHistoryBaselineReady=")
                .append(optimizerFamilyRuntimeEquivalenceHistoryBaselineReady)
                .append('\n');
        builder.append("optimizerFamily.promotionPreflightReady=")
                .append(optimizerFamilyPromotionPreflightReady)
                .append('\n');
        builder.append("productionSourceSwitchingAllowed=").append(productionSourceSwitchingEnabled && blockers.isEmpty()).append('\n');
        builder.append("productionSourceSwitchingEnabled=").append(productionSourceSwitchingEnabled).append('\n');
        builder.append("productionSourceSwitchingEnabled.count=").append(productionSourceSwitchingEnabledCount).append('\n');
        builder.append("productionSourceSwitchingEnabled.all=").append(allProductionSourceSwitchingEnabled).append('\n');
        builder.append("productionPromotionDecisionMode.productionEnabled.count=").append(productionPromotionDecisionEnabledCount).append('\n');
        builder.append("productionPromotionDecisionMode.productionEnabled.all=").append(allProductionPromotionDecisionsEnabled).append('\n');
        builder.append("sourceSwitching.productionDecision.count=").append(productionSourceDecisionCount).append('\n');
        builder.append("sourceSwitching.productionDecision.all=").append(allProductionSourceDecisions).append('\n');
        builder.append("productionMutationAllowed=").append(productionMutationEnabled && blockers.isEmpty()).append('\n');
        builder.append("productionMutationEnabled=").append(productionMutationEnabled).append('\n');
        builder.append("backendPromotionArtifactSupport.complete=").append(backendPromotionArtifactSupportComplete).append('\n');
        builder.append("backendPromotionArtifactSupport.supported.count=").append(parsePositiveInt(
                promotionSupport.getProperty("supported.count", "0")
        )).append('\n');
        builder.append("backendPromotionArtifactSupport.missing.count=").append(parsePositiveInt(
                promotionSupport.getProperty("missing.count", "0")
        )).append('\n');
        builder.append("controlledProductionSourceSwitching.status=")
                .append(controlledSourceSwitchingStatus)
                .append('\n');
        builder.append("controlledProductionSourceSwitching.kernel.count=")
                .append(controlledSourceSwitchingKernelCount)
                .append('\n');
        builder.append("controlledProductionSourceSwitching.reviewReady=")
                .append(controlledSourceSwitching.getProperty("reviewReady", "false"))
                .append('\n');
        builder.append("controlledProductionSourceSwitching.productionSourceSwitching=")
                .append(controlledSourceSwitching.getProperty("productionSourceSwitching", "unknown"))
                .append('\n');
        builder.append("controlledProductionSourceSwitching.productionPromotionDecisionMode=")
                .append(controlledSourceSwitching.getProperty("productionPromotionDecisionMode", GpuProductionPromotionDecision.DIAGNOSTIC_ONLY))
                .append('\n');
        builder.append("controlledProductionSourceSwitching.realWorkload.covered.count=")
                .append(controlledSourceSwitchingCoverage.coveredResources().size())
                .append('\n');
        builder.append("controlledProductionSourceSwitching.realWorkload.total.count=")
                .append(controlledSourceSwitchingCoverage.realWorkloadResources().size())
                .append('\n');
        builder.append("controlledProductionSourceSwitching.realWorkload.uncovered.count=")
                .append(controlledSourceSwitchingCoverage.uncoveredResources().size())
                .append('\n');
        builder.append("controlledProductionSourceSwitching.realWorkload.covered.all=")
                .append(controlledSourceSwitchingCoverage.allCovered())
                .append('\n');
        appendIndexedResources(builder, "controlledProductionSourceSwitching.realWorkload.covered",
                controlledSourceSwitchingCoverage.coveredResources());
        appendIndexedResources(builder, "controlledProductionSourceSwitching.realWorkload.uncovered",
                controlledSourceSwitchingCoverage.uncoveredResources());
        appendReadinessChecklist(builder, readinessChecklist);
        builder.append("blocker.count=").append(blockers.size()).append('\n');
        for (int index = 0; index < blockers.size(); index++) {
            builder.append("blocker.").append(index).append('=').append(blockers.get(index)).append('\n');
        }
        builder.append("diagnostic.0=").append(diagnostic(blockers, i3ReviewReadyCount, i3BlockedCount)).append('\n');
        return appendContractFields(builder.toString());
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

    private record ControlledSourceSwitchingCoverage(
            List<String> realWorkloadResources,
            List<String> coveredResources,
            List<String> uncoveredResources
    ) {
        boolean allCovered() {
            return !realWorkloadResources.isEmpty() && uncoveredResources.isEmpty();
        }
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
