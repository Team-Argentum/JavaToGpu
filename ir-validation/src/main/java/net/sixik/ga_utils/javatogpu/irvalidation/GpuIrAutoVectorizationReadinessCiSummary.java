package net.sixik.ga_utils.javatogpu.irvalidation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Compact CI decision for the current auto-vectorization readiness gate.
 *
 * <p>This summary joins the raw auto-vectorization readiness rollup with the standard validation
 * rule artifact and its acceptance decision. It is read-only and never enables production
 * auto-vectorization mutation.</p>
 */
public record GpuIrAutoVectorizationReadinessCiSummary(
        GpuIrAutoVectorizationReadinessSummaryReport readiness,
        GpuIrOptimizationValidationRuleArtifactReport ruleArtifact,
        GpuIrOptimizationValidationRuleArtifactAcceptance acceptance
) {
    public GpuIrAutoVectorizationReadinessCiSummary {
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

    public static GpuIrAutoVectorizationReadinessCiSummary from(
            GpuIrAutoVectorizationReadinessSummaryReport readiness,
            GpuIrOptimizationValidationRuleArtifactReport ruleArtifact
    ) {
        return new GpuIrAutoVectorizationReadinessCiSummary(
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
        return artifactFields("autoVectorizationReadinessCi");
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
        values.put(prefix + "ReadyForPrototypeRewrite", Boolean.toString(readiness.readyForPrototypeRewrite()));
        values.put(prefix + "BlockingReasonCount", Integer.toString(readiness.blockingReasonCount()));
        values.put(prefix + "RemainingWorkCount", Integer.toString(readiness.remainingWorkCount()));
        readiness.firstBlockingReason().ifPresent(reason -> values.put(prefix + "FirstBlockingReason", reason));
        readiness.firstRemainingWork().ifPresent(work -> values.put(prefix + "FirstRemainingWork", work));
        values.put(prefix + "CiSummaryLine", ciSummaryLine());
        values.put(prefix + "Summary", summary());
        return Collections.unmodifiableMap(values);
    }

    public String ciSummaryLine() {
        return "auto-vectorization readiness ci method=" + methodName()
                + " verdict=" + verdict()
                + " ruleVerdict=" + ruleVerdict()
                + " accepted=" + accepted()
                + " acceptanceReason=" + acceptanceReason()
                + " blockers=" + readiness.blockingReasonCount()
                + readiness.firstBlockingReason().map(reason -> " firstBlockingReason=" + reason).orElse("");
    }

    public String summary() {
        return ciSummaryLine()
                + " readinessSummary={" + readiness.ciSummaryLine() + "}"
                + " ruleSummary={" + ruleArtifact.ciSummaryLine() + "}"
                + " acceptanceSummary={" + acceptance.ciSummaryLine() + "}";
    }
}
