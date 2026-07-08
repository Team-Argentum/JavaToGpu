package net.sixik.ga_utils.javatogpu.irvalidation;

import java.util.Objects;

/**
 * Compact typed summary for explicit CSE artifact/equivalence runs.
 */
public record GpuIrCommonSubexpressionArtifactSummary(
        boolean successful,
        int insertionCount,
        int replacementCount,
        int skippedCount,
        boolean runtimeEquivalenceSuccessful,
        int inputCaseCount,
        int comparedOutputCount,
        int diagnosticCount,
        String firstDiagnostic,
        String skippedDominanceStatusCounts
) {
    public GpuIrCommonSubexpressionArtifactSummary {
        if (insertionCount < 0) {
            throw new IllegalArgumentException("insertionCount must be non-negative");
        }
        if (replacementCount < 0) {
            throw new IllegalArgumentException("replacementCount must be non-negative");
        }
        if (skippedCount < 0) {
            throw new IllegalArgumentException("skippedCount must be non-negative");
        }
        if (inputCaseCount < 0) {
            throw new IllegalArgumentException("inputCaseCount must be non-negative");
        }
        if (comparedOutputCount < 0) {
            throw new IllegalArgumentException("comparedOutputCount must be non-negative");
        }
        if (diagnosticCount < 0) {
            throw new IllegalArgumentException("diagnosticCount must be non-negative");
        }
        firstDiagnostic = Objects.requireNonNull(firstDiagnostic, "firstDiagnostic");
        skippedDominanceStatusCounts = Objects.requireNonNull(
                skippedDominanceStatusCounts,
                "skippedDominanceStatusCounts"
        );
    }

    public boolean hasDiagnostics() {
        return diagnosticCount > 0;
    }

    public boolean hasSkippedCandidates() {
        return skippedCount > 0;
    }

    public String summaryLine() {
        return "CSE artifact"
                + " successful=" + successful
                + " insertions=" + insertionCount
                + " replacements=" + replacementCount
                + " skipped=" + skippedCount
                + " runtimeEquivalenceSuccessful=" + runtimeEquivalenceSuccessful
                + " inputCases=" + inputCaseCount
                + " comparedOutputs=" + comparedOutputCount
                + " diagnostics=" + diagnosticCount
                + " skippedDominanceStatusCounts=" + skippedDominanceStatusCounts
                + (hasDiagnostics() ? " firstDiagnostic=" + firstDiagnostic : "");
    }
}
