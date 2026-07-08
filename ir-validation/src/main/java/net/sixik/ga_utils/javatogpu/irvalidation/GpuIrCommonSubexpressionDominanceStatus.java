package net.sixik.ga_utils.javatogpu.irvalidation;

/**
 * Read-only explanation for why a CSE candidate does or does not have a safe rewrite anchor.
 */
public enum GpuIrCommonSubexpressionDominanceStatus {
    // The first top-level occurrence can anchor at least one later top-level replacement.
    TOP_LEVEL_DOWNSTREAM_REPLACEMENTS("topLevelDownstreamReplacements"),

    // The first same-statement expression occurrence can anchor later sibling/child replacements.
    LOCAL_EXPRESSION_DOWNSTREAM_REPLACEMENTS("localExpressionDownstreamReplacements"),

    // The scanner location cannot be mapped to a top-level statement anchor.
    MISSING_TOP_LEVEL_ANCHOR("missingTopLevelAnchor"),

    // A later occurrence appears before an earlier occurrence in top-level statement order.
    LOCATIONS_MOVE_BACKWARDS("locationsMoveBackwards"),

    // Same-statement rewrites still need a stronger expression-level dominance proof before mutation.
    REQUIRES_LOCAL_EXPRESSION_DOMINANCE("requiresLocalExpressionDominance");

    private final String artifactValue;

    GpuIrCommonSubexpressionDominanceStatus(String artifactValue) {
        this.artifactValue = artifactValue;
    }

    public String artifactValue() {
        return artifactValue;
    }
}
