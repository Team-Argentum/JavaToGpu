package net.sixik.ga_utils.javatogpu.irvalidation;

import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrArrayAccess;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrBinary;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrExpression;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrLiteral;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrUnary;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrVariableRef;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuIrAutoVectorizationPrototypeLaneExpressionRewriterTest {
    private final GpuIrAutoVectorizationPrototypeLaneExpressionRewriter rewriter =
            new GpuIrAutoVectorizationPrototypeLaneExpressionRewriter(
                    new GpuIrAutoVectorizationPrototypeRewriteShapeDetector()
            );

    @Test
    void rewritesLaneCopyArrayAccessToFixedLaneIndex() {
        GpuIrExpression rewritten = rewriter.rewrite(
                new GpuIrArrayAccess("left", new GpuIrVariableRef("i")),
                "i",
                3
        );

        GpuIrArrayAccess arrayAccess = (GpuIrArrayAccess) rewritten;
        assertEquals("left", arrayAccess.arrayName());
        assertEquals("3", ((GpuIrLiteral) arrayAccess.index()).sourceText());
    }

    @Test
    void rewritesBinaryLaneExpressionRecursively() {
        GpuIrExpression rewritten = rewriter.rewrite(
                new GpuIrBinary("^",
                        new GpuIrArrayAccess("left", new GpuIrVariableRef("i")),
                        new GpuIrArrayAccess("right", new GpuIrVariableRef("i"))
                ),
                "i",
                2
        );

        GpuIrBinary binary = (GpuIrBinary) rewritten;
        GpuIrArrayAccess left = (GpuIrArrayAccess) binary.left();
        GpuIrArrayAccess right = (GpuIrArrayAccess) binary.right();
        assertEquals("^", binary.operator());
        assertEquals("left", left.arrayName());
        assertEquals("2", ((GpuIrLiteral) left.index()).sourceText());
        assertEquals("right", right.arrayName());
        assertEquals("2", ((GpuIrLiteral) right.index()).sourceText());
    }

    @Test
    void rewritesLaneLiteralBinaryExpression() {
        GpuIrExpression rewritten = rewriter.rewrite(
                new GpuIrBinary("+",
                        new GpuIrArrayAccess("left", new GpuIrVariableRef("i")),
                        new GpuIrLiteral("7")
                ),
                "i",
                1
        );

        GpuIrBinary binary = (GpuIrBinary) rewritten;
        GpuIrArrayAccess left = (GpuIrArrayAccess) binary.left();
        GpuIrLiteral right = (GpuIrLiteral) binary.right();
        assertEquals("+", binary.operator());
        assertEquals("left", left.arrayName());
        assertEquals("1", ((GpuIrLiteral) left.index()).sourceText());
        assertEquals("7", right.sourceText());
    }

    @Test
    void rewritesUnaryLaneExpression() {
        GpuIrExpression rewritten = rewriter.rewrite(
                new GpuIrUnary("-", new GpuIrArrayAccess("left", new GpuIrVariableRef("i"))),
                "i",
                2
        );

        GpuIrUnary unary = (GpuIrUnary) rewritten;
        GpuIrArrayAccess operand = (GpuIrArrayAccess) unary.operand();
        assertEquals("-", unary.operator());
        assertEquals("left", operand.arrayName());
        assertEquals("2", ((GpuIrLiteral) operand.index()).sourceText());
    }

    @Test
    void rejectsUnsupportedLaneExpression() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> rewriter.rewrite(
                        new GpuIrVariableRef("value"),
                        "i",
                        0
                )
        );

        assertTrue(exception.getMessage().contains("Unsupported prototype lane expression"));
    }
}
