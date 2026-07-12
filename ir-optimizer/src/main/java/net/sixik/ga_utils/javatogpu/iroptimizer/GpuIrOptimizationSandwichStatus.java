package net.sixik.ga_utils.javatogpu.iroptimizer;

/**
 * Final selection decision after pre/post validation around an optimizer proposal.
 */
public enum GpuIrOptimizationSandwichStatus {
    ORIGINAL_INVALID,
    NO_CHANGE,
    PROPOSAL_REJECTED,
    PROPOSAL_ONLY,
    OPTIMIZED_INVALID_ROLLED_BACK,
    OPTIMIZED_SELECTED
}
