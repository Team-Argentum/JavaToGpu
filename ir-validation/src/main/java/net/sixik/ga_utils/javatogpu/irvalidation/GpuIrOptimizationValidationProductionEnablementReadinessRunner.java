package net.sixik.ga_utils.javatogpu.irvalidation;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

/**
 * Explicit opt-in A1/A2 production-enable readiness runner.
 *
 * <p>This runner is the tooling entrypoint for asking "is this validation report ready for a
 * future production optimizer switch?" It delegates to the read-only optimizer validation bundle
 * and production preflight decision, and never mutates IR or enables production rewrites.</p>
 */
public final class GpuIrOptimizationValidationProductionEnablementReadinessRunner {
    private final GpuIrOptimizationValidationOptimizerEnablementArtifactRunner enablementRunner;

    public GpuIrOptimizationValidationProductionEnablementReadinessRunner() {
        this(new GpuIrOptimizationValidationOptimizerEnablementArtifactRunner());
    }

    public GpuIrOptimizationValidationProductionEnablementReadinessRunner(
            GpuIrOptimizationValidationRuleRegistry registry
    ) {
        this(new GpuIrOptimizationValidationOptimizerEnablementArtifactRunner(registry));
    }

    public GpuIrOptimizationValidationProductionEnablementReadinessRunner(
            GpuIrOptimizationValidationOptimizerEnablementArtifactRunner enablementRunner
    ) {
        this.enablementRunner = Objects.requireNonNull(enablementRunner, "enablementRunner");
    }

    public GpuIrOptimizationValidationProductionEnablementPreflightDecision run(
            GpuIrOptimizationValidationReport validationReport
    ) {
        return enablementRunner.runProductionPreflight(validationReport);
    }

    public Map<String, String> runFields(
            GpuIrOptimizationValidationReport validationReport
    ) {
        return run(validationReport).artifactFields();
    }

    public GpuIrOptimizationValidationOptimizerValidationBundle runBundle(
            GpuIrOptimizationValidationReport validationReport
    ) {
        return enablementRunner.runBundle(validationReport);
    }

    public Map<String, String> runBundleFields(
            GpuIrOptimizationValidationReport validationReport
    ) {
        return runBundle(validationReport).artifactFields();
    }

    public GpuIrOptimizationValidationOptimizerLayerReadinessSummaryReport runOptimizerLayerReadiness(
            GpuIrOptimizationValidationReport validationReport
    ) {
        return enablementRunner.runOptimizerLayerReadiness(validationReport);
    }

    public Map<String, String> runOptimizerLayerReadinessFields(
            GpuIrOptimizationValidationReport validationReport
    ) {
        return runOptimizerLayerReadiness(validationReport).artifactFields();
    }

    public GpuIrOptimizationValidationOptimizerLayerReadinessBaselineSnapshot runOptimizerLayerReadinessBaselineSnapshot(
            GpuIrOptimizationValidationReport validationReport
    ) {
        return enablementRunner.runOptimizerLayerReadinessBaselineSnapshot(validationReport);
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
        enablementRunner.saveOptimizerLayerReadinessBaselineSnapshot(path, validationReport);
    }

    public Map<String, String> loadOptimizerLayerReadinessBaselineSnapshotFields(
            Path path
    ) throws IOException {
        return enablementRunner.loadOptimizerLayerReadinessBaselineSnapshotFields(path);
    }

    public Map<String, String> runOptimizerLayerReadinessBaselineCiFailClosedFields(
            Path baselinePath,
            GpuIrOptimizationValidationReport currentValidationReport
    ) throws IOException {
        return enablementRunner.runOptimizerLayerReadinessBaselineCiFailClosedFields(
                baselinePath,
                currentValidationReport
        );
    }

    public GpuIrOptimizationValidationOptimizerLayerReadinessBaselineComparisonReport runOptimizerLayerReadinessBaselineComparison(
            GpuIrOptimizationValidationOptimizerLayerReadinessBaselineSnapshot baseline,
            GpuIrOptimizationValidationReport currentValidationReport
    ) {
        return enablementRunner.runOptimizerLayerReadinessBaselineComparison(baseline, currentValidationReport);
    }

    public GpuIrOptimizationValidationOptimizerLayerReadinessBaselineComparisonReport runOptimizerLayerReadinessBaselineComparison(
            Map<String, String> baselineFields,
            GpuIrOptimizationValidationReport currentValidationReport
    ) {
        return enablementRunner.runOptimizerLayerReadinessBaselineComparison(baselineFields, currentValidationReport);
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
        return enablementRunner.runOptimizerLayerReadinessBaselineCi(baseline, currentValidationReport);
    }

    public GpuIrOptimizationValidationOptimizerLayerReadinessBaselineCiSummary runOptimizerLayerReadinessBaselineCi(
            Map<String, String> baselineFields,
            GpuIrOptimizationValidationReport currentValidationReport
    ) {
        return enablementRunner.runOptimizerLayerReadinessBaselineCi(baselineFields, currentValidationReport);
    }

    public Map<String, String> runOptimizerLayerReadinessBaselineCiFields(
            GpuIrOptimizationValidationOptimizerLayerReadinessBaselineSnapshot baseline,
            GpuIrOptimizationValidationReport currentValidationReport
    ) {
        return enablementRunner.runOptimizerLayerReadinessBaselineCiFields(baseline, currentValidationReport);
    }

    public Map<String, String> runOptimizerLayerReadinessBaselineCiFields(
            Map<String, String> baselineFields,
            GpuIrOptimizationValidationReport currentValidationReport
    ) {
        return enablementRunner.runOptimizerLayerReadinessBaselineCiFields(baselineFields, currentValidationReport);
    }

    public Map<String, String> runOptimizerLayerReadinessBaselineCiFailClosedFields(
            Map<String, String> baselineFields,
            GpuIrOptimizationValidationReport currentValidationReport
    ) {
        return enablementRunner.runOptimizerLayerReadinessBaselineCiFailClosedFields(
                baselineFields,
                currentValidationReport
        );
    }

    public GpuIrOptimizationValidationOptimizerLayerReadinessRegressionReport runOptimizerLayerReadinessRegression(
            GpuIrOptimizationValidationOptimizerLayerReadinessSummaryReport baseline,
            GpuIrOptimizationValidationReport currentValidationReport
    ) {
        return enablementRunner.runOptimizerLayerReadinessRegression(baseline, currentValidationReport);
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
        return enablementRunner.runOptimizerLayerReadinessRegressionRuleArtifact(
                baseline,
                currentValidationReport
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
        return enablementRunner.runOptimizerLayerReadinessRegressionRuleArtifactAcceptance(
                baseline,
                currentValidationReport
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
        return enablementRunner.runOptimizerLayerReadinessRegressionCiFields(
                baseline,
                currentValidationReport
        );
    }

    public GpuIrOptimizationValidationProductionMutationSwitchContract runProductionMutationSwitchContract(
            GpuIrOptimizationValidationReport validationReport
    ) {
        return enablementRunner.runProductionMutationSwitchContract(validationReport);
    }

    public Map<String, String> runProductionMutationSwitchContractFields(
            GpuIrOptimizationValidationReport validationReport
    ) {
        return runProductionMutationSwitchContract(validationReport).artifactFields();
    }

    public GpuIrOptimizationValidationOptimizerPromotionConfidenceContract runOptimizerPromotionConfidenceContract(
            GpuIrOptimizationValidationReport validationReport
    ) {
        return enablementRunner.runOptimizerPromotionConfidenceContract(validationReport);
    }

    public Map<String, String> runOptimizerPromotionConfidenceContractFields(
            GpuIrOptimizationValidationReport validationReport
    ) {
        return runOptimizerPromotionConfidenceContract(validationReport).artifactFields();
    }

    public GpuIrOptimizationValidationProductionReadinessArtifact runProductionReadinessArtifact(
            GpuIrOptimizationValidationReport validationReport
    ) {
        return enablementRunner.runProductionReadinessArtifact(validationReport);
    }

    public Map<String, String> runProductionReadinessArtifactFields(
            GpuIrOptimizationValidationReport validationReport
    ) {
        return runProductionReadinessArtifact(validationReport).artifactFields();
    }

    public GpuIrOptimizationValidationProductionReadinessArtifactAcceptance runProductionReadinessArtifactAcceptance(
            GpuIrOptimizationValidationReport validationReport
    ) {
        return enablementRunner.runProductionReadinessArtifactAcceptance(validationReport);
    }

    public Map<String, String> runProductionReadinessArtifactAcceptanceFields(
            GpuIrOptimizationValidationReport validationReport
    ) {
        return runProductionReadinessArtifactAcceptance(validationReport).artifactFields();
    }

    public GpuIrOptimizationValidationOptimizerEnablementArtifactRunner enablementRunner() {
        return enablementRunner;
    }
}
