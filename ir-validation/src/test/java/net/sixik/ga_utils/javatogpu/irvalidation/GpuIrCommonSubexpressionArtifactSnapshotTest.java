package net.sixik.ga_utils.javatogpu.irvalidation;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        Map<String, String> defaultFields = snapshot.artifactFields();

        assertEquals("1", fields.get("cseInsertions"));
        assertEquals("1", fields.get("cseReplacements"));
        assertEquals("1", fields.get("cseSkipped"));
        assertEquals("1", defaultFields.get("cseInsertions"));
        assertEquals("{requiresLocalExpressionDominance=1}", defaultFields.get("cseSkippedDominanceStatusCounts"));
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
        assertEquals("clear", fields.get("cseControlFlowRegionReadiness"));
        assertEquals("0", fields.get("cseControlFlowRegionBlockedCandidates"));
        assertEquals("referenceOnlyNestedArithmetic", fields.get("cseSimpleArithmeticProofProofBoundary"));
        assertEquals("literalsAndCastsRequireTypedNumericProof", fields.get("cseSimpleArithmeticProofBlockedBoundary"));
        assertEquals("false", fields.get("cseRewritePolicyCanRewrite"));
        assertEquals("blockedBySkippedCandidate", fields.get("cseRewritePolicyReadiness"));
        assertEquals("1", fields.get("cseRewritePolicyBlockingSkippedCandidates"));
        assertEquals("NO_DOMINATING_FIRST_OCCURRENCE", fields.get("cseRewritePolicyFirstBlockingSkippedReason"));
        assertEquals("blocked", fields.get("cseRewriteBlockerVerdict"));
        assertEquals("skipReason.NO_DOMINATING_FIRST_OCCURRENCE", fields.get("cseRewriteBlockerFirstFamily"));
        assertEquals("proveDominatingAnchorForAllReplacements", fields.get("cseRewriteBlockerFirstRemainingWork"));
        assertTrue(fields.get("cseRewriteBlockerFirstHint").contains("production rewrite"));
        assertEquals("blocked", fields.get("cseLayerReadinessVerdict"));
        assertEquals("false", fields.get("cseLayerReadinessAllLayersReady"));
        assertEquals("true", fields.get("cseLayerReadinessHasBlockingLayers"));
        assertEquals(
                "rewritePolicy,skipReason,dominance,localExpression,simpleArithmeticProof",
                fields.get("cseLayerReadinessLayerOrder")
        );
        assertEquals(
                "rewritePolicy,skipReason,dominance,localExpression",
                fields.get("cseLayerReadinessPresentLayers")
        );
        assertEquals(
                "rewritePolicy,skipReason,dominance,localExpression",
                fields.get("cseLayerReadinessBlockingLayers")
        );
        assertEquals("4", fields.get("cseLayerReadinessBlockingLayerCount"));
        assertEquals("0", fields.get("cseLayerReadinessReadyLayerCount"));
        assertEquals("rewritePolicy", fields.get("cseLayerReadinessFirstBlockingLayer"));
        assertEquals("blocked", fields.get("cseLayerReadinessLayer.rewritePolicy"));
        assertEquals("blocked", fields.get("cseLayerReadinessLayer.skipReason"));
        assertEquals("blocked", fields.get("cseLayerReadinessLayer.dominance"));
        assertEquals("blocked", fields.get("cseLayerReadinessLayer.localExpression"));
        assertEquals("notPresent", fields.get("cseLayerReadinessLayer.simpleArithmeticProof"));
        assertTrue(fields.get("cseLayerReadinessCiSummaryLine").contains("firstBlockingLayer=rewritePolicy"));
        assertTrue(snapshot.summary().contains("firstSkippedDominanceStatus=requiresLocalExpressionDominance"));
        assertTrue(snapshot.summary().contains("localExpression={"));
        assertTrue(snapshot.summary().contains("simpleArithmeticProof={"));
        assertTrue(snapshot.summary().contains("controlFlowRegion={"));
        assertTrue(snapshot.summary().contains("rewritePolicy={"));
        assertTrue(snapshot.summary().contains("layerReadiness={"));
        assertThrows(UnsupportedOperationException.class, () -> fields.put("x", "y"));
        assertThrows(UnsupportedOperationException.class, () -> defaultFields.put("x", "y"));
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
        assertTrue(snapshot.layerReadinessSummaryReport().allLayersReady());
        assertFalse(snapshot.layerReadinessSummaryReport().hasBlockingLayers());
        assertEquals("ready", fields.get("cseLayerReadinessVerdict"));
        assertEquals("true", fields.get("cseLayerReadinessAllLayersReady"));
        assertEquals("rewritePolicy,simpleArithmeticProof", fields.get("cseLayerReadinessPresentLayers"));
        assertEquals("", fields.get("cseLayerReadinessBlockingLayers"));
        assertEquals("2", fields.get("cseLayerReadinessReadyLayerCount"));
        assertEquals("none", fields.get("cseLayerReadinessFirstBlockingLayer"));
        assertEquals("ready", fields.get("cseLayerReadinessLayer.rewritePolicy"));
        assertEquals("ready", fields.get("cseLayerReadinessLayer.simpleArithmeticProof"));
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
        assertEquals("clear", fields.get("cseControlFlowRegionReadiness"));
        assertEquals("0", fields.get("cseControlFlowRegionBlockedCandidates"));
        assertEquals("none", fields.get("cseRewritePolicyReadiness"));
        assertEquals("false", fields.get("cseRewritePolicyCanRewrite"));
        assertEquals("noRewriteWork", fields.get("cseLayerReadinessVerdict"));
        assertEquals("true", fields.get("cseLayerReadinessAllLayersReady"));
        assertEquals("false", fields.get("cseLayerReadinessHasBlockingLayers"));
        assertEquals("", fields.get("cseLayerReadinessPresentLayers"));
        assertEquals("", fields.get("cseLayerReadinessBlockingLayers"));
        assertEquals("0", fields.get("cseLayerReadinessBlockingLayerCount"));
        assertEquals("0", fields.get("cseLayerReadinessReadyLayerCount"));
        assertEquals("none", fields.get("cseLayerReadinessFirstBlockingLayer"));
        assertEquals("notPresent", fields.get("cseLayerReadinessLayer.rewritePolicy"));
        assertEquals("notPresent", fields.get("cseLayerReadinessLayer.skipReason"));
        assertEquals("notPresent", fields.get("cseLayerReadinessLayer.dominance"));
        assertEquals("notPresent", fields.get("cseLayerReadinessLayer.localExpression"));
        assertEquals("notPresent", fields.get("cseLayerReadinessLayer.simpleArithmeticProof"));
        assertTrue(!fields.containsKey("cseFirstSkippedReason"));
        assertThrows(NullPointerException.class, () -> new GpuIrCommonSubexpressionArtifactSnapshot(null));
    }
}
