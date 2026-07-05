package net.sixik.ga_utils.javatogpu.irvalidation;

import java.util.Objects;

/**
 * Compact typed summary for explicit literal canonicalization artifact/equivalence runs.
 */
public record GpuIrCommonSubexpressionSimpleArithmeticLiteralArtifactSummary(
        boolean successful,
        int previewCandidateCount,
        int uniqueCanonicalKeyCount,
        boolean numericSemanticsFullyProven,
        boolean runtimeEquivalenceSuccessful,
        int inputCaseCount,
        int comparedOutputCount,
        int diagnosticCount,
        String firstDiagnostic,
        String gateReadiness,
        String gateBlockingReasons
) {
    public GpuIrCommonSubexpressionSimpleArithmeticLiteralArtifactSummary {
        if (previewCandidateCount < 0) {
            throw new IllegalArgumentException("previewCandidateCount must be non-negative");
        }
        if (uniqueCanonicalKeyCount < 0) {
            throw new IllegalArgumentException("uniqueCanonicalKeyCount must be non-negative");
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
        gateReadiness = Objects.requireNonNull(gateReadiness, "gateReadiness");
        gateBlockingReasons = Objects.requireNonNull(gateBlockingReasons, "gateBlockingReasons");
    }

    public boolean hasDiagnostics() {
        return diagnosticCount > 0;
    }

    public boolean hasPreviewCandidates() {
        return previewCandidateCount > 0;
    }

    public String summaryLine() {
        return "CSE simple arithmetic literal artifact"
                + " successful=" + successful
                + " previewCandidates=" + previewCandidateCount
                + " uniqueCanonicalKeys=" + uniqueCanonicalKeyCount
                + " numericSemanticsFullyProven=" + numericSemanticsFullyProven
                + " runtimeEquivalenceSuccessful=" + runtimeEquivalenceSuccessful
                + " inputCases=" + inputCaseCount
                + " comparedOutputs=" + comparedOutputCount
                + " diagnostics=" + diagnosticCount
                + " gateReadiness=" + gateReadiness
                + " gateBlockingReasons=" + gateBlockingReasons
                + (hasDiagnostics() ? " firstDiagnostic=" + firstDiagnostic : "");
    }
}
