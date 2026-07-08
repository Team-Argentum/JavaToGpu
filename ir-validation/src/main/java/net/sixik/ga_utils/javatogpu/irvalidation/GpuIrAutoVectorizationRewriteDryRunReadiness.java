package net.sixik.ga_utils.javatogpu.irvalidation;

/**
 * Stable artifact state for no-mutation auto-vectorization rewrite dry-runs.
 */
public enum GpuIrAutoVectorizationRewriteDryRunReadiness {
    READY("ready"),
    FAILED("failed"),
    SKIPPED("skipped");

    private final String artifactValue;

    GpuIrAutoVectorizationRewriteDryRunReadiness(String artifactValue) {
        this.artifactValue = artifactValue;
    }

    public String artifactValue() {
        return artifactValue;
    }
}
