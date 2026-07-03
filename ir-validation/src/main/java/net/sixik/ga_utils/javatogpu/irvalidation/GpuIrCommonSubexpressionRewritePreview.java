package net.sixik.ga_utils.javatogpu.irvalidation;

import java.util.List;

/**
 * Read-only aggregate of planned CSE insertions and replacements before any IR mutation happens.
 */
public record GpuIrCommonSubexpressionRewritePreview(
        List<GpuIrCommonSubexpressionRewriteInsertion> insertions,
        List<GpuIrCommonSubexpressionRewriteEdit> replacementEdits,
        int skippedCandidateCount
) {
    public GpuIrCommonSubexpressionRewritePreview {
        insertions = List.copyOf(insertions);
        replacementEdits = List.copyOf(replacementEdits);
        if (skippedCandidateCount < 0) {
            throw new IllegalArgumentException("skippedCandidateCount must be non-negative");
        }
    }

    public boolean hasRewriteWork() {
        return !insertions.isEmpty() || !replacementEdits.isEmpty();
    }

    public int insertionCount() {
        return insertions.size();
    }

    public int replacementEditCount() {
        return replacementEdits.size();
    }
}
