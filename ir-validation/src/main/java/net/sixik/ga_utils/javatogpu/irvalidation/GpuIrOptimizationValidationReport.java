package net.sixik.ga_utils.javatogpu.irvalidation;

import java.util.Map;
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

    public GpuIrCommonSubexpressionArtifactSnapshot commonSubexpressionArtifactSnapshot() {
        return new GpuIrCommonSubexpressionArtifactSnapshot(commonSubexpressionPreview);
    }

    public GpuIrCommonSubexpressionLocalExpressionDominanceReport commonSubexpressionLocalExpressionDominanceReport() {
        return commonSubexpressionArtifactSnapshot().localExpressionDominanceReport();
    }

    public Optional<GpuIrCommonSubexpressionSkippedDiagnostic> firstCommonSubexpressionSkippedDiagnostic() {
        return commonSubexpressionArtifactSnapshot().firstSkippedDiagnostic();
    }

    public Optional<GpuIrCommonSubexpressionDominanceStatus> firstCommonSubexpressionSkippedDominanceStatus() {
        return commonSubexpressionArtifactSnapshot().firstSkippedDominanceStatus();
    }

    public Optional<String> firstCommonSubexpressionSkippedDominanceSummary() {
        return commonSubexpressionArtifactSnapshot().firstSkippedDominanceSummary();
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

    public GpuIrAutoVectorizationArtifactSnapshot autoVectorizationArtifactSnapshot() {
        return new GpuIrAutoVectorizationArtifactSnapshot(
                autoVectorizationPreview,
                autoVectorizationRewriteDryRunReport,
                autoVectorizationResolvedRewriteOperations
        );
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

    public GpuIrOptimizerGateExplanation optimizerGateExplanation() {
        return optimizerGateSnapshot().explanation();
    }

    public GpuIrOptimizerGateSnapshot optimizerGateSnapshot() {
        return GpuIrOptimizerGateSnapshot.from(this);
    }

    public Map<String, Long> optimizerGateSourceCounts() {
        return optimizerGateSnapshot().sourceCounts();
    }

    public String optimizerGateSourceCountsSummary() {
        return optimizerGateSnapshot().sourceCountsSummary();
    }

    public Map<String, Long> optimizerGateFamilyCounts() {
        return optimizerGateSnapshot().familyCounts();
    }

    public String optimizerGateFamilyCountsSummary() {
        return optimizerGateSnapshot().familyCountsSummary();
    }

    public GpuIrOptimizerGatePolicyDecision optimizerGatePolicyDecision(GpuIrOptimizationValidationMode mode) {
        return GpuIrOptimizerGatePolicyDecision.from(mode, this);
    }

    /**
     * Short one-line summary intended for javac diagnostics and CI logs.
     */
    public String compactSummary() {
        return "ir optimization validation method=" + methodName
                + " safety=" + (hasSafetyError() ? "failed" : "ok")
                + " optimizerGateBlocked=" + optimizerGateExplanation().blocked()
                + " optimizerGateSource=" + optimizerGateExplanation().source()
                + " optimizerGateFamily=" + optimizerGateExplanation().family()
                + " optimizerGateSourceCounts=" + optimizerGateSourceCountsSummary()
                + " optimizerGateFamilyCounts=" + optimizerGateFamilyCountsSummary()
                + " optimizerDiagnostics=" + optimizerDiagnosticCount()
                + " cseInsertions=" + commonSubexpressionInsertionCount()
                + " cseReplacements=" + commonSubexpressionReplacementCount()
                + " cseSkipped=" + commonSubexpressionSkippedCount()
                + " cseLocalExpressionProvenCandidates=" + commonSubexpressionLocalExpressionDominanceReport().provenCandidateCount()
                + " cseLocalExpressionProvenReplacements=" + commonSubexpressionLocalExpressionDominanceReport().provenReplacementCount()
                + " cseLocalExpressionBlockedCandidates=" + commonSubexpressionLocalExpressionDominanceReport().blockedCandidateCount()
                + " cseLocalExpressionHasEvidence=" + commonSubexpressionLocalExpressionDominanceReport().hasLocalExpressionEvidence()
                + firstCommonSubexpressionSkippedDominanceStatus()
                .map(status -> " cseFirstSkippedDominanceStatus=" + status.artifactValue())
                .orElse("")
                + " autoVectorizationCandidates=" + autoVectorizationRewriteCandidateCount()
                + " autoVectorizationRewriteReadiness=" + autoVectorizationPreview.rewriteReadiness().artifactValue()
                + " autoVectorizationCanApplyRewrite=" + autoVectorizationPreview.canApplyRewrite()
                + " autoVectorizationProofDecision=" + autoVectorizationPreview.proofDecision().status().artifactValue()
                + " autoVectorizationProofDecisionAllowRewrite=" + autoVectorizationPreview.proofDecision().allowRewrite()
                + (autoVectorizationPreview.proofDecision().blockingProofKinds().isEmpty()
                ? ""
                : " autoVectorizationProofDecisionBlockingKinds=" + autoVectorizationPreview.proofDecision().blockingProofKinds())
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
                + " autoVectorizationProofBundleRewriteSafe=" + autoVectorizationPreview.proofBundle().rewriteSafe()
                + " autoVectorizationProofBundleDiagnostics=" + autoVectorizationPreview.proofBundle().diagnosticCount()
                + " autoVectorizationProofBundleUnsafeProofs=" + autoVectorizationPreview.proofBundle().unsafeProofSummaries().size()
                + autoVectorizationPreview.proofBundle().firstUnsafeProofSummary()
                .map(summary -> " autoVectorizationProofBundleFirstUnsafeProof=" + summary.proofKind() + "@" + summary.location())
                .orElse("")
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
                + " optimizerGate={" + optimizerGateSnapshot().compactSummary() + "}"
                + " optimizerGateSourceCounts=" + optimizerGateSourceCountsSummary()
                + " optimizerGateFamilyCounts=" + optimizerGateFamilyCountsSummary()
                + " optimizerDiagnostics=" + optimizerDiagnosticCount()
                + " cse={" + commonSubexpressionPreview.summary() + "}"
                + " cseArtifacts={" + commonSubexpressionArtifactSnapshot().summary() + "}"
                + " cseLocalExpression={" + commonSubexpressionLocalExpressionDominanceReport().summary() + "}"
                + firstCommonSubexpressionSkippedDominanceSummary()
                .map(summary -> " cseFirstSkippedDominance={" + summary + "}")
                .orElse("")
                + " autoVectorizationRewritePolicy={" + autoVectorizationPreview.rewritePolicy().summary() + "}"
                + " autoVectorizationRewriteDryRun={" + autoVectorizationRewriteDryRunReport.summary() + "}"
                + " autoVectorizationResolvedRewriteOperations={" + autoVectorizationResolvedRewriteOperations.summary() + "}"
                + " autoVectorizationProofBundle={" + autoVectorizationPreview.proofBundle().summaryLine() + "}"
                + " autoVectorizationArtifacts={" + autoVectorizationArtifactSnapshot().summary() + "}"
                + " autoVectorization={" + autoVectorizationPreview.summary() + "}";
    }

    public String summary() {
        return detailedSummary();
    }
}
