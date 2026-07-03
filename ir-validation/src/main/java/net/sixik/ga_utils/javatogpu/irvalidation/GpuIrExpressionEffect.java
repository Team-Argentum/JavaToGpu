package net.sixik.ga_utils.javatogpu.irvalidation;

public enum GpuIrExpressionEffect {
    // Safe to reuse or compare structurally because evaluation has no observable side effects.
    PURE,

    // Must not be duplicated, reordered, or removed without a stronger backend-specific proof.
    SIDE_EFFECTING
}
