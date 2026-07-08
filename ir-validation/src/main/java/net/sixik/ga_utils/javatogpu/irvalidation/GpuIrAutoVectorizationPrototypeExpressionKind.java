package net.sixik.ga_utils.javatogpu.irvalidation;

/**
 * Supported expression families for the opt-in prototype vector rewrite.
 */
public enum GpuIrAutoVectorizationPrototypeExpressionKind {
    LANE_COPY("laneCopy", false),
    BINARY_LANE_OP("binaryLaneOp", true),
    LANE_LITERAL_BINARY_OP("laneLiteralBinaryOp", true),
    UNARY_LANE_OP("unaryLaneOp", false);

    private final String artifactValue;
    private final boolean requiresBinaryOperator;

    GpuIrAutoVectorizationPrototypeExpressionKind(String artifactValue, boolean requiresBinaryOperator) {
        this.artifactValue = artifactValue;
        this.requiresBinaryOperator = requiresBinaryOperator;
    }

    public String artifactValue() {
        return artifactValue;
    }

    public boolean requiresBinaryOperator() {
        return requiresBinaryOperator;
    }

    public String summary() {
        return artifactValue;
    }
}
