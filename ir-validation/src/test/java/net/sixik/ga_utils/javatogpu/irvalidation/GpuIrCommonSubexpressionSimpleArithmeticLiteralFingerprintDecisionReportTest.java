package net.sixik.ga_utils.javatogpu.irvalidation;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuIrCommonSubexpressionSimpleArithmeticLiteralFingerprintDecisionReportTest {
    @Test
    void reportsNoPreviewCandidatesAsInactiveDecision() {
        GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationReport canonicalization =
                GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationReport.empty("kernel");
        GpuIrCommonSubexpressionSimpleArithmeticLiteralNumericSemanticsProofReport numericProof =
                GpuIrCommonSubexpressionSimpleArithmeticLiteralNumericSemanticsProofReport.from(canonicalization);
        GpuIrCommonSubexpressionSimpleArithmeticLiteralRuntimeEquivalenceReport runtimeEquivalence =
                GpuIrCommonSubexpressionSimpleArithmeticLiteralRuntimeEquivalenceReport.notRun(canonicalization, numericProof);
        GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationGate gate =
                GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationGate.from(
                        canonicalization,
                        numericProof,
                        runtimeEquivalence
                );

        GpuIrCommonSubexpressionSimpleArithmeticLiteralFingerprintDecisionReport report =
                GpuIrCommonSubexpressionSimpleArithmeticLiteralFingerprintDecisionReport.from(
                        canonicalization,
                        numericProof,
                        runtimeEquivalence,
                        gate
                );

        assertEquals("none", report.readiness());
        assertFalse(report.evidenceComplete());
        assertFalse(report.readyForProductionFingerprintIntegration());
        assertEquals(List.of("noPreviewCandidates"), report.blockingReasons());
        assertEquals("noPreviewCandidates", report.firstBlockingReason().orElseThrow());
    }

    @Test
    void reportsRuntimeBlockerBeforeExplicitEvidenceRuns() {
        GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationReport canonicalization = canonicalizationReport();
        GpuIrCommonSubexpressionSimpleArithmeticLiteralNumericSemanticsProofReport numericProof =
                GpuIrCommonSubexpressionSimpleArithmeticLiteralNumericSemanticsProofReport.from(canonicalization);
        GpuIrCommonSubexpressionSimpleArithmeticLiteralRuntimeEquivalenceReport runtimeEquivalence =
                GpuIrCommonSubexpressionSimpleArithmeticLiteralRuntimeEquivalenceReport.notRun(canonicalization, numericProof);
        GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationGate gate =
                GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationGate.from(
                        canonicalization,
                        numericProof,
                        runtimeEquivalence
                );

        GpuIrCommonSubexpressionSimpleArithmeticLiteralFingerprintDecisionReport report =
                GpuIrCommonSubexpressionSimpleArithmeticLiteralFingerprintDecisionReport.from(
                        canonicalization,
                        numericProof,
                        runtimeEquivalence,
                        gate
                );
        Map<String, String> fields = report.artifactFields("literalFingerprintDecision");

        assertEquals("blockedPreview", report.readiness());
        assertFalse(report.evidenceComplete());
        assertEquals(List.of("runtimeEquivalenceNotProven", "productionFingerprintIntegrationDisabled"), report.blockingReasons());
        assertEquals("false", fields.get("literalFingerprintDecisionReadyForProduction"));
        assertEquals("blockedPreview", fields.get("literalFingerprintDecisionReadiness"));
        assertEquals("false", fields.get("literalFingerprintDecisionEvidenceComplete"));
        assertEquals("runtimeEquivalenceNotProven", fields.get("literalFingerprintDecisionFirstBlockingReason"));
        assertThrows(UnsupportedOperationException.class, () -> fields.put("x", "y"));
    }

    @Test
    void reportsCompleteEvidenceButKeepsProductionFingerprintDisabled() {
        GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationReport canonicalization = canonicalizationReport();
        GpuIrCommonSubexpressionSimpleArithmeticLiteralNumericSemanticsProofReport numericProof =
                GpuIrCommonSubexpressionSimpleArithmeticLiteralNumericSemanticsProofReport.from(canonicalization);
        GpuIrCommonSubexpressionSimpleArithmeticLiteralRuntimeEquivalenceReport runtimeEquivalence =
                GpuIrCommonSubexpressionSimpleArithmeticLiteralRuntimeEquivalenceReport.equivalent(
                        canonicalization,
                        numericProof,
                        3,
                        List.of("out", "return")
                );
        GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationGate gate =
                GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationGate.from(
                        canonicalization,
                        numericProof,
                        runtimeEquivalence
                );

        GpuIrCommonSubexpressionSimpleArithmeticLiteralFingerprintDecisionReport report =
                GpuIrCommonSubexpressionSimpleArithmeticLiteralFingerprintDecisionReport.from(
                        canonicalization,
                        numericProof,
                        runtimeEquivalence,
                        gate
                );

        assertEquals("evidenceCompleteButDisabled", report.readiness());
        assertTrue(report.evidenceComplete());
        assertTrue(report.blockedByProductionDisablementOnly());
        assertFalse(report.readyForProductionFingerprintIntegration());
        assertEquals(List.of("productionFingerprintIntegrationDisabled"), report.blockingReasons());
        assertTrue(report.summary().contains("readyForProduction=false"));
    }

    @Test
    void rejectsInvalidMetadata() {
        assertThrows(IllegalArgumentException.class, () -> new GpuIrCommonSubexpressionSimpleArithmeticLiteralFingerprintDecisionReport(
                "",
                0,
                0,
                false,
                false,
                false,
                List.of("noPreviewCandidates")
        ));
        assertThrows(IllegalArgumentException.class, () -> new GpuIrCommonSubexpressionSimpleArithmeticLiteralFingerprintDecisionReport(
                "kernel",
                -1,
                0,
                false,
                false,
                false,
                List.of("noPreviewCandidates")
        ));
        assertThrows(NullPointerException.class, () -> GpuIrCommonSubexpressionSimpleArithmeticLiteralFingerprintDecisionReport.from(
                null,
                GpuIrCommonSubexpressionSimpleArithmeticLiteralNumericSemanticsProofReport.empty("kernel"),
                GpuIrCommonSubexpressionSimpleArithmeticLiteralRuntimeEquivalenceReport.notRun(
                        GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationReport.empty("kernel"),
                        GpuIrCommonSubexpressionSimpleArithmeticLiteralNumericSemanticsProofReport.empty("kernel")
                ),
                GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationGate.from(
                        GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationReport.empty("kernel")
                )
        ));
    }

    private GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationReport canonicalizationReport() {
        return new GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationReport(
                "kernel",
                List.of(new GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationReport.Candidate(
                        "stmt[0].initializer",
                        "+",
                        "plus:int,int,int",
                        "literal_assoc_preview(plus:int,int,int;literals=1)",
                        List.of("int", "int", "int"),
                        List.of("1")
                ))
        );
    }
}
