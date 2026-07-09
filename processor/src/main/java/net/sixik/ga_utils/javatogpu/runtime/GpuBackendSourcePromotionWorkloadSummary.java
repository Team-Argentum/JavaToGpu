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
            return "blocked (productionSourceSwitching=disabled, unexpectedWorkloadReviewReady=true)";
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

    private String sourceKernelResourceText() {
        return sourceKernelResource.isBlank() ? "" : ", sourceKernelResource=" + sourceKernelResource;
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
}
