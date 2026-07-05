package net.sixik.ga_utils.javatogpu.irvalidation;

/**
 * Stable readiness state for future CSE rewrites.
 */
public enum GpuIrCommonSubexpressionRewriteReadiness {
    NONE("none"),
    READY("ready"),
    BLOCKED_BY_SKIPPED_CANDIDATE("blockedBySkippedCandidate");

    private final String artifactValue;

    GpuIrCommonSubexpressionRewriteReadiness(String artifactValue) {
        this.artifactValue = artifactValue;
    }

    public String artifactValue() {
        return artifactValue;
    }
}
