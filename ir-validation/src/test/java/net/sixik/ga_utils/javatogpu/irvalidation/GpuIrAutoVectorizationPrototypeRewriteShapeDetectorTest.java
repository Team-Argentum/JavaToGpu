package net.sixik.ga_utils.javatogpu.irvalidation;

import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrArrayAccess;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrBinary;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrLiteral;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrUnary;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrVariableRef;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrAssignment;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrForLoop;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrStatement;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrVariableDeclaration;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuIrAutoVectorizationPrototypeRewriteShapeDetectorTest {
    private final GpuIrAutoVectorizationPrototypeRewriteShapeDetector detector =
            new GpuIrAutoVectorizationPrototypeRewriteShapeDetector();

    @Test
    void detectsLaneCopyPrototypeShape() {
        GpuIrAssignment assignment = laneAssignment(
                "out",
                new GpuIrArrayAccess("left", new GpuIrVariableRef("i"))
        );

        GpuIrAutoVectorizationPrototypeRewriteShape shape = detector.detect(
                fixedWidthLoop(List.of(assignment)),
                candidate(1, List.of("out"), List.of("left")),
                insertion(3)
        );

        assertEquals("stmt[3]", shape.loopLocation());
        assertEquals(3, shape.statementIndex());
        assertEquals("Int4", shape.vectorType());
        assertEquals(0, shape.startInclusive());
        assertEquals(4, shape.endExclusive());
        assertEquals(4, shape.laneCount());
        assertEquals("i", shape.inductionVariable());
        assertEquals(List.of("out"), shape.targetArrays());
        assertEquals(List.of("left"), shape.sourceArrays());
        assertEquals(GpuIrAutoVectorizationPrototypeExpressionKind.LANE_COPY, shape.expressionKind());
        assertEquals("laneCopy", shape.expressionKindArtifactValue());
        assertEquals("", shape.binaryOperator());
        assertSame(assignment, shape.assignment());
    }

    @Test
    void detectsSimpleBinaryPrototypeShape() {
        GpuIrAssignment assignment = laneAssignment(
                "out",
                new GpuIrBinary("+",
                        new GpuIrArrayAccess("left", new GpuIrVariableRef("i")),
                        new GpuIrArrayAccess("right", new GpuIrVariableRef("i"))
                )
        );

        GpuIrAutoVectorizationPrototypeRewriteShape shape = detector.detect(
                fixedWidthLoop(List.of(assignment)),
                candidate(1, List.of("out"), List.of("left", "right")),
                insertion(0)
        );

        assertEquals("Int4", shape.vectorType());
        assertEquals(List.of("out"), shape.targetArrays());
        assertEquals(List.of("left", "right"), shape.sourceArrays());
        assertEquals(GpuIrAutoVectorizationPrototypeExpressionKind.BINARY_LANE_OP, shape.expressionKind());
        assertEquals("binaryLaneOp", shape.expressionKindArtifactValue());
        assertEquals("+", shape.binaryOperator());
        assertSame(assignment, shape.assignment());
    }

    @Test
    void detectsLaneLiteralBinaryPrototypeShape() {
        GpuIrAssignment assignment = laneAssignment(
                "out",
                new GpuIrBinary("+",
                        new GpuIrArrayAccess("left", new GpuIrVariableRef("i")),
                        new GpuIrLiteral("5")
                )
        );

        GpuIrAutoVectorizationPrototypeRewriteShape shape = detector.detect(
                fixedWidthLoop(List.of(assignment)),
                candidate(1, List.of("out"), List.of("left")),
                insertion(0)
        );

        assertEquals(GpuIrAutoVectorizationPrototypeExpressionKind.LANE_LITERAL_BINARY_OP, shape.expressionKind());
        assertEquals("laneLiteralBinaryOp", shape.expressionKindArtifactValue());
        assertEquals("laneLiteralBinaryOp", shape.expressionKindSummary());
        assertEquals("+", shape.binaryOperator());
        assertSame(assignment, shape.assignment());
    }

    @Test
    void rejectsMultiAssignmentPrototypeShape() {
        GpuIrAssignment firstAssignment = laneAssignment(
                "out",
                new GpuIrArrayAccess("left", new GpuIrVariableRef("i"))
        );
        GpuIrAssignment secondAssignment = laneAssignment(
                "mask",
                new GpuIrArrayAccess("right", new GpuIrVariableRef("i"))
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> detector.detect(
                        fixedWidthLoop(List.of(firstAssignment, secondAssignment)),
                        candidate(2, List.of("out", "mask"), List.of("left", "right")),
                        insertion(0)
                )
        );

        assertTrue(exception.getMessage().contains("only supports one lane assignment"));
    }

    @Test
    void detectsLiteralLaneBinaryPrototypeShape() {
        GpuIrAssignment assignment = laneAssignment(
                "out",
                new GpuIrBinary("+",
                        new GpuIrLiteral("5"),
                        new GpuIrArrayAccess("left", new GpuIrVariableRef("i"))
                )
        );

        GpuIrAutoVectorizationPrototypeRewriteShape shape = detector.detect(
                fixedWidthLoop(List.of(assignment)),
                candidate(1, List.of("out"), List.of("left")),
                insertion(0)
        );

        assertEquals(GpuIrAutoVectorizationPrototypeExpressionKind.LANE_LITERAL_BINARY_OP, shape.expressionKind());
        assertEquals("+", shape.binaryOperator());
    }

    @Test
    void detectsUnaryLanePrototypeShape() {
        GpuIrAssignment assignment = laneAssignment(
                "out",
                new GpuIrUnary("-", new GpuIrArrayAccess("left", new GpuIrVariableRef("i")))
        );

        GpuIrAutoVectorizationPrototypeRewriteShape shape = detector.detect(
                fixedWidthLoop(List.of(assignment)),
                candidate(1, List.of("out"), List.of("left")),
                insertion(0)
        );

        assertEquals(GpuIrAutoVectorizationPrototypeExpressionKind.UNARY_LANE_OP, shape.expressionKind());
        assertEquals("unaryLaneOp", shape.expressionKindArtifactValue());
        assertEquals("", shape.binaryOperator());
        assertEquals("-", shape.unaryOperator());
        assertSame(assignment, shape.assignment());
    }

    @Test
    void rejectsUnsupportedLaneValuePrototypeShape() {
        GpuIrAssignment assignment = laneAssignment(
                "out",
                new GpuIrBinary("+",
                        new GpuIrLiteral("1"),
                        new GpuIrLiteral("2")
                )
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> detector.detect(
                        fixedWidthLoop(List.of(assignment)),
                        candidate(1, List.of("out"), List.of("left")),
                        insertion(0)
                )
        );

        assertTrue(exception.getMessage().contains("only supports lane-copy, unary lane, or simple binary lane values"));
    }

    @Test
    void rejectsNonInductionTargetPrototypeShape() {
        GpuIrAssignment assignment = new GpuIrAssignment(
                new GpuIrArrayAccess("out", new GpuIrVariableRef("j")),
                new GpuIrArrayAccess("left", new GpuIrVariableRef("i"))
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> detector.detect(
                        fixedWidthLoop(List.of(assignment)),
                        candidate(1, List.of("out"), List.of("left")),
                        insertion(0)
                )
        );

        assertTrue(exception.getMessage().contains("requires targetArray[induction]"));
    }

    private GpuIrForLoop fixedWidthLoop(List<GpuIrStatement> body) {
        return new GpuIrForLoop(
                new GpuIrVariableDeclaration("int", "i", new GpuIrLiteral("0")),
                new GpuIrBinary("<", new GpuIrVariableRef("i"), new GpuIrLiteral("4")),
                new GpuIrAssignment(
                        new GpuIrVariableRef("i"),
                        new GpuIrBinary("+", new GpuIrVariableRef("i"), new GpuIrLiteral("1"))
                ),
                body
        );
    }

    private GpuIrAssignment laneAssignment(String arrayName, net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrExpression value) {
        return new GpuIrAssignment(
                new GpuIrArrayAccess(arrayName, new GpuIrVariableRef("i")),
                value
        );
    }

    private GpuIrAutoVectorizationRewriteCandidatePreview candidate(
            int assignmentCount,
            List<String> targetArrays,
            List<String> sourceArrays
    ) {
        return new GpuIrAutoVectorizationRewriteCandidatePreview(
                "stmt[3]",
                "i",
                0,
                4,
                4,
                assignmentCount,
                10,
                "x4",
                "int",
                "int4",
                List.of("write out[i=0..3]"),
                List.of("read left[i=0..3]"),
                List.of(),
                targetArrays,
                sourceArrays
        );
    }

    private GpuIrAutoVectorizationResolvedInsertionOperation insertion(int statementIndex) {
        return new GpuIrAutoVectorizationResolvedInsertionOperation(
                "stmt[3]",
                statementIndex,
                "int4",
                0,
                4,
                1,
                1,
                List.of("read left[i=0..3]")
        );
    }
}
