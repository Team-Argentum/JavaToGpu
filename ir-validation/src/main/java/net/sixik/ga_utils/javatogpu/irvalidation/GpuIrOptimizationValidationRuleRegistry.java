package net.sixik.ga_utils.javatogpu.irvalidation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Ordered opt-in registry for read-only optimizer validation rules.
 */
public record GpuIrOptimizationValidationRuleRegistry(
        List<GpuIrOptimizationValidationRule> rules
) {
    public GpuIrOptimizationValidationRuleRegistry {
        Objects.requireNonNull(rules, "rules");
        if (rules.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("rules must not contain null entries");
        }
        rules.forEach(rule -> requireRuleId(rule.id()));
        Map<String, Long> idCounts = rules.stream()
                .collect(Collectors.groupingBy(GpuIrOptimizationValidationRule::id, LinkedHashMap::new, Collectors.counting()));
        idCounts.entrySet().stream()
                .filter(entry -> entry.getValue() > 1)
                .findFirst()
                .ifPresent(entry -> {
                    throw new IllegalArgumentException("duplicate validation rule id: " + entry.getKey());
                });
        rules = List.copyOf(rules);
    }

    public static GpuIrOptimizationValidationRuleRegistry of(List<GpuIrOptimizationValidationRule> rules) {
        return new GpuIrOptimizationValidationRuleRegistry(rules);
    }

    public List<GpuIrOptimizationValidationRuleResult> evaluate(GpuIrOptimizationValidationRuleContext context) {
        Objects.requireNonNull(context, "context");
        List<GpuIrOptimizationValidationRuleResult> results = new java.util.ArrayList<>(rules.size());
        for (GpuIrOptimizationValidationRule rule : rules) {
            GpuIrOptimizationValidationRuleResult result = Objects.requireNonNull(
                    rule.evaluate(context),
                    "validation rule result must not be null: " + rule.id()
            );
            if (!rule.id().equals(result.ruleId())) {
                throw new IllegalStateException("validation rule result id must match rule id: expected "
                        + rule.id() + " but was " + result.ruleId());
            }
            results.add(result);
        }
        return List.copyOf(results);
    }

    public Map<String, String> artifactFields(
            String prefix,
            List<GpuIrOptimizationValidationRuleResult> results
    ) {
        if (prefix == null || prefix.isBlank()) {
            throw new IllegalArgumentException("prefix must not be blank");
        }
        validateResults(results);
        Map<String, String> values = new LinkedHashMap<>();
        values.put(prefix + "Rules", Integer.toString(rules.size()));
        values.put(prefix + "Results", Integer.toString(results.size()));
        values.put(prefix + "Passed", Boolean.toString(results.stream().allMatch(GpuIrOptimizationValidationRuleResult::passed)));
        values.put(prefix + "RuleIndex", ruleIndex(results));
        values.put(prefix + "WarningRuleIndex", warningRuleIndex(results));
        values.put(prefix + "BlockingRuleIndex", blockingRuleIndex(results));
        for (int index = 0; index < results.size(); index++) {
            values.putAll(results.get(index).artifactFields(prefix + "Result." + index + "."));
        }
        return Collections.unmodifiableMap(values);
    }

    /**
     * Builds a compact ordered status index for CI logs and report summaries.
     */
    public String ruleIndex(List<GpuIrOptimizationValidationRuleResult> results) {
        validateResults(results);
        return results.stream()
                .map(result -> result.ruleId() + "=" + result.status().artifactValue())
                .collect(Collectors.joining(",", "[", "]"));
    }

    /**
     * Builds a compact ordered status index for warning rule results only.
     */
    public String warningRuleIndex(List<GpuIrOptimizationValidationRuleResult> results) {
        validateResults(results);
        return results.stream()
                .filter(result -> result.status() == GpuIrOptimizationValidationRuleStatus.WARN)
                .map(result -> result.ruleId() + "=" + result.status().artifactValue())
                .collect(Collectors.joining(",", "[", "]"));
    }

    /**
     * Builds a compact ordered status index for blocking rule results only.
     */
    public String blockingRuleIndex(List<GpuIrOptimizationValidationRuleResult> results) {
        validateResults(results);
        return results.stream()
                .filter(GpuIrOptimizationValidationRuleResult::blocking)
                .map(result -> result.ruleId() + "=" + result.status().artifactValue())
                .collect(Collectors.joining(",", "[", "]"));
    }

    private void validateResults(List<GpuIrOptimizationValidationRuleResult> results) {
        Objects.requireNonNull(results, "results");
        if (results.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("results must not contain null entries");
        }
        if (rules.size() != results.size()) {
            throw new IllegalArgumentException("rule result count must match registry rule count");
        }
        for (int index = 0; index < results.size(); index++) {
            String expectedRuleId = rules.get(index).id();
            String actualRuleId = results.get(index).ruleId();
            if (!expectedRuleId.equals(actualRuleId)) {
                throw new IllegalArgumentException("rule result id must match registry rule id at index " + index
                        + ": expected " + expectedRuleId + " but was " + actualRuleId);
            }
        }
    }

    private static String requireRuleId(String ruleId) {
        if (ruleId == null || ruleId.isBlank()) {
            throw new IllegalArgumentException("validation rule id must not be blank");
        }
        return ruleId;
    }
}
