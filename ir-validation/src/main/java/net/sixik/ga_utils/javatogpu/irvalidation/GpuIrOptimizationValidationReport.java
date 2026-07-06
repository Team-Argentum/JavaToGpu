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
        GpuIrCommonSubexpressionSimpleArithmeticNumericBoundaryReport commonSubexpressionNumericBoundaryReport,
        GpuIrCommonSubexpressionSimpleArithmeticLiteralProofReport commonSubexpressionLiteralProofReport,
        GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationReport commonSubexpressionLiteralCanonicalizationReport,
        GpuIrCommonSubexpressionSimpleArithmeticLiteralNumericSemanticsProofReport commonSubexpressionLiteralNumericSemanticsProofReport,
        GpuIrCommonSubexpressionSimpleArithmeticLiteralTypedNumericBlockerSummaryReport commonSubexpressionLiteralTypedNumericBlockerSummaryReport,
        GpuIrCommonSubexpressionSimpleArithmeticLiteralRuntimeEquivalenceReport commonSubexpressionLiteralRuntimeEquivalenceReport,
        GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationGate commonSubexpressionLiteralCanonicalizationGate,
        GpuIrCommonSubexpressionSimpleArithmeticLiteralFingerprintDecisionReport commonSubexpressionLiteralFingerprintDecisionReport,
        GpuIrCommonSubexpressionSimpleArithmeticLiteralFingerprintParityReport commonSubexpressionLiteralFingerprintParityReport,
        GpuIrCommonSubexpressionSimpleArithmeticLiteralEnablementReport commonSubexpressionLiteralEnablementReport,
        GpuIrCommonSubexpressionSimpleArithmeticLiteralRewritePreflightReport commonSubexpressionLiteralRewritePreflightReport,
        GpuIrCommonSubexpressionSimpleArithmeticLiteralRewriteOperationPreviewReport commonSubexpressionLiteralRewriteOperationPreviewReport,
        GpuIrCommonSubexpressionSimpleArithmeticLiteralPromotionChecklistReport commonSubexpressionLiteralPromotionChecklistReport,
        GpuIrCommonSubexpressionSimpleArithmeticLiteralPromotionReadinessSummaryReport commonSubexpressionLiteralPromotionReadinessSummaryReport,
        GpuIrCommonSubexpressionSimpleArithmeticLiteralConsistencyCheckReport commonSubexpressionLiteralConsistencyCheckReport,
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
        commonSubexpressionNumericBoundaryReport = Objects.requireNonNull(commonSubexpressionNumericBoundaryReport, "commonSubexpressionNumericBoundaryReport");
        commonSubexpressionLiteralProofReport = Objects.requireNonNull(commonSubexpressionLiteralProofReport, "commonSubexpressionLiteralProofReport");
        commonSubexpressionLiteralCanonicalizationReport = Objects.requireNonNull(commonSubexpressionLiteralCanonicalizationReport, "commonSubexpressionLiteralCanonicalizationReport");
        commonSubexpressionLiteralNumericSemanticsProofReport = Objects.requireNonNull(commonSubexpressionLiteralNumericSemanticsProofReport, "commonSubexpressionLiteralNumericSemanticsProofReport");
        commonSubexpressionLiteralTypedNumericBlockerSummaryReport = Objects.requireNonNull(commonSubexpressionLiteralTypedNumericBlockerSummaryReport, "commonSubexpressionLiteralTypedNumericBlockerSummaryReport");
        commonSubexpressionLiteralRuntimeEquivalenceReport = Objects.requireNonNull(commonSubexpressionLiteralRuntimeEquivalenceReport, "commonSubexpressionLiteralRuntimeEquivalenceReport");
        commonSubexpressionLiteralCanonicalizationGate = Objects.requireNonNull(commonSubexpressionLiteralCanonicalizationGate, "commonSubexpressionLiteralCanonicalizationGate");
        commonSubexpressionLiteralFingerprintDecisionReport = Objects.requireNonNull(commonSubexpressionLiteralFingerprintDecisionReport, "commonSubexpressionLiteralFingerprintDecisionReport");
        commonSubexpressionLiteralFingerprintParityReport = Objects.requireNonNull(commonSubexpressionLiteralFingerprintParityReport, "commonSubexpressionLiteralFingerprintParityReport");
        commonSubexpressionLiteralEnablementReport = Objects.requireNonNull(commonSubexpressionLiteralEnablementReport, "commonSubexpressionLiteralEnablementReport");
        commonSubexpressionLiteralRewritePreflightReport = Objects.requireNonNull(commonSubexpressionLiteralRewritePreflightReport, "commonSubexpressionLiteralRewritePreflightReport");
        commonSubexpressionLiteralRewriteOperationPreviewReport = Objects.requireNonNull(commonSubexpressionLiteralRewriteOperationPreviewReport, "commonSubexpressionLiteralRewriteOperationPreviewReport");
        commonSubexpressionLiteralPromotionChecklistReport = Objects.requireNonNull(commonSubexpressionLiteralPromotionChecklistReport, "commonSubexpressionLiteralPromotionChecklistReport");
        commonSubexpressionLiteralPromotionReadinessSummaryReport = Objects.requireNonNull(commonSubexpressionLiteralPromotionReadinessSummaryReport, "commonSubexpressionLiteralPromotionReadinessSummaryReport");
        commonSubexpressionLiteralConsistencyCheckReport = Objects.requireNonNull(commonSubexpressionLiteralConsistencyCheckReport, "commonSubexpressionLiteralConsistencyCheckReport");
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
        return new GpuIrCommonSubexpressionArtifactSnapshot(methodName, commonSubexpressionPreview);
    }

    public GpuIrCommonSubexpressionLocalExpressionDominanceReport commonSubexpressionLocalExpressionDominanceReport() {
        return commonSubexpressionArtifactSnapshot().localExpressionDominanceReport();
    }

    public GpuIrCommonSubexpressionSimpleArithmeticNumericBoundaryReport commonSubexpressionNumericBoundaryReport() {
        return commonSubexpressionNumericBoundaryReport;
    }

    public GpuIrCommonSubexpressionSimpleArithmeticLiteralProofReport commonSubexpressionLiteralProofReport() {
        return commonSubexpressionLiteralProofReport;
    }

    public GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationReport commonSubexpressionLiteralCanonicalizationReport() {
        return commonSubexpressionLiteralCanonicalizationReport;
    }

    public GpuIrCommonSubexpressionSimpleArithmeticLiteralNumericSemanticsProofReport commonSubexpressionLiteralNumericSemanticsProofReport() {
        return commonSubexpressionLiteralNumericSemanticsProofReport;
    }

    public GpuIrCommonSubexpressionSimpleArithmeticLiteralTypedNumericBlockerSummaryReport commonSubexpressionLiteralTypedNumericBlockerSummaryReport() {
        return commonSubexpressionLiteralTypedNumericBlockerSummaryReport;
    }

    public GpuIrCommonSubexpressionSimpleArithmeticLiteralRuntimeEquivalenceReport commonSubexpressionLiteralRuntimeEquivalenceReport() {
        return commonSubexpressionLiteralRuntimeEquivalenceReport;
    }

    public GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationGate commonSubexpressionLiteralCanonicalizationGate() {
        return commonSubexpressionLiteralCanonicalizationGate;
    }

    public GpuIrCommonSubexpressionSimpleArithmeticLiteralFingerprintDecisionReport commonSubexpressionLiteralFingerprintDecisionReport() {
        return commonSubexpressionLiteralFingerprintDecisionReport;
    }

    public GpuIrCommonSubexpressionSimpleArithmeticLiteralFingerprintParityReport commonSubexpressionLiteralFingerprintParityReport() {
        return commonSubexpressionLiteralFingerprintParityReport;
    }

    public GpuIrCommonSubexpressionSimpleArithmeticLiteralEnablementReport commonSubexpressionLiteralEnablementReport() {
        return commonSubexpressionLiteralEnablementReport;
    }

    public GpuIrCommonSubexpressionSimpleArithmeticLiteralRewritePreflightReport commonSubexpressionLiteralRewritePreflightReport() {
        return commonSubexpressionLiteralRewritePreflightReport;
    }

    public GpuIrCommonSubexpressionSimpleArithmeticLiteralRewriteOperationPreviewReport commonSubexpressionLiteralRewriteOperationPreviewReport() {
        return commonSubexpressionLiteralRewriteOperationPreviewReport;
    }

    public GpuIrCommonSubexpressionSimpleArithmeticLiteralPromotionChecklistReport commonSubexpressionLiteralPromotionChecklistReport() {
        return commonSubexpressionLiteralPromotionChecklistReport;
    }

    public GpuIrCommonSubexpressionSimpleArithmeticLiteralPromotionReadinessSummaryReport commonSubexpressionLiteralPromotionReadinessSummaryReport() {
        return commonSubexpressionLiteralPromotionReadinessSummaryReport;
    }

    public GpuIrCommonSubexpressionSimpleArithmeticLiteralConsistencyCheckReport commonSubexpressionLiteralConsistencyCheckReport() {
        return commonSubexpressionLiteralConsistencyCheckReport;
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

    public GpuIrOptimizationValidationOptimizerLayerReadinessSummaryReport optimizerLayerReadinessSummaryReport() {
        return GpuIrOptimizationValidationOptimizerLayerReadinessSummaryReport.from(this);
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
                + " cseSimpleArithmeticNumericBoundaryBlockedCandidates=" + commonSubexpressionNumericBoundaryReport.blockedCandidateCount()
                + " cseSimpleArithmeticNumericBoundaryLiteralOperands=" + commonSubexpressionNumericBoundaryReport.literalOperandCount()
                + " cseSimpleArithmeticNumericBoundaryCastOperands=" + commonSubexpressionNumericBoundaryReport.castOperandCount()
                + " cseSimpleArithmeticLiteralProofSafeCandidates=" + commonSubexpressionLiteralProofReport.safeCandidateCount()
                + " cseSimpleArithmeticLiteralProofBlockedCandidates=" + commonSubexpressionLiteralProofReport.blockedCandidateCount()
                + " cseSimpleArithmeticLiteralProofHasSafeCandidates=" + commonSubexpressionLiteralProofReport.hasSafeCandidates()
                + " cseSimpleArithmeticLiteralProofSafeOperatorTypeCounts=" + commonSubexpressionLiteralProofReport.safeOperatorTypeCounts()
                + " cseSimpleArithmeticLiteralProofBlockedOperatorTypeCounts=" + commonSubexpressionLiteralProofReport.blockedOperatorTypeCounts()
                + " cseSimpleArithmeticLiteralCanonicalizationCandidates=" + commonSubexpressionLiteralCanonicalizationReport.candidateCount()
                + " cseSimpleArithmeticLiteralCanonicalizationReadiness=" + commonSubexpressionLiteralCanonicalizationReport.readiness()
                + " cseSimpleArithmeticLiteralCanonicalizationUniqueCanonicalKeys=" + commonSubexpressionLiteralCanonicalizationReport.uniqueCanonicalKeyCount()
                + " cseSimpleArithmeticLiteralCanonicalizationCanonicalKeyCounts=" + commonSubexpressionLiteralCanonicalizationReport.canonicalKeyCounts()
                + " cseSimpleArithmeticLiteralNumericSemanticsProofReadiness=" + commonSubexpressionLiteralNumericSemanticsProofReport.readiness()
                + " cseSimpleArithmeticLiteralNumericSemanticsProofFullyProven=" + commonSubexpressionLiteralNumericSemanticsProofReport.fullyProven()
                + " cseSimpleArithmeticLiteralNumericSemanticsProofProvenCandidates=" + commonSubexpressionLiteralNumericSemanticsProofReport.provenCandidateCount()
                + " cseSimpleArithmeticLiteralNumericSemanticsProofBlockedCandidates=" + commonSubexpressionLiteralNumericSemanticsProofReport.blockedCandidateCount()
                + " cseSimpleArithmeticLiteralTypedNumericBlockersReadiness=" + commonSubexpressionLiteralTypedNumericBlockerSummaryReport.readiness()
                + " cseSimpleArithmeticLiteralTypedNumericBlockersTotal=" + commonSubexpressionLiteralTypedNumericBlockerSummaryReport.totalBlockedCandidateCount()
                + " cseSimpleArithmeticLiteralTypedNumericBlockersFamilies=" + commonSubexpressionLiteralTypedNumericBlockerSummaryReport.combinedBlockerCounts()
                + " cseSimpleArithmeticLiteralRuntimeEquivalenceReadiness=" + commonSubexpressionLiteralRuntimeEquivalenceReport.readiness()
                + " cseSimpleArithmeticLiteralRuntimeEquivalenceSuccessful=" + commonSubexpressionLiteralRuntimeEquivalenceReport.successful()
                + " cseSimpleArithmeticLiteralRuntimeEquivalenceDiagnostics=" + commonSubexpressionLiteralRuntimeEquivalenceReport.diagnosticCount()
                + " runtimeEquivalenceDiagnosticFamilyCounts=" + runtimeEquivalenceDiagnosticFamilyCountsSummary()
                + " cseSimpleArithmeticLiteralCanonicalizationGateReadiness=" + commonSubexpressionLiteralCanonicalizationGate.readiness()
                + " cseSimpleArithmeticLiteralCanonicalizationGateCanPromoteToFingerprint=" + commonSubexpressionLiteralCanonicalizationGate.canPromoteToFingerprint()
                + " cseSimpleArithmeticLiteralCanonicalizationGateBlockingReasons=" + commonSubexpressionLiteralCanonicalizationGate.blockingReasons()
                + " cseSimpleArithmeticLiteralFingerprintDecisionReadiness=" + commonSubexpressionLiteralFingerprintDecisionReport.readiness()
                + " cseSimpleArithmeticLiteralFingerprintDecisionReadyForProduction=" + commonSubexpressionLiteralFingerprintDecisionReport.readyForProductionFingerprintIntegration()
                + " cseSimpleArithmeticLiteralFingerprintDecisionBlockingReasons=" + commonSubexpressionLiteralFingerprintDecisionReport.blockingReasons()
                + " cseSimpleArithmeticLiteralFingerprintParityReadiness=" + commonSubexpressionLiteralFingerprintParityReport.readiness()
                + " cseSimpleArithmeticLiteralFingerprintParityPreviewOnlyKeys=" + commonSubexpressionLiteralFingerprintParityReport.previewOnlyKeyCount()
                + " cseSimpleArithmeticLiteralFingerprintParityBlockers=" + commonSubexpressionLiteralFingerprintParityReport.blockers()
                + " cseSimpleArithmeticLiteralEnablementVerdict=" + commonSubexpressionLiteralEnablementReport.verdict()
                + " cseSimpleArithmeticLiteralEnablementBlockers=" + commonSubexpressionLiteralEnablementReport.blockers()
                + " cseSimpleArithmeticLiteralRewritePreflightReadiness=" + commonSubexpressionLiteralRewritePreflightReport.readiness()
                + " cseSimpleArithmeticLiteralRewritePreflightEligibleCandidates=" + commonSubexpressionLiteralRewritePreflightReport.eligibleCandidateCount()
                + " cseSimpleArithmeticLiteralRewritePreflightBlockedCandidates=" + commonSubexpressionLiteralRewritePreflightReport.blockedCandidateCount()
                + " cseSimpleArithmeticLiteralRewriteOperationPreviewReadiness=" + commonSubexpressionLiteralRewriteOperationPreviewReport.readiness()
                + " cseSimpleArithmeticLiteralRewriteOperationPreviewEligibleOperations=" + commonSubexpressionLiteralRewriteOperationPreviewReport.eligibleOperationCount()
                + " cseSimpleArithmeticLiteralRewriteOperationPreviewBlockedOperations=" + commonSubexpressionLiteralRewriteOperationPreviewReport.blockedOperationCount()
                + " cseSimpleArithmeticLiteralPromotionChecklistVerdict=" + commonSubexpressionLiteralPromotionChecklistReport.verdict()
                + " cseSimpleArithmeticLiteralPromotionChecklistReadyForProductionMutation=" + commonSubexpressionLiteralPromotionChecklistReport.readyForProductionMutation()
                + " cseSimpleArithmeticLiteralPromotionChecklistRemainingWorkCount=" + commonSubexpressionLiteralPromotionChecklistReport.remainingWorkCount()
                + " cseSimpleArithmeticLiteralPromotionReadinessVerdict=" + commonSubexpressionLiteralPromotionReadinessSummaryReport.verdict()
                + " cseSimpleArithmeticLiteralPromotionReadinessReadyForProductionMutation=" + commonSubexpressionLiteralPromotionReadinessSummaryReport.readyForProductionMutation()
                + " cseSimpleArithmeticLiteralPromotionReadinessBlockingReasons=" + commonSubexpressionLiteralPromotionReadinessSummaryReport.blockingReasons()
                + " cseSimpleArithmeticLiteralConsistencyCheckVerdict=" + commonSubexpressionLiteralConsistencyCheckReport.verdict()
                + " cseSimpleArithmeticLiteralConsistencyCheckConsistent=" + commonSubexpressionLiteralConsistencyCheckReport.consistent()
                + " cseSimpleArithmeticLiteralConsistencyCheckFailedChecks=" + commonSubexpressionLiteralConsistencyCheckReport.failedCheckCount()
                + " cseRewritePolicyCanRewrite=" + commonSubexpressionArtifactSnapshot().rewritePolicy().canRewrite()
                + " cseRewritePolicyReadiness=" + commonSubexpressionArtifactSnapshot().rewritePolicy().readiness().artifactValue()
                + " cseRewritePolicyBlockingSkippedCandidates=" + commonSubexpressionArtifactSnapshot().rewritePolicy().blockingSkippedCandidateCount()
                + firstCommonSubexpressionSkippedDominanceStatus()
                .map(status -> " cseFirstSkippedDominanceStatus=" + status.artifactValue())
                .orElse("")
                + " autoVectorizationCandidates=" + autoVectorizationRewriteCandidateCount()
                + " autoVectorizationRewriteReadiness=" + autoVectorizationPreview.rewriteReadiness().artifactValue()
                + " autoVectorizationCanApplyRewrite=" + autoVectorizationPreview.canApplyRewrite()
                + " autoVectorizationVectorTypeCounts=" + autoVectorizationPreview.vectorTypeCounts()
                + " autoVectorizationUniqueVectorTypes=" + autoVectorizationPreview.vectorTypeCounts().size()
                + " autoVectorizationWarningFamilyCounts=" + autoVectorizationPreview.warningFamilyCounts()
                + " autoVectorizationUniqueWarningFamilies=" + autoVectorizationPreview.warningFamilyCounts().size()
                + " autoVectorizationRejectionReasonCounts=" + autoVectorizationPreview.rejectionReasonCounts()
                + " autoVectorizationUniqueRejectionReasons=" + autoVectorizationPreview.rejectionReasonCounts().size()
                + " autoVectorizationProofDecision=" + autoVectorizationPreview.proofDecision().status().artifactValue()
                + " autoVectorizationProofDecisionAllowRewrite=" + autoVectorizationPreview.proofDecision().allowRewrite()
                + (autoVectorizationPreview.proofDecision().blockingProofKinds().isEmpty()
                ? ""
                : " autoVectorizationProofDecisionBlockingKinds=" + autoVectorizationPreview.proofDecision().blockingProofKinds())
                + " autoVectorizationReadinessVerdict=" + autoVectorizationArtifactSnapshot().readinessSummaryReport().verdict()
                + " autoVectorizationReadinessReadyForPrototypeRewrite=" + autoVectorizationArtifactSnapshot().readinessSummaryReport().readyForPrototypeRewrite()
                + " autoVectorizationReadinessBlockingReasons=" + autoVectorizationArtifactSnapshot().readinessSummaryReport().blockingReasons()
                + " optimizerLayerReadinessVerdict=" + optimizerLayerReadinessSummaryReport().verdict()
                + " optimizerLayerReadinessBlockingLayers=" + optimizerLayerReadinessSummaryReport().blockingLayers()
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
                + " cseSimpleArithmeticNumericBoundary={" + commonSubexpressionNumericBoundaryReport.summary() + "}"
                + " cseSimpleArithmeticLiteralProof={" + commonSubexpressionLiteralProofReport.summary() + "}"
                + " cseSimpleArithmeticLiteralCanonicalization={" + commonSubexpressionLiteralCanonicalizationReport.summary() + "}"
                + " cseSimpleArithmeticLiteralNumericSemanticsProof={" + commonSubexpressionLiteralNumericSemanticsProofReport.summary() + "}"
                + " cseSimpleArithmeticLiteralTypedNumericBlockers={" + commonSubexpressionLiteralTypedNumericBlockerSummaryReport.summary() + "}"
                + " cseSimpleArithmeticLiteralRuntimeEquivalence={" + commonSubexpressionLiteralRuntimeEquivalenceReport.summary() + "}"
                + " runtimeEquivalenceDiagnosticFamilyCounts=" + runtimeEquivalenceDiagnosticFamilyCountsSummary()
                + " cseSimpleArithmeticLiteralCanonicalizationGate={" + commonSubexpressionLiteralCanonicalizationGate.summary() + "}"
                + " cseSimpleArithmeticLiteralFingerprintDecision={" + commonSubexpressionLiteralFingerprintDecisionReport.summary() + "}"
                + " cseSimpleArithmeticLiteralFingerprintParity={" + commonSubexpressionLiteralFingerprintParityReport.summary() + "}"
                + " cseSimpleArithmeticLiteralEnablement={" + commonSubexpressionLiteralEnablementReport.summary() + "}"
                + " cseSimpleArithmeticLiteralRewritePreflight={" + commonSubexpressionLiteralRewritePreflightReport.summary() + "}"
                + " cseSimpleArithmeticLiteralRewriteOperationPreview={" + commonSubexpressionLiteralRewriteOperationPreviewReport.summary() + "}"
                + " cseSimpleArithmeticLiteralPromotionChecklist={" + commonSubexpressionLiteralPromotionChecklistReport.summary() + "}"
                + " cseSimpleArithmeticLiteralPromotionReadiness={" + commonSubexpressionLiteralPromotionReadinessSummaryReport.summary() + "}"
                + " cseSimpleArithmeticLiteralConsistencyCheck={" + commonSubexpressionLiteralConsistencyCheckReport.summary() + "}"
                + " cseRewritePolicy={" + commonSubexpressionArtifactSnapshot().rewritePolicy().summary() + "}"
                + " optimizerLayerReadiness={" + optimizerLayerReadinessSummaryReport().summary() + "}"
                + firstCommonSubexpressionSkippedDominanceSummary()
                .map(summary -> " cseFirstSkippedDominance={" + summary + "}")
                .orElse("")
                + " autoVectorizationRewritePolicy={" + autoVectorizationPreview.rewritePolicy().summary() + "}"
                + " autoVectorizationRewriteDryRun={" + autoVectorizationRewriteDryRunReport.summary() + "}"
                + " autoVectorizationResolvedRewriteOperations={" + autoVectorizationResolvedRewriteOperations.summary() + "}"
                + " autoVectorizationProofBundle={" + autoVectorizationPreview.proofBundle().summaryLine() + "}"
                + " autoVectorizationReadiness={" + autoVectorizationArtifactSnapshot().readinessSummaryReport().summary() + "}"
                + " autoVectorizationArtifacts={" + autoVectorizationArtifactSnapshot().summary() + "}"
                + " autoVectorization={" + autoVectorizationPreview.summary() + "}";
    }

    public String summary() {
        return detailedSummary();
    }

    public String runtimeEquivalenceDiagnosticFamilyCountsSummary() {
        return GpuIrRuntimeEquivalenceDiagnosticFamilies.countsSummary(
                commonSubexpressionLiteralRuntimeEquivalenceReport.diagnostics()
        );
    }
}
