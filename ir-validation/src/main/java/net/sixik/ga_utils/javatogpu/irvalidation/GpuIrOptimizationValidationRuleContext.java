package net.sixik.ga_utils.javatogpu.irvalidation;

import java.util.Objects;

/**
 * Immutable input passed to opt-in optimizer validation rules.
 */
public record GpuIrOptimizationValidationRuleContext(
        GpuIrOptimizationValidationReport report
) {
    public GpuIrOptimizationValidationRuleContext {
        report = Objects.requireNonNull(report, "report");
    }

    public String methodName() {
        return report.methodName();
    }

    public boolean hasSafetyError() {
        return report.hasSafetyError();
    }

    public boolean hasOptimizerDiagnostics() {
        return report.hasOptimizerDiagnostics();
    }
}
