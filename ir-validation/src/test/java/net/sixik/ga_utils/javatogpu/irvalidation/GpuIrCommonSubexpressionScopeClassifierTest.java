package net.sixik.ga_utils.javatogpu.irvalidation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuIrCommonSubexpressionScopeClassifierTest {
    private final GpuIrCommonSubexpressionScopeClassifier classifier = new GpuIrCommonSubexpressionScopeClassifier();

    @Test
    void classifiesTopLevelStatementLocationsAsStraightLine() {
        GpuIrCommonSubexpression candidate = new GpuIrCommonSubexpression(
                "binary(+,var(a),var(b))",
                2,
                List.of("stmt[0].initializer", "stmt[1].value")
        );

        assertEquals(GpuIrCommonSubexpressionScope.STRAIGHT_LINE, classifier.classify(candidate));
        assertTrue(classifier.isStraightLine(candidate));
    }

    @Test
    void classifiesBranchLoopAndSwitchLocationsAsControlFlowBoundary() {
        List<GpuIrCommonSubexpression> candidates = List.of(
                new GpuIrCommonSubexpression("binary(+,var(a),var(b))", 2, List.of("stmt[0].then.stmt[0].initializer", "stmt[1].initializer")),
                new GpuIrCommonSubexpression("binary(-,var(a),var(b))", 2, List.of("stmt[0].body.stmt[0].value", "stmt[1].value")),
                new GpuIrCommonSubexpression("binary(*,var(a),var(b))", 2, List.of("stmt[0].case[0].stmt[0].value", "stmt[1].value")),
                new GpuIrCommonSubexpression("binary(/,var(a),var(b))", 2, List.of("stmt[0].condition", "stmt[1].value"))
        );

        for (GpuIrCommonSubexpression candidate : candidates) {
            assertEquals(GpuIrCommonSubexpressionScope.CONTROL_FLOW_BOUNDARY, classifier.classify(candidate));
            assertFalse(classifier.isStraightLine(candidate));
        }
    }
}
