package net.sixik.ga_utils.javatogpu.irvalidation;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Opt-in export artifact for validation-rule registry evaluations.
 *
 * <p>This report packages rule results produced by explicit callers. It does not run as part of
 * normal compiler validation and does not enable any optimizer mutation.</p>
 */
public record GpuIrOptimizationValidationRuleArtifactReport(
        GpuIrOptimizationValidationReport validationReport,
        GpuIrOptimizationValidationRuleRegistry registry,
        List<GpuIrOptimizationValidationRuleResult> results
) {
    public GpuIrOptimizationValidationRuleArtifactReport {
        validationReport = Objects.requireNonNull(validationReport, "validationReport");
        registry = Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(results, "results");
        if (results.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("results must not contain null entries");
        }
        if (registry.rules().size() != results.size()) {
            throw new IllegalArgumentException("rule result count must match registry rule count");
        }
        for (int index = 0; index < results.size(); index++) {
            String expectedRuleId = registry.rules().get(index).id();
            String actualRuleId = results.get(index).ruleId();
            if (!expectedRuleId.equals(actualRuleId)) {
                throw new IllegalArgumentException("rule result id must match registry rule id at index " + index
                        + ": expected " + expectedRuleId + " but was " + actualRuleId);
            }
        }
        results = List.copyOf(results);
    }

    public static GpuIrOptimizationValidationRuleArtifactReport evaluate(
            GpuIrOptimizationValidationReport validationReport,
            GpuIrOptimizationValidationRuleRegistry registry
    ) {
        Objects.requireNonNull(registry, "registry");
        List<GpuIrOptimizationValidationRuleResult> results = registry.evaluate(
                new GpuIrOptimizationValidationRuleContext(validationReport)
        );
        return new GpuIrOptimizationValidationRuleArtifactReport(validationReport, registry, results);
    }

    public static GpuIrOptimizationValidationRuleArtifactReport evaluateDefault(
            GpuIrOptimizationValidationReport validationReport
    ) {
        return evaluate(validationReport, GpuIrOptimizationValidationRules.defaultRegistry());
    }

    public boolean passed() {
        return results.stream().allMatch(GpuIrOptimizationValidationRuleResult::passed);
    }

    public String verdict() {
        if (!passed()) {
            return "fail";
        }
        if (warningCount() > 0) {
            return "warn";
        }
        return "pass";
    }

    public int ruleCount() {
        return registry.rules().size();
    }

    public int resultCount() {
        return results.size();
    }

    public int failedCount() {
        return (int) results.stream()
                .filter(result -> !result.passed())
                .count();
    }

    public boolean hasFailures() {
        return failedCount() > 0;
    }

    public int warningCount() {
        return (int) results.stream()
                .filter(result -> result.status() == GpuIrOptimizationValidationRuleStatus.WARN)
                .count();
    }

    public boolean hasWarnings() {
        return warningCount() > 0;
    }

    public int blockingCount() {
        return (int) results.stream()
                .filter(GpuIrOptimizationValidationRuleResult::blocking)
                .count();
    }

    public boolean hasBlockingResults() {
        return blockingCount() > 0;
    }

    public String failedRuleIds() {
        return results.stream()
                .filter(result -> !result.passed())
                .map(GpuIrOptimizationValidationRuleResult::ruleId)
                .collect(java.util.stream.Collectors.joining(","));
    }

    public String warningRuleIds() {
        return results.stream()
                .filter(result -> result.status() == GpuIrOptimizationValidationRuleStatus.WARN)
                .map(GpuIrOptimizationValidationRuleResult::ruleId)
                .collect(java.util.stream.Collectors.joining(","));
    }

    public String blockingRuleIds() {
        return results.stream()
                .filter(GpuIrOptimizationValidationRuleResult::blocking)
                .map(GpuIrOptimizationValidationRuleResult::ruleId)
                .collect(java.util.stream.Collectors.joining(","));
    }

    public String ruleIndex() {
        return registry.ruleIndex(results);
    }

    public String warningRuleIndex() {
        return registry.warningRuleIndex(results);
    }

    public String blockingRuleIndex() {
        return registry.blockingRuleIndex(results);
    }

    public Optional<GpuIrOptimizationValidationRuleResult> firstFailedResult() {
        return results.stream()
                .filter(result -> !result.passed())
                .findFirst();
    }

    public Optional<GpuIrOptimizationValidationRuleResult> firstWarningResult() {
        return results.stream()
                .filter(result -> result.status() == GpuIrOptimizationValidationRuleStatus.WARN)
                .findFirst();
    }

    public Optional<GpuIrOptimizationValidationRuleResult> firstBlockingResult() {
        return results.stream()
                .filter(GpuIrOptimizationValidationRuleResult::blocking)
                .findFirst();
    }

    public Map<GpuIrOptimizationValidationRuleStatus, Long> statusCounts() {
        Map<GpuIrOptimizationValidationRuleStatus, Long> counts = new java.util.LinkedHashMap<>();
        results.forEach(result -> counts.merge(result.status(), 1L, Long::sum));
        return Collections.unmodifiableMap(counts);
    }

    public String statusCountsSummary() {
        return statusCounts().entrySet().stream()
                .map(entry -> entry.getKey().artifactValue() + "=" + entry.getValue())
                .collect(java.util.stream.Collectors.joining(",", "{", "}"));
    }

    public Map<String, Long> ruleFamilyCounts() {
        return familyCounts(results);
    }

    public Map<String, Long> failedRuleFamilyCounts() {
        return familyCounts(results.stream()
                .filter(result -> !result.passed())
                .toList());
    }

    public Map<String, Long> warningRuleFamilyCounts() {
        return familyCounts(results.stream()
                .filter(result -> result.status() == GpuIrOptimizationValidationRuleStatus.WARN)
                .toList());
    }

    public Map<String, Long> blockingRuleFamilyCounts() {
        return familyCounts(results.stream()
                .filter(GpuIrOptimizationValidationRuleResult::blocking)
                .toList());
    }

    public String ruleFamilyCountsSummary() {
        return countsSummary(ruleFamilyCounts());
    }

    public String warningRuleFamilyCountsSummary() {
        return countsSummary(warningRuleFamilyCounts());
    }

    public String blockingRuleFamilyCountsSummary() {
        return countsSummary(blockingRuleFamilyCounts());
    }

    public String failedRuleFamilyCountsSummary() {
        return countsSummary(failedRuleFamilyCounts());
    }

    public GpuIrOptimizationValidationRuleArtifactSummary artifactSummary() {
        return new GpuIrOptimizationValidationRuleArtifactSummary(
                validationReport.methodName(),
                verdict(),
                passed(),
                ruleCount(),
                resultCount(),
                failedCount(),
                warningCount(),
                blockingCount(),
                failedRuleIds(),
                warningRuleIds(),
                blockingRuleIds(),
                ruleIndex(),
                warningRuleIndex(),
                blockingRuleIndex(),
                firstFailedResult().map(GpuIrOptimizationValidationRuleResult::ruleId).orElse(""),
                firstWarningResult().map(GpuIrOptimizationValidationRuleResult::ruleId).orElse(""),
                firstBlockingResult().map(GpuIrOptimizationValidationRuleResult::ruleId).orElse(""),
                statusCountsSummary(),
                ruleFamilyCountsSummary(),
                warningRuleFamilyCountsSummary(),
                blockingRuleFamilyCountsSummary(),
                failedRuleFamilyCountsSummary()
        );
    }

    public String ciSummaryLine() {
        return artifactSummary().summaryLine();
    }

    public String summary() {
        return ciSummaryLine();
    }

    public GpuIrOptimizationValidationRuleArtifactConsistencyReport consistencyReport() {
        return GpuIrOptimizationValidationRuleArtifactConsistencyReport.from(this);
    }

    public Map<String, String> artifactFields(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            throw new IllegalArgumentException("prefix must not be blank");
        }
        Map<String, String> values = new java.util.LinkedHashMap<>();
        values.put(prefix + "Method", validationReport.methodName());
        values.put(prefix + "Verdict", verdict());
        values.put(prefix + "Passed", Boolean.toString(passed()));
        values.put(prefix + "Rules", Integer.toString(ruleCount()));
        values.put(prefix + "Results", Integer.toString(resultCount()));
        values.put(prefix + "Failed", Integer.toString(failedCount()));
        values.put(prefix + "HasFailures", Boolean.toString(hasFailures()));
        values.put(prefix + "Warnings", Integer.toString(warningCount()));
        values.put(prefix + "HasWarnings", Boolean.toString(hasWarnings()));
        values.put(prefix + "Blocking", Integer.toString(blockingCount()));
        values.put(prefix + "HasBlockingResults", Boolean.toString(hasBlockingResults()));
        values.put(prefix + "FailedRuleIds", failedRuleIds());
        values.put(prefix + "WarningRuleIds", warningRuleIds());
        values.put(prefix + "BlockingRuleIds", blockingRuleIds());
        values.put(prefix + "RuleIndex", ruleIndex());
        values.put(prefix + "WarningRuleIndex", warningRuleIndex());
        values.put(prefix + "BlockingRuleIndex", blockingRuleIndex());
        firstFailedResult().ifPresent(result -> putFirstResultFields(values, prefix + "FirstFailed", result));
        firstWarningResult().ifPresent(result -> putFirstResultFields(values, prefix + "FirstWarning", result));
        firstBlockingResult().ifPresent(result -> putFirstResultFields(values, prefix + "FirstBlocking", result));
        values.put(prefix + "StatusCounts", statusCountsSummary());
        statusCounts().forEach((status, count) -> values.put(
                prefix + "Status." + status.artifactValue(),
                Long.toString(count)
        ));
        values.put(prefix + "RuleFamilyCounts", ruleFamilyCountsSummary());
        ruleFamilyCounts().forEach((family, count) -> values.put(
                prefix + "RuleFamily." + family,
                Long.toString(count)
        ));
        values.put(prefix + "WarningRuleFamilyCounts", warningRuleFamilyCountsSummary());
        warningRuleFamilyCounts().forEach((family, count) -> values.put(
                prefix + "WarningRuleFamily." + family,
                Long.toString(count)
        ));
        values.put(prefix + "BlockingRuleFamilyCounts", blockingRuleFamilyCountsSummary());
        blockingRuleFamilyCounts().forEach((family, count) -> values.put(
                prefix + "BlockingRuleFamily." + family,
                Long.toString(count)
        ));
        values.put(prefix + "FailedRuleFamilyCounts", failedRuleFamilyCountsSummary());
        failedRuleFamilyCounts().forEach((family, count) -> values.put(
                prefix + "FailedRuleFamily." + family,
                Long.toString(count)
        ));
        values.put(prefix + "Summary", summary());
        values.put(prefix + "CiSummaryLine", ciSummaryLine());
        values.putAll(consistencyReport().artifactFields(prefix + "Consistency."));
        values.putAll(registry.artifactFields(prefix + "Registry", results));
        return Collections.unmodifiableMap(values);
    }

    public Map<String, String> artifactFields() {
        return artifactFields("validationRules");
    }

    private static Map<String, Long> familyCounts(List<GpuIrOptimizationValidationRuleResult> results) {
        Map<String, Long> counts = new java.util.LinkedHashMap<>();
        results.forEach(result -> counts.merge(ruleFamily(result.ruleId()), 1L, Long::sum));
        return Collections.unmodifiableMap(counts);
    }

    private static String ruleFamily(String ruleId) {
        int separator = ruleId.indexOf('.');
        if (separator <= 0) {
            return ruleId;
        }
        return ruleId.substring(0, separator);
    }

    private static String countsSummary(Map<String, Long> counts) {
        return counts.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(java.util.stream.Collectors.joining(",", "{", "}"));
    }

    private static void putFirstResultFields(
            Map<String, String> values,
            String prefix,
            GpuIrOptimizationValidationRuleResult result
    ) {
        values.put(prefix + "RuleId", result.ruleId());
        values.put(prefix + "Status", result.status().artifactValue());
        values.put(prefix + "Blocking", Boolean.toString(result.blocking()));
        values.put(prefix + "Message", result.message());
    }
}
