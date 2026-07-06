package net.sixik.ga_utils.javatogpu.irvalidation;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuIrCommonSubexpressionRewriteBlockerExplanationTest {
    @Test
    void explainsSkippedCandidateWithActionableRemainingWork() {
        GpuIrCommonSubexpressionRewriteBlockerExplanation explanation =
                GpuIrCommonSubexpressionRewriteBlockerExplanation.from(policyWithSkippedCandidate(
                        GpuIrCommonSubexpressionSkipReason.NOT_LOCAL_REUSE,
                        GpuIrCommonSubexpressionDominanceStatus.TOP_LEVEL_DOWNSTREAM_REPLACEMENTS
                ));

        Map<String, String> fields = explanation.artifactFields();

        assertTrue(explanation.blocked());
        assertEquals("blocked", explanation.verdict());
        assertEquals("skipReason.NOT_LOCAL_REUSE", explanation.firstBlockerFamily());
        assertEquals("proveLocalReuseOrKeepExpressionInline", explanation.firstRemainingWork());
        assertEquals("kernel", fields.get("cseRewriteBlockerMethod"));
        assertEquals("true", fields.get("cseRewriteBlockerBlocked"));
        assertEquals("blockedBySkippedCandidate", fields.get("cseRewriteBlockerReadiness"));
        assertEquals("skipReason.NOT_LOCAL_REUSE", fields.get("cseRewriteBlockerFirstFamily"));
        assertEquals("proveLocalReuseOrKeepExpressionInline", fields.get("cseRewriteBlockerFirstRemainingWork"));
        assertEquals("NOT_LOCAL_REUSE", fields.get("cseRewriteBlockerFirstReason"));
        assertEquals("topLevelDownstreamReplacements", fields.get("cseRewriteBlockerFirstDominanceStatus"));
        assertTrue(fields.get("cseRewriteBlockerFirstHint").contains("proveLocalReuseOrKeepExpressionInline"));
        assertTrue(fields.get("cseRewriteBlockerSummary").contains("firstBlocker="));
        assertThrows(UnsupportedOperationException.class, () -> fields.put("x", "y"));
    }

    @Test
    void mapsControlFlowBoundaryAndLocalDominanceToStableWorkItems() {
        GpuIrCommonSubexpressionRewriteBlockerExplanation explanation =
                GpuIrCommonSubexpressionRewriteBlockerExplanation.from(policyWithSkippedCandidate(
                        GpuIrCommonSubexpressionSkipReason.CONTROL_FLOW_BOUNDARY,
                        GpuIrCommonSubexpressionDominanceStatus.REQUIRES_LOCAL_EXPRESSION_DOMINANCE
                ));

        assertEquals(List.of(
                "splitControlFlowRegionsBeforeCse",
                "addLocalExpressionDominanceProof",
                "addRuntimeEquivalenceEvidenceBeforeMutation"
        ), explanation.remainingWork());
        assertEquals("splitControlFlowRegionsBeforeCse", explanation.firstRemainingWork());
        assertTrue(explanation.summary().contains("addLocalExpressionDominanceProof"));
    }

    @Test
    void reportsReadyAndNonePoliciesWithoutFakeBlockers() {
        GpuIrCommonSubexpressionRewriteBlockerExplanation ready =
                GpuIrCommonSubexpressionRewriteBlockerExplanation.from(policyWithPlanOnly());
        GpuIrCommonSubexpressionRewriteBlockerExplanation none =
                GpuIrCommonSubexpressionRewriteBlockerExplanation.from(policyWithNoWork());

        assertFalse(ready.blocked());
        assertEquals("ready", ready.verdict());
        assertEquals("none", ready.firstBlockerFamily());
        assertEquals("none", ready.firstRemainingWork());
        assertEquals("", ready.artifactFields().get("cseRewriteBlockerFamilies"));

        assertFalse(none.blocked());
        assertEquals("none", none.verdict());
        assertEquals("none", none.artifactFields().get("cseRewriteBlockerFirstFamily"));
    }

    @Test
    void rejectsInvalidExplanationMetadata() {
        assertThrows(IllegalArgumentException.class, () -> new GpuIrCommonSubexpressionRewriteBlockerExplanation(
                "",
                GpuIrCommonSubexpressionRewriteReadiness.NONE,
                0,
                Optional.empty(),
                List.of(),
                List.of()
        ));
        assertThrows(IllegalArgumentException.class, () -> new GpuIrCommonSubexpressionRewriteBlockerExplanation(
                "kernel",
                GpuIrCommonSubexpressionRewriteReadiness.NONE,
                -1,
                Optional.empty(),
                List.of(),
                List.of()
        ));
        assertThrows(IllegalArgumentException.class, () -> new GpuIrCommonSubexpressionRewriteBlockerExplanation(
                "kernel",
                GpuIrCommonSubexpressionRewriteReadiness.NONE,
                0,
                Optional.of(skippedDiagnostic(
                        GpuIrCommonSubexpressionSkipReason.NOT_LOCAL_REUSE,
                        GpuIrCommonSubexpressionDominanceStatus.TOP_LEVEL_DOWNSTREAM_REPLACEMENTS
                )),
                List.of(),
                List.of()
        ));
        assertThrows(IllegalArgumentException.class, () -> GpuIrCommonSubexpressionRewriteBlockerExplanation
                .from(policyWithNoWork())
                .artifactFields(""));
    }

    private GpuIrCommonSubexpressionRewritePolicy policyWithSkippedCandidate(
            GpuIrCommonSubexpressionSkipReason reason,
            GpuIrCommonSubexpressionDominanceStatus dominanceStatus
    ) {
        return GpuIrCommonSubexpressionRewritePolicy.from(
                "kernel",
                new GpuIrCommonSubexpressionRewritePlanReport(
                        List.of(new GpuIrCommonSubexpressionRewritePlan(
                                "__gpu_cse_0",
                                "binary(+,var(x),var(y))",
                                1,
                                0,
                                "stmt[0].value",
                                List.of("stmt[0].value", "stmt[1].value")
                        )),
                        List.of(new GpuIrCommonSubexpressionSkippedCandidate(
                                new GpuIrCommonSubexpression(
                                        "call(sin,var(value))",
                                        2,
                                        List.of("stmt[1].initializer", "stmt[2].initializer")
                                ),
                                GpuIrCommonSubexpressionKind.LOCAL_REUSE,
                                GpuIrCommonSubexpressionScope.STRAIGHT_LINE,
                                reason,
                                dominanceStatus
                        ))
                )
        );
    }

    private GpuIrCommonSubexpressionRewritePolicy policyWithPlanOnly() {
        return GpuIrCommonSubexpressionRewritePolicy.from(
                "kernel",
                new GpuIrCommonSubexpressionRewritePlanReport(
                        List.of(new GpuIrCommonSubexpressionRewritePlan(
                                "__gpu_cse_0",
                                "binary(+,var(x),var(y))",
                                1,
                                0,
                                "stmt[0].value",
                                List.of("stmt[0].value", "stmt[1].value")
                        )),
                        List.of()
                )
        );
    }

    private GpuIrCommonSubexpressionRewritePolicy policyWithNoWork() {
        return GpuIrCommonSubexpressionRewritePolicy.from(
                "kernel",
                new GpuIrCommonSubexpressionRewritePlanReport(List.of(), List.of())
        );
    }

    private GpuIrCommonSubexpressionSkippedDiagnostic skippedDiagnostic(
            GpuIrCommonSubexpressionSkipReason reason,
            GpuIrCommonSubexpressionDominanceStatus dominanceStatus
    ) {
        return new GpuIrCommonSubexpressionSkippedDiagnostic(
                "call(sin,var(value))",
                2,
                List.of("stmt[1].initializer", "stmt[2].initializer"),
                GpuIrCommonSubexpressionKind.LOCAL_REUSE,
                GpuIrCommonSubexpressionScope.STRAIGHT_LINE,
                reason,
                dominanceStatus
        );
    }
}
