package net.sixik.ga_utils.javatogpu.irvalidation;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuIrCommonSubexpressionArtifactSnapshotTest {
    @Test
    void artifactFieldsExposeCountsAndFirstDominanceDetails() {
        GpuIrCommonSubexpressionSkippedDiagnostic skipped = new GpuIrCommonSubexpressionSkippedDiagnostic(
                "binary(*,var(x),var(y))",
                2,
                List.of("stmt[0].value.left", "stmt[0].value.right"),
                GpuIrCommonSubexpressionKind.LOCAL_REUSE,
                GpuIrCommonSubexpressionScope.STRAIGHT_LINE,
                GpuIrCommonSubexpressionSkipReason.NO_DOMINATING_FIRST_OCCURRENCE,
                GpuIrCommonSubexpressionDominanceStatus.REQUIRES_LOCAL_EXPRESSION_DOMINANCE
        );
        GpuIrCommonSubexpressionArtifactSnapshot snapshot = new GpuIrCommonSubexpressionArtifactSnapshot(
                new GpuIrCommonSubexpressionRewritePreview(
                        List.of(new GpuIrCommonSubexpressionRewriteInsertion(
                                "__gpu_cse_0",
                                "binary(+,var(x),var(y))",
                                0,
                                "stmt[0].value.left"
                        )),
                        List.of(new GpuIrCommonSubexpressionRewriteEdit(
                                "__gpu_cse_0",
                                "binary(+,var(x),var(y))",
                                0,
                                "stmt[0].value.left",
                                "stmt[0].value.right"
                        )),
                        List.of(skipped)
                )
        );

        Map<String, String> fields = snapshot.artifactFields("cse");

        assertEquals("1", fields.get("cseInsertions"));
        assertEquals("1", fields.get("cseReplacements"));
        assertEquals("1", fields.get("cseSkipped"));
        assertEquals("NO_DOMINATING_FIRST_OCCURRENCE", fields.get("cseFirstSkippedReason"));
        assertEquals("requiresLocalExpressionDominance", fields.get("cseFirstSkippedDominanceStatus"));
        assertTrue(fields.get("cseFirstSkippedDominanceSummary").contains("dominance=requiresLocalExpressionDominance"));
        assertEquals("{requiresLocalExpressionDominance=1}", fields.get("cseSkippedDominanceStatusCounts"));
        assertEquals("1", fields.get("cseSkippedDominanceStatus.requiresLocalExpressionDominance"));
        assertEquals("1", fields.get("cseLocalExpressionProvenCandidates"));
        assertEquals("1", fields.get("cseLocalExpressionProvenReplacements"));
        assertEquals("1", fields.get("cseLocalExpressionBlockedCandidates"));
        assertEquals("true", fields.get("cseLocalExpressionHasEvidence"));
        assertEquals("NO_DOMINATING_FIRST_OCCURRENCE", fields.get("cseLocalExpressionFirstBlockedReason"));
        assertEquals("requiresLocalExpressionDominance", fields.get("cseLocalExpressionFirstBlockedDominanceStatus"));
        assertEquals("0", fields.get("cseSimpleArithmeticProofProvenCandidates"));
        assertEquals("0", fields.get("cseSimpleArithmeticProofProvenInsertions"));
        assertEquals("0", fields.get("cseSimpleArithmeticProofProvenReplacements"));
        assertEquals("false", fields.get("cseSimpleArithmeticProofHasProofs"));
        assertEquals("referenceOnlyNestedArithmetic", fields.get("cseSimpleArithmeticProofProofBoundary"));
        assertEquals("literalsAndCastsRequireTypedNumericProof", fields.get("cseSimpleArithmeticProofBlockedBoundary"));
        assertEquals("false", fields.get("cseRewritePolicyCanRewrite"));
        assertEquals("blockedBySkippedCandidate", fields.get("cseRewritePolicyReadiness"));
        assertEquals("1", fields.get("cseRewritePolicyBlockingSkippedCandidates"));
        assertEquals("NO_DOMINATING_FIRST_OCCURRENCE", fields.get("cseRewritePolicyFirstBlockingSkippedReason"));
        assertTrue(snapshot.summary().contains("firstSkippedDominanceStatus=requiresLocalExpressionDominance"));
        assertTrue(snapshot.summary().contains("localExpression={"));
        assertTrue(snapshot.summary().contains("simpleArithmeticProof={"));
        assertTrue(snapshot.summary().contains("rewritePolicy={"));
        assertThrows(UnsupportedOperationException.class, () -> fields.put("x", "y"));
    }

    @Test
    void artifactFieldsExposeSimpleArithmeticProofDetails() {
        GpuIrCommonSubexpressionArtifactSnapshot snapshot = new GpuIrCommonSubexpressionArtifactSnapshot(
                new GpuIrCommonSubexpressionRewritePreview(
                        List.of(new GpuIrCommonSubexpressionRewriteInsertion(
                                "__gpu_cse_0",
                                "binary_assoc_simple(+,var(x),var(y),var(z))",
                                0,
                                "stmt[0].initializer"
                        )),
                        List.of(new GpuIrCommonSubexpressionRewriteEdit(
                                "__gpu_cse_0",
                                "binary_assoc_simple(+,var(x),var(y),var(z))",
                                0,
                                "stmt[0].initializer",
                                "stmt[1].initializer"
                        )),
                        List.of()
                )
        );

        Map<String, String> fields = snapshot.artifactFields("cse");

        assertEquals("1", fields.get("cseSimpleArithmeticProofProvenCandidates"));
        assertEquals("1", fields.get("cseSimpleArithmeticProofProvenInsertions"));
        assertEquals("1", fields.get("cseSimpleArithmeticProofProvenReplacements"));
        assertEquals("true", fields.get("cseSimpleArithmeticProofHasProofs"));
        assertEquals("referenceOnlyNestedArithmetic", fields.get("cseSimpleArithmeticProofProofBoundary"));
        assertEquals("literalsAndCastsRequireTypedNumericProof", fields.get("cseSimpleArithmeticProofBlockedBoundary"));
        assertEquals(
                "binary_assoc_simple(+,var(x),var(y),var(z))",
                fields.get("cseSimpleArithmeticProofFirstProvenFingerprint")
        );
        assertEquals("stmt[0].initializer", fields.get("cseSimpleArithmeticProofFirstProvenAnchor"));
        assertTrue(snapshot.simpleArithmeticProofReport().hasProofs());
        assertTrue(snapshot.simpleArithmeticProofReport().summary().contains("referenceOnlyNestedArithmetic"));
    }

    @Test
    void artifactFieldsHandleEmptyPreview() {
        GpuIrCommonSubexpressionArtifactSnapshot snapshot = new GpuIrCommonSubexpressionArtifactSnapshot(
                new GpuIrCommonSubexpressionRewritePreview(List.of(), List.of(), List.of())
        );

        Map<String, String> fields = snapshot.artifactFields("cse");

        assertEquals("0", fields.get("cseInsertions"));
        assertEquals("0", fields.get("cseReplacements"));
        assertEquals("0", fields.get("cseSkipped"));
        assertEquals("{}", fields.get("cseSkippedDominanceStatusCounts"));
        assertEquals("0", fields.get("cseLocalExpressionProvenCandidates"));
        assertEquals("0", fields.get("cseLocalExpressionBlockedCandidates"));
        assertEquals("false", fields.get("cseLocalExpressionHasEvidence"));
        assertEquals("0", fields.get("cseSimpleArithmeticProofProvenCandidates"));
        assertEquals("false", fields.get("cseSimpleArithmeticProofHasProofs"));
        assertEquals("none", fields.get("cseRewritePolicyReadiness"));
        assertEquals("false", fields.get("cseRewritePolicyCanRewrite"));
        assertTrue(!fields.containsKey("cseFirstSkippedReason"));
        assertThrows(NullPointerException.class, () -> new GpuIrCommonSubexpressionArtifactSnapshot(null));
    }
}
