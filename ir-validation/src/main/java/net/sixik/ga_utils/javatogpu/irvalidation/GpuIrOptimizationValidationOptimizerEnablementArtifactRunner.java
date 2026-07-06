package net.sixik.ga_utils.javatogpu.irvalidation;

import java.io.IOException;
import java.nio.file.Path;
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

    public GpuIrOptimizationValidationOptimizerLayerReadinessBaselineSnapshot runOptimizerLayerReadinessBaselineSnapshot(
            GpuIrOptimizationValidationReport validationReport
    ) {
        return GpuIrOptimizationValidationOptimizerLayerReadinessBaselineSnapshot.from(
                runOptimizerLayerReadiness(validationReport)
        );
    }

    public Map<String, String> runOptimizerLayerReadinessBaselineSnapshotFields(
            GpuIrOptimizationValidationReport validationReport
    ) {
        return runOptimizerLayerReadinessBaselineSnapshot(validationReport).artifactFields();
    }

    public void saveOptimizerLayerReadinessBaselineSnapshot(
            Path path,
            GpuIrOptimizationValidationReport validationReport
    ) throws IOException {
        GpuIrOptimizationValidationOptimizerLayerReadinessBaselinePropertiesStore.save(
                path,
                runOptimizerLayerReadinessBaselineSnapshot(validationReport)
        );
    }

    public Map<String, String> loadOptimizerLayerReadinessBaselineSnapshotFields(
            Path path
    ) throws IOException {
        return GpuIrOptimizationValidationOptimizerLayerReadinessBaselinePropertiesStore.loadFields(path);
    }

    public Map<String, String> runOptimizerLayerReadinessBaselineCiFailClosedFields(
            Path baselinePath,
            GpuIrOptimizationValidationReport currentValidationReport
    ) throws IOException {
        return runOptimizerLayerReadinessBaselineCiFailClosedFields(
                loadOptimizerLayerReadinessBaselineSnapshotFields(baselinePath),
                currentValidationReport
        );
    }

    public GpuIrOptimizationValidationOptimizerLayerReadinessBaselineComparisonReport runOptimizerLayerReadinessBaselineComparison(
            GpuIrOptimizationValidationOptimizerLayerReadinessBaselineSnapshot baseline,
            GpuIrOptimizationValidationReport currentValidationReport
    ) {
        Objects.requireNonNull(baseline, "baseline");
        return GpuIrOptimizationValidationOptimizerLayerReadinessBaselineComparisonReport.from(
                baseline,
                runOptimizerLayerReadiness(currentValidationReport)
        );
    }

    public GpuIrOptimizationValidationOptimizerLayerReadinessBaselineComparisonReport runOptimizerLayerReadinessBaselineComparison(
            Map<String, String> baselineFields,
            GpuIrOptimizationValidationReport currentValidationReport
    ) {
        return runOptimizerLayerReadinessBaselineComparison(
                GpuIrOptimizationValidationOptimizerLayerReadinessBaselineSnapshot.fromArtifactFields(baselineFields),
                currentValidationReport
        );
    }

    public Map<String, String> runOptimizerLayerReadinessBaselineComparisonFields(
            GpuIrOptimizationValidationOptimizerLayerReadinessBaselineSnapshot baseline,
            GpuIrOptimizationValidationReport currentValidationReport
    ) {
        return runOptimizerLayerReadinessBaselineComparison(baseline, currentValidationReport).artifactFields();
    }

    public Map<String, String> runOptimizerLayerReadinessBaselineComparisonFields(
            Map<String, String> baselineFields,
            GpuIrOptimizationValidationReport currentValidationReport
    ) {
        return runOptimizerLayerReadinessBaselineComparison(baselineFields, currentValidationReport).artifactFields();
    }

    public GpuIrOptimizationValidationOptimizerLayerReadinessBaselineCiSummary runOptimizerLayerReadinessBaselineCi(
            GpuIrOptimizationValidationOptimizerLayerReadinessBaselineSnapshot baseline,
            GpuIrOptimizationValidationReport currentValidationReport
    ) {
        return new GpuIrOptimizationValidationOptimizerLayerReadinessBaselineCiSummary(
                runOptimizerLayerReadinessBaselineComparison(baseline, currentValidationReport)
        );
    }

    public GpuIrOptimizationValidationOptimizerLayerReadinessBaselineCiSummary runOptimizerLayerReadinessBaselineCi(
            Map<String, String> baselineFields,
            GpuIrOptimizationValidationReport currentValidationReport
    ) {
        return new GpuIrOptimizationValidationOptimizerLayerReadinessBaselineCiSummary(
                runOptimizerLayerReadinessBaselineComparison(baselineFields, currentValidationReport)
        );
    }

    public Map<String, String> runOptimizerLayerReadinessBaselineCiFields(
            GpuIrOptimizationValidationOptimizerLayerReadinessBaselineSnapshot baseline,
            GpuIrOptimizationValidationReport currentValidationReport
    ) {
        Map<String, String> values = new java.util.LinkedHashMap<>();
        GpuIrOptimizationValidationOptimizerLayerReadinessBaselineComparisonReport comparison =
                runOptimizerLayerReadinessBaselineComparison(baseline, currentValidationReport);
        values.putAll(comparison.artifactFields());
        values.putAll(new GpuIrOptimizationValidationOptimizerLayerReadinessBaselineCiSummary(comparison).artifactFields());
        return Map.copyOf(values);
    }

    public Map<String, String> runOptimizerLayerReadinessBaselineCiFields(
            Map<String, String> baselineFields,
            GpuIrOptimizationValidationReport currentValidationReport
    ) {
        Map<String, String> values = new java.util.LinkedHashMap<>();
        GpuIrOptimizationValidationOptimizerLayerReadinessBaselineComparisonReport comparison =
                runOptimizerLayerReadinessBaselineComparison(baselineFields, currentValidationReport);
        values.putAll(comparison.artifactFields());
        values.putAll(new GpuIrOptimizationValidationOptimizerLayerReadinessBaselineCiSummary(comparison).artifactFields());
        return Map.copyOf(values);
    }

    public Map<String, String> runOptimizerLayerReadinessBaselineCiFailClosedFields(
            Map<String, String> baselineFields,
            GpuIrOptimizationValidationReport currentValidationReport
    ) {
        Objects.requireNonNull(baselineFields, "baselineFields");
        Objects.requireNonNull(currentValidationReport, "currentValidationReport");
        try {
            return runOptimizerLayerReadinessBaselineCiFields(baselineFields, currentValidationReport);
        } catch (RuntimeException failure) {
            return GpuIrOptimizationValidationOptimizerLayerReadinessBaselineCiSummary.invalidBaselineArtifactFields(
                    "optimizerLayerReadinessBaselineCi",
                    currentValidationReport.methodName(),
                    baselineFields.get("optimizerLayerReadinessBaselineSnapshotMethod"),
                    failure
            );
        }
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

    public GpuIrOptimizationValidationRuleArtifactAcceptance runOptimizerLayerReadinessRegressionRuleArtifactAcceptance(
            GpuIrOptimizationValidationOptimizerLayerReadinessSummaryReport baseline,
            GpuIrOptimizationValidationReport currentValidationReport
    ) {
        return GpuIrOptimizationValidationRuleArtifactAcceptance.from(
                runOptimizerLayerReadinessRegressionRuleArtifact(baseline, currentValidationReport)
        );
    }

    public Map<String, String> runOptimizerLayerReadinessRegressionRuleArtifactAcceptanceFields(
            GpuIrOptimizationValidationOptimizerLayerReadinessSummaryReport baseline,
            GpuIrOptimizationValidationReport currentValidationReport
    ) {
        return runOptimizerLayerReadinessRegressionRuleArtifactAcceptance(
                baseline,
                currentValidationReport
        ).artifactFields();
    }

    public Map<String, String> runOptimizerLayerReadinessRegressionCiFields(
            GpuIrOptimizationValidationOptimizerLayerReadinessSummaryReport baseline,
            GpuIrOptimizationValidationReport currentValidationReport
    ) {
        Map<String, String> values = new java.util.LinkedHashMap<>();
        GpuIrOptimizationValidationOptimizerLayerReadinessRegressionReport regression =
                runOptimizerLayerReadinessRegression(baseline, currentValidationReport);
        values.putAll(regression.artifactFields());
        GpuIrOptimizationValidationRuleArtifactReport ruleArtifact = runOptimizerLayerReadinessRegressionRuleArtifact(
                baseline,
                currentValidationReport
        );
        values.putAll(ruleArtifact.artifactFields());
        GpuIrOptimizationValidationRuleArtifactAcceptance acceptance =
                GpuIrOptimizationValidationRuleArtifactAcceptance.from(ruleArtifact);
        values.putAll(acceptance.artifactFields());
        values.putAll(new GpuIrOptimizationValidationOptimizerLayerReadinessRegressionCiSummary(
                regression,
                ruleArtifact,
                acceptance
        ).artifactFields());
        return Map.copyOf(values);
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
