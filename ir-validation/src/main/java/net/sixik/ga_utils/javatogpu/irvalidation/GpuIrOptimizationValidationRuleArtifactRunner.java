package net.sixik.ga_utils.javatogpu.irvalidation;

import java.util.Objects;

/**
 * Opt-in helper that evaluates a validation-rule registry for an existing validation report.
 *
 * <p>The runner is intentionally detached from normal compiler validation. Callers must pass an
 * already-created validation report, and the runner only packages rule results into an artifact.</p>
 */
public final class GpuIrOptimizationValidationRuleArtifactRunner {
    private final GpuIrOptimizationValidationRuleRegistry registry;

    public GpuIrOptimizationValidationRuleArtifactRunner() {
        this(GpuIrOptimizationValidationRules.defaultRegistry());
    }

    public GpuIrOptimizationValidationRuleArtifactRunner(
            GpuIrOptimizationValidationRuleRegistry registry
    ) {
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    public GpuIrOptimizationValidationRuleArtifactReport run(
            GpuIrOptimizationValidationReport validationReport
    ) {
        return GpuIrOptimizationValidationRuleArtifactReport.evaluate(validationReport, registry);
    }

    public GpuIrOptimizationValidationRuleRegistry registry() {
        return registry;
    }
}
