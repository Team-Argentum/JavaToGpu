package net.sixik.ga_utils.javatogpu.runtime;

/**
 * Final decision produced by one runtime IR optimizer hook.
 */
public enum GpuRuntimeIrOptimizationOutcome {
    APPLIED,
    SKIPPED,
    ROLLED_BACK,
    FAILED
}
