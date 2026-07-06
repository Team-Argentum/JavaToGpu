package net.sixik.ga_utils.javatogpu.irvalidation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Compact CI summary for the optimizer-layer readiness regression gate.
 *
 * <p>This is a log-friendly view over the raw readiness-regression comparison, the validation
 * rule artifact, and the rule-artifact acceptance decision. It does not execute optimizer
 * mutation and only summarizes already-created read-only artifacts.</p>
 */
public record GpuIrOptimizationValidationOptimizerLayerReadinessRegressionCiSummary(
        GpuIrOptimizationValidationOptimizerLayerReadinessRegressionReport regression,
        GpuIrOptimizationValidationRuleArtifactReport ruleArtifact,
        GpuIrOptimizationValidationRuleArtifactAcceptance acceptance
) {
    public GpuIrOptimizationValidationOptimizerLayerReadinessRegressionCiSummary {
        regression = Objects.requireNonNull(regression, "regression");
        ruleArtifact = Objects.requireNonNull(ruleArtifact, "ruleArtifact");
        acceptance = Objects.requireNonNull(acceptance, "acceptance");
        if (!regression.methodName().equals(ruleArtifact.validationReport().methodName())) {
            throw new IllegalArgumentException("rule artifact method must match regression method");
        }
        if (!regression.methodName().equals(acceptance.methodName())) {
            throw new IllegalArgumentException("acceptance method must match regression method");
        }
    }

    public static GpuIrOptimizationValidationOptimizerLayerReadinessRegressionCiSummary from(
            GpuIrOptimizationValidationOptimizerLayerReadinessRegressionReport regression,
            GpuIrOptimizationValidationRuleArtifactReport ruleArtifact
    ) {
        return new GpuIrOptimizationValidationOptimizerLayerReadinessRegressionCiSummary(
                regression,
                ruleArtifact,
                GpuIrOptimizationValidationRuleArtifactAcceptance.from(ruleArtifact)
        );
    }

    public String methodName() {
        return regression.methodName();
    }

    public String outcome() {
        return regression.outcome();
    }

    public String ruleVerdict() {
        return ruleArtifact.verdict();
    }

    public String acceptanceReason() {
        return acceptance.reason();
    }

    public boolean accepted() {
        return acceptance.accepted();
    }

    public boolean rejected() {
        return acceptance.rejected();
    }

    public boolean acceptedWithWarnings() {
        return acceptance.acceptedWithWarnings();
    }

    public Map<String, String> artifactFields() {
        return artifactFields("optimizerLayerReadinessRegressionCi");
    }

    public Map<String, String> artifactFields(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            throw new IllegalArgumentException("prefix must not be blank");
        }
        Map<String, String> values = new LinkedHashMap<>();
        values.put(prefix + "Method", methodName());
        values.put(prefix + "Outcome", outcome());
        values.put(prefix + "RuleVerdict", ruleVerdict());
        values.put(prefix + "Accepted", Boolean.toString(accepted()));
        values.put(prefix + "Rejected", Boolean.toString(rejected()));
        values.put(prefix + "AcceptedWithWarnings", Boolean.toString(acceptedWithWarnings()));
        values.put(prefix + "AcceptanceReason", acceptanceReason());
        values.put(prefix + "BlockingLayerDelta", Integer.toString(regression.blockingLayerDelta()));
        values.put(prefix + "ReadyLayerDelta", Integer.toString(regression.readyLayerDelta()));
        values.put(prefix + "ChangedLayerCount", Integer.toString(regression.changedLayerCount()));
        regression.firstChangedLayer().ifPresent(layer -> values.put(prefix + "FirstChangedLayer", layer));
        regression.firstImprovedLayer().ifPresent(layer -> values.put(prefix + "FirstImprovedLayer", layer));
        regression.firstRegressedLayer().ifPresent(layer -> values.put(prefix + "FirstRegressedLayer", layer));
        GpuIrOptimizationValidationArtifactFieldMaps.putNestedFields(values, prefix + "Regression.", regression.artifactFields());
        GpuIrOptimizationValidationArtifactFieldMaps.putNestedFields(values, prefix + "RuleArtifact.", ruleArtifact.artifactFields());
        GpuIrOptimizationValidationArtifactFieldMaps.putNestedFields(values, prefix + "Acceptance.", acceptance.artifactFields());
        values.put(prefix + "CiSummaryLine", ciSummaryLine());
        values.put(prefix + "Summary", summary());
        return Collections.unmodifiableMap(values);
    }

    public String ciSummaryLine() {
        return "optimizer layer readiness regression ci method=" + methodName()
                + " outcome=" + outcome()
                + " ruleVerdict=" + ruleVerdict()
                + " accepted=" + accepted()
                + " acceptanceReason=" + acceptanceReason()
                + " blockingLayerDelta=" + regression.blockingLayerDelta()
                + " readyLayerDelta=" + regression.readyLayerDelta()
                + regression.firstChangedLayer().map(layer -> " firstChangedLayer=" + layer).orElse("");
    }

    public String summary() {
        return ciSummaryLine()
                + " regressionSummary={" + regression.ciSummaryLine() + "}"
                + " ruleSummary={" + ruleArtifact.ciSummaryLine() + "}"
                + " acceptanceSummary={" + acceptance.ciSummaryLine() + "}";
    }
}
