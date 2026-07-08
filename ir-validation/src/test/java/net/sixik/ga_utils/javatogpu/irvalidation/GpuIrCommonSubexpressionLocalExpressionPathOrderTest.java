package net.sixik.ga_utils.javatogpu.irvalidation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuIrCommonSubexpressionLocalExpressionPathOrderTest {
    private final GpuIrCommonSubexpressionLocalExpressionPathOrder order =
            new GpuIrCommonSubexpressionLocalExpressionPathOrder();

    @Test
    void comparesBinarySiblingPathsInScannerOrder() {
        assertTrue(order.appearsBefore("stmt[0].value.left", "stmt[0].value.right"));
        assertFalse(order.appearsBefore("stmt[0].value.right", "stmt[0].value.left"));
    }

    @Test
    void comparesReceiverBeforeArgumentPaths() {
        assertTrue(order.appearsBefore("stmt[0].value.receiver", "stmt[0].value.arg[0]"));
        assertFalse(order.appearsBefore("stmt[0].value.arg[0]", "stmt[0].value.receiver"));
    }

    @Test
    void comparesArgumentIndexesNumerically() {
        assertTrue(order.appearsBefore("stmt[0].value.arg[2]", "stmt[0].value.arg[10]"));
        assertFalse(order.appearsBefore("stmt[0].value.arg[10]", "stmt[0].value.arg[2]"));
    }

    @Test
    void comparesTernaryBranchesInScannerOrder() {
        assertTrue(order.appearsBefore("stmt[0].value.condition", "stmt[0].value.true"));
        assertTrue(order.appearsBefore("stmt[0].value.true", "stmt[0].value.false"));
        assertFalse(order.appearsBefore("stmt[0].value.false", "stmt[0].value.true"));
    }

    @Test
    void extractsExpressionPathFromSupportedTopLevelLocations() {
        assertEquals(".initializer.arg[1]", order.expressionPath("stmt[0].initializer.arg[1]"));
        assertEquals(".value.receiver", order.expressionPath("stmt[1].value.receiver"));
        assertEquals(".return.condition", order.expressionPath("stmt[2].return.condition"));
    }
}
