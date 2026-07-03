package net.sixik.ga_utils.javatogpu.irvalidation;

public enum GpuIrCommonSubexpressionKind {
    // Repeated local expression that is the safest first target for temporary reuse.
    LOCAL_REUSE,

    // Helper calls need helper-body and dependency checks before rewrite decisions.
    HELPER_REUSE,

    // Intrinsics may hide backend-specific cost or semantics, even when classified as pure.
    INTRINSIC_REUSE,

    // Leaf or unknown shapes are useful diagnostics but not rewrite candidates yet.
    UNSAFE_FOR_REWRITE
}
