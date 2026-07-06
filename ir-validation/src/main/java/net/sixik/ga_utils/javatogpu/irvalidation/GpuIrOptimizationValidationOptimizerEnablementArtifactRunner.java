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

    public GpuIrOptimizationValidationRuleArtifactReport runOptimizerLayerReadinessRuleArtifact(
            GpuIrOptimizationValidationReport validationReport
    ) {
        Objects.requireNonNull(validationReport, "validationReport");
        return GpuIrOptimizationValidationRuleArtifactReport.evaluate(
                validationReport,
                GpuIrOptimizationValidationRules.optimizerLayerReadinessRegistry()
        );
    }

    public Map<String, String> runOptimizerLayerReadinessRuleArtifactFields(
            GpuIrOptimizationValidationReport validationReport
    ) {
        return runOptimizerLayerReadinessRuleArtifact(validationReport).artifactFields();
    }

    public GpuIrOptimizationValidationRuleArtifactAcceptance runOptimizerLayerReadinessRuleArtifactAcceptance(
            GpuIrOptimizationValidationReport validationReport
    ) {
        return GpuIrOptimizationValidationRuleArtifactAcceptance.from(
                runOptimizerLayerReadinessRuleArtifact(validationReport)
        );
    }

    public Map<String, String> runOptimizerLayerReadinessRuleArtifactAcceptanceFields(
            GpuIrOptimizationValidationReport validationReport
    ) {
        return runOptimizerLayerReadinessRuleArtifactAcceptance(validationReport).artifactFields();
    }

    public Map<String, String> runOptimizerLayerReadinessCiFields(
            GpuIrOptimizationValidationReport validationReport
    ) {
        GpuIrOptimizationValidationOptimizerLayerReadinessSummaryReport readiness =
                runOptimizerLayerReadiness(validationReport);
        GpuIrOptimizationValidationRuleArtifactReport ruleArtifact = runOptimizerLayerReadinessRuleArtifact(
                validationReport
        );
        GpuIrOptimizationValidationRuleArtifactAcceptance acceptance =
                GpuIrOptimizationValidationRuleArtifactAcceptance.from(ruleArtifact);
        return readinessCiFields(
                readiness.artifactFields(),
                ruleArtifact,
                acceptance,
                new GpuIrOptimizationValidationOptimizerLayerReadinessCiSummary(
                readiness,
                ruleArtifact,
                acceptance
                ).artifactFields()
        );
    }

    public GpuIrCommonSubexpressionLayerReadinessSummaryReport runCseLayerReadiness(
            GpuIrOptimizationValidationReport validationReport
    ) {
        Objects.requireNonNull(validationReport, "validationReport");
        return validationReport.commonSubexpressionArtifactSnapshot().layerReadinessSummaryReport();
    }

    public Map<String, String> runCseLayerReadinessFields(
            GpuIrOptimizationValidationReport validationReport
    ) {
        return runCseLayerReadiness(validationReport).artifactFields();
    }

    public GpuIrOptimizationValidationRuleArtifactReport runCseLayerReadinessRuleArtifact(
            GpuIrOptimizationValidationReport validationReport
    ) {
        Objects.requireNonNull(validationReport, "validationReport");
        return GpuIrOptimizationValidationRuleArtifactReport.evaluate(
                validationReport,
                GpuIrOptimizationValidationRules.cseLayerReadinessRegistry()
        );
    }

    public Map<String, String> runCseLayerReadinessRuleArtifactFields(
            GpuIrOptimizationValidationReport validationReport
    ) {
        return runCseLayerReadinessRuleArtifact(validationReport).artifactFields();
    }

    public GpuIrOptimizationValidationRuleArtifactAcceptance runCseLayerReadinessRuleArtifactAcceptance(
            GpuIrOptimizationValidationReport validationReport
    ) {
        return GpuIrOptimizationValidationRuleArtifactAcceptance.from(
                runCseLayerReadinessRuleArtifact(validationReport)
        );
    }

    public Map<String, String> runCseLayerReadinessRuleArtifactAcceptanceFields(
            GpuIrOptimizationValidationReport validationReport
    ) {
        return runCseLayerReadinessRuleArtifactAcceptance(validationReport).artifactFields();
    }

    public Map<String, String> runCseLayerReadinessCiFields(
            GpuIrOptimizationValidationReport validationReport
    ) {
        GpuIrCommonSubexpressionLayerReadinessSummaryReport readiness = runCseLayerReadiness(validationReport);
        GpuIrOptimizationValidationRuleArtifactReport ruleArtifact = runCseLayerReadinessRuleArtifact(
                validationReport
        );
        GpuIrOptimizationValidationRuleArtifactAcceptance acceptance =
                GpuIrOptimizationValidationRuleArtifactAcceptance.from(ruleArtifact);
        return readinessCiFields(
                readiness.artifactFields(),
                ruleArtifact,
                acceptance,
                new GpuIrCommonSubexpressionLayerReadinessCiSummary(
                readiness,
                ruleArtifact,
                acceptance
                ).artifactFields()
        );
    }

    public GpuIrAutoVectorizationReadinessSummaryReport runAutoVectorizationReadiness(
            GpuIrOptimizationValidationReport validationReport
    ) {
        Objects.requireNonNull(validationReport, "validationReport");
        return validationReport.autoVectorizationArtifactSnapshot().readinessSummaryReport();
    }

    public Map<String, String> runAutoVectorizationReadinessFields(
            GpuIrOptimizationValidationReport validationReport
    ) {
        return runAutoVectorizationReadiness(validationReport).artifactFields();
    }

    public GpuIrOptimizationValidationRuleArtifactReport runAutoVectorizationReadinessRuleArtifact(
            GpuIrOptimizationValidationReport validationReport
    ) {
        Objects.requireNonNull(validationReport, "validationReport");
        return GpuIrOptimizationValidationRuleArtifactReport.evaluate(
                validationReport,
                GpuIrOptimizationValidationRules.autoVectorizationLayerReadinessRegistry()
        );
    }

    public Map<String, String> runAutoVectorizationReadinessRuleArtifactFields(
            GpuIrOptimizationValidationReport validationReport
    ) {
        return runAutoVectorizationReadinessRuleArtifact(validationReport).artifactFields();
    }

    public GpuIrOptimizationValidationRuleArtifactAcceptance runAutoVectorizationReadinessRuleArtifactAcceptance(
            GpuIrOptimizationValidationReport validationReport
    ) {
        return GpuIrOptimizationValidationRuleArtifactAcceptance.from(
                runAutoVectorizationReadinessRuleArtifact(validationReport)
        );
    }

    public Map<String, String> runAutoVectorizationReadinessRuleArtifactAcceptanceFields(
            GpuIrOptimizationValidationReport validationReport
    ) {
        return runAutoVectorizationReadinessRuleArtifactAcceptance(validationReport).artifactFields();
    }

    public Map<String, String> runAutoVectorizationReadinessCiFields(
            GpuIrOptimizationValidationReport validationReport
    ) {
        GpuIrAutoVectorizationReadinessSummaryReport readiness = runAutoVectorizationReadiness(validationReport);
        GpuIrOptimizationValidationRuleArtifactReport ruleArtifact = runAutoVectorizationReadinessRuleArtifact(
                validationReport
        );
        GpuIrOptimizationValidationRuleArtifactAcceptance acceptance =
                GpuIrOptimizationValidationRuleArtifactAcceptance.from(ruleArtifact);
        return readinessCiFields(
                readiness.artifactFields(),
                ruleArtifact,
                acceptance,
                new GpuIrAutoVectorizationReadinessCiSummary(
                readiness,
                ruleArtifact,
                acceptance
                ).artifactFields()
        );
    }

    private static Map<String, String> readinessCiFields(
            Map<String, String> readinessFields,
            GpuIrOptimizationValidationRuleArtifactReport ruleArtifact,
            GpuIrOptimizationValidationRuleArtifactAcceptance acceptance,
            Map<String, String> ciSummaryFields
    ) {
        Map<String, String> values = new java.util.LinkedHashMap<>();
        values.putAll(readinessFields);
        values.putAll(ruleArtifact.artifactFields());
        values.putAll(acceptance.artifactFields());
        values.putAll(ciSummaryFields);
        return Map.copyOf(values);
    }

    public GpuIrOptimizationValidationCiGateIndex runCiGateIndex(
            GpuIrOptimizationValidationReport validationReport
    ) {
        Objects.requireNonNull(validationReport, "validationReport");
        GpuIrCommonSubexpressionLayerReadinessSummaryReport cseReadiness = runCseLayerReadiness(validationReport);
        GpuIrOptimizationValidationRuleArtifactReport cseRuleArtifact = runCseLayerReadinessRuleArtifact(validationReport);
        GpuIrAutoVectorizationReadinessSummaryReport autoReadiness = runAutoVectorizationReadiness(validationReport);
        GpuIrOptimizationValidationRuleArtifactReport autoRuleArtifact = runAutoVectorizationReadinessRuleArtifact(validationReport);
        GpuIrOptimizationValidationOptimizerLayerReadinessSummaryReport optimizerReadiness =
                runOptimizerLayerReadiness(validationReport);
        GpuIrOptimizationValidationRuleArtifactReport optimizerRuleArtifact =
                runOptimizerLayerReadinessRuleArtifact(validationReport);
        return new GpuIrOptimizationValidationCiGateIndex(
                GpuIrCommonSubexpressionLayerReadinessCiSummary.from(cseReadiness, cseRuleArtifact),
                GpuIrAutoVectorizationReadinessCiSummary.from(autoReadiness, autoRuleArtifact),
                GpuIrOptimizationValidationOptimizerLayerReadinessCiSummary.from(
                        optimizerReadiness,
                        optimizerRuleArtifact
                )
        );
    }

    public Map<String, String> runCiGateIndexFields(
            GpuIrOptimizationValidationReport validationReport
    ) {
        Map<String, String> values = new java.util.LinkedHashMap<>();
        values.putAll(runCseLayerReadinessCiFields(validationReport));
        values.putAll(runAutoVectorizationReadinessCiFields(validationReport));
        values.putAll(runOptimizerLayerReadinessCiFields(validationReport));
        values.putAll(runCiGateIndex(validationReport).artifactFields());
        return Map.copyOf(values);
    }

    public GpuIrOptimizerGateSnapshot runOptimizerGateSnapshot(
            GpuIrOptimizationValidationReport validationReport
    ) {
        Objects.requireNonNull(validationReport, "validationReport");
        return validationReport.optimizerGateSnapshot();
    }

    public Map<String, String> runOptimizerGateSnapshotFields(
            GpuIrOptimizationValidationReport validationReport
    ) {
        return runOptimizerGateSnapshot(validationReport).artifactFields();
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
