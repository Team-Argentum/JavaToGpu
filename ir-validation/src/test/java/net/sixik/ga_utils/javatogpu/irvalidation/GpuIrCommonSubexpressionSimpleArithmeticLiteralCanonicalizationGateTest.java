package net.sixik.ga_utils.javatogpu.irvalidation;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationGateTest {
    @Test
    void blocksFingerprintPromotionForPreviewCandidates() {
        GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationReport report =
                GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationReport.from(new GpuIrCommonSubexpressionSimpleArithmeticLiteralProofReport(
                        "kernel",
                        List.of(
                                safeCandidate("stmt[0].initializer", "+", "1"),
                                safeCandidate("stmt[1].initializer", "+", "2")
                        ),
                        List.of()
                ));

        GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationGate gate =
                GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationGate.from(report);
        Map<String, String> fields = gate.artifactFields("literalGate");

        assertTrue(gate.hasPreviewCandidates());
        assertFalse(gate.canPromoteToFingerprint());
        assertTrue(gate.blocksRewriteReadiness());
        assertEquals("blockedPreview", gate.readiness());
        assertEquals(2, gate.previewCandidateCount());
        assertEquals(2, gate.uniqueCanonicalKeyCount());
        assertEquals(2, gate.blockingReasonCount());
        assertEquals("runtimeEquivalenceNotProven", gate.firstBlockingReason().orElseThrow());
        assertEquals("false", fields.get("literalGateCanPromoteToFingerprint"));
        assertEquals("blockedPreview", fields.get("literalGateReadiness"));
        assertEquals("2", fields.get("literalGatePreviewCandidates"));
        assertEquals("2", fields.get("literalGateUniqueCanonicalKeys"));
        assertEquals("true", fields.get("literalGateBlocksRewriteReadiness"));
        assertEquals("[runtimeEquivalenceNotProven,fingerprintIntegrationDisabled]", fields.get("literalGateBlockingReasons"));
        assertEquals("2", fields.get("literalGateBlockingReasonCount"));
        assertEquals("runtimeEquivalenceNotProven", fields.get("literalGateFirstBlockingReason"));
        assertTrue(gate.summary().contains("canPromoteToFingerprint=false"));
        assertThrows(UnsupportedOperationException.class, () -> fields.put("x", "y"));
    }

    @Test
    void keepsTypedNumericBlockerWhenNumericSemanticsProofIsNotComplete() {
        GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationReport report =
                GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationReport.from(new GpuIrCommonSubexpressionSimpleArithmeticLiteralProofReport(
                        "kernel",
                        List.of(safeCandidate("stmt[0].initializer", "+", "1")),
                        List.of()
                ));

        GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationGate gate =
                GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationGate.from(
                        report,
                        GpuIrCommonSubexpressionSimpleArithmeticLiteralNumericSemanticsProofReport.empty("kernel")
                );

        assertEquals("typedNumericSemanticsNotProven", gate.firstBlockingReason().orElseThrow());
        assertEquals(List.of("typedNumericSemanticsNotProven", "runtimeEquivalenceNotProven", "fingerprintIntegrationDisabled"), gate.blockingReasons());
    }

    @Test
    void removesRuntimeEquivalenceBlockerWhenEvidenceIsSuccessful() {
        GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationReport report =
                GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationReport.from(new GpuIrCommonSubexpressionSimpleArithmeticLiteralProofReport(
                        "kernel",
                        List.of(safeCandidate("stmt[0].initializer", "+", "1")),
                        List.of()
                ));
        GpuIrCommonSubexpressionSimpleArithmeticLiteralNumericSemanticsProofReport numericProof =
                GpuIrCommonSubexpressionSimpleArithmeticLiteralNumericSemanticsProofReport.from(report);
        GpuIrCommonSubexpressionSimpleArithmeticLiteralRuntimeEquivalenceReport runtimeEquivalence =
                GpuIrCommonSubexpressionSimpleArithmeticLiteralRuntimeEquivalenceReport.equivalent(
                        report,
                        numericProof,
                        2,
                        List.of("out")
                );

        GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationGate gate =
                GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationGate.from(report, numericProof, runtimeEquivalence);

        assertEquals("fingerprintIntegrationDisabled", gate.firstBlockingReason().orElseThrow());
        assertEquals(List.of("fingerprintIntegrationDisabled"), gate.blockingReasons());
        assertEquals(1, gate.blockingReasonCount());
        assertTrue(gate.blocksRewriteReadiness());
    }

    @Test
    void reportsNoPromotionWorkWhenPreviewIsEmpty() {
        GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationGate gate =
                GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationGate.from(
                        GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationReport.empty("kernel")
                );
        Map<String, String> fields = gate.artifactFields("literalGate");

        assertFalse(gate.hasPreviewCandidates());
        assertFalse(gate.canPromoteToFingerprint());
        assertFalse(gate.blocksRewriteReadiness());
        assertEquals("none", gate.readiness());
        assertEquals(0, gate.previewCandidateCount());
        assertEquals(0, gate.uniqueCanonicalKeyCount());
        assertEquals(1, gate.blockingReasonCount());
        assertEquals("noPreviewCandidates", gate.firstBlockingReason().orElseThrow());
        assertEquals("false", fields.get("literalGateCanPromoteToFingerprint"));
        assertEquals("none", fields.get("literalGateReadiness"));
        assertEquals("0", fields.get("literalGatePreviewCandidates"));
        assertEquals("0", fields.get("literalGateUniqueCanonicalKeys"));
        assertEquals("false", fields.get("literalGateBlocksRewriteReadiness"));
        assertEquals("[noPreviewCandidates]", fields.get("literalGateBlockingReasons"));
        assertEquals("1", fields.get("literalGateBlockingReasonCount"));
        assertEquals("noPreviewCandidates", fields.get("literalGateFirstBlockingReason"));
    }

    @Test
    void validatesConstructorInputs() {
        assertThrows(IllegalArgumentException.class, () -> new GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationGate(
                " ",
                0,
                0,
                List.of("noPreviewCandidates")
        ));
        assertThrows(IllegalArgumentException.class, () -> new GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationGate(
                "kernel",
                -1,
                0,
                List.of("noPreviewCandidates")
        ));
        assertThrows(IllegalArgumentException.class, () -> new GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationGate(
                "kernel",
                0,
                -1,
                List.of("noPreviewCandidates")
        ));
    }

    private GpuIrCommonSubexpressionSimpleArithmeticLiteralProofReport.SafeCandidate safeCandidate(
            String location,
            String operator,
            String literalSource
    ) {
        return new GpuIrCommonSubexpressionSimpleArithmeticLiteralProofReport.SafeCandidate(
                location,
                operator,
                List.of("int", "int", "int"),
                List.of(literalSource)
        );
    }
}
