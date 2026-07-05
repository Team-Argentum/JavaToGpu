package net.sixik.ga_utils.javatogpu.irvalidation;

import java.util.List;

final class GpuIrCommonSubexpressionSimpleArithmeticLiteralTestFixtures {
    private GpuIrCommonSubexpressionSimpleArithmeticLiteralTestFixtures() {
    }

    static GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationReport emptyCanonicalizationReport() {
        return GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationReport.empty("kernel");
    }

    static GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationReport canonicalizationReport() {
        return new GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationReport(
                "kernel",
                List.of(
                        candidate("literal_assoc_preview(plus:int,int,int;literals=1)", "1"),
                        candidate("literal_assoc_preview(plus:int,int,int;literals=2)", "2")
                )
        );
    }

    static GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationReport mixedCanonicalizationReport() {
        return new GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationReport(
                "kernel",
                List.of(
                        candidate("stmt[0].initializer", "+", "plus:int,int,int", "literal_assoc_preview(plus:int,int,int;literals=1)", "1"),
                        candidate("stmt[1].initializer", "*", "times:int,int,int", "literal_assoc_preview(times:int,int,int;literals=2)", "2")
                )
        );
    }

    static GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationReport.Candidate candidate(
            String canonicalKey,
            String literalSource
    ) {
        return candidate("stmt[0].initializer", "+", "plus:int,int,int", canonicalKey, literalSource);
    }

    static GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationReport.Candidate candidate(
            String location,
            String operator,
            String operatorTypeKey,
            String canonicalKey,
            String literalSource
    ) {
        return new GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationReport.Candidate(
                location,
                operator,
                operatorTypeKey,
                canonicalKey,
                List.of("int", "int", "int"),
                List.of(literalSource)
        );
    }

    static GpuIrCommonSubexpressionArtifactSnapshot snapshot(List<String> productionFingerprints) {
        return new GpuIrCommonSubexpressionArtifactSnapshot(
                "kernel",
                new GpuIrCommonSubexpressionRewritePreview(
                        productionFingerprints.stream()
                                .map(fingerprint -> new GpuIrCommonSubexpressionRewriteInsertion("tmp", fingerprint, 0, "stmt[0]"))
                                .toList(),
                        List.of(),
                        List.of()
                )
        );
    }

    static EvidenceStack evidence(
            GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationReport canonicalization,
            boolean runtimeSuccessful,
            List<String> productionFingerprints
    ) {
        GpuIrCommonSubexpressionSimpleArithmeticLiteralNumericSemanticsProofReport numericProof =
                GpuIrCommonSubexpressionSimpleArithmeticLiteralNumericSemanticsProofReport.from(canonicalization);
        GpuIrCommonSubexpressionSimpleArithmeticLiteralRuntimeEquivalenceReport runtimeEquivalence = runtimeSuccessful
                ? GpuIrCommonSubexpressionSimpleArithmeticLiteralRuntimeEquivalenceReport.equivalent(
                canonicalization,
                numericProof,
                1,
                List.of("out")
        )
                : GpuIrCommonSubexpressionSimpleArithmeticLiteralRuntimeEquivalenceReport.notRun(canonicalization, numericProof);
        GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationGate gate =
                GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationGate.from(
                        canonicalization,
                        numericProof,
                        runtimeEquivalence
                );
        GpuIrCommonSubexpressionSimpleArithmeticLiteralFingerprintDecisionReport decision =
                GpuIrCommonSubexpressionSimpleArithmeticLiteralFingerprintDecisionReport.from(
                        canonicalization,
                        numericProof,
                        runtimeEquivalence,
                        gate
                );
        GpuIrCommonSubexpressionSimpleArithmeticLiteralFingerprintParityReport parity =
                GpuIrCommonSubexpressionSimpleArithmeticLiteralFingerprintParityReport.from(
                        snapshot(productionFingerprints),
                        canonicalization,
                        decision
                );
        GpuIrCommonSubexpressionSimpleArithmeticLiteralEnablementReport enablement =
                GpuIrCommonSubexpressionSimpleArithmeticLiteralEnablementReport.from(
                        canonicalization,
                        numericProof,
                        runtimeEquivalence,
                        decision,
                        parity
                );
        GpuIrCommonSubexpressionSimpleArithmeticLiteralRewritePreflightReport preflight =
                GpuIrCommonSubexpressionSimpleArithmeticLiteralRewritePreflightReport.from(
                        canonicalization,
                        numericProof,
                        runtimeEquivalence,
                        parity,
                        enablement
                );
        GpuIrCommonSubexpressionSimpleArithmeticLiteralRewriteOperationPreviewReport operationPreview =
                GpuIrCommonSubexpressionSimpleArithmeticLiteralRewriteOperationPreviewReport.from(preflight);
        GpuIrCommonSubexpressionSimpleArithmeticLiteralPromotionChecklistReport promotionChecklist =
                GpuIrCommonSubexpressionSimpleArithmeticLiteralPromotionChecklistReport.from(
                        enablement,
                        preflight,
                        operationPreview
                );
        GpuIrCommonSubexpressionSimpleArithmeticLiteralPromotionReadinessSummaryReport promotionReadiness =
                GpuIrCommonSubexpressionSimpleArithmeticLiteralPromotionReadinessSummaryReport.from(
                        canonicalization,
                        GpuIrCommonSubexpressionSimpleArithmeticLiteralTypedNumericBlockerSummaryReport.from(
                                GpuIrCommonSubexpressionSimpleArithmeticLiteralProofReport.empty(canonicalization.methodName()),
                                numericProof
                        ),
                        runtimeEquivalence,
                        decision,
                        parity,
                        promotionChecklist
                );
        return new EvidenceStack(
                numericProof,
                runtimeEquivalence,
                gate,
                decision,
                parity,
                enablement,
                preflight,
                operationPreview,
                promotionChecklist,
                promotionReadiness
        );
    }

    record EvidenceStack(
            GpuIrCommonSubexpressionSimpleArithmeticLiteralNumericSemanticsProofReport numericProof,
            GpuIrCommonSubexpressionSimpleArithmeticLiteralRuntimeEquivalenceReport runtimeEquivalence,
            GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationGate gate,
            GpuIrCommonSubexpressionSimpleArithmeticLiteralFingerprintDecisionReport decision,
            GpuIrCommonSubexpressionSimpleArithmeticLiteralFingerprintParityReport parity,
            GpuIrCommonSubexpressionSimpleArithmeticLiteralEnablementReport enablement,
            GpuIrCommonSubexpressionSimpleArithmeticLiteralRewritePreflightReport preflight,
            GpuIrCommonSubexpressionSimpleArithmeticLiteralRewriteOperationPreviewReport operationPreview,
            GpuIrCommonSubexpressionSimpleArithmeticLiteralPromotionChecklistReport promotionChecklist,
            GpuIrCommonSubexpressionSimpleArithmeticLiteralPromotionReadinessSummaryReport promotionReadiness
    ) {
    }
}
