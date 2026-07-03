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

    public boolean hasSkippedCandidates() {
        return !skippedCandidates.isEmpty();
    }

    public int insertionCount() {
        return plans.size();
    }

    public int replacementEditCount() {
        return plans.stream()
                .mapToInt(GpuIrCommonSubexpressionRewritePlan::replacementCountAfterAnchor)
                .sum();
    }

    public int skippedCandidateCount() {
        return skippedCandidates.size();
    }

    public List<GpuIrCommonSubexpressionRewriteEdit> previewReplacementEdits() {
        return plans.stream()
                .flatMap(plan -> plan.previewReplacementEdits().stream())
                .toList();
    }

    public List<GpuIrCommonSubexpressionRewriteInsertion> previewInsertions() {
        return plans.stream()
                .map(GpuIrCommonSubexpressionRewritePlan::previewInsertion)
                .toList();
    }

    public GpuIrCommonSubexpressionRewritePreview preview() {
        return new GpuIrCommonSubexpressionRewritePreview(
                previewInsertions(),
                previewReplacementEdits(),
                skippedCandidateCount()
        );
    }
}
