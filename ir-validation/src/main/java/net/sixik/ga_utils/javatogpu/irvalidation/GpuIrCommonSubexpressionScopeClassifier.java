package net.sixik.ga_utils.javatogpu.irvalidation;

/**
 * Classifies scanner locations by control-flow scope without inspecting or rewriting IR.
 */
public final class GpuIrCommonSubexpressionScopeClassifier {
    public GpuIrCommonSubexpressionScope classify(GpuIrCommonSubexpression candidate) {
        boolean crossesControlFlow = candidate.locations().stream().anyMatch(this::isControlFlowLocation);
        return crossesControlFlow
                ? GpuIrCommonSubexpressionScope.CONTROL_FLOW_BOUNDARY
                : GpuIrCommonSubexpressionScope.STRAIGHT_LINE;
    }

    public boolean isStraightLine(GpuIrCommonSubexpression candidate) {
        return classify(candidate) == GpuIrCommonSubexpressionScope.STRAIGHT_LINE;
    }

    private boolean isControlFlowLocation(String location) {
        return location.contains(".then")
                || location.contains(".else")
                || location.contains(".body")
                || location.contains(".case[")
                || location.contains(".condition")
                || location.contains(".initializer") && location.contains(".stmt[")
                || location.contains(".update");
    }
}
