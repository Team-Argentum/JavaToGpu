package net.sixik.ga_utils.javatogpu.iroptimizer;

/**
 * Immutable decision produced by a backend-neutral IR optimization proposal provider.
 */
public enum GpuIrOptimizationProposalDecision {
    NO_CHANGE,
    PROPOSED,
    REJECTED
}
