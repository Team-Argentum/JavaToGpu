package net.sixik.ga_utils.javatogpu.irvalidation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Compact CI decision for the current optimizer-layer readiness gate.
 *
 * <p>This summary joins the raw layer-readiness rollup with the standard validation-rule artifact
 * and its acceptance decision. It is intentionally read-only and never enables optimizer
 * mutation.</p>
 */
public record GpuIrOptimizationValidationOptimizerLayerReadinessCiSummary(
        GpuIrOptimizationValidationOptimizerLayerReadinessSummaryReport readiness,
        GpuIrOptimizationValidationRuleArtifactReport ruleArtifact,
        GpuIrOptimizationValidationRuleArtifactAcceptance acceptance
) {
    public GpuIrOptimizationValidationOptimizerLayerReadinessCiSummary {
        readiness = Objects.requireNonNull(readiness, "readiness");
        ruleArtifact = Objects.requireNonNull(ruleArtifact, "ruleArtifact");
        acceptance = Objects.requireNonNull(acceptance, "acceptance");
        if (!readiness.methodName().equals(ruleArtifact.validationReport().methodName())) {
            throw new IllegalArgumentException("rule artifact method must match readiness method");
        }
        if (!readiness.methodName().equals(acceptance.methodName())) {
            throw new IllegalArgumentException("acceptance method must match readiness method");
        }
    }

    public static GpuIrOptimizationValidationOptimizerLayerReadinessCiSummary from(
            GpuIrOptimizationValidationOptimizerLayerReadinessSummaryReport readiness,
            GpuIrOptimizationValidationRuleArtifactReport ruleArtifact
    ) {
        return new GpuIrOptimizationValidationOptimizerLayerReadinessCiSummary(
                readiness,
                ruleArtifact,
                GpuIrOptimizationValidationRuleArtifactAcceptance.from(ruleArtifact)
        );
    }

    public String methodName() {
        return readiness.methodName();
    }

    public String verdict() {
        return readiness.verdict();
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
        return artifactFields("optimizerLayerReadinessCi");
    }

    public Map<String, String> artifactFields(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            throw new IllegalArgumentException("prefix must not be blank");
        }
        Map<String, String> values = new LinkedHashMap<>();
        values.put(prefix + "Method", methodName());
        values.put(prefix + "Verdict", verdict());
        values.put(prefix + "RuleVerdict", ruleVerdict());
        values.put(prefix + "Accepted", Boolean.toString(accepted()));
        values.put(prefix + "Rejected", Boolean.toString(rejected()));
        values.put(prefix + "AcceptedWithWarnings", Boolean.toString(acceptedWithWarnings()));
        values.put(prefix + "AcceptanceReason", acceptanceReason());
        values.put(prefix + "BlockingLayerCount", Integer.toString(readiness.blockingLayerCount()));
        values.put(prefix + "ReadyLayerCount", Integer.toString(readiness.readyLayerCount()));
        values.put(prefix + "FirstBlockingLayer", readiness.firstBlockingLayer());
        values.put(prefix + "CiSummaryLine", ciSummaryLine());
        values.put(prefix + "Summary", summary());
        return Collections.unmodifiableMap(values);
    }

    public String ciSummaryLine() {
        return "optimizer layer readiness ci method=" + methodName()
                + " verdict=" + verdict()
                + " ruleVerdict=" + ruleVerdict()
                + " accepted=" + accepted()
                + " acceptanceReason=" + acceptanceReason()
                + " blockingLayers=" + readiness.blockingLayers()
                + " firstBlockingLayer=" + readiness.firstBlockingLayer();
    }

    public String summary() {
        return ciSummaryLine()
                + " readinessSummary={" + readiness.ciSummaryLine() + "}"
                + " ruleSummary={" + ruleArtifact.ciSummaryLine() + "}"
                + " acceptanceSummary={" + acceptance.ciSummaryLine() + "}";
    }
}
