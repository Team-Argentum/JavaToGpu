package net.sixik.ga_utils.javatogpu.irvalidation;

public enum GpuIrCommonSubexpressionScope {
    // All occurrences are in top-level straight-line statement locations.
    STRAIGHT_LINE,

    // At least one occurrence crosses a branch, loop, switch, or case boundary.
    CONTROL_FLOW_BOUNDARY
}
