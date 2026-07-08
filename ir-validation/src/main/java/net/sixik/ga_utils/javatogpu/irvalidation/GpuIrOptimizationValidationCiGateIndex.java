package net.sixik.ga_utils.javatogpu.irvalidation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Compact read-only index over the current optimizer CI gates.
 *
 * <p>The index intentionally reuses the dedicated CSE, auto-vectorization, and aggregate
 * optimizer readiness gate artifacts. It gives CI one stable entrypoint without teaching the
 * caller every individual runner method or artifact key.</p>
 */
public record GpuIrOptimizationValidationCiGateIndex(
        GpuIrCommonSubexpressionLayerReadinessCiSummary cseGate,
        GpuIrAutoVectorizationReadinessCiSummary autoVectorizationGate,
        GpuIrOptimizationValidationOptimizerLayerReadinessCiSummary optimizerGate
) {
    public GpuIrOptimizationValidationCiGateIndex {
        cseGate = Objects.requireNonNull(cseGate, "cseGate");
        autoVectorizationGate = Objects.requireNonNull(autoVectorizationGate, "autoVectorizationGate");
        optimizerGate = Objects.requireNonNull(optimizerGate, "optimizerGate");
        if (!cseGate.methodName().equals(autoVectorizationGate.methodName())) {
            throw new IllegalArgumentException("auto-vectorization gate method must match CSE gate method");
        }
        if (!cseGate.methodName().equals(optimizerGate.methodName())) {
            throw new IllegalArgumentException("optimizer gate method must match CSE gate method");
        }
    }

    public static GpuIrOptimizationValidationCiGateIndex from(
            GpuIrCommonSubexpressionLayerReadinessCiSummary cseGate,
            GpuIrAutoVectorizationReadinessCiSummary autoVectorizationGate,
            GpuIrOptimizationValidationOptimizerLayerReadinessCiSummary optimizerGate
    ) {
        return new GpuIrOptimizationValidationCiGateIndex(cseGate, autoVectorizationGate, optimizerGate);
    }

    public String methodName() {
        return cseGate.methodName();
    }

    public boolean accepted() {
        return cseGate.accepted() && autoVectorizationGate.accepted() && optimizerGate.accepted();
    }

    public boolean rejected() {
        return !accepted();
    }

    public String verdict() {
        return accepted() ? "accepted" : "rejected";
    }

    public String firstRejectedGate() {
        if (cseGate.rejected()) {
            return "cse";
        }
        if (autoVectorizationGate.rejected()) {
            return "autoVectorization";
        }
        if (optimizerGate.rejected()) {
            return "optimizer";
        }
        return "none";
    }

    public GpuIrOptimizationValidationCiGateIndexConsistencyReport consistencyReport() {
        return GpuIrOptimizationValidationCiGateIndexConsistencyReport.from(this);
    }

    public GpuIrOptimizationValidationCiGateIndexAcceptance acceptance() {
        return GpuIrOptimizationValidationCiGateIndexAcceptance.from(this);
    }

    public Map<String, String> artifactFields() {
        return artifactFields("optimizerCiGateIndex");
    }

    public Map<String, String> artifactFields(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            throw new IllegalArgumentException("prefix must not be blank");
        }
        Map<String, String> values = new LinkedHashMap<>();
        values.put(prefix + "Method", methodName());
        values.put(prefix + "Verdict", verdict());
        values.put(prefix + "Accepted", Boolean.toString(accepted()));
        values.put(prefix + "Rejected", Boolean.toString(rejected()));
        values.put(prefix + "FirstRejectedGate", firstRejectedGate());
        values.put(prefix + "CseVerdict", cseGate.verdict());
        values.put(prefix + "CseRuleVerdict", cseGate.ruleVerdict());
        values.put(prefix + "CseAccepted", Boolean.toString(cseGate.accepted()));
        values.put(prefix + "CseAcceptanceReason", cseGate.acceptanceReason());
        values.put(prefix + "AutoVectorizationVerdict", autoVectorizationGate.verdict());
        values.put(prefix + "AutoVectorizationRuleVerdict", autoVectorizationGate.ruleVerdict());
        values.put(prefix + "AutoVectorizationAccepted", Boolean.toString(autoVectorizationGate.accepted()));
        values.put(prefix + "AutoVectorizationAcceptanceReason", autoVectorizationGate.acceptanceReason());
        values.put(prefix + "OptimizerVerdict", optimizerGate.verdict());
        values.put(prefix + "OptimizerRuleVerdict", optimizerGate.ruleVerdict());
        values.put(prefix + "OptimizerAccepted", Boolean.toString(optimizerGate.accepted()));
        values.put(prefix + "OptimizerAcceptanceReason", optimizerGate.acceptanceReason());
        values.putAll(consistencyReport().artifactFields(prefix + "Consistency"));
        values.putAll(acceptance().artifactFields(prefix + "Acceptance"));
        GpuIrOptimizationValidationArtifactFieldMaps.putNestedFields(values, prefix + "CseGate.", cseGate.artifactFields());
        GpuIrOptimizationValidationArtifactFieldMaps.putNestedFields(values, prefix + "CseGate.Readiness.", cseGate.readiness().artifactFields());
        GpuIrOptimizationValidationArtifactFieldMaps.putNestedFields(values, prefix + "CseGate.RuleArtifact.", cseGate.ruleArtifact().artifactFields());
        GpuIrOptimizationValidationArtifactFieldMaps.putNestedFields(values, prefix + "CseGate.Acceptance.", cseGate.acceptance().artifactFields());
        GpuIrOptimizationValidationArtifactFieldMaps.putNestedFields(values, prefix + "AutoVectorizationGate.", autoVectorizationGate.artifactFields());
        GpuIrOptimizationValidationArtifactFieldMaps.putNestedFields(values, prefix + "AutoVectorizationGate.Readiness.", autoVectorizationGate.readiness().artifactFields());
        GpuIrOptimizationValidationArtifactFieldMaps.putNestedFields(values, prefix + "AutoVectorizationGate.RuleArtifact.", autoVectorizationGate.ruleArtifact().artifactFields());
        GpuIrOptimizationValidationArtifactFieldMaps.putNestedFields(values, prefix + "AutoVectorizationGate.Acceptance.", autoVectorizationGate.acceptance().artifactFields());
        GpuIrOptimizationValidationArtifactFieldMaps.putNestedFields(values, prefix + "OptimizerGate.", optimizerGate.artifactFields());
        GpuIrOptimizationValidationArtifactFieldMaps.putNestedFields(values, prefix + "OptimizerGate.Readiness.", optimizerGate.readiness().artifactFields());
        GpuIrOptimizationValidationArtifactFieldMaps.putNestedFields(values, prefix + "OptimizerGate.RuleArtifact.", optimizerGate.ruleArtifact().artifactFields());
        GpuIrOptimizationValidationArtifactFieldMaps.putNestedFields(values, prefix + "OptimizerGate.Acceptance.", optimizerGate.acceptance().artifactFields());
        values.put(prefix + "CiSummaryLine", ciSummaryLine());
        values.put(prefix + "Summary", summary());
        return Collections.unmodifiableMap(values);
    }

    public String ciSummaryLine() {
        return "optimizer ci gate index method=" + methodName()
                + " verdict=" + verdict()
                + " accepted=" + accepted()
                + " firstRejectedGate=" + firstRejectedGate()
                + " cse=" + cseGate.ruleVerdict() + "/" + cseGate.acceptanceReason()
                + " autoVectorization=" + autoVectorizationGate.ruleVerdict() + "/" + autoVectorizationGate.acceptanceReason()
                + " optimizer=" + optimizerGate.ruleVerdict() + "/" + optimizerGate.acceptanceReason();
    }

    public String summary() {
        return ciSummaryLine()
                + " cseSummary={" + cseGate.ciSummaryLine() + "}"
                + " autoVectorizationSummary={" + autoVectorizationGate.ciSummaryLine() + "}"
                + " optimizerSummary={" + optimizerGate.ciSummaryLine() + "}";
    }
}
