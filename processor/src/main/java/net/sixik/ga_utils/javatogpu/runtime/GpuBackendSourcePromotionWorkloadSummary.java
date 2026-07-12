package net.sixik.ga_utils.javatogpu.runtime;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Compact runtime-facing summary of backend source-promotion workload evidence.
 *
 * <p>The workload gate stays as the detailed machine-readable artifact. This record provides the stable, short summary
 * string used by reports, validation history, CI summaries, and future backend promotion entrypoints.</p>
 */
public record GpuBackendSourcePromotionWorkloadSummary(
        String status,
        String reviewReady,
        String sourceParityMatched,
        String runtimeEquivalencePassed,
        String realWorkloadEvidence,
        String productionSourceSwitching,
        int productionPromotionOperatorAcceptedCount,
        String productionPromotionOperatorAcceptedAll,
        String sourceKernelResource,
        int kernelCount,
        int optimizerProofArtifactCount,
        int optimizerAcceptedProofArtifactCount,
        int optimizerBlockingProofArtifactCount,
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
        int optimizerRuleCount,
        String optimizerRuleSummary,
        String optimizerRuleDetails,
        int optimizerFamilyCount,
        int optimizerFamilyPromotionReadyCount,
        String optimizerFamilySummary,
        int optimizerFamilyPayloadCompleteCount,
        String optimizerFamilyPayloadCompleteAll,
        int runtimeExtensionParticipationRecordedKernelCount,
        int runtimeExtensionParticipationEntryCount,
        int runtimeExtensionParticipationFailedContinuedCount,
        int runtimeExtensionParticipationFailedClosedCount,
        String runtimeExtensionParticipationSources,
        String sourceSwitchingDecisions,
        String sourcePromotionFirstBlockers,
        String sourcePromotionFirstBlockerFamilies,
        String kernelEvidence
) {

    public static GpuBackendSourcePromotionWorkloadSummary notRecorded() {
        return new GpuBackendSourcePromotionWorkloadSummary(
                "not-recorded",
                "unknown",
                "unknown",
                "unknown",
                "not-wired",
                "disabled",
                0,
                "false",
                "",
                0,
                0,
                0,
                0,
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
                "",
                "",
                0,
                "none",
                "none",
                0,
                0,
                "none",
                0,
                "false",
                0,
                0,
                0,
                0,
                "",
                "",
                "",
                "",
                ""
        );
    }

    public static GpuBackendSourcePromotionWorkloadSummary fromProperties(Properties properties) {
        if (properties == null || properties.isEmpty()) {
            return notRecorded();
        }
        int kernelCount = parsePositiveInt(properties.getProperty("kernel.count", "0"));
        int productionPromotionOperatorAcceptedCount = parsePositiveInt(properties.getProperty(
                "productionPromotionOperatorAccepted.count",
                String.valueOf(countKernelBooleanProperty(
                        properties,
                        kernelCount,
                        "sourceSwitching.productionPromotionOperatorAccepted"
                ))
        ));
        return new GpuBackendSourcePromotionWorkloadSummary(
                properties.getProperty("status", "unknown"),
                properties.getProperty("reviewReady", "unknown"),
                properties.getProperty("sourceParityMatched", "unknown"),
                properties.getProperty("runtimeEquivalencePassed", "unknown"),
                properties.getProperty("realWorkloadEvidence", "not-wired"),
                properties.getProperty("productionSourceSwitching", "disabled"),
                productionPromotionOperatorAcceptedCount,
                properties.getProperty(
                        "productionPromotionOperatorAccepted.all",
                        String.valueOf(kernelCount > 0 && productionPromotionOperatorAcceptedCount == kernelCount)
                ),
                properties.getProperty("sourceKernelResource", ""),
                kernelCount,
                sumKernelProperty(properties, kernelCount, "runtimeOptimizerDrift.proofArtifact.count"),
                sumKernelProperty(properties, kernelCount, "runtimeOptimizerDrift.proofArtifact.accepted.count"),
                sumKernelProperty(properties, kernelCount, "runtimeOptimizerDrift.proofArtifact.blocking.count"),
                sumKernelProperty(properties, kernelCount, "runtimeOptimizerDrift.replacementPlan.complete.count"),
                sumKernelProperty(properties, kernelCount, "runtimeOptimizerDrift.replacementPlan.partial.count"),
                summarizeReplacementPlanFirstBlockers(properties, kernelCount),
                sumKernelProperty(properties, kernelCount, "runtimeOptimizerDrift.replacementPlan.validation.count"),
                sumKernelProperty(properties, kernelCount, "runtimeOptimizerDrift.replacementPlan.validation.valid.count"),
                sumKernelProperty(properties, kernelCount, "runtimeOptimizerDrift.replacementPlan.validation.invalid.count"),
                summarizeKernelProperty(properties, kernelCount, "runtimeOptimizerDrift.replacementPlan.validation.firstBlocker"),
                sumKernelProperty(properties, kernelCount, "runtimeOptimizerDrift.rewriteSketch.count"),
                sumKernelProperty(properties, kernelCount, "runtimeOptimizerDrift.rewriteSketch.ready.count"),
                sumKernelProperty(properties, kernelCount, "runtimeOptimizerDrift.rewriteSketch.blocked.count"),
                summarizeKernelProperty(properties, kernelCount, "runtimeOptimizerDrift.rewriteSketch.firstBlocker"),
                sumKernelProperty(properties, kernelCount, "runtimeOptimizerDrift.rewriteSketch.conflict.count"),
                summarizeKernelProperty(properties, kernelCount, "runtimeOptimizerDrift.rewriteSketch.conflict.firstBlocker"),
                summarizeRewriteSelectionStatuses(properties, kernelCount),
                summarizeRewriteSelectionFirstBlockers(properties, kernelCount),
                sumKernelProperty(properties, kernelCount, "runtimeOptimizerDrift.optimizerRule.count"),
                summarizeKernelProperty(properties, kernelCount, "runtimeOptimizerDrift.optimizerRule.summary"),
                summarizeOptimizerRules(properties, kernelCount),
                sumKernelProperty(properties, kernelCount, "runtimeOptimizerDrift.optimizerFamily.count"),
                sumKernelProperty(properties, kernelCount, "runtimeOptimizerDrift.optimizerFamily.promotionReady.count"),
                summarizeOptimizerFamilies(properties, kernelCount),
                sumKernelProperty(properties, kernelCount, "optimizerFamilyPayload.family.complete.count"),
                allKernelBooleanPropertyWhenCountPresent(
                        properties,
                        kernelCount,
                        "optimizerFamilyPayload.family.count",
                        "optimizerFamilyPayload.family.complete.count",
                        "optimizerFamilyPayload.family.complete.all"
                ),
                runtimeExtensionParticipationRecordedKernelCount(properties, kernelCount),
                runtimeExtensionParticipationEntryCount(properties, kernelCount),
                runtimeExtensionParticipationFailedContinuedCount(properties, kernelCount),
                runtimeExtensionParticipationFailedClosedCount(properties, kernelCount),
                summarizeRuntimeExtensionParticipationSources(properties, kernelCount),
                summarizeSourceSwitchingDecisions(properties, kernelCount),
                summarizeSourcePromotionFirstBlockers(properties, kernelCount),
                summarizeSourcePromotionFirstBlockerFamilies(properties, kernelCount),
                summarizeKernelEvidence(properties, kernelCount)
        );
    }

    public String historyStatus() {
        if (kernelCount == 0 && "not-recorded".equals(status)) {
            return "not recorded";
        }
        if ("review-ready".equals(status)) {
            return "blocked (gateStatus=" + status
                    + ", reviewReady=" + reviewReady
                    + ", sourceParityMatched=" + sourceParityMatched
                    + ", runtimeEquivalencePassed=" + runtimeEquivalencePassed
                    + ", realWorkloadEvidence=" + realWorkloadEvidence
                    + ", productionPromotionOperatorAccepted="
                    + productionPromotionOperatorAcceptedCount
                    + "/"
                    + kernelCount
                    + ", productionPromotionOperatorAcceptedAll="
                    + productionPromotionOperatorAcceptedAll
                    + ", optimizerFamilies="
                    + optimizerFamilyCount
                    + ", optimizerPromotionReadyFamilies="
                    + optimizerFamilyPromotionReadyCount
                    + ", optimizerPayloadCompleteFamilies="
                    + optimizerFamilyPayloadCompleteCount
                    + ", optimizerPayloadCompleteAll="
                    + optimizerFamilyPayloadCompleteAll
                    + optimizerReplacementPlanEvidenceText()
                    + optimizerRewriteSketchEvidenceText()
                    + optimizerRuleSummaryText()
                    + optimizerFamilySummaryText()
                    + runtimeExtensionParticipationEvidenceText()
                    + sourceSwitchingEvidenceText()
                    + kernelEvidence
                    + sourceKernelResourceText()
                    + ", productionSourceSwitching=disabled"
                    + ", unexpectedWorkloadReviewReady=true)";
        }
        return "not-promoted (gateStatus=" + status
                + ", reviewReady=" + reviewReady
                + ", sourceParityMatched=" + sourceParityMatched
                + ", runtimeEquivalencePassed=" + runtimeEquivalencePassed
                + ", realWorkloadEvidence=" + realWorkloadEvidence
                + ", productionPromotionOperatorAccepted="
                + productionPromotionOperatorAcceptedCount
                + "/"
                + kernelCount
                + ", productionPromotionOperatorAcceptedAll="
                + productionPromotionOperatorAcceptedAll
                + ", optimizerFamilies="
                + optimizerFamilyCount
                + ", optimizerPromotionReadyFamilies="
                + optimizerFamilyPromotionReadyCount
                + ", optimizerPayloadCompleteFamilies="
                + optimizerFamilyPayloadCompleteCount
                + ", optimizerPayloadCompleteAll="
                + optimizerFamilyPayloadCompleteAll
                + optimizerReplacementPlanEvidenceText()
                + optimizerRewriteSketchEvidenceText()
                + optimizerRuleSummaryText()
                + optimizerFamilySummaryText()
                + runtimeExtensionParticipationEvidenceText()
                + sourceSwitchingEvidenceText()
                + kernelEvidence
                + sourceKernelResourceText()
                + ", productionSourceSwitching=disabled)";
    }

    public String sourceSwitchingEvidenceText() {
        if (sourceSwitchingDecisions.isBlank()) {
            return "";
        }
        StringBuilder builder = new StringBuilder(", sourceSwitching=").append(sourceSwitchingDecisions);
        if (!sourcePromotionFirstBlockers.isBlank()) {
            builder.append(", sourcePromotionFirstBlockers=").append(sourcePromotionFirstBlockers);
        }
        if (!sourcePromotionFirstBlockerFamilies.isBlank()) {
            builder.append(", sourcePromotionFirstBlockerFamilies=").append(sourcePromotionFirstBlockerFamilies);
        }
        return builder.toString();
    }

    public String runtimeExtensionParticipationEvidenceText() {
        if (runtimeExtensionParticipationEntryCount == 0
                && runtimeExtensionParticipationRecordedKernelCount == 0
                && runtimeExtensionParticipationSources.isBlank()) {
            return "";
        }
        StringBuilder builder = new StringBuilder(", runtimeExtensionParticipation=recordedKernels=")
                .append(runtimeExtensionParticipationRecordedKernelCount)
                .append("/executions=")
                .append(runtimeExtensionParticipationEntryCount)
                .append("/failedContinued=")
                .append(runtimeExtensionParticipationFailedContinuedCount)
                .append("/failedClosed=")
                .append(runtimeExtensionParticipationFailedClosedCount);
        if (!runtimeExtensionParticipationSources.isBlank()) {
            builder.append("/sources=").append(runtimeExtensionParticipationSources);
        }
        return builder.toString();
    }

    private String sourceKernelResourceText() {
        return sourceKernelResource.isBlank() ? "" : ", sourceKernelResource=" + sourceKernelResource;
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

    private String optimizerReplacementPlanEvidenceText() {
        if (optimizerReplacementPlanCompleteCount == 0
                && optimizerReplacementPlanPartialCount == 0
                && optimizerReplacementPlanValidationCount == 0
                && optimizerReplacementPlanValidationValidCount == 0
                && optimizerReplacementPlanValidationInvalidCount == 0
                && optimizerReplacementPlanFirstBlockers.isBlank()
                && optimizerReplacementPlanValidationFirstBlockers.isBlank()) {
            return "";
        }
        StringBuilder builder = new StringBuilder(", optimizerReplacementPlans=complete=")
                .append(optimizerReplacementPlanCompleteCount)
                .append("/partial=")
                .append(optimizerReplacementPlanPartialCount);
        if (optimizerReplacementPlanValidationCount > 0
                || optimizerReplacementPlanValidationValidCount > 0
                || optimizerReplacementPlanValidationInvalidCount > 0
                || !optimizerReplacementPlanValidationFirstBlockers.isBlank()) {
            builder.append("/validation=valid=")
                    .append(optimizerReplacementPlanValidationValidCount)
                    .append("/total=")
                    .append(optimizerReplacementPlanValidationCount)
                    .append("/invalid=")
                    .append(optimizerReplacementPlanValidationInvalidCount);
        }
        if (!optimizerReplacementPlanValidationFirstBlockers.isBlank()) {
            builder.append("/validationFirstBlockers=").append(optimizerReplacementPlanValidationFirstBlockers);
        }
        if (!optimizerReplacementPlanFirstBlockers.isBlank()) {
            builder.append("/firstBlockers=").append(optimizerReplacementPlanFirstBlockers);
        }
        return builder.toString();
    }

    private String optimizerRewriteSketchEvidenceText() {
        if (optimizerRewriteSketchCount == 0
                && optimizerRewriteSketchReadyCount == 0
                && optimizerRewriteSketchBlockedCount == 0
                && optimizerRewriteSketchConflictCount == 0
                && optimizerRewriteSketchFirstBlockers.isBlank()
                && optimizerRewriteSketchConflictFirstBlockers.isBlank()
                && optimizerRewriteSelectionStatuses.isBlank()
                && optimizerRewriteSelectionFirstBlockers.isBlank()) {
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
        if (!optimizerRewriteSketchFirstBlockers.isBlank()) {
            builder.append("/firstBlockers=").append(optimizerRewriteSketchFirstBlockers);
        }
        if (!optimizerRewriteSketchConflictFirstBlockers.isBlank()) {
            builder.append("/conflictFirstBlockers=").append(optimizerRewriteSketchConflictFirstBlockers);
        }
        if (!optimizerRewriteSelectionStatuses.isBlank()) {
            builder.append("/selectionStatus=").append(optimizerRewriteSelectionStatuses);
        }
        if (!optimizerRewriteSelectionFirstBlockers.isBlank()) {
            builder.append("/selectionFirstBlockers=").append(optimizerRewriteSelectionFirstBlockers);
        }
        builder.append("/selectionApplied=false");
        return builder.toString();
    }

    private static String summarizeReplacementPlanFirstBlockers(Properties properties, int kernelCount) {
        Map<String, Integer> blockerCounts = new LinkedHashMap<>();
        for (int index = 0; index < kernelCount; index++) {
            String blocker = properties.getProperty(
                    "kernel." + index + ".runtimeOptimizerDrift.replacementPlan.firstBlocker",
                    ""
            );
            if (blocker.isBlank() || "none".equals(blocker) || "unknown".equals(blocker)) {
                continue;
            }
            blockerCounts.merge(blocker, 1, Integer::sum);
        }
        return summarizeCounts(blockerCounts);
    }

    private static String summarizeKernelProperty(Properties properties, int kernelCount, String propertyName) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (int index = 0; index < kernelCount; index++) {
            String value = properties.getProperty("kernel." + index + "." + propertyName, "");
            if (value.isBlank() || "none".equals(value) || "unknown".equals(value)) {
                continue;
            }
            counts.merge(value, 1, Integer::sum);
        }
        return summarizeCounts(counts);
    }

    private static String summarizeRewriteSelectionStatuses(Properties properties, int kernelCount) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (int index = 0; index < kernelCount; index++) {
            String value = properties.getProperty(
                    "kernel." + index + ".runtimeOptimizerDrift.rewriteSelection.status",
                    ""
            );
            if (value.isBlank() || "unknown".equals(value) || "not-required".equals(value)) {
                continue;
            }
            counts.merge(value, 1, Integer::sum);
        }
        return summarizeCounts(counts);
    }

    private static String summarizeRewriteSelectionFirstBlockers(Properties properties, int kernelCount) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (int index = 0; index < kernelCount; index++) {
            String value = properties.getProperty(
                    "kernel." + index + ".runtimeOptimizerDrift.rewriteSelection.firstBlocker",
                    ""
            );
            if (value.isBlank() || "none".equals(value) || "unknown".equals(value) || "no-rewrite-sketches".equals(value)) {
                continue;
            }
            counts.merge(value, 1, Integer::sum);
        }
        return summarizeCounts(counts);
    }

    private static String summarizeOptimizerFamilies(Properties properties, int kernelCount) {
        Map<String, OptimizerFamilyAggregate> families = new LinkedHashMap<>();
        for (int index = 0; index < kernelCount; index++) {
            String summary = properties.getProperty(
                    "kernel." + index + ".runtimeOptimizerDrift.optimizerFamily.summary",
                    ""
            );
            mergeOptimizerFamilySummary(families, summary);
        }
        if (families.isEmpty()) {
            return "none";
        }
        StringBuilder builder = new StringBuilder();
        for (OptimizerFamilyAggregate family : families.values()) {
            if (!builder.isEmpty()) {
                builder.append(", ");
            }
            builder.append(family.name())
                    .append("[passes=")
                    .append(family.passCount())
                    .append(", acceptedProof=")
                    .append(family.acceptedProofCount())
                    .append(", blockingProof=")
                    .append(family.blockingProofCount())
                    .append(", rolledBack=")
                    .append(family.rolledBackCount())
                    .append(", failed=")
                    .append(family.failedCount())
                    .append(", promotionReady=")
                    .append(family.promotionReady())
                    .append(']');
        }
        return builder.toString();
    }

    private static void mergeOptimizerFamilySummary(
            Map<String, OptimizerFamilyAggregate> families,
            String summary
    ) {
        if (summary == null || summary.isBlank() || "none".equals(summary)) {
            return;
        }
        for (String entry : summary.split("\\], ")) {
            String normalized = entry.endsWith("]") ? entry : entry + "]";
            int start = normalized.indexOf('[');
            int end = normalized.lastIndexOf(']');
            if (start <= 0 || end <= start) {
                continue;
            }
            String name = normalized.substring(0, start);
            Map<String, String> fields = parseOptimizerFamilyFields(normalized.substring(start + 1, end));
            OptimizerFamilyAggregate existing = families.getOrDefault(name, OptimizerFamilyAggregate.empty(name));
            families.put(name, existing.add(
                    parsePositiveInt(fields.getOrDefault("passes", "0")),
                    parsePositiveInt(fields.getOrDefault("acceptedProof", "0")),
                    parsePositiveInt(fields.getOrDefault("blockingProof", "0")),
                    parsePositiveInt(fields.getOrDefault("rolledBack", "0")),
                    parsePositiveInt(fields.getOrDefault("failed", "0"))
            ));
        }
    }

    private static Map<String, String> parseOptimizerFamilyFields(String fieldsText) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (String field : fieldsText.split(", ")) {
            int separator = field.indexOf('=');
            if (separator <= 0 || separator == field.length() - 1) {
                continue;
            }
            fields.put(field.substring(0, separator), field.substring(separator + 1));
        }
        return fields;
    }

    private static String summarizeSourcePromotionFirstBlockerFamilies(Properties properties, int kernelCount) {
        int familyCount = parsePositiveInt(properties.getProperty(
                "sourceSwitching.sourcePromotionFirstBlockerFamily.count",
                "0"
        ));
        if (familyCount > 0) {
            return summarizeIndexed(properties, "sourceSwitching.sourcePromotionFirstBlockerFamily", familyCount);
        }
        Map<String, Integer> familyCounts = new LinkedHashMap<>();
        for (int index = 0; index < kernelCount; index++) {
            String blocker = properties.getProperty("kernel." + index + ".sourceSwitching.sourcePromotionFirstBlocker", "");
            if (blocker.isBlank() || "none".equals(blocker) || "unknown".equals(blocker)) {
                continue;
            }
            String family = GpuBackendSourcePromotionBlockerClassifier.classify(blocker);
            familyCounts.merge(family, 1, Integer::sum);
        }
        return summarizeCounts(familyCounts);
    }

    private static String summarizeSourcePromotionFirstBlockers(Properties properties, int kernelCount) {
        int blockerCount = parsePositiveInt(properties.getProperty("sourceSwitching.sourcePromotionFirstBlocker.count", "0"));
        if (blockerCount > 0) {
            return summarizeIndexed(properties, "sourceSwitching.sourcePromotionFirstBlocker", blockerCount);
        }
        Map<String, Integer> blockerCounts = new LinkedHashMap<>();
        for (int index = 0; index < kernelCount; index++) {
            String blocker = properties.getProperty("kernel." + index + ".sourceSwitching.sourcePromotionFirstBlocker", "");
            if (blocker.isBlank() || "none".equals(blocker) || "unknown".equals(blocker)) {
                continue;
            }
            blockerCounts.merge(blocker, 1, Integer::sum);
        }
        return summarizeCounts(blockerCounts);
    }

    private static String summarizeSourceSwitchingDecisions(Properties properties, int kernelCount) {
        Map<String, Integer> decisionCounts = new LinkedHashMap<>();
        for (int index = 0; index < kernelCount; index++) {
            String decision = properties.getProperty("kernel." + index + ".sourceSwitching.decision", "");
            if (decision.isBlank() || "not-recorded".equals(decision)) {
                continue;
            }
            decisionCounts.merge(decision, 1, Integer::sum);
        }
        return summarizeCounts(decisionCounts);
    }

    private static int runtimeExtensionParticipationRecordedKernelCount(Properties properties, int kernelCount) {
        String aggregate = properties.getProperty("runtimeExtensionParticipation.recordedKernel.count");
        if (aggregate != null) {
            return parsePositiveInt(aggregate);
        }
        int count = 0;
        for (int index = 0; index < kernelCount; index++) {
            if ("recorded".equals(properties.getProperty("kernel." + index + ".runtimeExtensionParticipation.status"))) {
                count++;
            }
        }
        return count;
    }

    private static int runtimeExtensionParticipationEntryCount(Properties properties, int kernelCount) {
        String aggregate = properties.getProperty("runtimeExtensionParticipation.entry.count");
        return aggregate == null
                ? sumKernelProperty(properties, kernelCount, "runtimeExtensionParticipation.entry.count")
                : parsePositiveInt(aggregate);
    }

    private static int runtimeExtensionParticipationFailedContinuedCount(Properties properties, int kernelCount) {
        String aggregate = properties.getProperty("runtimeExtensionParticipation.failedContinued.count");
        return aggregate == null
                ? sumKernelProperty(properties, kernelCount, "runtimeExtensionParticipation.failedContinued.count")
                : parsePositiveInt(aggregate);
    }

    private static int runtimeExtensionParticipationFailedClosedCount(Properties properties, int kernelCount) {
        String aggregate = properties.getProperty("runtimeExtensionParticipation.failedClosed.count");
        return aggregate == null
                ? sumKernelProperty(properties, kernelCount, "runtimeExtensionParticipation.failedClosed.count")
                : parsePositiveInt(aggregate);
    }

    private static String summarizeRuntimeExtensionParticipationSources(Properties properties, int kernelCount) {
        int aggregateSourceCount = parsePositiveInt(properties.getProperty("runtimeExtensionParticipation.source.count", "0"));
        if (aggregateSourceCount > 0) {
            return summarizeIndexed(properties, "runtimeExtensionParticipation.source", aggregateSourceCount);
        }
        Map<String, Integer> sourceCounts = new LinkedHashMap<>();
        for (int kernelIndex = 0; kernelIndex < kernelCount; kernelIndex++) {
            int sourceCount = parsePositiveInt(properties.getProperty(
                    "kernel." + kernelIndex + ".runtimeExtensionParticipation.source.count",
                    "0"
            ));
            for (int sourceIndex = 0; sourceIndex < sourceCount; sourceIndex++) {
                String source = properties.getProperty(
                        "kernel." + kernelIndex + ".runtimeExtensionParticipation.source." + sourceIndex + ".name",
                        ""
                );
                int count = parsePositiveInt(properties.getProperty(
                        "kernel." + kernelIndex + ".runtimeExtensionParticipation.source." + sourceIndex + ".count",
                        "0"
                ));
                if (!source.isBlank()) {
                    sourceCounts.merge(source, count, Integer::sum);
                }
            }
        }
        return summarizeCounts(sourceCounts);
    }

    private static String summarizeKernelEvidence(Properties properties, int kernelCount) {
        if (kernelCount == 0) {
            return "";
        }
        StringBuilder builder = new StringBuilder(", kernelCount=").append(kernelCount);
        for (int index = 0; index < kernelCount; index++) {
            builder.append(", kernel.")
                    .append(index)
                    .append('=')
                    .append(properties.getProperty("kernel." + index + ".sourceKernelResource", "unknown"))
                    .append("[diagnostics=")
                    .append(properties.getProperty("kernel." + index + ".diagnostic.count", "0"))
                    .append(", sourceSwitching=")
                    .append(properties.getProperty("kernel." + index + ".sourceSwitching.decision", "not-recorded"))
                    .append("/operatorAccepted=")
                    .append(properties.getProperty(
                            "kernel." + index + ".sourceSwitching.productionPromotionOperatorAccepted",
                            "false"
                    ))
                    .append("/sourcePromotionFirstBlocker=")
                    .append(properties.getProperty("kernel." + index + ".sourceSwitching.sourcePromotionFirstBlocker", "unknown"))
                    .append(", runtimeIr=")
                    .append(properties.getProperty("kernel." + index + ".runtimeIrHandoff.selectedStage", "unknown"))
                    .append(", optimizerDrift=")
                    .append(properties.getProperty("kernel." + index + ".runtimeOptimizerDrift.status", "not-recorded"))
                    .append('/')
                    .append(properties.getProperty("kernel." + index + ".runtimeOptimizerDrift.pass.count", "0"))
                    .append("passes")
                    .append("/rollback=")
                    .append(properties.getProperty("kernel." + index + ".runtimeOptimizerDrift.pass.rolledBack.count", "0"))
                    .append("/proof=")
                    .append(properties.getProperty("kernel." + index + ".runtimeOptimizerDrift.proofArtifact.count", "0"))
                    .append("/acceptedProof=")
                    .append(properties.getProperty("kernel." + index + ".runtimeOptimizerDrift.proofArtifact.accepted.count", "0"))
                    .append("/blockingProof=")
                    .append(properties.getProperty("kernel." + index + ".runtimeOptimizerDrift.proofArtifact.blocking.count", "0"))
                    .append("/replacementPlanComplete=")
                    .append(properties.getProperty(
                            "kernel." + index + ".runtimeOptimizerDrift.replacementPlan.complete.count",
                            "0"
                    ))
                    .append("/replacementPlanPartial=")
                    .append(properties.getProperty(
                            "kernel." + index + ".runtimeOptimizerDrift.replacementPlan.partial.count",
                            "0"
                    ))
                    .append("/replacementPlanFirstBlocker=")
                    .append(properties.getProperty(
                            "kernel." + index + ".runtimeOptimizerDrift.replacementPlan.firstBlocker",
                            "none"
                    ))
                    .append("/replacementPlanValidationValid=")
                    .append(properties.getProperty(
                            "kernel." + index + ".runtimeOptimizerDrift.replacementPlan.validation.valid.count",
                            "0"
                    ))
                    .append("/replacementPlanValidationTotal=")
                    .append(properties.getProperty(
                            "kernel." + index + ".runtimeOptimizerDrift.replacementPlan.validation.count",
                            "0"
                    ))
                    .append("/replacementPlanValidationInvalid=")
                    .append(properties.getProperty(
                            "kernel." + index + ".runtimeOptimizerDrift.replacementPlan.validation.invalid.count",
                            "0"
                    ))
                    .append("/replacementPlanValidationFirstBlocker=")
                    .append(properties.getProperty(
                            "kernel." + index + ".runtimeOptimizerDrift.replacementPlan.validation.firstBlocker",
                            "none"
                    ))
                    .append("/rewriteSketchReady=")
                    .append(properties.getProperty(
                            "kernel." + index + ".runtimeOptimizerDrift.rewriteSketch.ready.count",
                            "0"
                    ))
                    .append("/rewriteSketchTotal=")
                    .append(properties.getProperty(
                            "kernel." + index + ".runtimeOptimizerDrift.rewriteSketch.count",
                            "0"
                    ))
                    .append("/rewriteSketchBlocked=")
                    .append(properties.getProperty(
                            "kernel." + index + ".runtimeOptimizerDrift.rewriteSketch.blocked.count",
                            "0"
                    ))
                    .append("/rewriteSketchFirstBlocker=")
                    .append(properties.getProperty(
                            "kernel." + index + ".runtimeOptimizerDrift.rewriteSketch.firstBlocker",
                            "none"
                    ))
                    .append("/rewriteSketchConflicts=")
                    .append(properties.getProperty(
                            "kernel." + index + ".runtimeOptimizerDrift.rewriteSketch.conflict.count",
                            "0"
                    ))
                    .append("/rewriteSketchConflictFirstBlocker=")
                    .append(properties.getProperty(
                            "kernel." + index + ".runtimeOptimizerDrift.rewriteSketch.conflict.firstBlocker",
                            "none"
                    ))
                    .append("/rewriteSketchConflictResolutionImplemented=")
                    .append(properties.getProperty(
                            "kernel." + index + ".runtimeOptimizerDrift.rewriteSketch.conflict.conflictResolutionImplemented",
                            "false"
                    ))
                    .append("/rewriteSketchSelectionApplied=")
                    .append(properties.getProperty(
                            "kernel." + index + ".runtimeOptimizerDrift.rewriteSketch.conflict.selectionApplied",
                            "false"
                    ))
                    .append("/rewriteSelectionStatus=")
                    .append(properties.getProperty(
                            "kernel." + index + ".runtimeOptimizerDrift.rewriteSelection.status",
                            "not-required"
                    ))
                    .append("/rewriteSelectionFirstBlocker=")
                    .append(properties.getProperty(
                            "kernel." + index + ".runtimeOptimizerDrift.rewriteSelection.firstBlocker",
                            "no-rewrite-sketches"
                    ))
                    .append("/rewriteSelectionApplied=")
                    .append(properties.getProperty(
                            "kernel." + index + ".runtimeOptimizerDrift.rewriteSelection.selectionApplied",
                            "false"
                    ))
                    .append("/rewriteBuilderImplemented=")
                    .append(properties.getProperty(
                            "kernel." + index + ".runtimeOptimizerDrift.rewriteSketch.rewriteBuilderImplemented",
                            "false"
                    ))
                    .append("/selectedIrReplacement=")
                    .append(properties.getProperty(
                            "kernel." + index + ".runtimeOptimizerDrift.rewriteSketch.selectedIrReplacement",
                            "false"
                    ))
                    .append("/optimizerRules=")
                    .append(properties.getProperty("kernel." + index + ".runtimeOptimizerDrift.optimizerRule.count", "0"))
                    .append("/optimizerRuleDetails=")
                    .append(summarizeKernelOptimizerRules(properties, index))
                    .append("/optimizerFamilies=")
                    .append(properties.getProperty("kernel." + index + ".runtimeOptimizerDrift.optimizerFamily.count", "0"))
                    .append("/promotionReadyFamilies=")
                    .append(properties.getProperty(
                            "kernel." + index + ".runtimeOptimizerDrift.optimizerFamily.promotionReady.count",
                            "0"
                    ))
                    .append("/payloadCompleteFamilies=")
                    .append(properties.getProperty("kernel." + index + ".optimizerFamilyPayload.family.complete.count", "0"))
                    .append("/payloadCompleteAll=")
                    .append(properties.getProperty("kernel." + index + ".optimizerFamilyPayload.family.complete.all", "false"))
                    .append(", extensionParticipation=")
                    .append(properties.getProperty("kernel." + index + ".runtimeExtensionParticipation.status", "not-recorded"))
                    .append('/')
                    .append(properties.getProperty("kernel." + index + ".runtimeExtensionParticipation.entry.count", "0"))
                    .append("executions")
                    .append("/failedContinued=")
                    .append(properties.getProperty("kernel." + index + ".runtimeExtensionParticipation.failedContinued.count", "0"))
                    .append("/failedClosed=")
                    .append(properties.getProperty("kernel." + index + ".runtimeExtensionParticipation.failedClosed.count", "0"))
                    .append("/fallback=")
                    .append(properties.getProperty("kernel." + index + ".runtimeOptimizerDrift.fallbackDecision", "unknown"))
                    .append(", productionMutation=")
                    .append(properties.getProperty(
                            "kernel." + index + ".runtimeProductionMutationSafety.productionMutationEnabled",
                            "unknown"
                    ))
                    .append(", sourceReady=")
                    .append(properties.getProperty("kernel." + index + ".i3Readiness.sourceReady", "unknown"))
                    .append(", i3=")
                    .append(properties.getProperty("kernel." + index + ".i3Readiness.status", "unknown"))
                    .append(", families=")
                    .append(formatBlockerFamilies(
                            properties,
                            "kernel." + index + ".",
                            parsePositiveInt(properties.getProperty("kernel." + index + ".blockerFamily.count", "0"))
                    ))
                    .append(']');
        }
        return builder.toString();
    }

    private static String formatBlockerFamilies(Properties properties, String prefix, int familyCount) {
        if (familyCount == 0) {
            return "none";
        }
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < familyCount; index++) {
            if (index > 0) {
                builder.append(", ");
            }
            builder.append(properties.getProperty(prefix + "blockerFamily." + index + ".name", "unknown"))
                    .append('=')
                    .append(properties.getProperty(prefix + "blockerFamily." + index + ".count", "0"));
        }
        return builder.toString();
    }

    private static String summarizeIndexed(Properties properties, String keyPrefix, int count) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (int index = 0; index < count; index++) {
            String name = properties.getProperty(keyPrefix + "." + index + ".name", "unknown");
            int value = parsePositiveInt(properties.getProperty(keyPrefix + "." + index + ".count", "0"));
            counts.merge(name, value, Integer::sum);
        }
        return summarizeCounts(counts);
    }

    private static String summarizeCounts(Map<String, Integer> counts) {
        if (counts.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            if (!builder.isEmpty()) {
                builder.append(", ");
            }
            builder.append(entry.getKey()).append('=').append(entry.getValue());
        }
        return builder.toString();
    }

    private static int parsePositiveInt(String value) {
        try {
            return Math.max(0, Integer.parseInt(value));
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private static int sumKernelProperty(Properties properties, int kernelCount, String propertyName) {
        int sum = 0;
        for (int index = 0; index < kernelCount; index++) {
            sum += parsePositiveInt(properties.getProperty("kernel." + index + "." + propertyName, "0"));
        }
        return sum;
    }

    private static int countKernelBooleanProperty(Properties properties, int kernelCount, String propertyName) {
        int count = 0;
        for (int index = 0; index < kernelCount; index++) {
            if ("true".equals(properties.getProperty("kernel." + index + "." + propertyName))) {
                count++;
            }
        }
        return count;
    }

    private static String allKernelBooleanProperty(Properties properties, int kernelCount, String propertyName) {
        return Boolean.toString(kernelCount > 0 && countKernelBooleanProperty(properties, kernelCount, propertyName) == kernelCount);
    }

    private static String summarizeOptimizerRules(Properties properties, int kernelCount) {
        Map<String, OptimizerRuleAggregate> rules = new LinkedHashMap<>();
        for (int kernelIndex = 0; kernelIndex < kernelCount; kernelIndex++) {
            collectKernelOptimizerRules(properties, kernelIndex, rules);
        }
        return formatOptimizerRules(rules);
    }

    private static String summarizeKernelOptimizerRules(Properties properties, int kernelIndex) {
        Map<String, OptimizerRuleAggregate> rules = new LinkedHashMap<>();
        collectKernelOptimizerRules(properties, kernelIndex, rules);
        String summary = formatOptimizerRules(rules);
        return summary.isBlank() ? "none" : summary;
    }

    private static void collectKernelOptimizerRules(
            Properties properties,
            int kernelIndex,
            Map<String, OptimizerRuleAggregate> rules
    ) {
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
                    properties.getProperty(prefix + "firstBlocker", properties.getProperty(prefix + "replacementPlan.firstBlocker", "none"))
            ));
        }
    }

    private static String formatOptimizerRules(Map<String, OptimizerRuleAggregate> rules) {
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
                    .append(", firstBlocker=")
                    .append(rule.firstBlocker())
                    .append(']');
        }
        return builder.toString();
    }

    private static String allKernelBooleanPropertyWhenCountPresent(
            Properties properties,
            int kernelCount,
            String countPropertyName,
            String fallbackCountPropertyName,
            String booleanPropertyName
    ) {
        if (kernelCount <= 0) {
            return "false";
        }
        boolean present = false;
        for (int index = 0; index < kernelCount; index++) {
            String prefix = "kernel." + index + ".";
            int count = parsePositiveInt(properties.getProperty(prefix + countPropertyName, "0"));
            int fallbackCount = parsePositiveInt(properties.getProperty(prefix + fallbackCountPropertyName, "0"));
            if (count == 0 && fallbackCount == 0) {
                continue;
            }
            String value = properties.getProperty(prefix + booleanPropertyName, "");
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

    private record OptimizerFamilyAggregate(
            String name,
            int passCount,
            int acceptedProofCount,
            int blockingProofCount,
            int rolledBackCount,
            int failedCount
    ) {

        private static OptimizerFamilyAggregate empty(String name) {
            return new OptimizerFamilyAggregate(name, 0, 0, 0, 0, 0);
        }

        private OptimizerFamilyAggregate add(
                int passCount,
                int acceptedProofCount,
                int blockingProofCount,
                int rolledBackCount,
                int failedCount
        ) {
            return new OptimizerFamilyAggregate(
                    name,
                    this.passCount + passCount,
                    this.acceptedProofCount + acceptedProofCount,
                    this.blockingProofCount + blockingProofCount,
                    this.rolledBackCount + rolledBackCount,
                    this.failedCount + failedCount
            );
        }

        private boolean promotionReady() {
            return acceptedProofCount > 0 && blockingProofCount == 0 && rolledBackCount == 0 && failedCount == 0;
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
            String firstBlocker
    ) {

        private static OptimizerRuleAggregate empty(String id) {
            return new OptimizerRuleAggregate(id, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, "none", 0, 0, 0, "none", "none");
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
                    blocker
            );
        }
    }
}
