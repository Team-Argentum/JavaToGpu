package net.sixik.ga_utils.javatogpu.irvalidation;

import org.junit.jupiter.api.Test;

import java.util.List;

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
    }

    @Test
    void rejectsCandidatesWithoutTopLevelAnchors() {
        GpuIrCommonSubexpression candidate = new GpuIrCommonSubexpression(
                "binary(+,var(x),var(y))",
                2,
                List.of("helper[0].initializer", "stmt[1].value")
        );

        assertFalse(guard.firstOccurrenceDominatesReplacements(candidate));
    }

    @Test
    void rejectsCandidatesWhoseLocationsMoveBackwards() {
        GpuIrCommonSubexpression candidate = new GpuIrCommonSubexpression(
                "binary(+,var(x),var(y))",
                2,
                List.of("stmt[2].initializer", "stmt[1].value")
        );

        assertFalse(guard.firstOccurrenceDominatesReplacements(candidate));
    }
}
