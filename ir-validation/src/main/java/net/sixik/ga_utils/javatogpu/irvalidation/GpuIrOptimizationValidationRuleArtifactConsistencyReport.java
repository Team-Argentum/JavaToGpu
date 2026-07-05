package net.sixik.ga_utils.javatogpu.irvalidation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Read-only self-check for opt-in validation-rule artifacts.
 *
 * <p>The rule artifact already exports counts, verdicts, indexes, and summaries. This report keeps
 * those layers honest before CI or future optimizer tooling consumes the artifact.</p>
 */
public record GpuIrOptimizationValidationRuleArtifactConsistencyReport(
        String methodName,
        String verdict,
        boolean consistent,
        int checkCount,
        int failedCheckCount,
        List<String> failedChecks
) {
    private static final String VERDICT_OK = "consistent";
    private static final String VERDICT_FAILED = "inconsistent";

    public GpuIrOptimizationValidationRuleArtifactConsistencyReport {
        if (methodName == null || methodName.isBlank()) {
            throw new IllegalArgumentException("methodName must not be blank");
        }
        if (verdict == null || verdict.isBlank()) {
            throw new IllegalArgumentException("verdict must not be blank");
        }
        if (checkCount < 0) {
            throw new IllegalArgumentException("checkCount must be non-negative");
        }
        if (failedCheckCount < 0) {
            throw new IllegalArgumentException("failedCheckCount must be non-negative");
        }
        failedChecks = List.copyOf(Objects.requireNonNull(failedChecks, "failedChecks"));
        if (failedCheckCount != failedChecks.size()) {
            throw new IllegalArgumentException("failedCheckCount must match failedChecks size");
        }
    }

    public static GpuIrOptimizationValidationRuleArtifactConsistencyReport from(
            GpuIrOptimizationValidationRuleArtifactReport artifactReport
    ) {
        Objects.requireNonNull(artifactReport, "artifactReport");
        List<String> failedChecks = failedChecks(artifactReport);
        boolean consistent = failedChecks.isEmpty();
        return new GpuIrOptimizationValidationRuleArtifactConsistencyReport(
                artifactReport.validationReport().methodName(),
                consistent ? VERDICT_OK : VERDICT_FAILED,
                consistent,
                14,
                failedChecks.size(),
                failedChecks
        );
    }

    public boolean hasFailures() {
        return failedCheckCount > 0;
    }

    public Optional<String> firstFailedCheck() {
        return failedChecks.stream().findFirst();
    }

    public Optional<String> firstFailureExplanation() {
        return firstFailedCheck().map(GpuIrOptimizationValidationRuleArtifactConsistencyReport::failureExplanation);
    }

    public Map<String, Long> failedCheckCounts() {
        return failedChecks.stream()
                .collect(Collectors.groupingBy(
                        check -> check,
                        LinkedHashMap::new,
                        Collectors.counting()
                ));
    }

    public String ciSummaryLine() {
        if (consistent) {
            return "validation-rule artifact consistency check passed: " + checkCount + " checks";
        }
        return "validation-rule artifact consistency check failed: " + failedCheckCount + "/" + checkCount
                + " checks failed; first=" + firstFailedCheck().orElse("unknown")
                + "; explanation=" + firstFailureExplanation().orElse("unknown rule artifact drift");
    }

    public Map<String, String> artifactFields(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            throw new IllegalArgumentException("prefix must not be blank");
        }
        Map<String, String> values = new LinkedHashMap<>();
        values.put(prefix + "Verdict", verdict);
        values.put(prefix + "Consistent", Boolean.toString(consistent));
        values.put(prefix + "Checks", Integer.toString(checkCount));
        values.put(prefix + "FailedChecks", Integer.toString(failedCheckCount));
        values.put(prefix + "FailedCheckList", listSummary(failedChecks));
        values.put(prefix + "FailedCheckCounts", mapSummary(failedCheckCounts()));
        firstFailedCheck().ifPresent(check -> values.put(prefix + "FirstFailedCheck", check));
        firstFailureExplanation().ifPresent(explanation -> values.put(prefix + "FirstFailureExplanation", explanation));
        values.put(prefix + "CiSummaryLine", ciSummaryLine());
        values.put(prefix + "Summary", summary());
        return Collections.unmodifiableMap(values);
    }

    public Map<String, String> artifactFields() {
        return artifactFields("validationRulesConsistency");
    }

    public String summary() {
        return "validation-rule artifact consistency method=" + methodName
                + " verdict=" + verdict
                + " consistent=" + consistent
                + " checks=" + checkCount
                + " failedChecks=" + failedCheckCount
                + " failures=" + listSummary(failedChecks);
    }

    private static List<String> failedChecks(GpuIrOptimizationValidationRuleArtifactReport artifactReport) {
        GpuIrOptimizationValidationRuleArtifactSummary summary = artifactReport.artifactSummary();
        java.util.ArrayList<String> failures = new java.util.ArrayList<>();
        check(summary.passed() == artifactReport.passed(), "summaryPassed", failures);
        check(summary.verdict().equals(artifactReport.verdict()), "summaryVerdict", failures);
        check(summary.ruleCount() == artifactReport.ruleCount(), "summaryRuleCount", failures);
        check(summary.resultCount() == artifactReport.resultCount(), "summaryResultCount", failures);
        check(summary.failedCount() == artifactReport.failedCount(), "summaryFailedCount", failures);
        check(summary.warningCount() == artifactReport.warningCount(), "summaryWarningCount", failures);
        check(summary.blockingCount() == artifactReport.blockingCount(), "summaryBlockingCount", failures);
        check(summary.failedRuleIds().equals(artifactReport.failedRuleIds()), "summaryFailedRuleIds", failures);
        check(summary.warningRuleIds().equals(artifactReport.warningRuleIds()), "summaryWarningRuleIds", failures);
        check(summary.blockingRuleIds().equals(artifactReport.blockingRuleIds()), "summaryBlockingRuleIds", failures);
        check(summary.ruleIndex().equals(artifactReport.ruleIndex()), "summaryRuleIndex", failures);
        check(summary.statusCounts().equals(artifactReport.statusCountsSummary()), "summaryStatusCounts", failures);
        check(summary.warningRuleFamilyCounts().equals(artifactReport.warningRuleFamilyCountsSummary()), "summaryWarningRuleFamilyCounts", failures);
        check(summary.blockingRuleFamilyCounts().equals(artifactReport.blockingRuleFamilyCountsSummary()), "summaryBlockingRuleFamilyCounts", failures);
        return List.copyOf(failures);
    }

    private static void check(boolean condition, String failure, List<String> failures) {
        if (!condition) {
            failures.add(failure);
        }
    }

    private static String failureExplanation(String failedCheck) {
        return switch (failedCheck) {
            case "summaryPassed" -> "artifact summary and report disagree on pass/fail state";
            case "summaryVerdict" -> "artifact summary and report disagree on verdict";
            case "summaryRuleCount" -> "artifact summary and report disagree on rule count";
            case "summaryResultCount" -> "artifact summary and report disagree on result count";
            case "summaryFailedCount" -> "artifact summary and report disagree on failed count";
            case "summaryWarningCount" -> "artifact summary and report disagree on warning count";
            case "summaryBlockingCount" -> "artifact summary and report disagree on blocking count";
            case "summaryFailedRuleIds" -> "artifact summary and report disagree on failed rule ids";
            case "summaryWarningRuleIds" -> "artifact summary and report disagree on warning rule ids";
            case "summaryBlockingRuleIds" -> "artifact summary and report disagree on blocking rule ids";
            case "summaryRuleIndex" -> "artifact summary and report disagree on ordered rule index";
            case "summaryStatusCounts" -> "artifact summary and report disagree on status counts";
            case "summaryWarningRuleFamilyCounts" -> "artifact summary and report disagree on warning rule-family counts";
            case "summaryBlockingRuleFamilyCounts" -> "artifact summary and report disagree on blocking rule-family counts";
            default -> "unknown validation-rule artifact drift: " + failedCheck;
        };
    }

    private static String mapSummary(Map<String, Long> counts) {
        return counts.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining(",", "{", "}"));
    }

    private static String listSummary(List<String> values) {
        return values.stream().collect(Collectors.joining(",", "[", "]"));
    }
}
