package net.sixik.ga_utils.javatogpu.irvalidation;

/**
 * Stable readiness state for future auto-vectorization rewrites.
 */
public enum GpuIrAutoVectorizationRewriteReadiness {
    NONE("none"),
    READY("ready"),
    BLOCKED_BY_WARNING("blockedByWarning"),
    BLOCKED_BY_GUARD("blockedByGuard"),
    REJECTED("rejected");

    private final String artifactValue;

    GpuIrAutoVectorizationRewriteReadiness(String artifactValue) {
        this.artifactValue = artifactValue;
    }

    public String artifactValue() {
        return artifactValue;
    }
}
