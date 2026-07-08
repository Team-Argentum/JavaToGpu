package net.sixik.ga_utils.javatogpu.irvalidation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuIrCommonSubexpressionDominanceGuardTest {
    private final GpuIrCommonSubexpressionDominanceGuard guard = new GpuIrCommonSubexpressionDominanceGuard();

    @Test
    void acceptsCandidatesWithOrderedTopLevelLocations() {
        GpuIrCommonSubexpression candidate = new GpuIrCommonSubexpression(
                "binary(+,var(x),var(y))",
                2,
                List.of("stmt[0].initializer", "stmt[1].value")
        );

        assertTrue(guard.firstOccurrenceDominatesReplacements(candidate));
        assertEquals(0, guard.firstDominatingStatementIndex(candidate).orElseThrow());
        assertEquals(GpuIrCommonSubexpressionDominanceStatus.TOP_LEVEL_DOWNSTREAM_REPLACEMENTS, guard.status(candidate));
    }

    @Test
    void rejectsCandidatesWithoutTopLevelAnchors() {
        GpuIrCommonSubexpression candidate = new GpuIrCommonSubexpression(
                "binary(+,var(x),var(y))",
                2,
                List.of("helper[0].initializer", "stmt[1].value")
        );

        assertFalse(guard.firstOccurrenceDominatesReplacements(candidate));
        assertEquals(GpuIrCommonSubexpressionDominanceStatus.MISSING_TOP_LEVEL_ANCHOR, guard.status(candidate));
    }

    @Test
    void rejectsCandidatesWhoseLocationsMoveBackwards() {
        GpuIrCommonSubexpression candidate = new GpuIrCommonSubexpression(
                "binary(+,var(x),var(y))",
                2,
                List.of("stmt[2].initializer", "stmt[1].value")
        );

        assertFalse(guard.firstOccurrenceDominatesReplacements(candidate));
        assertEquals(GpuIrCommonSubexpressionDominanceStatus.LOCATIONS_MOVE_BACKWARDS, guard.status(candidate));
    }

    @Test
    void acceptsSameStatementExpressionReplacementsWhenPathsMoveForward() {
        GpuIrCommonSubexpression candidate = new GpuIrCommonSubexpression(
                "binary(+,var(x),var(y))",
                2,
                List.of("stmt[0].value.left", "stmt[0].value.right")
        );

        assertTrue(guard.firstOccurrenceDominatesReplacements(candidate));
        assertEquals(0, guard.firstDominatingStatementIndex(candidate).orElseThrow());
        assertEquals(GpuIrCommonSubexpressionDominanceStatus.LOCAL_EXPRESSION_DOWNSTREAM_REPLACEMENTS, guard.status(candidate));
    }

    @Test
    void rejectsSameStatementExpressionReplacementsWhenPathsMoveBackwards() {
        GpuIrCommonSubexpression candidate = new GpuIrCommonSubexpression(
                "binary(+,var(x),var(y))",
                2,
                List.of("stmt[0].value.right", "stmt[0].value.left")
        );

        assertFalse(guard.firstOccurrenceDominatesReplacements(candidate));
        assertEquals(GpuIrCommonSubexpressionDominanceStatus.REQUIRES_LOCAL_EXPRESSION_DOMINANCE, guard.status(candidate));
    }

    @Test
    void comparesSameStatementArgumentIndexesNumerically() {
        GpuIrCommonSubexpression candidate = new GpuIrCommonSubexpression(
                "binary(+,var(x),var(y))",
                2,
                List.of("stmt[0].value.arg[2]", "stmt[0].value.arg[10]")
        );

        assertTrue(guard.firstOccurrenceDominatesReplacements(candidate));
        assertEquals(GpuIrCommonSubexpressionDominanceStatus.LOCAL_EXPRESSION_DOWNSTREAM_REPLACEMENTS, guard.status(candidate));
    }

    @Test
    void acceptsReceiverBeforeArgumentPathsUsingScannerOrder() {
        GpuIrCommonSubexpression candidate = new GpuIrCommonSubexpression(
                "binary(+,var(x),var(y))",
                2,
                List.of("stmt[0].value.receiver", "stmt[0].value.arg[0]")
        );

        assertTrue(guard.firstOccurrenceDominatesReplacements(candidate));
        assertEquals(GpuIrCommonSubexpressionDominanceStatus.LOCAL_EXPRESSION_DOWNSTREAM_REPLACEMENTS, guard.status(candidate));
    }

    @Test
    void rejectsArgumentBeforeReceiverPathsAsBackwards() {
        GpuIrCommonSubexpression candidate = new GpuIrCommonSubexpression(
                "binary(+,var(x),var(y))",
                2,
                List.of("stmt[0].value.arg[0]", "stmt[0].value.receiver")
        );

        assertFalse(guard.firstOccurrenceDominatesReplacements(candidate));
        assertEquals(GpuIrCommonSubexpressionDominanceStatus.REQUIRES_LOCAL_EXPRESSION_DOMINANCE, guard.status(candidate));
    }

    @Test
    void acceptsTernaryConditionTrueFalseScannerOrder() {
        GpuIrCommonSubexpression candidate = new GpuIrCommonSubexpression(
                "binary(+,var(x),var(y))",
                3,
                List.of("stmt[0].value.condition", "stmt[0].value.true", "stmt[0].value.false")
        );

        assertTrue(guard.firstOccurrenceDominatesReplacements(candidate));
        assertEquals(GpuIrCommonSubexpressionDominanceStatus.LOCAL_EXPRESSION_DOWNSTREAM_REPLACEMENTS, guard.status(candidate));
    }

    @Test
    void rejectsTernaryFalseBeforeTruePathsAsBackwards() {
        GpuIrCommonSubexpression candidate = new GpuIrCommonSubexpression(
                "binary(+,var(x),var(y))",
                2,
                List.of("stmt[0].value.false", "stmt[0].value.true")
        );

        assertFalse(guard.firstOccurrenceDominatesReplacements(candidate));
        assertEquals(GpuIrCommonSubexpressionDominanceStatus.REQUIRES_LOCAL_EXPRESSION_DOMINANCE, guard.status(candidate));
    }

    @Test
    void rejectsUnsupportedSameStatementTargetReplacements() {
        GpuIrCommonSubexpression candidate = new GpuIrCommonSubexpression(
                "binary(+,var(x),var(y))",
                2,
                List.of("stmt[0].target.index.left", "stmt[0].target.index.right")
        );

        assertFalse(guard.firstOccurrenceDominatesReplacements(candidate));
        assertEquals(GpuIrCommonSubexpressionDominanceStatus.REQUIRES_LOCAL_EXPRESSION_DOMINANCE, guard.status(candidate));
    }
}
