package net.sixik.ga_utils.javatogpu.irvalidation;

public enum GpuIrCommonSubexpressionSkipReason {
    // The expression shape is not safe enough for the first local-temp rewrite pass.
    NOT_LOCAL_REUSE,

    // The repeated expression crosses branch, loop, switch, or condition locations.
    CONTROL_FLOW_BOUNDARY,

    // One of the referenced operands may be reassigned between repeated occurrences.
    MUTATED_BETWEEN_OCCURRENCES,

    // The first occurrence cannot be proven to dominate every planned replacement.
    NO_DOMINATING_FIRST_OCCURRENCE,

    // A broader parent expression rewrite already covers this nested candidate.
    COVERED_BY_PARENT_REWRITE
}
