package net.sixik.ga_utils.javatogpu.irvalidation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuIrAutoVectorizationPrototypeAppliedRewriteTest {
    @Test
    void acceptsLaneCopyAppliedRewriteMetadata() {
        GpuIrAutoVectorizationPrototypeAppliedRewrite rewrite = new GpuIrAutoVectorizationPrototypeAppliedRewrite(
                "stmt[0]",
                0,
                "Int4",
                0,
                4,
                List.of("out"),
                List.of("left"),
                GpuIrAutoVectorizationPrototypeExpressionKind.LANE_COPY,
                "",
                ""
        );

        assertEquals(4, rewrite.laneCount());
        assertEquals(GpuIrAutoVectorizationPrototypeExpressionKind.LANE_COPY, rewrite.expressionKind());
        assertEquals("laneCopy", rewrite.expressionKindArtifactValue());
        assertEquals("laneCopy", rewrite.expressionKindSummary());
        assertEquals("", rewrite.binaryOperator());
        assertEquals("", rewrite.unaryOperator());
        assertTrue(rewrite.summary().contains("expressionKind=laneCopy"));
        assertTrue(rewrite.summary().contains("targetArrays=[out]"));
        assertTrue(rewrite.summary().contains("sourceArrays=[left]"));
    }

    @Test
    void acceptsBinaryAppliedRewriteMetadata() {
        GpuIrAutoVectorizationPrototypeAppliedRewrite rewrite = new GpuIrAutoVectorizationPrototypeAppliedRewrite(
                "stmt[2]",
                2,
                "Int4",
                0,
                4,
                List.of("out"),
                List.of("left", "right"),
                GpuIrAutoVectorizationPrototypeExpressionKind.BINARY_LANE_OP,
                "^",
                ""
        );

        assertEquals(GpuIrAutoVectorizationPrototypeExpressionKind.BINARY_LANE_OP, rewrite.expressionKind());
        assertEquals("binaryLaneOp", rewrite.expressionKindArtifactValue());
        assertEquals("^", rewrite.binaryOperator());
        assertEquals("", rewrite.unaryOperator());
        assertTrue(rewrite.summary().contains("expressionKind=binaryLaneOp"));
        assertTrue(rewrite.summary().contains("binaryOperator=^"));
    }

    @Test
    void acceptsUnaryAppliedRewriteMetadata() {
        GpuIrAutoVectorizationPrototypeAppliedRewrite rewrite = new GpuIrAutoVectorizationPrototypeAppliedRewrite(
                "stmt[2]",
                2,
                "Int4",
                0,
                4,
                List.of("out"),
                List.of("left"),
                GpuIrAutoVectorizationPrototypeExpressionKind.UNARY_LANE_OP,
                "",
                "^"
        );

        assertEquals(GpuIrAutoVectorizationPrototypeExpressionKind.UNARY_LANE_OP, rewrite.expressionKind());
        assertEquals("unaryLaneOp", rewrite.expressionKindArtifactValue());
        assertEquals("", rewrite.binaryOperator());
        assertEquals("^", rewrite.unaryOperator());
        assertTrue(rewrite.summary().contains("expressionKind=unaryLaneOp"));
        assertTrue(rewrite.summary().contains("unaryOperator=^"));
    }

    @Test
    void acceptsLaneLiteralBinaryAppliedRewriteMetadata() {
        GpuIrAutoVectorizationPrototypeAppliedRewrite rewrite = new GpuIrAutoVectorizationPrototypeAppliedRewrite(
                "stmt[2]",
                2,
                "Int4",
                0,
                4,
                List.of("out"),
                List.of("left"),
                GpuIrAutoVectorizationPrototypeExpressionKind.LANE_LITERAL_BINARY_OP,
                "+",
                ""
        );

        assertEquals(GpuIrAutoVectorizationPrototypeExpressionKind.LANE_LITERAL_BINARY_OP, rewrite.expressionKind());
        assertEquals("laneLiteralBinaryOp", rewrite.expressionKindArtifactValue());
        assertEquals("+", rewrite.binaryOperator());
        assertEquals("", rewrite.unaryOperator());
        assertTrue(rewrite.summary().contains("expressionKind=laneLiteralBinaryOp"));
        assertTrue(rewrite.summary().contains("binaryOperator=+"));
    }

    @Test
    void rejectsUnaryAppliedRewriteWithoutOperator() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new GpuIrAutoVectorizationPrototypeAppliedRewrite(
                        "stmt[0]",
                        0,
                        "Int4",
                        0,
                        4,
                        List.of("out"),
                        List.of("left"),
                        GpuIrAutoVectorizationPrototypeExpressionKind.UNARY_LANE_OP,
                        "",
                        ""
                )
        );

        assertTrue(exception.getMessage().contains("unaryOperator must be set"));
    }

    @Test
    void rejectsLaneCopyAppliedRewriteWithUnaryOperator() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new GpuIrAutoVectorizationPrototypeAppliedRewrite(
                        "stmt[0]",
                        0,
                        "Int4",
                        0,
                        4,
                        List.of("out"),
                        List.of("left"),
                        GpuIrAutoVectorizationPrototypeExpressionKind.LANE_COPY,
                        "",
                        "-"
                )
        );

        assertTrue(exception.getMessage().contains("unaryOperator must be blank"));
    }

    @Test
    void rejectsUnaryAppliedRewriteWithBinaryOperator() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new GpuIrAutoVectorizationPrototypeAppliedRewrite(
                        "stmt[0]",
                        0,
                        "Int4",
                        0,
                        4,
                        List.of("out"),
                        List.of("left"),
                        GpuIrAutoVectorizationPrototypeExpressionKind.UNARY_LANE_OP,
                        "+",
                        "-"
                )
        );

        assertTrue(exception.getMessage().contains("binaryOperator must be blank"));
    }

    @Test
    void rejectsBinaryAppliedRewriteWithUnaryOperator() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new GpuIrAutoVectorizationPrototypeAppliedRewrite(
                        "stmt[0]",
                        0,
                        "Int4",
                        0,
                        4,
                        List.of("out"),
                        List.of("left", "right"),
                        GpuIrAutoVectorizationPrototypeExpressionKind.BINARY_LANE_OP,
                        "+",
                        "-"
                )
        );

        assertTrue(exception.getMessage().contains("unaryOperator must be blank"));
    }

    @Test
    void rejectsBinaryAppliedRewriteWithoutOperator() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new GpuIrAutoVectorizationPrototypeAppliedRewrite(
                        "stmt[0]",
                        0,
                        "Int4",
                        0,
                        4,
                        List.of("out"),
                        List.of("left", "right"),
                        GpuIrAutoVectorizationPrototypeExpressionKind.BINARY_LANE_OP,
                        "",
                        ""
                )
        );

        assertTrue(exception.getMessage().contains("binaryOperator must be set"));
    }

    @Test
    void rejectsLaneCopyAppliedRewriteWithOperator() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new GpuIrAutoVectorizationPrototypeAppliedRewrite(
                        "stmt[0]",
                        0,
                        "Int4",
                        0,
                        4,
                        List.of("out"),
                        List.of("left"),
                        GpuIrAutoVectorizationPrototypeExpressionKind.LANE_COPY,
                        "+"
                        , ""
                )
        );

        assertTrue(exception.getMessage().contains("binaryOperator must be blank"));
    }
}
