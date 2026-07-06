package net.sixik.ga_utils.javatogpu.irvalidation;

/**
 * Stable read-only proof decision for future auto-vectorization rewrite gates.
 */
public enum GpuIrAutoVectorizationProofDecisionStatus {
    ALLOW("allow"),
    BLOCKED_BY_REWRITE_PLAN("blockedByRewritePlan"),
    BLOCKED_BY_MEMORY("blockedByMemory"),
    BLOCKED_BY_CONTROL_FLOW("blockedByControlFlow"),
    BLOCKED_BY_SIDE_EFFECT("blockedBySideEffect"),
    BLOCKED_BY_MUTATION("blockedByMutation"),
    BLOCKED_BY_BACKEND("blockedByBackend"),
    BLOCKED_BY_MULTIPLE_PROOFS("blockedByMultipleProofs"),
    BLOCKED_BY_UNKNOWN_PROOF("blockedByUnknownProof");

    private final String artifactValue;

    GpuIrAutoVectorizationProofDecisionStatus(String artifactValue) {
        this.artifactValue = artifactValue;
    }

    public String artifactValue() {
        return artifactValue;
    }
}
