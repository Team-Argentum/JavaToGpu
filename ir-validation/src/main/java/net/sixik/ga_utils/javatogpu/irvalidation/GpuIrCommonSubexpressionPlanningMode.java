package net.sixik.ga_utils.javatogpu.irvalidation;

public enum GpuIrCommonSubexpressionPlanningMode {
    // Default build-safe mode: compute the report, but never fail or rewrite IR.
    DIAGNOSTIC_ONLY,

    // Opt-in mode for hardening optimizer assumptions before real rewrites are enabled.
    STRICT_FAIL_ON_SKIPPED_CANDIDATES
}
