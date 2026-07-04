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
        GpuIrAutoVectorizationPreview autoVectorizationPreview,
        GpuIrAutoVectorizationRewriteDryRunReport autoVectorizationRewriteDryRunReport,
        GpuIrAutoVectorizationResolvedRewriteOperations autoVectorizationResolvedRewriteOperations
) {
    public GpuIrOptimizationValidationReport {
        if (methodName == null || methodName.isBlank()) {
            throw new IllegalArgumentException("methodName must not be blank");
        }
        safetyError = Objects.requireNonNull(safetyError, "safetyError");
        commonSubexpressionPreview = Objects.requireNonNull(commonSubexpressionPreview, "commonSubexpressionPreview");
        autoVectorizationPreview = Objects.requireNonNull(autoVectorizationPreview, "autoVectorizationPreview");
        autoVectorizationRewriteDryRunReport = Objects.requireNonNull(autoVectorizationRewriteDryRunReport, "autoVectorizationRewriteDryRunReport");
        autoVectorizationResolvedRewriteOperations = Objects.requireNonNull(autoVectorizationResolvedRewriteOperations, "autoVectorizationResolvedRewriteOperations");
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

    public boolean autoVectorizationRewriteDryRunSuccessful() {
        return autoVectorizationRewriteDryRunReport.successful();
    }

    public GpuIrAutoVectorizationRewriteDryRunReadiness autoVectorizationRewriteDryRunReadiness() {
        return autoVectorizationRewriteDryRunReport.readiness();
    }

    public int autoVectorizationRewriteDryRunDiagnosticCount() {
        return autoVectorizationRewriteDryRunReport.diagnostics().size();
    }

    public int autoVectorizationResolvedRewriteOperationCount() {
        return autoVectorizationResolvedRewriteOperations.operationCount();
    }

    public int autoVectorizationResolvedRewriteInsertionCount() {
        return autoVectorizationResolvedRewriteOperations.insertions().size();
    }

    public int autoVectorizationResolvedRewriteReplacementCount() {
        return autoVectorizationResolvedRewriteOperations.replacements().size();
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
                + " autoVectorizationRewriteReadiness=" + autoVectorizationPreview.rewriteReadiness().artifactValue()
                + " autoVectorizationCanApplyRewrite=" + autoVectorizationPreview.canApplyRewrite()
                + " autoVectorizationHasPolicyBlockedRewrite=" + autoVectorizationPreview.hasPolicyBlockedRewrite()
                + " autoVectorizationRewritePolicyCanRewrite=" + autoVectorizationPreview.rewritePolicy().canRewrite()
                + " autoVectorizationRewritePolicyBlockingGuards=" + autoVectorizationPreview.rewritePolicy().blockingGuards().size()
                + " autoVectorizationRewriteDryRunReadiness=" + autoVectorizationRewriteDryRunReadiness().artifactValue()
                + " autoVectorizationRewriteDryRunSuccessful=" + autoVectorizationRewriteDryRunSuccessful()
                + " autoVectorizationRewriteDryRunDiagnostics=" + autoVectorizationRewriteDryRunDiagnosticCount()
                + " autoVectorizationResolvedRewriteOperations=" + autoVectorizationResolvedRewriteOperationCount()
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
                + " autoVectorizationRewritePolicy={" + autoVectorizationPreview.rewritePolicy().summary() + "}"
                + " autoVectorizationRewriteDryRun={" + autoVectorizationRewriteDryRunReport.summary() + "}"
                + " autoVectorizationResolvedRewriteOperations={" + autoVectorizationResolvedRewriteOperations.summary() + "}"
                + " autoVectorization={" + autoVectorizationPreview.summary() + "}";
    }

    public String summary() {
        return detailedSummary();
    }
}
