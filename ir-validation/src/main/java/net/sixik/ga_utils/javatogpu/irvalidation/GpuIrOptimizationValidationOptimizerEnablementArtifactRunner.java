package net.sixik.ga_utils.javatogpu.irvalidation;

import java.util.Map;
import java.util.Objects;

/**
 * Opt-in smoke runner for the full optimizer-enablement artifact chain.
 *
 * <p>The runner starts from an already-created validation report, evaluates the configured rule
 * registry, then packages rule fields, acceptance, handoff, and policy into one typed artifact.
 * It is deliberately detached from normal compiler validation and never enables production IR
 * mutation.</p>
 */
public final class GpuIrOptimizationValidationOptimizerEnablementArtifactRunner {
    private final GpuIrOptimizationValidationRuleArtifactRunner ruleArtifactRunner;

    public GpuIrOptimizationValidationOptimizerEnablementArtifactRunner() {
        this(new GpuIrOptimizationValidationRuleArtifactRunner());
    }

    public GpuIrOptimizationValidationOptimizerEnablementArtifactRunner(
            GpuIrOptimizationValidationRuleRegistry registry
    ) {
        this(new GpuIrOptimizationValidationRuleArtifactRunner(registry));
    }

    public GpuIrOptimizationValidationOptimizerEnablementArtifactRunner(
            GpuIrOptimizationValidationRuleArtifactRunner ruleArtifactRunner
    ) {
        this.ruleArtifactRunner = Objects.requireNonNull(ruleArtifactRunner, "ruleArtifactRunner");
    }

    public GpuIrOptimizationValidationOptimizerEnablementArtifact run(
            GpuIrOptimizationValidationReport validationReport
    ) {
        return GpuIrOptimizationValidationOptimizerEnablementArtifact.from(
                ruleArtifactRunner.run(validationReport)
        );
    }

    public Map<String, String> runArtifactFields(
            GpuIrOptimizationValidationReport validationReport
    ) {
        return run(validationReport).artifactFields();
    }

    public GpuIrOptimizationValidationOptimizerEnablementGateReport runGate(
            GpuIrOptimizationValidationReport validationReport
    ) {
        Objects.requireNonNull(validationReport, "validationReport");
        return GpuIrOptimizationValidationOptimizerEnablementGateReport.from(
                validationReport,
                run(validationReport)
        );
    }

    public Map<String, String> runGateFields(
            GpuIrOptimizationValidationReport validationReport
    ) {
        return runGate(validationReport).artifactFields();
    }

    public GpuIrOptimizationValidationOptimizerValidationBundle runBundle(
            GpuIrOptimizationValidationReport validationReport
    ) {
        return GpuIrOptimizationValidationOptimizerValidationBundle.from(validationReport, this);
    }

    public Map<String, String> runBundleFields(
            GpuIrOptimizationValidationReport validationReport
    ) {
        return runBundle(validationReport).artifactFields();
    }

    public GpuIrOptimizationValidationOptimizerLayerReadinessSummaryReport runOptimizerLayerReadiness(
            GpuIrOptimizationValidationReport validationReport
    ) {
        Objects.requireNonNull(validationReport, "validationReport");
        return validationReport.optimizerLayerReadinessSummaryReport();
    }

    public Map<String, String> runOptimizerLayerReadinessFields(
            GpuIrOptimizationValidationReport validationReport
    ) {
        return runOptimizerLayerReadiness(validationReport).artifactFields();
    }

    public GpuIrOptimizationValidationOptimizerLayerReadinessRegressionReport runOptimizerLayerReadinessRegression(
            GpuIrOptimizationValidationOptimizerLayerReadinessSummaryReport baseline,
            GpuIrOptimizationValidationReport currentValidationReport
    ) {
        Objects.requireNonNull(currentValidationReport, "currentValidationReport");
        return GpuIrOptimizationValidationOptimizerLayerReadinessRegressionReport.from(
                baseline,
                runOptimizerLayerReadiness(currentValidationReport)
        );
    }

    public Map<String, String> runOptimizerLayerReadinessRegressionFields(
            GpuIrOptimizationValidationOptimizerLayerReadinessSummaryReport baseline,
            GpuIrOptimizationValidationReport currentValidationReport
    ) {
        return runOptimizerLayerReadinessRegression(baseline, currentValidationReport).artifactFields();
    }

    public GpuIrOptimizationValidationRuleArtifactReport runOptimizerLayerReadinessRegressionRuleArtifact(
            GpuIrOptimizationValidationOptimizerLayerReadinessSummaryReport baseline,
            GpuIrOptimizationValidationReport currentValidationReport
    ) {
        Objects.requireNonNull(baseline, "baseline");
        Objects.requireNonNull(currentValidationReport, "currentValidationReport");
        return GpuIrOptimizationValidationRuleArtifactReport.evaluate(
                currentValidationReport,
                GpuIrOptimizationValidationRules.optimizerLayerReadinessRegressionRegistry(baseline)
        );
    }

    public Map<String, String> runOptimizerLayerReadinessRegressionRuleArtifactFields(
            GpuIrOptimizationValidationOptimizerLayerReadinessSummaryReport baseline,
            GpuIrOptimizationValidationReport currentValidationReport
    ) {
        return runOptimizerLayerReadinessRegressionRuleArtifact(baseline, currentValidationReport).artifactFields();
    }

    public GpuIrOptimizationValidationProductionEnablementPreflightDecision runProductionPreflight(
            GpuIrOptimizationValidationReport validationReport
    ) {
        return runBundle(validationReport).productionPreflightDecision();
    }

    public Map<String, String> runProductionPreflightFields(
            GpuIrOptimizationValidationReport validationReport
    ) {
        return runProductionPreflight(validationReport).artifactFields();
    }

    public GpuIrOptimizationValidationProductionMutationSwitchContract runProductionMutationSwitchContract(
            GpuIrOptimizationValidationReport validationReport
    ) {
        return runBundle(validationReport).productionMutationSwitchContract();
    }

    public Map<String, String> runProductionMutationSwitchContractFields(
            GpuIrOptimizationValidationReport validationReport
    ) {
        return runProductionMutationSwitchContract(validationReport).artifactFields();
    }

    public GpuIrOptimizationValidationOptimizerPromotionConfidenceContract runOptimizerPromotionConfidenceContract(
            GpuIrOptimizationValidationReport validationReport
    ) {
        return runBundle(validationReport).optimizerPromotionConfidenceContract();
    }

    public Map<String, String> runOptimizerPromotionConfidenceContractFields(
            GpuIrOptimizationValidationReport validationReport
    ) {
        return runOptimizerPromotionConfidenceContract(validationReport).artifactFields();
    }

    public GpuIrOptimizationValidationProductionReadinessArtifact runProductionReadinessArtifact(
            GpuIrOptimizationValidationReport validationReport
    ) {
        return GpuIrOptimizationValidationProductionReadinessArtifact.from(runBundle(validationReport));
    }

    public Map<String, String> runProductionReadinessArtifactFields(
            GpuIrOptimizationValidationReport validationReport
    ) {
        return runProductionReadinessArtifact(validationReport).artifactFields();
    }

    public GpuIrOptimizationValidationProductionReadinessArtifactAcceptance runProductionReadinessArtifactAcceptance(
            GpuIrOptimizationValidationReport validationReport
    ) {
        return runProductionReadinessArtifact(validationReport).acceptanceDecision();
    }

    public Map<String, String> runProductionReadinessArtifactAcceptanceFields(
            GpuIrOptimizationValidationReport validationReport
    ) {
        return runProductionReadinessArtifactAcceptance(validationReport).artifactFields();
    }

    public GpuIrOptimizationValidationRuleRegistry registry() {
        return ruleArtifactRunner.registry();
    }
}
