package net.sixik.ga_utils.javatogpu.irvalidation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Typed read-only bundle for optimizer validation and future production enablement checks.
 *
 * <p>The bundle keeps the original validation report, the opt-in optimizer enablement artifact,
 * and the aggregate enablement gate together. It gives CI/tooling one object to pass around while
 * keeping rule-registry execution and production IR mutation detached from normal compiler
 * validation.</p>
 */
public record GpuIrOptimizationValidationOptimizerValidationBundle(
        GpuIrOptimizationValidationReport validationReport,
        GpuIrOptimizationValidationOptimizerEnablementArtifact enablementArtifact,
        GpuIrOptimizationValidationOptimizerEnablementGateReport enablementGate
) {
    public GpuIrOptimizationValidationOptimizerValidationBundle {
        validationReport = Objects.requireNonNull(validationReport, "validationReport");
        enablementArtifact = Objects.requireNonNull(enablementArtifact, "enablementArtifact");
        enablementGate = Objects.requireNonNull(enablementGate, "enablementGate");
        if (!validationReport.methodName().equals(enablementArtifact.methodName())) {
            throw new IllegalArgumentException("enablement artifact method must match validation report method");
        }
        if (!validationReport.methodName().equals(enablementGate.methodName())) {
            throw new IllegalArgumentException("enablement gate method must match validation report method");
        }
    }

    public static GpuIrOptimizationValidationOptimizerValidationBundle from(
            GpuIrOptimizationValidationReport validationReport
    ) {
        return from(validationReport, new GpuIrOptimizationValidationOptimizerEnablementArtifactRunner());
    }

    public static GpuIrOptimizationValidationOptimizerValidationBundle from(
            GpuIrOptimizationValidationReport validationReport,
            GpuIrOptimizationValidationOptimizerEnablementArtifactRunner runner
    ) {
        Objects.requireNonNull(validationReport, "validationReport");
        Objects.requireNonNull(runner, "runner");
        GpuIrOptimizationValidationOptimizerEnablementArtifact artifact = runner.run(validationReport);
        return new GpuIrOptimizationValidationOptimizerValidationBundle(
                validationReport,
                artifact,
                GpuIrOptimizationValidationOptimizerEnablementGateReport.from(validationReport, artifact)
        );
    }

    public String methodName() {
        return validationReport.methodName();
    }

    public boolean readyForProductionMutation() {
        return enablementGate.readyForProductionMutation();
    }

    public boolean productionMutationEnabled() {
        return enablementGate.productionMutationEnabled();
    }

    public GpuIrOptimizationValidationProductionEnablementPreflightDecision productionPreflightDecision() {
        return GpuIrOptimizationValidationProductionEnablementPreflightDecision.from(this);
    }

    public GpuIrOptimizationValidationProductionMutationSwitchContract productionMutationSwitchContract() {
        return GpuIrOptimizationValidationProductionMutationSwitchContract.from(productionPreflightDecision());
    }

    public GpuIrOptimizationValidationOptimizerPromotionConfidenceContract optimizerPromotionConfidenceContract() {
        return GpuIrOptimizationValidationOptimizerPromotionConfidenceContract.from(productionMutationSwitchContract());
    }

    public String verdict() {
        return enablementGate.verdict();
    }

    public Map<String, String> artifactFields() {
        Map<String, String> values = new LinkedHashMap<>();
        putArtifactFields(values);
        return Collections.unmodifiableMap(values);
    }

    public void putArtifactFields(Map<String, String> values) {
        Objects.requireNonNull(values, "values");
        values.put("optimizerValidationBundleMethod", methodName());
        values.put("optimizerValidationBundleVerdict", verdict());
        values.put("optimizerValidationBundleReadyForProductionMutation", Boolean.toString(readyForProductionMutation()));
        values.put("optimizerValidationBundleProductionMutationEnabled", Boolean.toString(productionMutationEnabled()));
        values.put("optimizerValidationBundleValidationHasSafetyError", Boolean.toString(validationReport.hasSafetyError()));
        values.put("optimizerValidationBundleValidationHasOptimizerDiagnostics", Boolean.toString(validationReport.hasOptimizerDiagnostics()));
        values.put("optimizerValidationBundleRuleArtifactVerdict", enablementArtifact.ruleArtifact().verdict());
        values.put("optimizerValidationBundleRuleArtifactAccepted", Boolean.toString(enablementArtifact.accepted()));
        values.put("optimizerValidationBundleEnablementPolicyVerdict", enablementArtifact.policy().verdict());
        values.put("optimizerValidationBundleGateFirstBlockingReason", enablementGate.firstBlockingReason().orElse(""));
        values.put("optimizerValidationBundleGateFirstRemainingWork", enablementGate.firstRemainingWork().orElse(""));
        values.put("optimizerValidationBundleCiSummaryLine", ciSummaryLine());
        values.put("optimizerValidationBundleSummary", summary());
        enablementArtifact.putArtifactFields(values);
        values.putAll(enablementGate.artifactFields());
        values.putAll(productionPreflightDecision().artifactFields());
        values.putAll(productionMutationSwitchContract().artifactFields());
        values.putAll(optimizerPromotionConfidenceContract().artifactFields());
    }

    public String ciSummaryLine() {
        return "optimizer validation bundle method=" + methodName()
                + " verdict=" + verdict()
                + " readyForProductionMutation=" + readyForProductionMutation()
                + " productionMutationEnabled=" + productionMutationEnabled()
                + enablementGate.firstBlockingReason()
                .map(reason -> " firstBlockingReason=" + reason)
                .orElse("");
    }

    public String summary() {
        return "optimizer validation bundle method=" + methodName()
                + " verdict=" + verdict()
                + " validationHasSafetyError=" + validationReport.hasSafetyError()
                + " validationHasOptimizerDiagnostics=" + validationReport.hasOptimizerDiagnostics()
                + " ruleArtifactVerdict=" + enablementArtifact.ruleArtifact().verdict()
                + " ruleArtifactAccepted=" + enablementArtifact.accepted()
                + " enablementPolicyVerdict=" + enablementArtifact.policy().verdict()
                + " gateVerdict=" + enablementGate.verdict()
                + " gateBlockingReasons=" + enablementGate.blockingReasons()
                + " gateRemainingWork=" + enablementGate.remainingWork();
    }
}
