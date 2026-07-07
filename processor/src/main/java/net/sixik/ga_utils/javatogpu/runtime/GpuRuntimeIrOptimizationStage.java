package net.sixik.ga_utils.javatogpu.runtime;

/**
 * Stable runtime optimizer stages used to keep future IR mutation pipelines auditable.
 */
public enum GpuRuntimeIrOptimizationStage {
    CANONICALIZE,
    VALIDATE_BEFORE,
    TARGET_PROFILE_ANALYSIS,
    CANDIDATE_DISCOVERY,
    PROOF_COLLECTION,
    TRANSFORM,
    VALIDATE_AFTER,
    BACKEND_READINESS
}
