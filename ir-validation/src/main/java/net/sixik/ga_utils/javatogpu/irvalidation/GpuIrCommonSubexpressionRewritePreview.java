package net.sixik.ga_utils.javatogpu.irvalidation;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Read-only aggregate of planned CSE insertions and replacements before any IR mutation happens.
 */
public record GpuIrCommonSubexpressionRewritePreview(
        List<GpuIrCommonSubexpressionRewriteInsertion> insertions,
        List<GpuIrCommonSubexpressionRewriteEdit> replacementEdits,
        List<GpuIrCommonSubexpressionSkippedDiagnostic> skippedDiagnostics
) {
    public GpuIrCommonSubexpressionRewritePreview {
        insertions = List.copyOf(insertions);
        replacementEdits = List.copyOf(replacementEdits);
        skippedDiagnostics = List.copyOf(skippedDiagnostics);
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

    public int skippedCandidateCount() {
        return skippedDiagnostics.size();
    }

    public Map<GpuIrCommonSubexpressionSkipReason, List<GpuIrCommonSubexpressionSkippedDiagnostic>> skippedDiagnosticsByReason() {
        return skippedDiagnostics.stream()
                .collect(Collectors.groupingBy(
                        GpuIrCommonSubexpressionSkippedDiagnostic::reason,
                        java.util.LinkedHashMap::new,
                        Collectors.toList()
                ));
    }

    public Map<GpuIrCommonSubexpressionSkipReason, Long> skippedReasonCounts() {
        return skippedDiagnostics.stream()
                .collect(Collectors.groupingBy(
                        GpuIrCommonSubexpressionSkippedDiagnostic::reason,
                        java.util.LinkedHashMap::new,
                        Collectors.counting()
                ));
    }
}
