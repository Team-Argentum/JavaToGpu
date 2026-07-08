package net.sixik.ga_utils.javatogpu.irvalidation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuIrCommonSubexpressionLocationTest {
    @Test
    void parsesTopLevelStatementLocations() {
        GpuIrCommonSubexpressionLocation location = GpuIrCommonSubexpressionLocation.parse("stmt[12].initializer.left");

        assertEquals("stmt[12].initializer.left", location.rawLocation());
        assertEquals(12, location.topLevelStatementIndex().orElseThrow());
        assertEquals(java.util.Optional.of(12), GpuIrCommonSubexpressionLocation.topLevelStatementIndexOf("stmt[12].initializer.left"));
        assertEquals(12, GpuIrCommonSubexpressionLocation.requireTopLevelStatementIndex("stmt[12].initializer.left", "test failure"));
        assertFalse(location.controlFlowScoped());
    }

    @Test
    void requiredTopLevelStatementIndexRejectsUnsupportedLocations() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> GpuIrCommonSubexpressionLocation.requireTopLevelStatementIndex("helper[0].initializer", "dry-run failed")
        );

        assertTrue(exception.getMessage().contains("dry-run failed"));
        assertTrue(exception.getMessage().contains("unsupported top-level statement location helper[0].initializer"));
    }

    @Test
    void marksNestedControlFlowLocations() {
        assertTrue(GpuIrCommonSubexpressionLocation.parse("stmt[0].then.stmt[0].initializer").controlFlowScoped());
        assertTrue(GpuIrCommonSubexpressionLocation.parse("stmt[0].body.stmt[1].value").controlFlowScoped());
        assertTrue(GpuIrCommonSubexpressionLocation.parse("stmt[0].case[0].stmt[0].value").controlFlowScoped());
        assertTrue(GpuIrCommonSubexpressionLocation.parse("stmt[0].condition").controlFlowScoped());
        assertTrue(GpuIrCommonSubexpressionLocation.parse("stmt[0].initializer.stmt[0].value").controlFlowScoped());
    }

    @Test
    void leavesNonStatementLocationsWithoutTopLevelIndex() {
        GpuIrCommonSubexpressionLocation location = GpuIrCommonSubexpressionLocation.parse("helper[0].initializer");

        assertTrue(location.topLevelStatementIndex().isEmpty());
        assertFalse(location.controlFlowScoped());
    }
}
