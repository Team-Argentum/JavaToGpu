package net.sixik.ga_utils.javatogpu.irvalidation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuIrAutoVectorizationPrototypeExpressionKindTest {
    @Test
    void exposesStableArtifactValuesAndSummaries() {
        assertEquals("laneCopy", GpuIrAutoVectorizationPrototypeExpressionKind.LANE_COPY.artifactValue());
        assertEquals("binaryLaneOp", GpuIrAutoVectorizationPrototypeExpressionKind.BINARY_LANE_OP.artifactValue());
        assertEquals("laneLiteralBinaryOp", GpuIrAutoVectorizationPrototypeExpressionKind.LANE_LITERAL_BINARY_OP.artifactValue());
        assertEquals("unaryLaneOp", GpuIrAutoVectorizationPrototypeExpressionKind.UNARY_LANE_OP.artifactValue());

        assertEquals("laneCopy", GpuIrAutoVectorizationPrototypeExpressionKind.LANE_COPY.summary());
        assertEquals("binaryLaneOp", GpuIrAutoVectorizationPrototypeExpressionKind.BINARY_LANE_OP.summary());
        assertEquals("laneLiteralBinaryOp", GpuIrAutoVectorizationPrototypeExpressionKind.LANE_LITERAL_BINARY_OP.summary());
        assertEquals("unaryLaneOp", GpuIrAutoVectorizationPrototypeExpressionKind.UNARY_LANE_OP.summary());
    }

    @Test
    void marksOnlyBinaryFamiliesAsRequiringAnOperator() {
        assertFalse(GpuIrAutoVectorizationPrototypeExpressionKind.LANE_COPY.requiresBinaryOperator());
        assertTrue(GpuIrAutoVectorizationPrototypeExpressionKind.BINARY_LANE_OP.requiresBinaryOperator());
        assertTrue(GpuIrAutoVectorizationPrototypeExpressionKind.LANE_LITERAL_BINARY_OP.requiresBinaryOperator());
        assertFalse(GpuIrAutoVectorizationPrototypeExpressionKind.UNARY_LANE_OP.requiresBinaryOperator());
    }
}
