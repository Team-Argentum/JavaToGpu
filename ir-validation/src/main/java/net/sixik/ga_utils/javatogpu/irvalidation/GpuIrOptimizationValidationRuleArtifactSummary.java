package net.sixik.ga_utils.javatogpu.irvalidation;

import java.util.Objects;

/**
 * Compact CI-facing summary for opt-in validation-rule artifacts.
 */
public record GpuIrOptimizationValidationRuleArtifactSummary(
        String methodName,
        String verdict,
        boolean passed,
        int ruleCount,
        int resultCount,
        int failedCount,
        int warningCount,
        int blockingCount,
        String failedRuleIds,
        String warningRuleIds,
        String blockingRuleIds,
        String ruleIndex,
        String warningRuleIndex,
        String blockingRuleIndex,
        String firstFailedRuleId,
        String firstWarningRuleId,
        String firstBlockingRuleId,
        String statusCounts,
        String ruleFamilyCounts,
        String warningRuleFamilyCounts,
        String blockingRuleFamilyCounts,
        String failedRuleFamilyCounts
) {
    public GpuIrOptimizationValidationRuleArtifactSummary {
        methodName = requireNonBlank(methodName, "methodName");
        verdict = requireNonBlank(verdict, "verdict");
        if (ruleCount < 0 || resultCount < 0 || failedCount < 0 || warningCount < 0 || blockingCount < 0) {
            throw new IllegalArgumentException("counts must not be negative");
        }
        failedRuleIds = Objects.requireNonNull(failedRuleIds, "failedRuleIds");
        warningRuleIds = Objects.requireNonNull(warningRuleIds, "warningRuleIds");
        blockingRuleIds = Objects.requireNonNull(blockingRuleIds, "blockingRuleIds");
        ruleIndex = Objects.requireNonNull(ruleIndex, "ruleIndex");
        warningRuleIndex = Objects.requireNonNull(warningRuleIndex, "warningRuleIndex");
        blockingRuleIndex = Objects.requireNonNull(blockingRuleIndex, "blockingRuleIndex");
        firstFailedRuleId = Objects.requireNonNull(firstFailedRuleId, "firstFailedRuleId");
        firstWarningRuleId = Objects.requireNonNull(firstWarningRuleId, "firstWarningRuleId");
        firstBlockingRuleId = Objects.requireNonNull(firstBlockingRuleId, "firstBlockingRuleId");
        statusCounts = Objects.requireNonNull(statusCounts, "statusCounts");
        ruleFamilyCounts = Objects.requireNonNull(ruleFamilyCounts, "ruleFamilyCounts");
        warningRuleFamilyCounts = Objects.requireNonNull(warningRuleFamilyCounts, "warningRuleFamilyCounts");
        blockingRuleFamilyCounts = Objects.requireNonNull(blockingRuleFamilyCounts, "blockingRuleFamilyCounts");
        failedRuleFamilyCounts = Objects.requireNonNull(failedRuleFamilyCounts, "failedRuleFamilyCounts");
    }

    public boolean hasFailures() {
        return failedCount > 0;
    }

    public boolean hasWarnings() {
        return warningCount > 0;
    }

    public boolean hasBlockingResults() {
        return blockingCount > 0;
    }

    public String summaryLine() {
        return "validation rules method=" + methodName
                + " verdict=" + verdict
                + " passed=" + passed
                + " rules=" + ruleCount
                + " results=" + resultCount
                + " failed=" + failedCount
                + " hasFailures=" + hasFailures()
                + " warnings=" + warningCount
                + " hasWarnings=" + hasWarnings()
                + " blocking=" + blockingCount
                + " hasBlockingResults=" + hasBlockingResults()
                + (failedRuleIds.isBlank() ? "" : " failedRuleIds=" + failedRuleIds)
                + (warningRuleIds.isBlank() ? "" : " warningRuleIds=" + warningRuleIds)
                + (blockingRuleIds.isBlank() ? "" : " blockingRuleIds=" + blockingRuleIds)
                + " ruleIndex=" + ruleIndex
                + " warningRuleIndex=" + warningRuleIndex
                + " blockingRuleIndex=" + blockingRuleIndex
                + (firstFailedRuleId.isBlank() ? "" : " firstFailedRuleId=" + firstFailedRuleId)
                + (firstWarningRuleId.isBlank() ? "" : " firstWarningRuleId=" + firstWarningRuleId)
                + (firstBlockingRuleId.isBlank() ? "" : " firstBlockingRuleId=" + firstBlockingRuleId)
                + " statusCounts=" + statusCounts
                + " ruleFamilyCounts=" + ruleFamilyCounts
                + " warningRuleFamilyCounts=" + warningRuleFamilyCounts
                + " blockingRuleFamilyCounts=" + blockingRuleFamilyCounts
                + " failedRuleFamilyCounts=" + failedRuleFamilyCounts;
    }

    private static String requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
