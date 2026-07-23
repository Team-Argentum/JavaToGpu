package net.sixik.ga_utils.javatogpu.runtime;

/**
 * Candidate ordering mode for backend selection.
 */
public enum GpuRuntimeBackendCandidateOrdering {
    /**
     * Preserve explicit fallback order and select the first candidate that satisfies hard requirements.
     */
    FALLBACK_ORDER("fallback-order"),

    /**
     * Evaluate all candidates, then select the highest explainable score among candidates that passed hard checks.
     */
    SCORE_DESCENDING("score-descending");

    private final String key;

    GpuRuntimeBackendCandidateOrdering(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }
}
