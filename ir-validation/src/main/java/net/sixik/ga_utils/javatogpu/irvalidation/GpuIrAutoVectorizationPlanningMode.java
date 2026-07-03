package net.sixik.ga_utils.javatogpu.irvalidation;

/**
 * Controls how the diagnostic auto-vectorization bridge reacts to scan results.
 */
public enum GpuIrAutoVectorizationPlanningMode {
    DIAGNOSTIC_ONLY,
    STRICT_FAIL_ON_WARNED_CANDIDATES
}
