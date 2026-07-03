package net.sixik.ga_utils.javatogpu.irvalidation;

import java.util.List;

/**
 * Read-only planner output that keeps both proposed rewrites and skipped-candidate reasons.
 */
public record GpuIrCommonSubexpressionRewritePlanReport(
        List<GpuIrCommonSubexpressionRewritePlan> plans,
        List<GpuIrCommonSubexpressionSkippedCandidate> skippedCandidates
) {
    public GpuIrCommonSubexpressionRewritePlanReport {
        plans = List.copyOf(plans);
        skippedCandidates = List.copyOf(skippedCandidates);
    }

    public boolean hasPlans() {
        return !plans.isEmpty();
    }
}
