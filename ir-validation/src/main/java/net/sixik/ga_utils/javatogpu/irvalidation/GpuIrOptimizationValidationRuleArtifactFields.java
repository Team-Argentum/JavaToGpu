package net.sixik.ga_utils.javatogpu.irvalidation;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Opt-in field writer for validation-rule registry artifacts.
 *
 * <p>This utility is intentionally separate from {@link GpuIrOptimizationValidationProvider} so
 * callers can merge rule-registry fields into build reports without changing normal compiler
 * validation behavior.</p>
 */
public final class GpuIrOptimizationValidationRuleArtifactFields {
    public static final String DEFAULT_PREFIX = "validationRules";
    public static final String DEFAULT_ACCEPTANCE_PREFIX = "validationRulesAcceptance";

    private GpuIrOptimizationValidationRuleArtifactFields() {
    }

    public static Map<String, String> fields(GpuIrOptimizationValidationRuleArtifactReport report) {
        return fields(DEFAULT_PREFIX, report);
    }

    public static Map<String, String> fields(
            String prefix,
            GpuIrOptimizationValidationRuleArtifactReport report
    ) {
        Map<String, String> values = new LinkedHashMap<>();
        putFields(values, prefix, report);
        return Map.copyOf(values);
    }

    public static Map<String, String> acceptanceFields(
            GpuIrOptimizationValidationRuleArtifactReport report
    ) {
        return acceptanceFields(DEFAULT_ACCEPTANCE_PREFIX, report);
    }

    public static Map<String, String> acceptanceFields(
            String prefix,
            GpuIrOptimizationValidationRuleArtifactReport report
    ) {
        Map<String, String> values = new LinkedHashMap<>();
        putAcceptanceFields(values, prefix, report);
        return Map.copyOf(values);
    }

    public static Map<String, String> fieldsWithAcceptance(
            GpuIrOptimizationValidationRuleArtifactReport report
    ) {
        return fieldsWithAcceptance(DEFAULT_PREFIX, DEFAULT_ACCEPTANCE_PREFIX, report);
    }

    public static Map<String, String> fieldsWithAcceptance(
            String rulePrefix,
            String acceptancePrefix,
            GpuIrOptimizationValidationRuleArtifactReport report
    ) {
        Map<String, String> values = new LinkedHashMap<>();
        putFieldsWithAcceptance(values, rulePrefix, acceptancePrefix, report);
        return Map.copyOf(values);
    }

    public static void putFields(
            Map<String, String> values,
            GpuIrOptimizationValidationRuleArtifactReport report
    ) {
        putFields(values, DEFAULT_PREFIX, report);
    }

    public static void putFields(
            Map<String, String> values,
            String prefix,
            GpuIrOptimizationValidationRuleArtifactReport report
    ) {
        Objects.requireNonNull(values, "values");
        Objects.requireNonNull(report, "report");
        if (prefix == null || prefix.isBlank()) {
            throw new IllegalArgumentException("prefix must not be blank");
        }
        values.putAll(report.artifactFields(prefix));
    }

    public static void putAcceptanceFields(
            Map<String, String> values,
            GpuIrOptimizationValidationRuleArtifactReport report
    ) {
        putAcceptanceFields(values, DEFAULT_ACCEPTANCE_PREFIX, report);
    }

    public static void putAcceptanceFields(
            Map<String, String> values,
            String prefix,
            GpuIrOptimizationValidationRuleArtifactReport report
    ) {
        Objects.requireNonNull(values, "values");
        Objects.requireNonNull(report, "report");
        values.putAll(GpuIrOptimizationValidationRuleArtifactAcceptance.from(report).artifactFields(prefix));
    }

    public static void putFieldsWithAcceptance(
            Map<String, String> values,
            GpuIrOptimizationValidationRuleArtifactReport report
    ) {
        putFieldsWithAcceptance(values, DEFAULT_PREFIX, DEFAULT_ACCEPTANCE_PREFIX, report);
    }

    public static void putFieldsWithAcceptance(
            Map<String, String> values,
            String rulePrefix,
            String acceptancePrefix,
            GpuIrOptimizationValidationRuleArtifactReport report
    ) {
        Objects.requireNonNull(values, "values");
        Objects.requireNonNull(report, "report");
        putFields(values, rulePrefix, report);
        putAcceptanceFields(values, acceptancePrefix, report);
    }
}
