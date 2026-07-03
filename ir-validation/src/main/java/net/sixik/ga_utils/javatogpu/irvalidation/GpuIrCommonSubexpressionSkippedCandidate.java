package net.sixik.ga_utils.javatogpu.irvalidation;

/**
 * Read-only explanation for a candidate that was visible to analysis but not planned for rewrite.
 */
public record GpuIrCommonSubexpressionSkippedCandidate(
        GpuIrCommonSubexpression candidate,
        GpuIrCommonSubexpressionKind kind,
        GpuIrCommonSubexpressionScope scope,
        GpuIrCommonSubexpressionSkipReason reason
) {
}
