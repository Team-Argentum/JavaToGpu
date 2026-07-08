package net.sixik.ga_utils.javatogpu.irvalidation;

/**
 * Controls how the unified read-only optimization validation pipeline reacts to diagnostics.
 */
public enum GpuIrOptimizationValidationMode {
    DIAGNOSTIC_ONLY,
    STRICT_FAIL_ON_SAFETY_ERROR,
    STRICT_FAIL_ON_OPTIMIZER_DIAGNOSTICS
}
