package net.sixik.ga_utils.javatogpu.irvalidation;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuIrCommonSubexpressionRewritePolicyTest {
    @Test
    void reportsReadyWhenPlansExistWithoutSkippedCandidates() {
        GpuIrCommonSubexpressionRewritePolicy policy = GpuIrCommonSubexpressionRewritePolicy.from(
                "kernel",
                new GpuIrCommonSubexpressionRewritePlanReport(List.of(new GpuIrCommonSubexpressionRewritePlan(
                        "__gpu_cse_0",
                        "binary(+,var(x),var(y))",
                        1,
                        0,
                        "stmt[0].value",
                        List.of("stmt[0].value", "stmt[1].value")
                )), List.of())
        );

        Map<String, String> fields = policy.artifactFields();

        assertTrue(policy.canRewrite());
        assertEquals(GpuIrCommonSubexpressionRewriteReadiness.READY, policy.readiness());
        assertEquals(1, policy.planCount());
        assertEquals(1, policy.insertionCount());
        assertEquals(1, policy.replacementCount());
        assertEquals("true", fields.get("cseRewritePolicyCanRewrite"));
        assertEquals("ready", fields.get("cseRewritePolicyReadiness"));
        assertEquals("{}", fields.get("cseRewritePolicyBlockingSkipReasonCounts"));
        assertTrue(policy.summary().contains("canRewrite=true"));
    }

    @Test
    void reportsNoneWhenNoPlansOrSkippedCandidatesExist() {
        GpuIrCommonSubexpressionRewritePolicy policy = GpuIrCommonSubexpressionRewritePolicy.from(
                "kernel",
                new GpuIrCommonSubexpressionRewritePlanReport(List.of(), List.of())
        );

        assertFalse(policy.canRewrite());
        assertEquals(GpuIrCommonSubexpressionRewriteReadiness.NONE, policy.readiness());
        assertEquals("none", policy.artifactFields().get("cseRewritePolicyReadiness"));
    }

    @Test
    void blocksWhenSkippedCandidatesExistEvenWithPlans() {
        GpuIrCommonSubexpressionRewritePolicy policy = GpuIrCommonSubexpressionRewritePolicy.from(
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
                        List.of(skippedCandidate())
                )
        );

        Map<String, String> fields = policy.artifactFields();

        assertFalse(policy.canRewrite());
        assertEquals(GpuIrCommonSubexpressionRewriteReadiness.BLOCKED_BY_SKIPPED_CANDIDATE, policy.readiness());
        assertEquals(1, policy.blockingSkippedCandidateCount());
        assertTrue(policy.firstBlockingSkippedCandidate().isPresent());
        assertEquals("blockedBySkippedCandidate", fields.get("cseRewritePolicyReadiness"));
        assertEquals("1", fields.get("cseRewritePolicyBlockingSkippedCandidates"));
        assertEquals("{NO_DOMINATING_FIRST_OCCURRENCE=1}", fields.get("cseRewritePolicyBlockingSkipReasonCounts"));
        assertEquals("{requiresLocalExpressionDominance=1}", fields.get("cseRewritePolicyBlockingDominanceStatusCounts"));
        assertEquals("NO_DOMINATING_FIRST_OCCURRENCE", fields.get("cseRewritePolicyFirstBlockingSkippedReason"));
        assertEquals("requiresLocalExpressionDominance", fields.get("cseRewritePolicyFirstBlockingSkippedDominanceStatus"));
        assertTrue(fields.get("cseRewritePolicyFirstBlockingSkippedSummary").contains("requiresLocalExpressionDominance"));
        assertEquals("blocked", fields.get("cseRewritePolicyBlockerExplanationVerdict"));
        assertEquals("skipReason.NO_DOMINATING_FIRST_OCCURRENCE", fields.get("cseRewritePolicyBlockerExplanationFirstFamily"));
        assertEquals(
                "proveDominatingAnchorForAllReplacements",
                fields.get("cseRewritePolicyBlockerExplanationFirstRemainingWork")
        );
        assertTrue(fields.get("cseRewritePolicyBlockerExplanationFirstHint").contains("addLocalExpressionDominanceProof"));
    }

    @Test
    void rejectsInvalidPolicyMetadata() {
        assertThrows(IllegalArgumentException.class, () -> new GpuIrCommonSubexpressionRewritePolicy(
                "",
                0,
                0,
                0,
                List.of()
        ));
        assertThrows(IllegalArgumentException.class, () -> new GpuIrCommonSubexpressionRewritePolicy(
                "kernel",
                0,
                1,
                0,
                List.of()
        ));
        assertThrows(IllegalArgumentException.class, () -> GpuIrCommonSubexpressionRewritePolicy.from(
                "kernel",
                new GpuIrCommonSubexpressionRewritePlanReport(List.of(), List.of())
        ).artifactFields(""));
    }

    private GpuIrCommonSubexpressionSkippedCandidate skippedCandidate() {
        return new GpuIrCommonSubexpressionSkippedCandidate(
                new GpuIrCommonSubexpression(
                        "binary(*,var(x),var(y))",
                        2,
                        List.of("stmt[0].value.right", "stmt[0].value.left")
                ),
                GpuIrCommonSubexpressionKind.LOCAL_REUSE,
                GpuIrCommonSubexpressionScope.STRAIGHT_LINE,
                GpuIrCommonSubexpressionSkipReason.NO_DOMINATING_FIRST_OCCURRENCE,
                GpuIrCommonSubexpressionDominanceStatus.REQUIRES_LOCAL_EXPRESSION_DOMINANCE
        );
    }
}
