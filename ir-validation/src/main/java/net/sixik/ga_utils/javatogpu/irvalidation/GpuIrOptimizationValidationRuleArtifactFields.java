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
    public static final String DEFAULT_HANDOFF_PREFIX = "optimizerReadinessHandoff";
    public static final String DEFAULT_ENABLEMENT_POLICY_PREFIX = "optimizerEnablementPolicy";

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
            GpuIrOptimizationValidationRuleArtifactReport report,
            GpuIrOptimizationValidationRuleArtifactConsistencyReport consistencyReport
    ) {
        // Explicit consistency reports let CI/tooling reject externally detected artifact drift.
        return acceptanceFields(DEFAULT_ACCEPTANCE_PREFIX, report, consistencyReport);
    }

    public static Map<String, String> acceptanceFields(
            String prefix,
            GpuIrOptimizationValidationRuleArtifactReport report
    ) {
        Map<String, String> values = new LinkedHashMap<>();
        putAcceptanceFields(values, prefix, report);
        return Map.copyOf(values);
    }

    public static Map<String, String> acceptanceFields(
            String prefix,
            GpuIrOptimizationValidationRuleArtifactReport report,
            GpuIrOptimizationValidationRuleArtifactConsistencyReport consistencyReport
    ) {
        Map<String, String> values = new LinkedHashMap<>();
        putAcceptanceFields(values, prefix, report, consistencyReport);
        return Map.copyOf(values);
    }

    public static Map<String, String> fieldsWithAcceptance(
            GpuIrOptimizationValidationRuleArtifactReport report
    ) {
        return fieldsWithAcceptance(DEFAULT_PREFIX, DEFAULT_ACCEPTANCE_PREFIX, report);
    }

    public static Map<String, String> fieldsWithAcceptance(
            GpuIrOptimizationValidationRuleArtifactReport report,
            GpuIrOptimizationValidationRuleArtifactConsistencyReport consistencyReport
    ) {
        // Keep the full artifact export stable while letting acceptance fail closed on drift.
        return fieldsWithAcceptance(DEFAULT_PREFIX, DEFAULT_ACCEPTANCE_PREFIX, report, consistencyReport);
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

    public static Map<String, String> fieldsWithAcceptance(
            String rulePrefix,
            String acceptancePrefix,
            GpuIrOptimizationValidationRuleArtifactReport report,
            GpuIrOptimizationValidationRuleArtifactConsistencyReport consistencyReport
    ) {
        Map<String, String> values = new LinkedHashMap<>();
        putFieldsWithAcceptance(values, rulePrefix, acceptancePrefix, report, consistencyReport);
        return Map.copyOf(values);
    }

    public static Map<String, String> fieldsWithAcceptanceAndHandoff(
            GpuIrOptimizationValidationRuleArtifactReport report
    ) {
        return fieldsWithAcceptanceAndHandoff(DEFAULT_PREFIX, DEFAULT_ACCEPTANCE_PREFIX, DEFAULT_HANDOFF_PREFIX, report);
    }

    public static Map<String, String> fieldsWithAcceptanceAndHandoff(
            GpuIrOptimizationValidationRuleArtifactReport report,
            GpuIrOptimizationValidationRuleArtifactConsistencyReport consistencyReport
    ) {
        return fieldsWithAcceptanceAndHandoff(
                DEFAULT_PREFIX,
                DEFAULT_ACCEPTANCE_PREFIX,
                DEFAULT_HANDOFF_PREFIX,
                report,
                consistencyReport
        );
    }

    public static Map<String, String> fieldsWithAcceptanceAndHandoff(
            String rulePrefix,
            String acceptancePrefix,
            String handoffPrefix,
            GpuIrOptimizationValidationRuleArtifactReport report
    ) {
        Map<String, String> values = new LinkedHashMap<>();
        putFieldsWithAcceptanceAndHandoff(values, rulePrefix, acceptancePrefix, handoffPrefix, report);
        return Map.copyOf(values);
    }

    public static Map<String, String> fieldsWithAcceptanceAndHandoff(
            String rulePrefix,
            String acceptancePrefix,
            String handoffPrefix,
            GpuIrOptimizationValidationRuleArtifactReport report,
            GpuIrOptimizationValidationRuleArtifactConsistencyReport consistencyReport
    ) {
        Map<String, String> values = new LinkedHashMap<>();
        putFieldsWithAcceptanceAndHandoff(values, rulePrefix, acceptancePrefix, handoffPrefix, report, consistencyReport);
        return Map.copyOf(values);
    }

    public static Map<String, String> fieldsWithAcceptanceHandoffAndPolicy(
            GpuIrOptimizationValidationRuleArtifactReport report
    ) {
        return optimizerEnablementArtifactFields(report);
    }

    public static Map<String, String> fieldsWithAcceptanceHandoffAndPolicy(
            GpuIrOptimizationValidationRuleArtifactReport report,
            GpuIrOptimizationValidationRuleArtifactConsistencyReport consistencyReport
    ) {
        return optimizerEnablementArtifactFields(report, consistencyReport);
    }

    public static Map<String, String> optimizerEnablementArtifactFields(
            GpuIrOptimizationValidationRuleArtifactReport report
    ) {
        return GpuIrOptimizationValidationOptimizerEnablementArtifact.from(report).artifactFields();
    }

    public static Map<String, String> optimizerEnablementArtifactFields(
            GpuIrOptimizationValidationRuleArtifactReport report,
            GpuIrOptimizationValidationRuleArtifactConsistencyReport consistencyReport
    ) {
        return GpuIrOptimizationValidationOptimizerEnablementArtifact.from(report, consistencyReport).artifactFields();
    }

    public static Map<String, String> fieldsWithAcceptanceHandoffAndPolicy(
            String rulePrefix,
            String acceptancePrefix,
            String handoffPrefix,
            String policyPrefix,
            GpuIrOptimizationValidationRuleArtifactReport report
    ) {
        Map<String, String> values = new LinkedHashMap<>();
        putFieldsWithAcceptanceHandoffAndPolicy(values, rulePrefix, acceptancePrefix, handoffPrefix, policyPrefix, report);
        return Map.copyOf(values);
    }

    public static Map<String, String> fieldsWithAcceptanceHandoffAndPolicy(
            String rulePrefix,
            String acceptancePrefix,
            String handoffPrefix,
            String policyPrefix,
            GpuIrOptimizationValidationRuleArtifactReport report,
            GpuIrOptimizationValidationRuleArtifactConsistencyReport consistencyReport
    ) {
        Map<String, String> values = new LinkedHashMap<>();
        putFieldsWithAcceptanceHandoffAndPolicy(values, rulePrefix, acceptancePrefix, handoffPrefix, policyPrefix, report, consistencyReport);
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
            GpuIrOptimizationValidationRuleArtifactReport report,
            GpuIrOptimizationValidationRuleArtifactConsistencyReport consistencyReport
    ) {
        putAcceptanceFields(values, DEFAULT_ACCEPTANCE_PREFIX, report, consistencyReport);
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

    public static void putAcceptanceFields(
            Map<String, String> values,
            String prefix,
            GpuIrOptimizationValidationRuleArtifactReport report,
            GpuIrOptimizationValidationRuleArtifactConsistencyReport consistencyReport
    ) {
        Objects.requireNonNull(values, "values");
        Objects.requireNonNull(report, "report");
        Objects.requireNonNull(consistencyReport, "consistencyReport");
        values.putAll(GpuIrOptimizationValidationRuleArtifactAcceptance.from(report, consistencyReport).artifactFields(prefix));
    }

    public static void putFieldsWithAcceptance(
            Map<String, String> values,
            GpuIrOptimizationValidationRuleArtifactReport report
    ) {
        putFieldsWithAcceptance(values, DEFAULT_PREFIX, DEFAULT_ACCEPTANCE_PREFIX, report);
    }

    public static void putFieldsWithAcceptance(
            Map<String, String> values,
            GpuIrOptimizationValidationRuleArtifactReport report,
            GpuIrOptimizationValidationRuleArtifactConsistencyReport consistencyReport
    ) {
        putFieldsWithAcceptance(values, DEFAULT_PREFIX, DEFAULT_ACCEPTANCE_PREFIX, report, consistencyReport);
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

    public static void putFieldsWithAcceptance(
            Map<String, String> values,
            String rulePrefix,
            String acceptancePrefix,
            GpuIrOptimizationValidationRuleArtifactReport report,
            GpuIrOptimizationValidationRuleArtifactConsistencyReport consistencyReport
    ) {
        Objects.requireNonNull(values, "values");
        Objects.requireNonNull(report, "report");
        Objects.requireNonNull(consistencyReport, "consistencyReport");
        putFields(values, rulePrefix, report);
        putAcceptanceFields(values, acceptancePrefix, report, consistencyReport);
    }

    public static void putHandoffFields(
            Map<String, String> values,
            GpuIrOptimizationValidationRuleArtifactReport report
    ) {
        putHandoffFields(values, DEFAULT_HANDOFF_PREFIX, report);
    }

    public static void putHandoffFields(
            Map<String, String> values,
            GpuIrOptimizationValidationRuleArtifactReport report,
            GpuIrOptimizationValidationRuleArtifactConsistencyReport consistencyReport
    ) {
        putHandoffFields(values, DEFAULT_HANDOFF_PREFIX, report, consistencyReport);
    }

    public static void putHandoffFields(
            Map<String, String> values,
            String prefix,
            GpuIrOptimizationValidationRuleArtifactReport report
    ) {
        Objects.requireNonNull(values, "values");
        Objects.requireNonNull(report, "report");
        values.putAll(GpuIrOptimizationValidationOptimizerReadinessHandoffReport.from(report).artifactFields(prefix));
    }

    public static void putHandoffFields(
            Map<String, String> values,
            String prefix,
            GpuIrOptimizationValidationRuleArtifactReport report,
            GpuIrOptimizationValidationRuleArtifactConsistencyReport consistencyReport
    ) {
        Objects.requireNonNull(values, "values");
        Objects.requireNonNull(report, "report");
        Objects.requireNonNull(consistencyReport, "consistencyReport");
        values.putAll(GpuIrOptimizationValidationOptimizerReadinessHandoffReport.from(report, consistencyReport)
                .artifactFields(prefix));
    }

    public static void putFieldsWithAcceptanceAndHandoff(
            Map<String, String> values,
            GpuIrOptimizationValidationRuleArtifactReport report
    ) {
        putFieldsWithAcceptanceAndHandoff(values, DEFAULT_PREFIX, DEFAULT_ACCEPTANCE_PREFIX, DEFAULT_HANDOFF_PREFIX, report);
    }

    public static void putFieldsWithAcceptanceAndHandoff(
            Map<String, String> values,
            GpuIrOptimizationValidationRuleArtifactReport report,
            GpuIrOptimizationValidationRuleArtifactConsistencyReport consistencyReport
    ) {
        putFieldsWithAcceptanceAndHandoff(
                values,
                DEFAULT_PREFIX,
                DEFAULT_ACCEPTANCE_PREFIX,
                DEFAULT_HANDOFF_PREFIX,
                report,
                consistencyReport
        );
    }

    public static void putFieldsWithAcceptanceAndHandoff(
            Map<String, String> values,
            String rulePrefix,
            String acceptancePrefix,
            String handoffPrefix,
            GpuIrOptimizationValidationRuleArtifactReport report
    ) {
        Objects.requireNonNull(values, "values");
        Objects.requireNonNull(report, "report");
        putFieldsWithAcceptance(values, rulePrefix, acceptancePrefix, report);
        putHandoffFields(values, handoffPrefix, report);
    }

    public static void putFieldsWithAcceptanceAndHandoff(
            Map<String, String> values,
            String rulePrefix,
            String acceptancePrefix,
            String handoffPrefix,
            GpuIrOptimizationValidationRuleArtifactReport report,
            GpuIrOptimizationValidationRuleArtifactConsistencyReport consistencyReport
    ) {
        Objects.requireNonNull(values, "values");
        Objects.requireNonNull(report, "report");
        Objects.requireNonNull(consistencyReport, "consistencyReport");
        putFieldsWithAcceptance(values, rulePrefix, acceptancePrefix, report, consistencyReport);
        putHandoffFields(values, handoffPrefix, report, consistencyReport);
    }

    public static void putEnablementPolicyFields(
            Map<String, String> values,
            GpuIrOptimizationValidationRuleArtifactReport report
    ) {
        putEnablementPolicyFields(values, DEFAULT_ENABLEMENT_POLICY_PREFIX, report);
    }

    public static void putEnablementPolicyFields(
            Map<String, String> values,
            GpuIrOptimizationValidationRuleArtifactReport report,
            GpuIrOptimizationValidationRuleArtifactConsistencyReport consistencyReport
    ) {
        putEnablementPolicyFields(values, DEFAULT_ENABLEMENT_POLICY_PREFIX, report, consistencyReport);
    }

    public static void putEnablementPolicyFields(
            Map<String, String> values,
            String prefix,
            GpuIrOptimizationValidationRuleArtifactReport report
    ) {
        Objects.requireNonNull(values, "values");
        Objects.requireNonNull(report, "report");
        values.putAll(GpuIrOptimizationValidationOptimizerEnablementPolicyDecision.from(
                GpuIrOptimizationValidationOptimizerReadinessHandoffReport.from(report)
        ).artifactFields(prefix));
    }

    public static void putEnablementPolicyFields(
            Map<String, String> values,
            String prefix,
            GpuIrOptimizationValidationRuleArtifactReport report,
            GpuIrOptimizationValidationRuleArtifactConsistencyReport consistencyReport
    ) {
        Objects.requireNonNull(values, "values");
        Objects.requireNonNull(report, "report");
        Objects.requireNonNull(consistencyReport, "consistencyReport");
        values.putAll(GpuIrOptimizationValidationOptimizerEnablementPolicyDecision.from(
                GpuIrOptimizationValidationOptimizerReadinessHandoffReport.from(report, consistencyReport)
        ).artifactFields(prefix));
    }

    public static void putFieldsWithAcceptanceHandoffAndPolicy(
            Map<String, String> values,
            GpuIrOptimizationValidationRuleArtifactReport report
    ) {
        putOptimizerEnablementArtifactFields(values, report);
    }

    public static void putFieldsWithAcceptanceHandoffAndPolicy(
            Map<String, String> values,
            GpuIrOptimizationValidationRuleArtifactReport report,
            GpuIrOptimizationValidationRuleArtifactConsistencyReport consistencyReport
    ) {
        putOptimizerEnablementArtifactFields(values, report, consistencyReport);
    }

    public static void putOptimizerEnablementArtifactFields(
            Map<String, String> values,
            GpuIrOptimizationValidationRuleArtifactReport report
    ) {
        GpuIrOptimizationValidationOptimizerEnablementArtifact.from(report).putArtifactFields(values);
    }

    public static void putOptimizerEnablementArtifactFields(
            Map<String, String> values,
            GpuIrOptimizationValidationRuleArtifactReport report,
            GpuIrOptimizationValidationRuleArtifactConsistencyReport consistencyReport
    ) {
        GpuIrOptimizationValidationOptimizerEnablementArtifact.from(report, consistencyReport).putArtifactFields(values);
    }

    public static void putFieldsWithAcceptanceHandoffAndPolicy(
            Map<String, String> values,
            String rulePrefix,
            String acceptancePrefix,
            String handoffPrefix,
            String policyPrefix,
            GpuIrOptimizationValidationRuleArtifactReport report
    ) {
        Objects.requireNonNull(values, "values");
        Objects.requireNonNull(report, "report");
        putFieldsWithAcceptanceAndHandoff(values, rulePrefix, acceptancePrefix, handoffPrefix, report);
        putEnablementPolicyFields(values, policyPrefix, report);
    }

    public static void putFieldsWithAcceptanceHandoffAndPolicy(
            Map<String, String> values,
            String rulePrefix,
            String acceptancePrefix,
            String handoffPrefix,
            String policyPrefix,
            GpuIrOptimizationValidationRuleArtifactReport report,
            GpuIrOptimizationValidationRuleArtifactConsistencyReport consistencyReport
    ) {
        Objects.requireNonNull(values, "values");
        Objects.requireNonNull(report, "report");
        Objects.requireNonNull(consistencyReport, "consistencyReport");
        putFieldsWithAcceptanceAndHandoff(values, rulePrefix, acceptancePrefix, handoffPrefix, report, consistencyReport);
        putEnablementPolicyFields(values, policyPrefix, report, consistencyReport);
    }
}
