package net.sixik.ga_utils.javatogpu.irvalidation;

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

    public GpuIrOptimizationValidationOptimizerEnablementArtifactRunner enablementRunner() {
        return enablementRunner;
    }
}
