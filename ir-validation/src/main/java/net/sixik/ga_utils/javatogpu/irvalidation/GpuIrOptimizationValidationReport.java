package net.sixik.ga_utils.javatogpu.irvalidation;

import java.util.Objects;
import java.util.Optional;

/**
 * Unified read-only view of safety validation, CSE planning, and auto-vectorization diagnostics.
 */
public record GpuIrOptimizationValidationReport(
        String methodName,
        Optional<String> safetyError,
        GpuIrCommonSubexpressionRewritePreview commonSubexpressionPreview,
        GpuIrAutoVectorizationPreview autoVectorizationPreview
) {
    public GpuIrOptimizationValidationReport {
        if (methodName == null || methodName.isBlank()) {
            throw new IllegalArgumentException("methodName must not be blank");
        }
        safetyError = Objects.requireNonNull(safetyError, "safetyError");
        commonSubexpressionPreview = Objects.requireNonNull(commonSubexpressionPreview, "commonSubexpressionPreview");
        autoVectorizationPreview = Objects.requireNonNull(autoVectorizationPreview, "autoVectorizationPreview");
    }

    public boolean hasSafetyError() {
        return safetyError.isPresent();
    }

    public boolean hasCommonSubexpressionDiagnostics() {
        return commonSubexpressionPreview.skippedCandidateCount() > 0;
    }

    public boolean hasAutoVectorizationDiagnostics() {
        return autoVectorizationPreview.hasBlockingDiagnostics();
    }

    public boolean hasOptimizerDiagnostics() {
        return hasCommonSubexpressionDiagnostics() || hasAutoVectorizationDiagnostics();
    }

    public int commonSubexpressionInsertionCount() {
        return commonSubexpressionPreview.insertionCount();
    }

    public int commonSubexpressionReplacementCount() {
        return commonSubexpressionPreview.replacementEditCount();
    }

    public int commonSubexpressionSkippedCount() {
        return commonSubexpressionPreview.skippedCandidateCount();
    }

    public int autoVectorizationRewriteCandidateCount() {
        return autoVectorizationPreview.rewriteCandidateCount();
    }

    public int autoVectorizationWarningCount() {
        return autoVectorizationPreview.warningCount();
    }

    public int autoVectorizationRejectionCount() {
        return autoVectorizationPreview.rejectionCount();
    }

    public int optimizerDiagnosticCount() {
        return commonSubexpressionSkippedCount()
                + autoVectorizationWarningCount()
                + autoVectorizationRejectionCount()
                + autoVectorizationPreview.rewritePlanGuardCount();
    }

    public boolean hasBlockingDiagnostics() {
        return hasSafetyError() || hasOptimizerDiagnostics();
    }

    /**
     * Short one-line summary intended for javac diagnostics and CI logs.
     */
    public String compactSummary() {
        return "ir optimization validation method=" + methodName
                + " safety=" + (hasSafetyError() ? "failed" : "ok")
                + " optimizerDiagnostics=" + optimizerDiagnosticCount()
                + " cseInsertions=" + commonSubexpressionInsertionCount()
                + " cseReplacements=" + commonSubexpressionReplacementCount()
                + " cseSkipped=" + commonSubexpressionSkippedCount()
                + " autoVectorizationCandidates=" + autoVectorizationRewriteCandidateCount()
                + " autoVectorizationWarnings=" + autoVectorizationWarningCount()
                + " autoVectorizationRejections=" + autoVectorizationRejectionCount()
                + " autoVectorizationRewritePlanGuards=" + autoVectorizationPreview.rewritePlanGuardCount()
                + (autoVectorizationPreview.hasRewritePlanGuardDiagnostics()
                ? " autoVectorizationRewritePlanGuardFamilies=" + autoVectorizationPreview.rewritePlan().guardFamilyCounts()
                : "");
    }

    /**
     * Detailed summary with the full nested optimizer preview diagnostics.
     */
    public String detailedSummary() {
        return "ir optimization validation method=" + methodName
                + " safety=" + (hasSafetyError() ? "failed" : "ok")
                + (hasSafetyError() ? " safetyError=" + safetyError.orElseThrow() : "")
                + " optimizerDiagnostics=" + optimizerDiagnosticCount()
                + " cse={" + commonSubexpressionPreview.summary() + "}"
                + " autoVectorization={" + autoVectorizationPreview.summary() + "}";
    }

    public String summary() {
        return detailedSummary();
    }
}
