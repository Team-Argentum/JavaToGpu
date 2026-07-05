package net.sixik.ga_utils.javatogpu.irvalidation;

/**
 * Stable severity/status for opt-in validation-rule results.
 */
public enum GpuIrOptimizationValidationRuleStatus {
    PASS("pass", false),
    WARN("warn", false),
    FAIL("fail", true);

    private final String artifactValue;
    private final boolean blocking;

    GpuIrOptimizationValidationRuleStatus(String artifactValue, boolean blocking) {
        this.artifactValue = artifactValue;
        this.blocking = blocking;
    }

    public String artifactValue() {
        return artifactValue;
    }

    public boolean blocking() {
        return blocking;
    }
}
