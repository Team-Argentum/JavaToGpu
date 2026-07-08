package net.sixik.ga_utils.javatogpu.irvalidation;

/**
 * One read-only rule in the optimizer validation registry prototype.
 */
public interface GpuIrOptimizationValidationRule {
    String id();

    GpuIrOptimizationValidationRuleResult evaluate(GpuIrOptimizationValidationRuleContext context);
}
