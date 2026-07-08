package net.sixik.ga_utils.javatogpu.irvalidation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Typed read-only bundle for optimizer enablement tooling artifacts.
 *
 * <p>The bundle keeps the rule artifact, acceptance decision, handoff report, and conservative
 * enablement policy together so CI/tooling can consume one object instead of rebuilding the chain
 * manually. It remains intentionally disconnected from production optimizer mutation.</p>
 */
public record GpuIrOptimizationValidationOptimizerEnablementArtifact(
        GpuIrOptimizationValidationRuleArtifactReport ruleArtifact,
        GpuIrOptimizationValidationRuleArtifactAcceptance acceptance,
        GpuIrOptimizationValidationOptimizerReadinessHandoffReport handoff,
        GpuIrOptimizationValidationOptimizerEnablementPolicyDecision policy
) {
    public GpuIrOptimizationValidationOptimizerEnablementArtifact {
        ruleArtifact = Objects.requireNonNull(ruleArtifact, "ruleArtifact");
        acceptance = Objects.requireNonNull(acceptance, "acceptance");
        handoff = Objects.requireNonNull(handoff, "handoff");
        policy = Objects.requireNonNull(policy, "policy");
        if (!ruleArtifact.validationReport().methodName().equals(acceptance.methodName())) {
            throw new IllegalArgumentException("acceptance method must match rule artifact method");
        }
        if (!ruleArtifact.validationReport().methodName().equals(handoff.methodName())) {
            throw new IllegalArgumentException("handoff method must match rule artifact method");
        }
        if (!ruleArtifact.validationReport().methodName().equals(policy.methodName())) {
            throw new IllegalArgumentException("policy method must match rule artifact method");
        }
    }

    public static GpuIrOptimizationValidationOptimizerEnablementArtifact from(
            GpuIrOptimizationValidationRuleArtifactReport report
    ) {
        Objects.requireNonNull(report, "report");
        return from(report, report.consistencyReport());
    }

    public static GpuIrOptimizationValidationOptimizerEnablementArtifact from(
            GpuIrOptimizationValidationRuleArtifactReport report,
            GpuIrOptimizationValidationRuleArtifactConsistencyReport consistencyReport
    ) {
        Objects.requireNonNull(report, "report");
        Objects.requireNonNull(consistencyReport, "consistencyReport");
        GpuIrOptimizationValidationRuleArtifactAcceptance acceptance =
                GpuIrOptimizationValidationRuleArtifactAcceptance.from(report, consistencyReport);
        GpuIrOptimizationValidationOptimizerReadinessHandoffReport handoff =
                GpuIrOptimizationValidationOptimizerReadinessHandoffReport.from(report, consistencyReport);
        GpuIrOptimizationValidationOptimizerEnablementPolicyDecision policy =
                GpuIrOptimizationValidationOptimizerEnablementPolicyDecision.from(handoff);
        return new GpuIrOptimizationValidationOptimizerEnablementArtifact(report, acceptance, handoff, policy);
    }

    public String methodName() {
        return ruleArtifact.validationReport().methodName();
    }

    public boolean accepted() {
        return acceptance.accepted();
    }

    public boolean readyForOptimizerEnablementReview() {
        return handoff.readyForOptimizerEnablement();
    }

    public boolean allowOptimizerEnablementReview() {
        return policy.allowOptimizerEnablementReview();
    }

    public boolean productionMutationEnabled() {
        return policy.productionMutationEnabled();
    }

    public Map<String, String> artifactFields() {
        return artifactFields(
                GpuIrOptimizationValidationRuleArtifactFields.DEFAULT_PREFIX,
                GpuIrOptimizationValidationRuleArtifactFields.DEFAULT_ACCEPTANCE_PREFIX,
                GpuIrOptimizationValidationRuleArtifactFields.DEFAULT_HANDOFF_PREFIX,
                GpuIrOptimizationValidationRuleArtifactFields.DEFAULT_ENABLEMENT_POLICY_PREFIX
        );
    }

    public Map<String, String> artifactFields(
            String rulePrefix,
            String acceptancePrefix,
            String handoffPrefix,
            String policyPrefix
    ) {
        Map<String, String> values = new LinkedHashMap<>();
        putArtifactFields(values, rulePrefix, acceptancePrefix, handoffPrefix, policyPrefix);
        return Collections.unmodifiableMap(values);
    }

    public void putArtifactFields(Map<String, String> values) {
        putArtifactFields(
                values,
                GpuIrOptimizationValidationRuleArtifactFields.DEFAULT_PREFIX,
                GpuIrOptimizationValidationRuleArtifactFields.DEFAULT_ACCEPTANCE_PREFIX,
                GpuIrOptimizationValidationRuleArtifactFields.DEFAULT_HANDOFF_PREFIX,
                GpuIrOptimizationValidationRuleArtifactFields.DEFAULT_ENABLEMENT_POLICY_PREFIX
        );
    }

    public void putArtifactFields(
            Map<String, String> values,
            String rulePrefix,
            String acceptancePrefix,
            String handoffPrefix,
            String policyPrefix
    ) {
        Objects.requireNonNull(values, "values");
        values.putAll(ruleArtifact.artifactFields(rulePrefix));
        values.putAll(acceptance.artifactFields(acceptancePrefix));
        values.putAll(handoff.artifactFields(handoffPrefix));
        values.putAll(policy.artifactFields(policyPrefix));
    }
}
