package net.sixik.ga_utils.javatogpu.irvalidation;

import java.util.Objects;

/**
 * Compact typed summary for logging explicit prototype auto-vectorization artifact runs.
 */
public record GpuIrAutoVectorizationPrototypeArtifactSummary(
        String methodName,
        boolean successful,
        int appliedRewriteCount,
        boolean runtimeEquivalenceSuccessful,
        int inputCaseCount,
        int comparedOutputCount,
        int diagnosticCount,
        String firstDiagnostic,
        String appliedRewriteFamilies
) {
    public GpuIrAutoVectorizationPrototypeArtifactSummary {
        if (methodName == null || methodName.isBlank()) {
            throw new IllegalArgumentException("methodName must not be blank");
        }
        if (appliedRewriteCount < 0) {
            throw new IllegalArgumentException("appliedRewriteCount must be non-negative");
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
        appliedRewriteFamilies = Objects.requireNonNull(appliedRewriteFamilies, "appliedRewriteFamilies");
    }

    public boolean hasDiagnostics() {
        return diagnosticCount > 0;
    }

    public String summaryLine() {
        return "auto-vectorization prototype artifact"
                + " method=" + methodName
                + " successful=" + successful
                + " appliedRewrites=" + appliedRewriteCount
                + " runtimeEquivalenceSuccessful=" + runtimeEquivalenceSuccessful
                + " inputCases=" + inputCaseCount
                + " comparedOutputs=" + comparedOutputCount
                + " diagnostics=" + diagnosticCount
                + " appliedRewriteFamilies=" + appliedRewriteFamilies
                + (hasDiagnostics() ? " firstDiagnostic=" + firstDiagnostic : "");
    }
}
