package net.sixik.ga_utils.javatogpu.irvalidation;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuIrOptimizationValidationReportTest {
    @Test
    void summariesIncludeDryRunFailureDiagnostics() {
        EmptyLiteralReports literalReports = emptyLiteralReports("kernel");
        GpuIrOptimizationValidationReport report = new GpuIrOptimizationValidationReport(
                "kernel",
                Optional.empty(),
                new GpuIrCommonSubexpressionRewritePreview(List.of(), List.of(), List.of()),
                GpuIrCommonSubexpressionSimpleArithmeticNumericBoundaryReport.empty("kernel"),
                GpuIrCommonSubexpressionSimpleArithmeticLiteralProofReport.empty("kernel"),
                literalReports.canonicalizationReport(),
                literalReports.numericSemanticsProofReport(),
                literalReports.typedNumericBlockerSummaryReport(),
                literalReports.runtimeEquivalenceReport(),
                literalReports.canonicalizationGate(),
                literalReports.fingerprintDecisionReport(),
                literalReports.fingerprintParityReport(),
                literalReports.enablementReport(),
                literalReports.rewritePreflightReport(),
                literalReports.rewriteOperationPreviewReport(),
                literalReports.promotionChecklistReport(),
                literalReports.promotionReadinessSummaryReport(),
                literalReports.consistencyCheckReport(),
                new GpuIrAutoVectorizationPreview("kernel", List.of(), List.of(), List.of()),
                GpuIrAutoVectorizationRewriteDryRunReport.failed(
                        "kernel",
                        1,
                        1,
                        1,
                        List.of("Auto-vectorization rewrite dry-run failed for kernel: replacement loopLocation expected=stmt[0] actual=stmt[1]")
                ),
                GpuIrAutoVectorizationResolvedRewriteOperations.empty("kernel")
        );

        assertFalse(report.autoVectorizationRewriteDryRunSuccessful());
        assertTrue(report.compactSummary().contains("autoVectorizationRewriteDryRunReadiness=failed"));
        assertTrue(report.compactSummary().contains("autoVectorizationRewriteDryRunSuccessful=false"));
        assertTrue(report.compactSummary().contains("autoVectorizationRewriteDryRunDiagnostics=1"));
        assertTrue(report.compactSummary().contains("autoVectorizationResolvedRewriteOperations=0"));
        assertTrue(report.compactSummary().contains("optimizerBlockerSource=autoVectorization"));
        assertTrue(report.compactSummary().contains("optimizerBlockerFamily=candidateDiscovery.noRewriteCandidates"));
        assertTrue(report.compactSummary().contains("optimizerBlockerRemainingWork=collectRewriteCandidates"));
        assertTrue(report.compactSummary().contains("cseLocalExpressionProvenCandidates=0"));
        assertTrue(report.compactSummary().contains("cseLocalExpressionProvenReplacements=0"));
        assertTrue(report.compactSummary().contains("cseLocalExpressionBlockedCandidates=0"));
        assertTrue(report.compactSummary().contains("cseLocalExpressionHasEvidence=false"));
        assertTrue(report.compactSummary().contains("cseSimpleArithmeticNumericBoundaryBlockedCandidates=0"));
        assertTrue(report.compactSummary().contains("cseSimpleArithmeticNumericBoundaryLiteralOperands=0"));
        assertTrue(report.compactSummary().contains("cseSimpleArithmeticNumericBoundaryCastOperands=0"));
        assertTrue(report.compactSummary().contains("cseSimpleArithmeticLiteralProofSafeCandidates=0"));
        assertTrue(report.compactSummary().contains("cseSimpleArithmeticLiteralProofBlockedCandidates=0"));
        assertTrue(report.compactSummary().contains("cseSimpleArithmeticLiteralProofHasSafeCandidates=false"));
        assertTrue(report.compactSummary().contains("cseSimpleArithmeticLiteralProofSafeOperatorTypeCounts={}"));
        assertTrue(report.compactSummary().contains("cseSimpleArithmeticLiteralProofBlockedOperatorTypeCounts={}"));
        assertTrue(report.compactSummary().contains("cseSimpleArithmeticLiteralCanonicalizationCandidates=0"));
        assertTrue(report.compactSummary().contains("cseSimpleArithmeticLiteralCanonicalizationReadiness=none"));
        assertTrue(report.compactSummary().contains("cseSimpleArithmeticLiteralCanonicalizationUniqueCanonicalKeys=0"));
        assertTrue(report.compactSummary().contains("cseSimpleArithmeticLiteralCanonicalizationCanonicalKeyCounts={}"));
        assertTrue(report.compactSummary().contains("cseSimpleArithmeticLiteralNumericSemanticsProofReadiness=none"));
        assertTrue(report.compactSummary().contains("cseSimpleArithmeticLiteralNumericSemanticsProofFullyProven=false"));
        assertTrue(report.compactSummary().contains("cseSimpleArithmeticLiteralNumericSemanticsProofProvenCandidates=0"));
        assertTrue(report.compactSummary().contains("cseSimpleArithmeticLiteralNumericSemanticsProofBlockedCandidates=0"));
        assertTrue(report.compactSummary().contains("cseSimpleArithmeticLiteralTypedNumericBlockersReadiness=clear"));
        assertTrue(report.compactSummary().contains("cseSimpleArithmeticLiteralTypedNumericBlockersTotal=0"));
        assertTrue(report.compactSummary().contains("cseSimpleArithmeticLiteralTypedNumericBlockersFamilies={}"));
        assertTrue(report.compactSummary().contains("cseSimpleArithmeticLiteralRuntimeEquivalenceReadiness=none"));
        assertTrue(report.compactSummary().contains("cseSimpleArithmeticLiteralRuntimeEquivalenceSuccessful=false"));
        assertTrue(report.compactSummary().contains("cseSimpleArithmeticLiteralRuntimeEquivalenceDiagnostics=1"));
        assertTrue(report.compactSummary().contains("cseSimpleArithmeticLiteralCanonicalizationGateReadiness=none"));
        assertTrue(report.compactSummary().contains("cseSimpleArithmeticLiteralCanonicalizationGateCanPromoteToFingerprint=false"));
        assertTrue(report.compactSummary().contains("cseSimpleArithmeticLiteralCanonicalizationGateBlockingReasons=[noPreviewCandidates]"));
        assertTrue(report.compactSummary().contains("cseSimpleArithmeticLiteralFingerprintDecisionReadiness=none"));
        assertTrue(report.compactSummary().contains("cseSimpleArithmeticLiteralFingerprintDecisionReadyForProduction=false"));
        assertTrue(report.compactSummary().contains("cseSimpleArithmeticLiteralFingerprintDecisionBlockingReasons=[noPreviewCandidates]"));
        assertTrue(report.compactSummary().contains("cseSimpleArithmeticLiteralFingerprintParityReadiness=none"));
        assertTrue(report.compactSummary().contains("cseSimpleArithmeticLiteralFingerprintParityPreviewOnlyKeys=0"));
        assertTrue(report.compactSummary().contains("cseSimpleArithmeticLiteralFingerprintParityBlockers=[noPreviewCandidates, fingerprintDecisionNotReadyForProduction]"));
        assertTrue(report.compactSummary().contains("cseSimpleArithmeticLiteralEnablementVerdict=notReady/noPreviewCandidates"));
        assertTrue(report.compactSummary().contains("cseSimpleArithmeticLiteralEnablementBlockers=[noPreviewCandidates, fingerprintDecisionNotReadyForProduction]"));
        assertTrue(report.compactSummary().contains("cseSimpleArithmeticLiteralRewritePreflightReadiness=none"));
        assertTrue(report.compactSummary().contains("cseSimpleArithmeticLiteralRewritePreflightEligibleCandidates=0"));
        assertTrue(report.compactSummary().contains("cseSimpleArithmeticLiteralRewritePreflightBlockedCandidates=1"));
        assertTrue(report.compactSummary().contains("cseSimpleArithmeticLiteralRewriteOperationPreviewReadiness=none"));
        assertTrue(report.compactSummary().contains("cseSimpleArithmeticLiteralRewriteOperationPreviewEligibleOperations=0"));
        assertTrue(report.compactSummary().contains("cseSimpleArithmeticLiteralRewriteOperationPreviewBlockedOperations=1"));
        assertTrue(report.compactSummary().contains("cseSimpleArithmeticLiteralPromotionChecklistVerdict=notReady/noPreviewCandidates"));
        assertTrue(report.compactSummary().contains("cseSimpleArithmeticLiteralPromotionChecklistReadyForProductionMutation=false"));
        assertTrue(report.compactSummary().contains("cseSimpleArithmeticLiteralPromotionChecklistRemainingWorkCount=6"));
        assertTrue(report.compactSummary().contains("cseSimpleArithmeticLiteralPromotionReadinessVerdict=notReady/noPreviewCandidates"));
        assertTrue(report.compactSummary().contains("cseSimpleArithmeticLiteralPromotionReadinessReadyForProductionMutation=false"));
        assertTrue(report.compactSummary().contains("cseSimpleArithmeticLiteralPromotionReadinessBlockingReasons=[noPreviewCandidates, runtimeEquivalenceNotProven, productionFingerprintIntegrationDisabled, productionMutationDisabled]"));
        assertTrue(report.compactSummary().contains("cseSimpleArithmeticLiteralConsistencyCheckVerdict=consistent"));
        assertTrue(report.compactSummary().contains("cseSimpleArithmeticLiteralConsistencyCheckConsistent=true"));
        assertTrue(report.compactSummary().contains("cseSimpleArithmeticLiteralConsistencyCheckFailedChecks=0"));
        assertTrue(report.compactSummary().contains("cseRewritePolicyCanRewrite=false"));
        assertTrue(report.compactSummary().contains("cseRewritePolicyReadiness=none"));
        assertTrue(report.compactSummary().contains("cseRewritePolicyBlockingSkippedCandidates=0"));
        assertTrue(report.compactSummary().contains("autoVectorizationProofDecision=allow"));
        assertTrue(report.compactSummary().contains("autoVectorizationProofDecisionAllowRewrite=true"));
        assertTrue(report.compactSummary().contains("autoVectorizationReadinessVerdict=notReady/noCandidates"));
        assertTrue(report.compactSummary().contains("autoVectorizationReadinessReadyForPrototypeRewrite=false"));
        assertTrue(report.compactSummary().contains("autoVectorizationReadinessBlockingReasons=[noRewriteCandidates, rewritePolicyBlocksRewrite, dryRunNotReady]"));
        assertTrue(report.compactSummary().contains("optimizerLayerReadinessVerdict=blocked"));
        assertTrue(report.compactSummary().contains("optimizerLayerReadinessBlockingLayers=[cseLiteralPromotion, autoVectorization]"));
        assertTrue(report.compactSummary().contains("autoVectorizationVectorTypeCounts={}"));
        assertTrue(report.compactSummary().contains("autoVectorizationUniqueVectorTypes=0"));
        assertTrue(report.compactSummary().contains("autoVectorizationWarningFamilyCounts={}"));
        assertTrue(report.compactSummary().contains("autoVectorizationUniqueWarningFamilies=0"));
        assertTrue(report.compactSummary().contains("autoVectorizationRejectionReasonCounts={}"));
        assertTrue(report.compactSummary().contains("autoVectorizationUniqueRejectionReasons=0"));
        assertTrue(report.compactSummary().contains("autoVectorizationProofBundleRewriteSafe=true"));
        assertTrue(report.compactSummary().contains("autoVectorizationProofBundleDiagnostics=0"));
        assertTrue(report.compactSummary().contains("autoVectorizationProofBundleUnsafeProofs=0"));
        assertFalse(report.compactSummary().contains("autoVectorizationProofBundleFirstUnsafeProof="));
        assertTrue(report.detailedSummary().contains("autoVectorizationRewriteDryRun={"));
        assertTrue(report.detailedSummary().contains("optimizerBlocker={"));
        assertTrue(report.detailedSummary().contains("optimizer blocker method=kernel"));
        assertTrue(report.detailedSummary().contains("autoVectorizationResolvedRewriteOperations={"));
        assertTrue(report.detailedSummary().contains("autoVectorizationProofBundle={"));
        assertTrue(report.detailedSummary().contains("autoVectorizationReadiness={"));
        assertTrue(report.detailedSummary().contains("cseLocalExpression={"));
        assertTrue(report.detailedSummary().contains("cseSimpleArithmeticNumericBoundary={"));
        assertTrue(report.detailedSummary().contains("cseSimpleArithmeticLiteralProof={"));
        assertTrue(report.detailedSummary().contains("cseSimpleArithmeticLiteralCanonicalization={"));
        assertTrue(report.detailedSummary().contains("cseSimpleArithmeticLiteralNumericSemanticsProof={"));
        assertTrue(report.detailedSummary().contains("cseSimpleArithmeticLiteralTypedNumericBlockers={"));
        assertTrue(report.detailedSummary().contains("cseSimpleArithmeticLiteralRuntimeEquivalence={"));
        assertTrue(report.detailedSummary().contains("cseSimpleArithmeticLiteralCanonicalizationGate={"));
        assertTrue(report.detailedSummary().contains("cseSimpleArithmeticLiteralFingerprintDecision={"));
        assertTrue(report.detailedSummary().contains("cseSimpleArithmeticLiteralFingerprintParity={"));
        assertTrue(report.detailedSummary().contains("cseSimpleArithmeticLiteralEnablement={"));
        assertTrue(report.detailedSummary().contains("cseSimpleArithmeticLiteralRewritePreflight={"));
        assertTrue(report.detailedSummary().contains("cseSimpleArithmeticLiteralRewriteOperationPreview={"));
        assertTrue(report.detailedSummary().contains("cseSimpleArithmeticLiteralPromotionChecklist={"));
        assertTrue(report.detailedSummary().contains("cseSimpleArithmeticLiteralConsistencyCheck={"));
        assertTrue(report.detailedSummary().contains("cseRewritePolicy={"));
        assertTrue(report.detailedSummary().contains("optimizerLayerReadiness={"));
        assertTrue(report.detailedSummary().contains("optimizer layers blocked method=kernel"));
        assertTrue(report.detailedSummary().contains("CSE rewrite policy method=kernel"));
        assertTrue(report.detailedSummary().contains("successful=false"));
        assertTrue(report.detailedSummary().contains("replacement loopLocation expected=stmt[0] actual=stmt[1]"));
    }

    @Test
    void dryRunReportFactoriesKeepReadinessContractExplicit() {
        GpuIrAutoVectorizationRewriteDryRunReport ready = GpuIrAutoVectorizationRewriteDryRunReport.ready(
                "kernel",
                1,
                1,
                1
        );
        GpuIrAutoVectorizationRewriteDryRunReport failed = GpuIrAutoVectorizationRewriteDryRunReport.failed(
                "kernel",
                1,
                1,
                1,
                List.of("dry-run failed")
        );
        GpuIrAutoVectorizationRewriteDryRunReport skipped = GpuIrAutoVectorizationRewriteDryRunReport.skipped(
                "kernel",
                0,
                0,
                0,
                List.of("dry-run skipped")
        );

        assertEquals(GpuIrAutoVectorizationRewriteDryRunReadiness.READY, ready.readiness());
        assertTrue(ready.successful());
        assertEquals(GpuIrAutoVectorizationRewriteDryRunReadiness.FAILED, failed.readiness());
        assertTrue(failed.hasFailures());
        assertEquals(GpuIrAutoVectorizationRewriteDryRunReadiness.SKIPPED, skipped.readiness());
        assertTrue(skipped.hasFailures());
    }

    @Test
    void readyReportsRejectDiagnostics() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> new GpuIrAutoVectorizationRewriteDryRunReport(
                "kernel",
                GpuIrAutoVectorizationRewriteDryRunReadiness.READY,
                1,
                1,
                1,
                List.of("unexpected diagnostic")
        ));

        assertTrue(exception.getMessage().contains("ready dry-runs must not have diagnostics"));
    }

    private EmptyLiteralReports emptyLiteralReports(String methodName) {
        GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationReport canonicalizationReport =
                GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationReport.empty(methodName);
        GpuIrCommonSubexpressionSimpleArithmeticLiteralNumericSemanticsProofReport numericSemanticsProofReport =
                GpuIrCommonSubexpressionSimpleArithmeticLiteralNumericSemanticsProofReport.empty(methodName);
        GpuIrCommonSubexpressionSimpleArithmeticLiteralRuntimeEquivalenceReport runtimeEquivalenceReport =
                GpuIrCommonSubexpressionSimpleArithmeticLiteralRuntimeEquivalenceReport.notRun(
                        canonicalizationReport,
                        numericSemanticsProofReport
                );
        GpuIrCommonSubexpressionSimpleArithmeticLiteralTypedNumericBlockerSummaryReport typedNumericBlockerSummaryReport =
                GpuIrCommonSubexpressionSimpleArithmeticLiteralTypedNumericBlockerSummaryReport.from(
                        GpuIrCommonSubexpressionSimpleArithmeticLiteralProofReport.empty(methodName),
                        numericSemanticsProofReport
                );
        GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationGate canonicalizationGate =
                GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationGate.from(
                        canonicalizationReport,
                        numericSemanticsProofReport,
                        runtimeEquivalenceReport
                );
        GpuIrCommonSubexpressionSimpleArithmeticLiteralFingerprintDecisionReport fingerprintDecisionReport =
                GpuIrCommonSubexpressionSimpleArithmeticLiteralFingerprintDecisionReport.from(
                        canonicalizationReport,
                        numericSemanticsProofReport,
                        runtimeEquivalenceReport,
                        canonicalizationGate
                );
        GpuIrCommonSubexpressionSimpleArithmeticLiteralFingerprintParityReport fingerprintParityReport =
                GpuIrCommonSubexpressionSimpleArithmeticLiteralFingerprintParityReport.from(
                        new GpuIrCommonSubexpressionArtifactSnapshot(
                                methodName,
                                new GpuIrCommonSubexpressionRewritePreview(List.of(), List.of(), List.of())
                        ),
                        canonicalizationReport,
                        fingerprintDecisionReport
                );
        GpuIrCommonSubexpressionSimpleArithmeticLiteralEnablementReport enablementReport =
                GpuIrCommonSubexpressionSimpleArithmeticLiteralEnablementReport.from(
                        canonicalizationReport,
                        numericSemanticsProofReport,
                        runtimeEquivalenceReport,
                        fingerprintDecisionReport,
                        fingerprintParityReport
                );
        GpuIrCommonSubexpressionSimpleArithmeticLiteralRewritePreflightReport rewritePreflightReport =
                GpuIrCommonSubexpressionSimpleArithmeticLiteralRewritePreflightReport.from(
                        canonicalizationReport,
                        numericSemanticsProofReport,
                        runtimeEquivalenceReport,
                        fingerprintParityReport,
                        enablementReport
                );
        GpuIrCommonSubexpressionSimpleArithmeticLiteralRewriteOperationPreviewReport rewriteOperationPreviewReport =
                GpuIrCommonSubexpressionSimpleArithmeticLiteralRewriteOperationPreviewReport.from(
                        rewritePreflightReport
                );
        GpuIrCommonSubexpressionSimpleArithmeticLiteralPromotionChecklistReport promotionChecklistReport =
                GpuIrCommonSubexpressionSimpleArithmeticLiteralPromotionChecklistReport.from(
                        enablementReport,
                        rewritePreflightReport,
                        rewriteOperationPreviewReport
                );
        GpuIrCommonSubexpressionSimpleArithmeticLiteralConsistencyCheckReport consistencyCheckReport =
                GpuIrCommonSubexpressionSimpleArithmeticLiteralConsistencyCheckReport.from(
                        enablementReport,
                        rewritePreflightReport,
                        rewriteOperationPreviewReport,
                        promotionChecklistReport
                );
        GpuIrCommonSubexpressionSimpleArithmeticLiteralPromotionReadinessSummaryReport promotionReadinessSummaryReport =
                GpuIrCommonSubexpressionSimpleArithmeticLiteralPromotionReadinessSummaryReport.from(
                        canonicalizationReport,
                        typedNumericBlockerSummaryReport,
                        runtimeEquivalenceReport,
                        fingerprintDecisionReport,
                        fingerprintParityReport,
                        promotionChecklistReport
                );
        return new EmptyLiteralReports(
                canonicalizationReport,
                numericSemanticsProofReport,
                typedNumericBlockerSummaryReport,
                runtimeEquivalenceReport,
                canonicalizationGate,
                fingerprintDecisionReport,
                fingerprintParityReport,
                enablementReport,
                rewritePreflightReport,
                rewriteOperationPreviewReport,
                promotionChecklistReport,
                promotionReadinessSummaryReport,
                consistencyCheckReport
        );
    }

    private record EmptyLiteralReports(
            GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationReport canonicalizationReport,
            GpuIrCommonSubexpressionSimpleArithmeticLiteralNumericSemanticsProofReport numericSemanticsProofReport,
            GpuIrCommonSubexpressionSimpleArithmeticLiteralTypedNumericBlockerSummaryReport typedNumericBlockerSummaryReport,
            GpuIrCommonSubexpressionSimpleArithmeticLiteralRuntimeEquivalenceReport runtimeEquivalenceReport,
            GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationGate canonicalizationGate,
            GpuIrCommonSubexpressionSimpleArithmeticLiteralFingerprintDecisionReport fingerprintDecisionReport,
            GpuIrCommonSubexpressionSimpleArithmeticLiteralFingerprintParityReport fingerprintParityReport,
            GpuIrCommonSubexpressionSimpleArithmeticLiteralEnablementReport enablementReport,
            GpuIrCommonSubexpressionSimpleArithmeticLiteralRewritePreflightReport rewritePreflightReport,
            GpuIrCommonSubexpressionSimpleArithmeticLiteralRewriteOperationPreviewReport rewriteOperationPreviewReport,
            GpuIrCommonSubexpressionSimpleArithmeticLiteralPromotionChecklistReport promotionChecklistReport,
            GpuIrCommonSubexpressionSimpleArithmeticLiteralPromotionReadinessSummaryReport promotionReadinessSummaryReport,
            GpuIrCommonSubexpressionSimpleArithmeticLiteralConsistencyCheckReport consistencyCheckReport
    ) {
    }
}
